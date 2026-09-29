using System.Security.Cryptography;
using System.Text.Json;
using System.Text.Json.Serialization;
using Evidrilo.Api.Ai;
using Evidrilo.Api.Auth;
using Evidrilo.Api.Common;

namespace Evidrilo.Api.ProjectAi;

public interface IProjectAiGeneralChatGenerator
{
    bool IsEnabled { get; }

    int EstimateMaximumCreditCost(ProjectAiGeneralChatProviderRequest request);

    Task<ProjectAiGeneralChatOutput?> GenerateGeneralAsync(
        ProjectAiGeneralChatProviderRequest request,
        CancellationToken cancellationToken);
}

public sealed class DisabledProjectAiGeneralChatGenerator : IProjectAiGeneralChatGenerator
{
    public bool IsEnabled => false;

    public int EstimateMaximumCreditCost(ProjectAiGeneralChatProviderRequest request) => 1;

    public Task<ProjectAiGeneralChatOutput?> GenerateGeneralAsync(
        ProjectAiGeneralChatProviderRequest request,
        CancellationToken cancellationToken) => Task.FromResult<ProjectAiGeneralChatOutput?>(null);
}

public static class ProjectAiGeneralChatEndpoints
{
    private static readonly JsonSerializerOptions RequestJsonOptions = new(JsonSerializerDefaults.Web)
    {
        UnmappedMemberHandling = JsonUnmappedMemberHandling.Disallow,
    };

    private static readonly JsonSerializerOptions ResponseJsonOptions = new(JsonSerializerDefaults.Web)
    {
        DefaultIgnoreCondition = JsonIgnoreCondition.Never,
    };

    public static IEndpointRouteBuilder MapProjectAiGeneralChatEndpoints(this IEndpointRouteBuilder endpoints)
    {
        endpoints.MapPost(
            "/v2/project-ai/general-chat",
            async (
                HttpContext context,
                IProjectAiGeneralChatGenerator generator,
                AiProviderOptions pricing,
                IAiCreditLedger creditLedger,
                IProjectAiConsentStore consentStore,
                IProjectAiActivityStore activityStore,
                ILoggerFactory loggerFactory,
                CancellationToken cancellationToken) =>
            {
                var logger = loggerFactory.CreateLogger("Evidrilo.Api.ProjectAiGeneralChat");
                if (!AuthenticatedUser.TryGetAccountId(context.User, out var accountId))
                    return Error(context, "AUTH_REQUIRED", "Authentication is required.", StatusCodes.Status401Unauthorized);
                if (!AuthenticatedUser.IsEmailVerified(context.User))
                    return Error(context, "FORBIDDEN", "A verified account is required for project AI.", StatusCodes.Status403Forbidden);
                if (!TryGetRequestId(context, out var requestId))
                    return Invalid(context, "INVALID_IDEMPOTENCY_KEY");
                if (!generator.IsEnabled)
                    return Unavailable(context, "PROJECT_AI_GENERAL_CHAT_NOT_READY");

                var payload = await RequestJsonReader.ReadAsync(
                    context.Request,
                    "INVALID_PROJECT_AI_GENERAL_CHAT",
                    "The General chat request is invalid.",
                    cancellationToken);
                ProjectAiGeneralChatRequest? request;
                try
                {
                    request = payload.Deserialize<ProjectAiGeneralChatRequest>(RequestJsonOptions);
                }
                catch (JsonException)
                {
                    return Invalid(context, "INVALID_PROJECT_AI_GENERAL_CHAT");
                }

                var requestError = ProjectAiGeneralChatValidator.ValidateRequest(request);
                if (requestError is not null) return Invalid(context, requestError);
                var validRequest = request!;
                var installationId = Guid.Parse(validRequest.InstallationId!);

                var consent = await consentStore.ReadOwnAsync(accountId, cancellationToken);
                if (!ProjectAiConsentPolicy.StillAuthorizesDispatch(consent, consent))
                    return ConsentRequired(context);

                var providerRequest = new ProjectAiGeneralChatProviderRequest(
                    accountId,
                    requestId,
                    AiRedactor.Redact(validRequest.Message!),
                    validRequest.Locale!);
                int maximumCreditCost;
                try
                {
                    maximumCreditCost = generator.EstimateMaximumCreditCost(providerRequest);
                    if (maximumCreditCost is < 1 or > AiCreditPricing.MaximumCreditsPerRequest)
                        return Unavailable(context, "PROJECT_AI_COST_UNAVAILABLE");
                }
                catch (AiProviderFailureException exception)
                    when (exception.ReasonCode == "PROJECT_AI_CONTEXT_TOO_LARGE")
                {
                    return Error(
                        context,
                        "PROJECT_AI_CONTEXT_TOO_LARGE",
                        "Shorten the message before requesting assistance.",
                        StatusCodes.Status413PayloadTooLarge);
                }
                catch (Exception)
                {
                    return Unavailable(context, "PROJECT_AI_COST_UNAVAILABLE");
                }

                await creditLedger.EnsureConsentAsync(accountId, AiGateway.ConsentVersion, cancellationToken);
                var requestHash = ComputeRequestHash(
                    requestId,
                    installationId,
                    providerRequest.Locale,
                    providerRequest.Message,
                    maximumCreditCost);
                var reservation = await creditLedger.TryReserveAsync(
                    accountId,
                    requestId,
                    requestHash,
                    maximumCreditCost,
                    cancellationToken);
                if (reservation is null)
                    return Error(
                        context,
                        "AI_CREDITS_INSUFFICIENT",
                        "There are not enough AI credits for this request.",
                        StatusCodes.Status402PaymentRequired);
                if (reservation.IsReplay)
                    return Error(
                        context,
                        reservation.ExistingStatus == "reserved"
                            ? "PROJECT_AI_REQUEST_IN_PROGRESS"
                            : "PROJECT_AI_REQUEST_REPLAYED",
                        "This General chat request key has already been used. Start a new request to try again.",
                        StatusCodes.Status409Conflict);

                bool activityCreated;
                try
                {
                    activityCreated = await activityStore.CreatePendingOwnAsync(
                        accountId,
                        new ProjectAiActivityCreate(
                            installationId,
                            requestId,
                            ProjectAiGeneralChatValidator.Mode,
                            ProjectId: null,
                            StageId: null,
                            OperationId: null,
                            BaseProjectRevision: null,
                            ConsentGeneration: null),
                        cancellationToken);
                }
                catch
                {
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    throw;
                }
                if (!activityCreated)
                {
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    return Error(
                        context,
                        "PROJECT_AI_REQUEST_REPLAYED",
                        "This General chat request key has already been used. Start a new request.",
                        StatusCodes.Status409Conflict);
                }

                ProjectAiConsentState beforeDispatchConsent;
                try
                {
                    beforeDispatchConsent = await consentStore.ReadOwnAsync(accountId, cancellationToken);
                }
                catch
                {
                    await CompleteActivityAsync(logger, activityStore, accountId, installationId, requestId, ProjectAiActivityOutcomes.Failed);
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    throw;
                }
                if (!ProjectAiConsentPolicy.StillAuthorizesDispatch(consent, beforeDispatchConsent))
                {
                    await CompleteActivityAsync(logger, activityStore, accountId, installationId, requestId, ProjectAiActivityOutcomes.Failed);
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    return ConsentRequired(context);
                }

                ProjectAiGeneralChatOutput? output;
                try
                {
                    output = await generator.GenerateGeneralAsync(providerRequest, cancellationToken);
                }
                catch (OperationCanceledException)
                {
                    await CompleteActivityAsync(logger, activityStore, accountId, installationId, requestId, ProjectAiActivityOutcomes.Failed);
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    if (cancellationToken.IsCancellationRequested) throw;
                    return Unavailable(context, "PROJECT_AI_PROVIDER_TIMEOUT");
                }
                catch (Exception)
                {
                    await CompleteActivityAsync(logger, activityStore, accountId, installationId, requestId, ProjectAiActivityOutcomes.Failed);
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    return Unavailable(context, "PROJECT_AI_PROVIDER_UNAVAILABLE");
                }

                ProjectAiConsentState afterProviderConsent;
                try
                {
                    afterProviderConsent = await consentStore.ReadOwnAsync(accountId, cancellationToken);
                }
                catch
                {
                    await CompleteActivityAsync(logger, activityStore, accountId, installationId, requestId, ProjectAiActivityOutcomes.Failed);
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    throw;
                }
                if (!ProjectAiConsentPolicy.StillAuthorizesDispatch(consent, afterProviderConsent))
                {
                    await CompleteActivityAsync(logger, activityStore, accountId, installationId, requestId, ProjectAiActivityOutcomes.Failed);
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    return ConsentRequired(context);
                }

                if (output is null)
                {
                    await CompleteActivityAsync(logger, activityStore, accountId, installationId, requestId, ProjectAiActivityOutcomes.Failed);
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    return Error(context, "PROJECT_AI_REFUSED", "General chat did not return an answer.", StatusCodes.Status422UnprocessableEntity);
                }

                if (string.IsNullOrWhiteSpace(output.Answer)
                    || output.Answer.Length > ProjectAiGeneralChatValidator.MaximumAnswerLength)
                {
                    await CompleteActivityAsync(logger, activityStore, accountId, installationId, requestId, ProjectAiActivityOutcomes.Failed);
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    return Error(context, "PROJECT_AI_INVALID_RESPONSE", "The General chat response could not be verified.", StatusCodes.Status502BadGateway);
                }

                int settledCreditCost;
                try
                {
                    if (output.Usage is null || !output.Usage.IsValid)
                        throw new InvalidOperationException("The provider usage report is invalid.");
                    settledCreditCost = AiCreditPricing.CreditsForCostUsd(
                        pricing.EstimateActualCostUsd(output.Usage));
                }
                catch (Exception)
                {
                    await CompleteActivityAsync(logger, activityStore, accountId, installationId, requestId, ProjectAiActivityOutcomes.Failed);
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    return Error(context, "PROJECT_AI_INVALID_USAGE", "The General chat token usage could not be verified.", StatusCodes.Status502BadGateway);
                }
                if (settledCreditCost is < 1 or > AiCreditPricing.MaximumCreditsPerRequest
                    || settledCreditCost > maximumCreditCost)
                {
                    await CompleteActivityAsync(logger, activityStore, accountId, installationId, requestId, ProjectAiActivityOutcomes.Failed);
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    return Error(context, "PROJECT_AI_COST_LIMIT", "General chat usage exceeded the reserved credit amount.", StatusCodes.Status502BadGateway);
                }

                bool settled;
                try
                {
                    settled = await creditLedger.CompleteAsync(
                        accountId,
                        reservation,
                        accepted: true,
                        settledCreditCost,
                        cancellationToken);
                }
                catch
                {
                    await CompleteActivityAsync(logger, activityStore, accountId, installationId, requestId, ProjectAiActivityOutcomes.Failed);
                    throw;
                }
                if (!settled)
                {
                    await CompleteActivityAsync(logger, activityStore, accountId, installationId, requestId, ProjectAiActivityOutcomes.Failed);
                    return Error(context, "PROJECT_AI_RESERVATION_EXPIRED", "The credit reservation expired before the answer was ready.", StatusCodes.Status409Conflict);
                }

                await CompleteActivityAsync(logger, activityStore, accountId, installationId, requestId, ProjectAiActivityOutcomes.Completed);
                return Results.Json(
                    new ProjectAiGeneralChatResponse(
                        ProjectAiGeneralChatValidator.Schema,
                        ProjectAiGeneralChatValidator.Version,
                        ProjectAiGeneralChatValidator.Mode,
                        "success",
                        output.Answer,
                        requestId,
                        settledCreditCost),
                    options: ResponseJsonOptions);
            })
            .RequireAuthorization()
            .RequireRateLimiting("ai");

        return endpoints;
    }

    private static bool TryGetRequestId(HttpContext context, out string requestId)
    {
        requestId = context.Request.Headers["Idempotency-Key"].ToString();
        return ProjectAiScaffoldValidator.IsValidRequestId(requestId);
    }

    private static string ComputeRequestHash(
        string requestId,
        Guid installationId,
        string locale,
        string redactedMessage,
        int maximumCreditCost)
    {
        var bytes = JsonSerializer.SerializeToUtf8Bytes(new
        {
            Operation = "project-ai-general-chat:v2",
            RequestId = requestId,
            InstallationId = installationId,
            Locale = locale,
            Message = redactedMessage,
            MaximumCreditCost = maximumCreditCost,
        });
        return Convert.ToHexString(SHA256.HashData(bytes)).ToLowerInvariant();
    }

    private static async Task CompleteActivityAsync(
        ILogger logger,
        IProjectAiActivityStore activityStore,
        Guid accountId,
        Guid installationId,
        string requestId,
        string outcome)
    {
        try
        {
            await activityStore.CompleteOwnAsync(
                accountId,
                installationId,
                requestId,
                outcome,
                resultProjectRevision: null,
                requestedSettlementOutcome: null,
                settlementHash: null,
                cancellationToken: CancellationToken.None);
        }
        catch
        {
            logger.LogWarning("General chat activity outcome could not be recorded.");
        }
    }

    private static Task<bool> ReleaseReservationAsync(
        IAiCreditLedger creditLedger,
        Guid accountId,
        AiCreditReservation reservation) =>
        creditLedger.CompleteAsync(
            accountId,
            reservation,
            accepted: false,
            settledCreditCost: 0,
            CancellationToken.None);

    private static IResult ConsentRequired(HttpContext context) =>
        Error(context, "PROJECT_AI_CONSENT_REQUIRED", "Review and accept the current Project AI data-use policy before using General chat.", StatusCodes.Status403Forbidden);

    private static IResult Invalid(HttpContext context, string code) =>
        Error(context, code, "The General chat request is invalid.", StatusCodes.Status400BadRequest);

    private static IResult Unavailable(HttpContext context, string code) =>
        Error(context, code, "General chat is unavailable. Try again later.", StatusCodes.Status503ServiceUnavailable);

    private static IResult Error(HttpContext context, string code, string message, int statusCode) =>
        Results.Json(ApiErrors.Create(context, code, message), statusCode: statusCode);
}
