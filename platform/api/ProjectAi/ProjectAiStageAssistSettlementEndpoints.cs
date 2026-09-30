using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using Evidrilo.Api.Ai;
using Evidrilo.Api.Auth;
using Evidrilo.Api.Common;
using Evidrilo.Api.Projects;

namespace Evidrilo.Api.ProjectAi;

public static class ProjectAiStageAssistSettlementEndpoints
{
    private static readonly JsonSerializerOptions RequestJsonOptions = new(JsonSerializerDefaults.Web)
    {
        UnmappedMemberHandling = JsonUnmappedMemberHandling.Disallow,
    };
    private static readonly JsonSerializerOptions ResponseJsonOptions = new(JsonSerializerDefaults.Web)
    {
        DefaultIgnoreCondition = JsonIgnoreCondition.Never,
    };

    public static IEndpointRouteBuilder MapProjectAiStageAssistSettlementEndpoints(this IEndpointRouteBuilder endpoints)
    {
        endpoints.MapPost(
            "/v1/project-ai/stage-assist/settlement",
            async (
                HttpContext context,
                IAiCreditLedger creditLedger,
                IProjectAiActivityStore activityStore,
                IProjectAiConsentStore consentStore,
                IStudentProjectStore studentProjectStore,
                IProjectAiLocalProjectContextStore localProjectContextStore,
                CancellationToken cancellationToken) =>
            {
                if (!AuthenticatedUser.TryGetAccountId(context.User, out var accountId))
                    return Error(context, "AUTH_REQUIRED", "Authentication is required.", StatusCodes.Status401Unauthorized);
                if (!AuthenticatedUser.IsEmailVerified(context.User))
                    return Error(context, "FORBIDDEN", "A verified account is required for project AI.", StatusCodes.Status403Forbidden);

                var payload = await RequestJsonReader.ReadAsync(
                    context.Request,
                    "INVALID_PROJECT_AI_STAGE_ASSIST_SETTLEMENT",
                    "The stage-assist settlement is invalid.",
                    cancellationToken);
                ProjectAiStageAssistSettlementRequest? request;
                try
                {
                    request = payload.Deserialize<ProjectAiStageAssistSettlementRequest>(RequestJsonOptions);
                }
                catch (JsonException)
                {
                    return Invalid(context, "INVALID_PROJECT_AI_STAGE_ASSIST_SETTLEMENT");
                }

                var requestError = ProjectAiStageAssistValidator.ValidateSettlementRequest(request);
                if (requestError is not null) return Invalid(context, requestError);
                var validRequest = request!;
                var idempotencyKey = context.Request.Headers["Idempotency-Key"].ToString();
                if (!string.Equals(idempotencyKey, validRequest.RequestId, StringComparison.Ordinal))
                    return Error(context, "PROJECT_AI_SETTLEMENT_KEY_MISMATCH", "Use the same idempotency key as the stage-assist preview.", StatusCodes.Status409Conflict);

                var installationId = Guid.Parse(validRequest.InstallationId!);
                var activity = await activityStore.ReadOwnAsync(
                    accountId,
                    installationId,
                    validRequest.RequestId!,
                    cancellationToken);
                if (activity is null)
                    return Error(context, "PROJECT_AI_ACTIVITY_NOT_FOUND", "The project-AI activity was not found.", StatusCodes.Status404NotFound);
                if (activity.Mode != ProjectAiStageAssistValidator.ProjectMode
                    || activity.ProjectId is null
                    || activity.BaseProjectRevision is null)
                    return Error(context, "PROJECT_AI_SETTLEMENT_CONFLICT", "The activity cannot be settled as a project preview.", StatusCodes.Status409Conflict);
                if ((validRequest.Outcome is "APPLIED" or "EDITED")
                    && activity.UsesLocalProjectContext != validRequest.ResultProjectBindingGeneration.HasValue)
                    return Invalid(context, "INVALID_PROJECT_AI_STAGE_ASSIST_SETTLEMENT");

                var settlementHash = ComputeSettlementHash(validRequest);
                if (activity.Outcome != ProjectAiActivityOutcomes.Pending)
                {
                    if (!string.Equals(activity.RequestedSettlementOutcome, validRequest.Outcome, StringComparison.Ordinal)
                        || !string.Equals(activity.SettlementHash, settlementHash, StringComparison.Ordinal))
                        return Error(context, "PROJECT_AI_SETTLEMENT_CONFLICT", "The activity was already settled with a different outcome.", StatusCodes.Status409Conflict);

                    var replayResult = await SettleCreditAsync(
                        context,
                        creditLedger,
                        accountId,
                        validRequest.RequestId!,
                        settlementHash,
                        activity.Outcome is ProjectAiActivityOutcomes.Applied or ProjectAiActivityOutcomes.Edited,
                        cancellationToken);
                    if (replayResult.Result is not null) return replayResult.Result;
                    if (activity.Outcome == ProjectAiActivityOutcomes.Stale
                        && validRequest.Outcome is "APPLIED" or "EDITED")
                        return Error(context, "PROJECT_AI_RESULT_STALE", "The proposal became stale before it could be confirmed.", StatusCodes.Status409Conflict);
                    return Results.Json(
                        new ProjectAiStageAssistSettlementResponse(
                            ProjectAiStageAssistValidator.SettlementSchema,
                            ProjectAiStageAssistValidator.Version,
                            activity.Outcome,
                            validRequest.RequestId!,
                            replayResult.CreditCost),
                        options: ResponseJsonOptions);
                }

                var finalOutcome = validRequest.Outcome!;
                int? finalRevision = validRequest.ResultProjectRevision;
                string? staleCode = null;
                if (finalOutcome is "APPLIED" or "EDITED")
                {
                    var consent = await consentStore.ReadOwnAsync(accountId, cancellationToken);
                    if (!consent.Granted
                        || !string.Equals(consent.PolicyVersion, ProjectAiConsentPolicy.CurrentPolicyVersion, StringComparison.Ordinal)
                        || consent.Generation != activity.ConsentGeneration)
                    {
                        finalOutcome = ProjectAiActivityOutcomes.Stale;
                        finalRevision = null;
                        staleCode = "PROJECT_AI_RESULT_STALE";
                    }
                    else if (activity.UsesLocalProjectContext)
                    {
                        var resultBindingGeneration = validRequest.ResultProjectBindingGeneration!.Value;
                        var project = await localProjectContextStore.ReadOwnAsync(
                            accountId,
                            activity.ProjectId.Value,
                            cancellationToken);
                        if (project is null
                            || project.CurrentRevision != validRequest.ResultProjectRevision
                            || project.CurrentRevision <= activity.BaseProjectRevision
                            || activity.BaseProjectBindingGeneration is null
                            || project.BindingGeneration != resultBindingGeneration
                            || resultBindingGeneration <= activity.BaseProjectBindingGeneration.Value)
                        {
                            finalOutcome = ProjectAiActivityOutcomes.Stale;
                            finalRevision = null;
                            staleCode = "PROJECT_AI_RESULT_STALE";
                        }
                    }
                    else
                    {
                        var project = await studentProjectStore.ReadOwnAsync(accountId, activity.ProjectId.Value, cancellationToken);
                        if (project is null || project.Version != validRequest.ResultProjectRevision
                            || validRequest.ResultProjectRevision <= activity.BaseProjectRevision)
                        {
                            finalOutcome = ProjectAiActivityOutcomes.Stale;
                            finalRevision = null;
                            staleCode = "PROJECT_AI_RESULT_STALE";
                        }
                    }
                }

                if (creditLedger is not IProjectAiCreditSettlementLedger settlementLedger)
                    return Unavailable(context, "PROJECT_AI_SETTLEMENT_NOT_READY");
                var settlesAsApplied = finalOutcome is ProjectAiActivityOutcomes.Applied or ProjectAiActivityOutcomes.Edited;
                var creditResult = await settlementLedger.SettleProjectAiAsync(
                    accountId,
                    validRequest.RequestId!,
                    settlementHash,
                    settlesAsApplied,
                    cancellationToken);
                var creditError = SettlementError(context, creditResult);
                if (creditError is not null) return creditError;

                var completion = await activityStore.CompleteOwnAsync(
                    accountId,
                    installationId,
                    validRequest.RequestId!,
                    finalOutcome,
                    finalRevision,
                    validRequest.Outcome,
                    settlementHash,
                    cancellationToken);
                if (completion == ProjectAiActivityCompletionStatus.NotFound)
                    return Error(context, "PROJECT_AI_ACTIVITY_NOT_FOUND", "The project-AI activity was not found.", StatusCodes.Status404NotFound);
                if (completion == ProjectAiActivityCompletionStatus.Conflict)
                    return Error(context, "PROJECT_AI_SETTLEMENT_CONFLICT", "The activity was already settled with a different outcome.", StatusCodes.Status409Conflict);
                if (staleCode is not null)
                    return Error(context, staleCode, "The proposal became stale before it could be confirmed.", StatusCodes.Status409Conflict);

                return Results.Json(
                    new ProjectAiStageAssistSettlementResponse(
                        ProjectAiStageAssistValidator.SettlementSchema,
                        ProjectAiStageAssistValidator.Version,
                        finalOutcome,
                        validRequest.RequestId!,
                        creditResult.CreditCost),
                    options: ResponseJsonOptions);
            })
            .RequireAuthorization()
            .RequireRateLimiting("ai");

        return endpoints;
    }

    internal static string ComputeSettlementHash(ProjectAiStageAssistSettlementRequest request)
    {
        var canonical = string.Concat(
            "project-ai-stage-assist-settlement:v1\n",
            request.InstallationId,
            "\n",
            request.RequestId,
            "\n",
            request.Outcome,
            "\n",
            request.ResultProjectRevision?.ToString(System.Globalization.CultureInfo.InvariantCulture) ?? "-",
            "\n",
            request.ResultProjectBindingGeneration?.ToString(System.Globalization.CultureInfo.InvariantCulture) ?? "-");
        return Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(canonical))).ToLowerInvariant();
    }

    private static async Task<(int CreditCost, IResult? Result)> SettleCreditAsync(
        HttpContext context,
        IAiCreditLedger creditLedger,
        Guid accountId,
        string requestId,
        string settlementHash,
        bool apply,
        CancellationToken cancellationToken)
    {
        if (creditLedger is not IProjectAiCreditSettlementLedger settlementLedger)
            return (0, Unavailable(context, "PROJECT_AI_SETTLEMENT_NOT_READY"));
        var result = await settlementLedger.SettleProjectAiAsync(
            accountId,
            requestId,
            settlementHash,
            apply,
            cancellationToken);
        return (result.CreditCost, SettlementError(context, result));
    }

    private static IResult? SettlementError(HttpContext context, ProjectAiCreditSettlementResult result) => result.Status switch
    {
        ProjectAiCreditSettlementStatus.NotFound => Error(context, "PROJECT_AI_RESERVATION_NOT_FOUND", "The project-AI reservation was not found.", StatusCodes.Status404NotFound),
        ProjectAiCreditSettlementStatus.Conflict => Error(context, "PROJECT_AI_SETTLEMENT_CONFLICT", "The reservation was settled, expired, or belongs to another operation.", StatusCodes.Status409Conflict),
        _ => null,
    };

    private static IResult Invalid(HttpContext context, string code) =>
        Error(context, code, "The stage-assist settlement is invalid.", StatusCodes.Status400BadRequest);

    private static IResult Unavailable(HttpContext context, string code) =>
        Error(context, code, "Project AI is unavailable. Manual project work remains available.", StatusCodes.Status503ServiceUnavailable);

    private static IResult Error(HttpContext context, string code, string message, int statusCode) =>
        Results.Json(ApiErrors.Create(context, code, message), statusCode: statusCode);
}
