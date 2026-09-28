using System.Security.Cryptography;
using System.Text.Json;
using System.Text.Json.Serialization;
using Evidrilo.Api.Ai;
using Evidrilo.Api.Auth;
using Evidrilo.Api.Common;
using Evidrilo.Api.ProjectTemplates;
using Evidrilo.Api.Projects;

namespace Evidrilo.Api.ProjectAi;

public static class ProjectAiStageAssistEndpoints
{
    private static readonly JsonSerializerOptions RequestJsonOptions = new(JsonSerializerDefaults.Web)
    {
        UnmappedMemberHandling = JsonUnmappedMemberHandling.Disallow,
    };
    private static readonly JsonSerializerOptions ResponseJsonOptions = new(JsonSerializerDefaults.Web)
    {
        DefaultIgnoreCondition = JsonIgnoreCondition.Never,
    };
    private static readonly JsonSerializerOptions FingerprintJsonOptions = new(JsonSerializerDefaults.Web)
    {
        DefaultIgnoreCondition = JsonIgnoreCondition.Never,
    };

    public static IEndpointRouteBuilder MapProjectAiStageAssistEndpoints(this IEndpointRouteBuilder endpoints)
    {
        endpoints.MapPost(
            "/v1/project-ai/stage-assist",
            async (
                HttpContext context,
                IProjectAiScaffoldGenerator generator,
                IAiCreditLedger creditLedger,
                IProjectTemplateStore templateStore,
                IProjectAiConsentStore consentStore,
                IStudentProjectStore studentProjectStore,
                IProjectAiActivityStore activityStore,
                ILoggerFactory loggerFactory,
                CancellationToken cancellationToken) =>
            {
                var logger = loggerFactory.CreateLogger("Evidrilo.Api.ProjectAiStageAssist");
                if (!AuthenticatedUser.TryGetAccountId(context.User, out var accountId))
                    return Error(context, "AUTH_REQUIRED", "Authentication is required.", StatusCodes.Status401Unauthorized);
                if (!AuthenticatedUser.IsEmailVerified(context.User))
                    return Error(context, "FORBIDDEN", "A verified account is required for project AI.", StatusCodes.Status403Forbidden);
                if (!TryGetRequestId(context, out var requestId))
                    return Invalid(context, "INVALID_IDEMPOTENCY_KEY");

                var payload = await RequestJsonReader.ReadAsync(
                    context.Request,
                    "INVALID_PROJECT_AI_STAGE_ASSIST",
                    "The stage-assist request is invalid.",
                    cancellationToken);
                ProjectAiStageAssistRequest? request;
                try
                {
                    request = payload.Deserialize<ProjectAiStageAssistRequest>(RequestJsonOptions);
                }
                catch (JsonException)
                {
                    return Invalid(context, "INVALID_PROJECT_AI_STAGE_ASSIST");
                }

                var requestError = ProjectAiStageAssistValidator.ValidateRequest(request);
                if (requestError is not null) return Invalid(context, requestError);
                var validRequest = request!;

                if (validRequest.Mode == ProjectAiStageAssistValidator.GeneralMode)
                    return Error(
                        context,
                        "PROJECT_AI_GENERAL_NOT_READY",
                        "General chat is unavailable until its cost, limit, and retention policy is approved.",
                        StatusCodes.Status503ServiceUnavailable);

                // Keep the provider closed before reading template or student context.
                if (!generator.IsEnabled)
                    return Unavailable(context, "PROJECT_AI_NOT_READY");

                var consent = await consentStore.ReadOwnAsync(accountId, cancellationToken);
                if (!ProjectAiConsentPolicy.StillAuthorizesDispatch(consent, consent))
                    return ConsentRequired(context);

                var template = await templateStore.GetPublishedAsync(
                    validRequest.TemplateId!,
                    validRequest.TemplateVersion!.Value,
                    cancellationToken);
                var templateError = ProjectAiStageAssistValidator.ValidateTemplateCapability(validRequest, template);
                if (templateError is not null)
                {
                    var status = templateError == "PROJECT_AI_TEMPLATE_NOT_READY"
                        ? StatusCodes.Status404NotFound
                        : StatusCodes.Status400BadRequest;
                    return Error(context, templateError, "The selected stage, operation, or fields are unavailable.", status);
                }

                var projectId = Guid.Parse(validRequest.ProjectId!);
                var savedProject = await studentProjectStore.ReadOwnAsync(accountId, projectId, cancellationToken);
                var projectError = ProjectContextError(context, savedProject, validRequest.BaseProjectRevision);
                if (projectError is not null) return projectError;

                var beforeReservationConsent = await consentStore.ReadOwnAsync(accountId, cancellationToken);
                if (!ProjectAiConsentPolicy.StillAuthorizesDispatch(consent, beforeReservationConsent))
                    return ConsentRequired(context);

                const int creditCost = 1;
                var requestHash = ComputeRequestHash(validRequest);
                await creditLedger.EnsureConsentAsync(accountId, AiGateway.ConsentVersion, cancellationToken);
                var reservation = await creditLedger.TryReserveAsync(
                    accountId,
                    requestId,
                    requestHash,
                    creditCost,
                    cancellationToken);
                if (reservation is null)
                    return Error(context, "AI_CREDITS_INSUFFICIENT", "There are not enough AI credits for this request.", StatusCodes.Status402PaymentRequired);
                if (reservation.IsReplay)
                    return Error(context, "PROJECT_AI_REQUEST_REPLAYED", "This request key has already been used. Start a new request.", StatusCodes.Status409Conflict);

                if (creditLedger is not IProjectAiCreditSettlementLedger settlementLedger)
                {
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    return Unavailable(context, "PROJECT_AI_SETTLEMENT_NOT_READY");
                }

                bool reservationBound;
                try
                {
                    reservationBound = await settlementLedger.BindProjectAiReservationAsync(
                        accountId,
                        reservation,
                        requestHash,
                        creditCost,
                        cancellationToken);
                }
                catch
                {
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    throw;
                }
                if (!reservationBound)
                {
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    return Error(context, "PROJECT_AI_RESERVATION_CONFLICT", "The credit reservation is no longer available.", StatusCodes.Status409Conflict);
                }

                var beforeDispatchConsent = await consentStore.ReadOwnAsync(accountId, cancellationToken);
                if (!ProjectAiConsentPolicy.StillAuthorizesDispatch(consent, beforeDispatchConsent))
                {
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    return ConsentRequired(context);
                }

                StudentProjectRecord? currentProject;
                try
                {
                    currentProject = await studentProjectStore.ReadOwnAsync(accountId, projectId, cancellationToken);
                }
                catch
                {
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    throw;
                }
                projectError = ProjectContextError(context, currentProject, validRequest.BaseProjectRevision);
                if (projectError is not null)
                {
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    return projectError;
                }

                bool historyCreated;
                try
                {
                    historyCreated = await activityStore.CreatePendingOwnAsync(
                        accountId,
                        new ProjectAiActivityCreate(
                            Guid.Parse(validRequest.InstallationId!),
                            requestId,
                            ProjectAiStageAssistValidator.ProjectMode,
                            projectId,
                            validRequest.StageId,
                            validRequest.OperationId,
                            validRequest.BaseProjectRevision,
                            consent.Generation),
                        cancellationToken);
                }
                catch
                {
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    throw;
                }
                if (!historyCreated)
                {
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    return Error(context, "PROJECT_AI_REQUEST_REPLAYED", "This request key has already been used. Start a new request.", StatusCodes.Status409Conflict);
                }

                var capability = ProjectAiStageAssistValidator.FindCapability(validRequest, template!);
                var selectedFields = validRequest.SelectedFields!
                    .ToDictionary(pair => pair.Key, pair => AiRedactor.Redact(pair.Value), StringComparer.Ordinal);
                var providerRequest = new ProjectAiScaffoldProviderRequest(
                    accountId,
                    requestId,
                    ProjectAiScaffoldValidator.AssistOperation,
                    template!,
                    validRequest.BaseProjectRevision,
                    selectedFields.GetValueOrDefault("assignment_brief") ?? string.Empty,
                    selectedFields.GetValueOrDefault("research_question"),
                    selectedFields.GetValueOrDefault("student_question"),
                    selectedFields,
                    template!.Template.MethodSpecificLimitations!,
                    validRequest.Locale!)
                {
                    StageId = validRequest.StageId,
                    StageOperationId = validRequest.OperationId,
                    AllowedOutputFieldIds = capability!.OutputFieldIds,
                };

                ProjectAiScaffoldOutput? output;
                try
                {
                    output = await generator.GenerateAsync(providerRequest, cancellationToken);
                }
                catch (OperationCanceledException)
                {
                    await CompleteActivityAsync(
                        logger,
                        activityStore,
                        accountId,
                        validRequest,
                        requestId,
                        ProjectAiActivityOutcomes.Failed,
                        null);
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    if (cancellationToken.IsCancellationRequested) throw;
                    return Unavailable(context, "PROJECT_AI_PROVIDER_TIMEOUT");
                }
                catch (Exception)
                {
                    await CompleteActivityAsync(
                        logger,
                        activityStore,
                        accountId,
                        validRequest,
                        requestId,
                        ProjectAiActivityOutcomes.Failed,
                        null);
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    return Unavailable(context, "PROJECT_AI_PROVIDER_UNAVAILABLE");
                }

                ProjectAiConsentState afterProviderConsent;
                StudentProjectRecord? afterProviderProject;
                try
                {
                    afterProviderConsent = await consentStore.ReadOwnAsync(accountId, cancellationToken);
                    afterProviderProject = await studentProjectStore.ReadOwnAsync(accountId, projectId, cancellationToken);
                }
                catch
                {
                    await CompleteActivityAsync(
                        logger,
                        activityStore,
                        accountId,
                        validRequest,
                        requestId,
                        ProjectAiActivityOutcomes.Failed,
                        null);
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    throw;
                }
                if (!ProjectAiConsentPolicy.StillAuthorizesDispatch(consent, afterProviderConsent)
                    || afterProviderProject?.Version != validRequest.BaseProjectRevision)
                {
                    await CompleteActivityAsync(
                        logger,
                        activityStore,
                        accountId,
                        validRequest,
                        requestId,
                        ProjectAiActivityOutcomes.Stale,
                        null);
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    if (!ProjectAiConsentPolicy.StillAuthorizesDispatch(consent, afterProviderConsent))
                        return ConsentRequired(context);
                    return ProjectContextError(context, afterProviderProject, validRequest.BaseProjectRevision)!;
                }

                if (output is null)
                {
                    await CompleteActivityAsync(
                        logger,
                        activityStore,
                        accountId,
                        validRequest,
                        requestId,
                        ProjectAiActivityOutcomes.Failed,
                        null);
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    return Error(context, "PROJECT_AI_REFUSED", "Project AI did not return a proposal.", StatusCodes.Status422UnprocessableEntity);
                }

                if (ProjectAiStageAssistValidator.ValidateOutput(validRequest, template, output) is not null)
                {
                    await CompleteActivityAsync(
                        logger,
                        activityStore,
                        accountId,
                        validRequest,
                        requestId,
                        ProjectAiActivityOutcomes.Failed,
                        null);
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    return Error(context, "PROJECT_AI_INVALID_RESPONSE", "The project-AI response could not be verified.", StatusCodes.Status502BadGateway);
                }

                bool previewReady;
                try
                {
                    previewReady = await settlementLedger.MarkProjectAiPreviewReadyAsync(
                        accountId,
                        reservation,
                        requestHash,
                        creditCost,
                        cancellationToken);
                }
                catch
                {
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    throw;
                }
                if (!previewReady)
                {
                    await CompleteActivityAsync(
                        logger,
                        activityStore,
                        accountId,
                        validRequest,
                        requestId,
                        ProjectAiActivityOutcomes.Failed,
                        null);
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    return Error(context, "PROJECT_AI_RESERVATION_EXPIRED", "The credit reservation expired before the preview was ready.", StatusCodes.Status409Conflict);
                }

                return Results.Json(
                    new ProjectAiStageAssistResponse(
                        ProjectAiStageAssistValidator.Schema,
                        ProjectAiStageAssistValidator.Version,
                        "preview",
                        ProjectAiStageAssistValidator.ProjectMode,
                        validRequest.ProjectId!,
                        validRequest.BaseProjectRevision!.Value,
                        validRequest.StageId!,
                        validRequest.OperationId!,
                        output,
                        requestId,
                        creditCost),
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

    private static string ComputeRequestHash(ProjectAiStageAssistRequest request)
    {
        var canonical = request with
        {
            SelectedFields = request.SelectedFields is null
                ? null
                : request.SelectedFields
                    .OrderBy(pair => pair.Key, StringComparer.Ordinal)
                    .ToDictionary(pair => pair.Key, pair => pair.Value, StringComparer.Ordinal),
        };
        var bytes = JsonSerializer.SerializeToUtf8Bytes(new
        {
            Operation = "project-ai-stage-assist:v1",
            Request = canonical,
        }, FingerprintJsonOptions);
        return Convert.ToHexString(SHA256.HashData(bytes)).ToLowerInvariant();
    }

    private static IResult? ProjectContextError(
        HttpContext context,
        StudentProjectRecord? project,
        int? baseProjectRevision)
    {
        if (project is null)
            return Error(context, "PROJECT_NOT_FOUND", "The student project was not found.", StatusCodes.Status404NotFound);
        if (project.Version != baseProjectRevision)
            return Error(context, "PROJECT_VERSION_CONFLICT", "The student project changed. Reload it before requesting assistance.", StatusCodes.Status409Conflict);
        return null;
    }

    private static Task<bool> ReleaseReservationAsync(
        IAiCreditLedger creditLedger,
        Guid accountId,
        AiCreditReservation reservation) =>
        creditLedger.CompleteAsync(accountId, reservation, accepted: false, CancellationToken.None);

    private static async Task CompleteActivityAsync(
        ILogger logger,
        IProjectAiActivityStore activityStore,
        Guid accountId,
        ProjectAiStageAssistRequest request,
        string requestId,
        string outcome,
        int? resultProjectRevision)
    {
        try
        {
            await activityStore.CompleteOwnAsync(
                accountId,
                Guid.Parse(request.InstallationId!),
                requestId,
                outcome,
                resultProjectRevision,
                requestedSettlementOutcome: null,
                settlementHash: null,
                cancellationToken: CancellationToken.None);
        }
        catch
        {
            logger.LogWarning("Project AI activity outcome could not be recorded.");
        }
    }

    private static IResult ConsentRequired(HttpContext context) =>
        Error(context, "PROJECT_AI_CONSENT_REQUIRED", "Review and accept the current Project AI data-use policy before requesting assistance.", StatusCodes.Status403Forbidden);

    private static IResult Invalid(HttpContext context, string code) =>
        Error(context, code, "The stage-assist request is invalid.", StatusCodes.Status400BadRequest);

    private static IResult Unavailable(HttpContext context, string code) =>
        Error(context, code, "Project AI is unavailable. Manual project work remains available.", StatusCodes.Status503ServiceUnavailable);

    private static IResult Error(HttpContext context, string code, string message, int statusCode) =>
        Results.Json(ApiErrors.Create(context, code, message), statusCode: statusCode);
}
