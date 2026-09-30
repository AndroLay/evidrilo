using System.Security.Cryptography;
using System.Text.Json;
using System.Text.Json.Serialization;
using Evidrilo.Api.Ai;
using Evidrilo.Api.Auth;
using Evidrilo.Api.Common;
using Evidrilo.Api.ProjectTemplates;
using Evidrilo.Api.Projects;

namespace Evidrilo.Api.ProjectAi;

public interface IProjectAiStageAssistGenerator
{
    bool IsEnabled { get; }

    int EstimateMaximumCreditCost(ProjectAiStageAssistProviderRequest request);

    Task<ProjectAiStageAssistOutput?> GenerateAsync(
        ProjectAiStageAssistProviderRequest request,
        CancellationToken cancellationToken);
}

public sealed class DisabledProjectAiStageAssistGenerator : IProjectAiStageAssistGenerator
{
    public bool IsEnabled => false;

    public int EstimateMaximumCreditCost(ProjectAiStageAssistProviderRequest request) => 1;

    public Task<ProjectAiStageAssistOutput?> GenerateAsync(
        ProjectAiStageAssistProviderRequest request,
        CancellationToken cancellationToken) => Task.FromResult<ProjectAiStageAssistOutput?>(null);
}

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
                IProjectAiStageAssistGenerator generator,
                AiProviderOptions pricing,
                IAiCreditLedger creditLedger,
                IProjectTemplateStore templateStore,
                IProjectAiConsentStore consentStore,
                IStudentProjectStore studentProjectStore,
                IProjectAiLocalProjectContextStore localProjectContextStore,
                IProjectAiActivityStore activityStore,
                ILoggerFactory loggerFactory,
                CancellationToken cancellationToken) =>
            {
                var logger = loggerFactory.CreateLogger("Evidrilo.Api.ProjectAiStageAssist");
                if (!AuthenticatedUser.TryGetAccountId(context.User, out var accountId))
                    return Error(context, "AUTH_REQUIRED", "Authentication is required.", StatusCodes.Status401Unauthorized);
                if (!AuthenticatedUser.IsEmailVerified(context.User))
                    return Error(context, "FORBIDDEN", "A verified account is required for project AI.", StatusCodes.Status403Forbidden);
                if (!ProjectAiStageAssistValidator.HasExplicitRequestConsent(context))
                    return Error(context, "PROJECT_AI_REQUEST_CONSENT_REQUIRED", "Confirm the selected project context for this request.", StatusCodes.Status403Forbidden);
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
                var usesLocalProjectContext = validRequest.SelectedFields is not null;
                StudentProjectRecord? savedProject;
                IResult? projectError;
                if (usesLocalProjectContext)
                {
                    var localContext = await localProjectContextStore.ReadOwnAsync(accountId, projectId, cancellationToken);
                    projectError = LocalProjectContextError(context, localContext, validRequest);
                    savedProject = projectError is null ? CreateSelectedContextProject(projectId, validRequest) : null;
                }
                else
                {
                    savedProject = await studentProjectStore.ReadOwnAsync(accountId, projectId, cancellationToken);
                    projectError = ProjectContextError(context, savedProject, validRequest.BaseProjectRevision);
                }
                if (projectError is not null) return projectError;
                var evidenceError = ProjectAiStageAssistValidator.ValidateSelectedEvidence(validRequest, template, savedProject);
                if (evidenceError is not null) return Invalid(context, evidenceError);
                var fieldContextError = ProjectAiStageAssistValidator.BuildSelectedFieldContexts(
                    validRequest,
                    template,
                    savedProject,
                    redact: false,
                    out var selectedFieldValues);
                if (fieldContextError is not null) return Invalid(context, fieldContextError);

                var capability = ProjectAiStageAssistValidator.FindCapability(validRequest, template!);
                var selectedFieldContext = selectedFieldValues
                    .Select(field => field with { Value = AiRedactor.Redact(field.Value) })
                    .ToArray();
                var stage = template!.Template.Steps!
                    .Single(step => string.Equals(step.Id, validRequest.StageId, StringComparison.Ordinal));
                var evidenceById = savedProject!.Document.EvidenceItems!
                    .ToDictionary(item => item.Id!, StringComparer.Ordinal);
                var selectedEvidence = validRequest.SelectedEvidenceIds!
                    .Select(id => evidenceById[id])
                    .Select(item => new ProjectAiStageAssistEvidenceContext(
                        item.Id!,
                        item.Kind switch
                        {
                            StudentProjectEvidenceKind.Source => "SOURCE",
                            StudentProjectEvidenceKind.Data => "DATA",
                            StudentProjectEvidenceKind.Observation => "OBSERVATION",
                            _ => "UNKNOWN",
                        },
                        AiRedactor.Redact(item.Label!),
                        item.Summary is null ? null : AiRedactor.Redact(item.Summary),
                        item.Origin is null ? null : AiRedactor.Redact(item.Origin)))
                    .ToArray();
                var providerRequest = new ProjectAiStageAssistProviderRequest(
                    template.TemplateId,
                    template.TemplateVersion,
                    template.Family,
                    template.Template.Title!,
                    template.Template.Summary!,
                    validRequest.StageId!,
                    stage.Title!,
                    validRequest.OperationId!,
                    selectedFieldContext,
                    selectedEvidence,
                    capability!.OutputFieldIds!
                        .Where(fieldId => validRequest.SelectedFieldIds!.Contains(fieldId, StringComparer.Ordinal))
                        .ToArray(),
                    template.Template.MethodSpecificLimitations!,
                    template.Template.ProvenanceRequirements!,
                    validRequest.Locale!)
                {
                    AccountId = accountId,
                    RequestId = requestId,
                };

                var beforeReservationConsent = await consentStore.ReadOwnAsync(accountId, cancellationToken);
                if (!ProjectAiConsentPolicy.StillAuthorizesDispatch(consent, beforeReservationConsent))
                    return ConsentRequired(context);

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
                        "Select less project context before requesting assistance.",
                        StatusCodes.Status413PayloadTooLarge);
                }
                catch (Exception)
                {
                    return Unavailable(context, "PROJECT_AI_COST_UNAVAILABLE");
                }

                var requestHash = ComputeRequestHash(validRequest, maximumCreditCost);
                await creditLedger.EnsureConsentAsync(accountId, AiGateway.ConsentVersion, cancellationToken);
                var reservation = await creditLedger.TryReserveAsync(
                    accountId,
                    requestId,
                    requestHash,
                    maximumCreditCost,
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
                        maximumCreditCost,
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

                ProjectAiConsentState beforeDispatchConsent;
                try
                {
                    beforeDispatchConsent = await consentStore.ReadOwnAsync(accountId, cancellationToken);
                }
                catch
                {
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    throw;
                }
                if (!ProjectAiConsentPolicy.StillAuthorizesDispatch(consent, beforeDispatchConsent))
                {
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    return ConsentRequired(context);
                }

                StudentProjectRecord? currentProject;
                IResult? currentProjectError;
                try
                {
                    if (usesLocalProjectContext)
                    {
                        var currentLocalContext = await localProjectContextStore.ReadOwnAsync(
                            accountId,
                            projectId,
                            cancellationToken);
                        currentProjectError = LocalProjectContextError(context, currentLocalContext, validRequest);
                        currentProject = currentProjectError is null
                            ? CreateSelectedContextProject(projectId, validRequest)
                            : null;
                    }
                    else
                    {
                        currentProject = await studentProjectStore.ReadOwnAsync(accountId, projectId, cancellationToken);
                        currentProjectError = ProjectContextError(context, currentProject, validRequest.BaseProjectRevision);
                    }
                }
                catch
                {
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    throw;
                }
                projectError = currentProjectError;
                if (projectError is not null)
                {
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    return projectError;
                }
                evidenceError = ProjectAiStageAssistValidator.ValidateSelectedEvidence(validRequest, template, currentProject);
                if (evidenceError is not null)
                {
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    return Invalid(context, evidenceError);
                }
                fieldContextError = ProjectAiStageAssistValidator.BuildSelectedFieldContexts(
                    validRequest,
                    template,
                    currentProject,
                    redact: false,
                    out var currentFieldValues);
                if (fieldContextError is not null)
                {
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    return Invalid(context, fieldContextError);
                }
                if (!selectedFieldValues.SequenceEqual(currentFieldValues))
                {
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    return Error(context, "PROJECT_AI_CONTEXT_STALE", "The selected project context changed before dispatch.", StatusCodes.Status409Conflict);
                }

                // Project activity now references a metadata-only owner/project registry so
                // both local-first and server-stored projects can have auditable AI activity.
                // Cloud project content remains in its existing store; only identity and
                // evidence IDs are mirrored here.
                if (!usesLocalProjectContext)
                {
                    ProjectAiLocalContextWriteOutcome bindingOutcome;
                    try
                    {
                        bindingOutcome = await localProjectContextStore.UpsertOwnAsync(
                            accountId,
                            projectId,
                            currentProject!.Version,
                            template!.TemplateId,
                            template.TemplateVersion,
                            currentProject.Document.EvidenceItems?.Select(item => item.Id!).ToArray() ?? [],
                            cancellationToken);
                    }
                    catch
                    {
                        await ReleaseReservationAsync(creditLedger, accountId, reservation);
                        throw;
                    }
                    if (bindingOutcome is ProjectAiLocalContextWriteOutcome.Stale or ProjectAiLocalContextWriteOutcome.Conflict)
                    {
                        await ReleaseReservationAsync(creditLedger, accountId, reservation);
                        return Error(
                            context,
                            "PROJECT_AI_CONTEXT_STALE",
                            "The project metadata changed before activity could be recorded. No provider request was sent.",
                            StatusCodes.Status409Conflict);
                    }
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
                            consent.Generation,
                            UsesLocalProjectContext: usesLocalProjectContext,
                            BaseProjectBindingGeneration: validRequest.ProjectBindingGeneration),
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

                ProjectAiConsentState immediatelyBeforeProviderConsent;
                try
                {
                    immediatelyBeforeProviderConsent = await consentStore.ReadOwnAsync(accountId, cancellationToken);
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
                if (!ProjectAiConsentPolicy.StillAuthorizesDispatch(consent, immediatelyBeforeProviderConsent))
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
                    return ConsentRequired(context);
                }

                ProjectAiStageAssistOutput? output;
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

                ProjectAiConsentState? afterProviderConsent = null;
                var afterProviderConsentReadFailed = false;
                StudentProjectRecord? afterProviderProject = null;
                IResult? afterProviderProjectError = null;
                var afterProviderProjectReadFailed = false;
                try
                {
                    afterProviderConsent = await consentStore.ReadOwnAsync(accountId, CancellationToken.None);
                }
                catch
                {
                    afterProviderConsentReadFailed = true;
                }
                try
                {
                    if (usesLocalProjectContext)
                    {
                        var afterProviderLocalContext = await localProjectContextStore.ReadOwnAsync(
                            accountId,
                            projectId,
                            CancellationToken.None);
                        afterProviderProjectError = LocalProjectContextError(
                            context,
                            afterProviderLocalContext,
                            validRequest);
                        afterProviderProject = afterProviderProjectError is null
                            ? CreateSelectedContextProject(projectId, validRequest)
                            : null;
                    }
                    else
                    {
                        afterProviderProject = await studentProjectStore.ReadOwnAsync(accountId, projectId, CancellationToken.None);
                        afterProviderProjectError = ProjectContextError(
                            context,
                            afterProviderProject,
                            validRequest.BaseProjectRevision);
                    }
                }
                catch
                {
                    afterProviderProjectReadFailed = true;
                }

                var consentStillAuthorizesDelivery = afterProviderConsent is not null
                    && ProjectAiConsentPolicy.StillAuthorizesDispatch(consent, afterProviderConsent);
                if (!consentStillAuthorizesDelivery)
                {
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    await CompleteActivityAsync(logger, activityStore, accountId, validRequest, requestId, ProjectAiActivityOutcomes.Stale, null);
                    return afterProviderConsentReadFailed
                        ? Unavailable(context, "PROJECT_AI_CONSENT_CHECK_UNAVAILABLE_AFTER_PROVIDER")
                        : Error(
                            context,
                            "PROJECT_AI_CONSENT_REVOKED_AFTER_PROVIDER",
                            "Consent changed during provider processing. The response was withheld and the reservation was released.",
                            StatusCodes.Status403Forbidden);
                }
                if (afterProviderProjectReadFailed)
                {
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    await CompleteActivityAsync(logger, activityStore, accountId, validRequest, requestId, ProjectAiActivityOutcomes.Failed, null);
                    return Unavailable(context, "PROJECT_AI_CONTEXT_CHECK_UNAVAILABLE_AFTER_PROVIDER");
                }
                if (afterProviderProjectError is not null)
                {
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    await CompleteActivityAsync(logger, activityStore, accountId, validRequest, requestId, ProjectAiActivityOutcomes.Stale, null);
                    return Error(
                        context,
                        "PROJECT_AI_CONTEXT_STALE_AFTER_PROVIDER",
                        "The project changed during provider processing. The response was withheld and the reservation was released.",
                        StatusCodes.Status409Conflict);
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

                int settledCreditCost;
                try
                {
                    if (output.Usage is null || !output.Usage.IsValid)
                        throw new InvalidOperationException("The project-AI usage report is invalid.");
                    settledCreditCost = AiCreditPricing.CreditsForCostUsd(
                        pricing.EstimateActualCostUsd(output.Usage));
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
                    return Error(context, "PROJECT_AI_INVALID_USAGE", "The project-AI token usage could not be verified.", StatusCodes.Status502BadGateway);
                }
                if (settledCreditCost > maximumCreditCost)
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
                    return Error(context, "PROJECT_AI_COST_LIMIT", "Project AI usage exceeded the reserved credit amount.", StatusCodes.Status502BadGateway);
                }

                if (ProjectAiStageAssistValidator.ValidateOutput(validRequest, template, afterProviderProject, output) is not null)
                {
                    await CompleteActivityAsync(logger, activityStore, accountId, validRequest, requestId, ProjectAiActivityOutcomes.Failed, null);
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    return Error(context, "PROJECT_AI_INVALID_RESPONSE", "The project-AI response could not be verified.", StatusCodes.Status502BadGateway);
                }

                var rawSelectedFields = selectedFieldValues
                    .ToDictionary(field => field.Id, field => field.Value, StringComparer.Ordinal);
                var responseOutput = ProjectAiStageAssistValidator.BindBeforeValues(rawSelectedFields, output);
                if (!ProjectAiStageAssistValidator.IsBoundResponseWithinLimits(responseOutput))
                {
                    await CompleteActivityAsync(logger, activityStore, accountId, validRequest, requestId, ProjectAiActivityOutcomes.Failed, null);
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
                        maximumCreditCost,
                        settledCreditCost,
                        CancellationToken.None);
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
                    return Unavailable(context, "PROJECT_AI_USAGE_SETTLEMENT_UNKNOWN");
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
                    return Unavailable(context, "PROJECT_AI_USAGE_SETTLEMENT_UNKNOWN");
                }

                return Results.Json(
                    new ProjectAiStageAssistResponse(
                        ProjectAiStageAssistValidator.Schema,
                        ProjectAiStageAssistValidator.Version,
                        "preview",
                        ProjectAiStageAssistValidator.ProjectMode,
                        validRequest.ProjectId!,
                        validRequest.BaseProjectRevision!.Value,
                        validRequest.ProjectBindingGeneration,
                        consent.Generation,
                        validRequest.StageId!,
                        validRequest.OperationId!,
                        responseOutput,
                        ProjectAiStageAssistValidator.CreateEvaluationPreview(
                            validRequest,
                            template!,
                            afterProviderProject!,
                        responseOutput),
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

    private static string ComputeRequestHash(ProjectAiStageAssistRequest request, int maximumCreditCost)
    {
        var canonical = request with
        {
            SelectedFieldIds = request.SelectedFieldIds is null
                ? null
                : request.SelectedFieldIds.OrderBy(fieldId => fieldId, StringComparer.Ordinal).ToArray(),
            SelectedEvidenceIds = request.SelectedEvidenceIds is null
                ? null
                : request.SelectedEvidenceIds.OrderBy(evidenceId => evidenceId, StringComparer.Ordinal).ToArray(),
        };
        var bytes = JsonSerializer.SerializeToUtf8Bytes(new
        {
            Operation = "project-ai-stage-assist:v1",
            Request = canonical,
            MaximumCreditCost = maximumCreditCost,
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

    private static IResult? LocalProjectContextError(
        HttpContext context,
        ProjectAiLocalProjectContext? project,
        ProjectAiStageAssistRequest request)
    {
        if (project is null)
            return Error(context, "PROJECT_NOT_FOUND", "The account-bound local project was not registered.", StatusCodes.Status404NotFound);
        if (project.CurrentRevision != request.BaseProjectRevision)
            return Error(context, "PROJECT_AI_CONTEXT_STALE", "The local project revision changed. Save and retry assistance.", StatusCodes.Status409Conflict);
        if (project.BindingGeneration != request.ProjectBindingGeneration)
            return Error(context, "PROJECT_AI_CONTEXT_STALE", "The local project context changed. Review the saved project and retry assistance.", StatusCodes.Status409Conflict);
        if (!string.Equals(project.TemplateId, request.TemplateId, StringComparison.Ordinal)
            || project.TemplateVersion != request.TemplateVersion)
            return Error(context, "PROJECT_AI_TEMPLATE_MISMATCH", "The local project method changed. Reload the project before requesting assistance.", StatusCodes.Status409Conflict);
        if (request.SelectedEvidenceIds!.Any(id => !project.AvailableEvidenceIds.Contains(id, StringComparer.Ordinal)))
            return Error(context, "PROJECT_AI_EVIDENCE_NOT_FOUND", "One or more selected evidence items are no longer available in this project.", StatusCodes.Status400BadRequest);
        return null;
    }

    private static StudentProjectRecord CreateSelectedContextProject(
        Guid projectId,
        ProjectAiStageAssistRequest request)
    {
        var now = DateTimeOffset.UtcNow;
        var selectedEvidence = request.SelectedEvidence!.Select(item => new StudentProjectEvidenceItem(
            item.Id,
            item.Kind switch
            {
                "SOURCE" => StudentProjectEvidenceKind.Source,
                "DATA" => StudentProjectEvidenceKind.Data,
                "OBSERVATION" => StudentProjectEvidenceKind.Observation,
                _ => throw new InvalidOperationException("Validated Project AI evidence kind expected."),
            },
            item.Label,
            item.Summary,
            item.Origin)).ToArray();
        var document = new StudentProjectDocument(
            Title: null,
            TaskBrief: null,
            Question: null,
            Method: null,
            Hypothesis: null,
            Criteria: null,
            EvidenceItems: selectedEvidence,
            CriterionEvidenceLinks: null,
            Analysis: null,
            Claim: null,
            ClaimEvidenceIds: null,
            Limitations: null,
            NextAction: null);
        return new StudentProjectRecord(
            projectId,
            request.BaseProjectRevision!.Value,
            document,
            now,
            now);
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

    private static async Task CompleteActivityAsync(
        ILogger logger,
        IProjectAiActivityStore activityStore,
        Guid accountId,
        ProjectAiStageAssistRequest request,
        string requestId,
        string outcome,
        int? resultProjectRevision,
        string? requestedSettlementOutcome = null,
        string? settlementHash = null)
    {
        try
        {
            await activityStore.CompleteOwnAsync(
                accountId,
                Guid.Parse(request.InstallationId!),
                requestId,
                outcome,
                resultProjectRevision,
                requestedSettlementOutcome,
                settlementHash,
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
