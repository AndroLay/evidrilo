using System.Text.Json;
using System.Text.Json.Serialization;
using Evidrilo.Api.Auth;
using Evidrilo.Api.Common;

namespace Evidrilo.Api.Authorization;

public static class MembershipEndpoints
{
    private static readonly JsonSerializerOptions RequestJsonOptions = new(JsonSerializerDefaults.Web)
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        UnmappedMemberHandling = JsonUnmappedMemberHandling.Disallow,
    };

    static MembershipEndpoints()
    {
        RequestJsonOptions.Converters.Add(new JsonStringEnumConverter(JsonNamingPolicy.SnakeCaseLower));
    }

    public static IEndpointRouteBuilder MapMembershipEndpoints(this IEndpointRouteBuilder endpoints)
    {
        endpoints.MapPost(
            "/v1/organizations/{organizationId:guid}/members",
            async (
                HttpContext context,
                Guid organizationId,
                IMembershipStore store,
                CancellationToken cancellationToken) =>
            {
                if (!TryGetVerifiedAccount(context, out var accountId, out var failure)) return failure!;
                var payload = await RequestJsonReader.ReadAsync(
                    context.Request,
                    "INVALID_MEMBERSHIP_COMMAND",
                    "The membership command is invalid.",
                    cancellationToken);
                MembershipCommand? request;
                try
                {
                    request = payload.Deserialize<MembershipCommand>(RequestJsonOptions);
                }
                catch (JsonException)
                {
                    return InvalidCommand(context);
                }

                var validationCode = MembershipCommandValidator.Validate(request);
                if (validationCode is not null) return InvalidCommand(context);

                var result = await store.GrantAsync(accountId, organizationId, request!, cancellationToken);
                return Results.Ok(ToResponse(context, result));
            })
            .RequireAuthorization()
            .RequireRateLimiting("api");

        endpoints.MapDelete(
            "/v1/organizations/{organizationId:guid}/members/{targetAccountId:guid}",
            async (
                HttpContext context,
                Guid organizationId,
                Guid targetAccountId,
                IMembershipStore store,
                CancellationToken cancellationToken) =>
            {
                if (!TryGetVerifiedAccount(context, out var accountId, out var failure)) return failure!;
                var result = await store.RevokeAsync(
                    accountId,
                    organizationId,
                    targetAccountId,
                    cancellationToken);
                return Results.Ok(ToResponse(context, result));
            })
            .RequireAuthorization()
            .RequireRateLimiting("api");

        endpoints.MapDelete(
            "/v1/organizations/{organizationId:guid}/members/me",
            async (
                HttpContext context,
                Guid organizationId,
                IMembershipStore store,
                CancellationToken cancellationToken) =>
            {
                if (!TryGetVerifiedAccount(context, out var accountId, out var failure)) return failure!;
                var result = await store.LeaveAsync(accountId, organizationId, cancellationToken);
                return Results.Ok(ToResponse(context, result));
            })
            .RequireAuthorization()
            .RequireRateLimiting("api");

        return endpoints;
    }

    private static MembershipOperationResponse ToResponse(
        HttpContext context,
        MembershipOperation result) => new(
        "evidrilo.membership-operation-result",
        "1",
        result.Outcome,
        result.OrganizationId,
        result.TargetAccountId,
        result.Role,
        RequestIdMiddleware.Get(context));

    private static IResult InvalidCommand(HttpContext context) => Results.Json(
        ApiErrors.Create(context, "INVALID_MEMBERSHIP_COMMAND", "The membership command is invalid."),
        statusCode: StatusCodes.Status400BadRequest);

    private static bool TryGetVerifiedAccount(
        HttpContext context,
        out Guid accountId,
        out IResult? failure)
    {
        failure = null;
        if (!AuthenticatedUser.TryGetAccountId(context.User, out accountId))
        {
            failure = Results.Json(
                ApiErrors.Create(context, "AUTH_REQUIRED", "Authentication is required."),
                statusCode: StatusCodes.Status401Unauthorized);
            return false;
        }

        if (!AuthenticatedUser.IsEmailVerified(context.User))
        {
            failure = Results.Json(
                ApiErrors.Create(context, "FORBIDDEN", "You are not allowed to manage organization memberships."),
                statusCode: StatusCodes.Status403Forbidden);
            return false;
        }

        return true;
    }
}
