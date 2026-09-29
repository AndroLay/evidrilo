using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using Evidrilo.Api.Ai;
using Evidrilo.Api.Auth;
using Evidrilo.Api.Common;
using Evidrilo.Api.ProjectTemplates;
using Evidrilo.Api.Projects;

namespace Evidrilo.Api.ProjectAi;

public sealed record ProjectAiScaffoldProviderRequest(
    Guid AccountId,
    string RequestId,
    string Operation,
    ProjectTemplateCatalogEntry Template,
    int? BaseProjectRevision,
    string AssignmentBrief,
    string? ResearchQuestion,
    string? StudentQuestion,
    IReadOnlyDictionary<string, string> CurrentFields,
    IReadOnlyList<string> Constraints,
    string Locale)
{
    public string? StageId { get; init; }
    public string? StageOperationId { get; init; }
    public IReadOnlyList<string>? AllowedOutputFieldIds { get; init; }
}

/// <summary>
/// Production implementations must apply the approved project-AI consent,
/// privacy, provider-spend, and variable credit-cost policies. The endpoint
/// passes redacted student text and never persists it.
/// </summary>
public interface IProjectAiScaffoldGenerator
{
    bool IsEnabled { get; }

    int EstimateMaximumCreditCost(ProjectAiScaffoldProviderRequest request);

    Task<ProjectAiScaffoldOutput?> GenerateAsync(
        ProjectAiScaffoldProviderRequest request,
        CancellationToken cancellationToken);
}

public sealed class DisabledProjectAiScaffoldGenerator : IProjectAiScaffoldGenerator
{
    public bool IsEnabled => false;

    public int EstimateMaximumCreditCost(ProjectAiScaffoldProviderRequest request) => 1;

    public Task<ProjectAiScaffoldOutput?> GenerateAsync(
        ProjectAiScaffoldProviderRequest request,
        CancellationToken cancellationToken) => Task.FromResult<ProjectAiScaffoldOutput?>(null);
}

public static class ProjectAiScaffoldEndpoints
{
    private static readonly JsonSerializerOptions RequestJsonOptions = new(JsonSerializerDefaults.Web)
    {
        UnmappedMemberHandling = System.Text.Json.Serialization.JsonUnmappedMemberHandling.Disallow,
    };
    private static readonly JsonSerializerOptions ResponseJsonOptions = new(JsonSerializerDefaults.Web)
    {
        DefaultIgnoreCondition = JsonIgnoreCondition.Never,
    };
    private static readonly JsonSerializerOptions FingerprintJsonOptions = new(JsonSerializerDefaults.Web)
    {
        DefaultIgnoreCondition = JsonIgnoreCondition.Never,
    };

    public static IEndpointRouteBuilder MapProjectAiScaffoldEndpoints(this IEndpointRouteBuilder endpoints)
    {
        endpoints.MapPost(
            "/v1/project-ai/scaffold",
            async (
                HttpContext context,
                IProjectAiScaffoldGenerator generator,
                AiProviderOptions pricing,
                IAiCreditLedger creditLedger,
                IProjectTemplateStore templateStore,
                IProjectAiConsentStore consentStore,
                IStudentProjectStore studentProjectStore,
                CancellationToken cancellationToken) =>
            {
                if (!AuthenticatedUser.TryGetAccountId(context.User, out var accountId))
                {
                    return Results.Json(
                        ApiErrors.Create(context, "AUTH_REQUIRED", "Authentication is required."),
                        statusCode: StatusCodes.Status401Unauthorized);
                }
                if (!AuthenticatedUser.IsEmailVerified(context.User))
                {
                    return Results.Json(
                        ApiErrors.Create(context, "FORBIDDEN", "A verified account is required for project AI."),
                        statusCode: StatusCodes.Status403Forbidden);
                }
                var consent = await consentStore.ReadOwnAsync(accountId, cancellationToken);
                if (!consent.Granted
                    || !string.Equals(
                        consent.PolicyVersion,
                        ProjectAiConsentPolicy.CurrentPolicyVersion,
                        StringComparison.Ordinal))
                {
                    return Results.Json(
                        ApiErrors.Create(
                            context,
                            "PROJECT_AI_CONSENT_REQUIRED",
                            "Review and accept the current Project AI data-use policy before requesting assistance."),
                        statusCode: StatusCodes.Status403Forbidden);
                }
                if (!TryGetRequestId(context, out var requestId))
                    return Invalid(context, "INVALID_IDEMPOTENCY_KEY");

                var payload = await RequestJsonReader.ReadAsync(
                    context.Request,
                    "INVALID_PROJECT_AI_REQUEST",
                    "The project-AI request is invalid.",
                    cancellationToken);
                ProjectAiScaffoldRequest? request;
                try
                {
                    request = payload.Deserialize<ProjectAiScaffoldRequest>(RequestJsonOptions);
                }
                catch (JsonException)
                {
                    return Invalid(context, "INVALID_PROJECT_AI_REQUEST");
                }

                var requestError = ProjectAiScaffoldValidator.ValidateRequest(request);
                if (requestError is not null) return Invalid(context, requestError);
                var validRequest = request!;
                var operation = validRequest.Operation!;

                // Do not consult the catalog or touch student text while the
                // real provider/cost/privacy path remains deliberately closed.
                if (!generator.IsEnabled)
                    return Unavailable(context, "PROJECT_AI_NOT_READY");

                Guid? assistProjectId = null;
                if (operation == ProjectAiScaffoldValidator.AssistOperation)
                {
                    assistProjectId = Guid.Parse(validRequest.ProjectId!);
                    var savedProject = await studentProjectStore.ReadOwnAsync(
                        accountId,
                        assistProjectId.Value,
                        cancellationToken);
                    var projectError = ProjectContextError(
                        context,
                        savedProject,
                        validRequest.BaseProjectRevision);
                    if (projectError is not null) return projectError;
                }

                var template = await templateStore.GetPublishedAsync(
                    validRequest.TemplateId!,
                    validRequest.TemplateVersion,
                    cancellationToken);
                var fieldError = ProjectAiScaffoldValidator.ValidateRequestFields(validRequest, template);
                if (fieldError is not null)
                {
                    var status = fieldError == "PROJECT_AI_TEMPLATE_NOT_READY"
                        ? StatusCodes.Status404NotFound
                        : StatusCodes.Status400BadRequest;
                    return Results.Json(ApiErrors.Create(context, fieldError, "The project template or fields are unavailable."),
                        statusCode: status);
                }

                // Consent may be revoked while the request is being validated.
                // Recheck before any credit is held, then again immediately
                // before dispatch. A request already dispatched cannot be
                // recalled from a provider.
                var dispatchConsent = await consentStore.ReadOwnAsync(accountId, cancellationToken);
                if (!ProjectAiConsentPolicy.StillAuthorizesDispatch(consent, dispatchConsent))
                    return Results.Json(
                        ApiErrors.Create(
                            context,
                            "PROJECT_AI_CONSENT_REQUIRED",
                            "Project AI consent changed before this request could be sent."),
                        statusCode: StatusCodes.Status403Forbidden);

                var providerRequest = new ProjectAiScaffoldProviderRequest(
                    accountId,
                    requestId,
                    operation,
                    template!,
                    validRequest.BaseProjectRevision,
                    AiRedactor.Redact(validRequest.AssignmentBrief!),
                    validRequest.ResearchQuestion is null ? null : AiRedactor.Redact(validRequest.ResearchQuestion),
                    validRequest.StudentQuestion is null ? null : AiRedactor.Redact(validRequest.StudentQuestion),
                    (validRequest.CurrentFields ?? new Dictionary<string, string>())
                        .ToDictionary(pair => pair.Key, pair => AiRedactor.Redact(pair.Value), StringComparer.Ordinal),
                    (validRequest.Constraints ?? Array.Empty<string>()).Select(AiRedactor.Redact).ToArray(),
                    validRequest.Locale!);
                int maximumCreditCost;
                try
                {
                    maximumCreditCost = generator.EstimateMaximumCreditCost(providerRequest);
                    if (maximumCreditCost is < 1 or > AiCreditPricing.MaximumCreditsPerRequest)
                        return Unavailable(context, "PROJECT_AI_COST_UNAVAILABLE");
                }
                catch (Exception)
                {
                    return Unavailable(context, "PROJECT_AI_COST_UNAVAILABLE");
                }

                // The project-data consent above remains authoritative for
                // dispatch. The shared ledger's consent version only seeds
                // account-scoped credit grants after that separate decision.
                await creditLedger.EnsureConsentAsync(accountId, AiGateway.ConsentVersion, cancellationToken);
                var requestHash = ComputeScaffoldRequestHash(validRequest, maximumCreditCost);
                var reservation = await creditLedger.TryReserveAsync(
                    accountId,
                    requestId,
                    requestHash,
                    maximumCreditCost,
                    cancellationToken);
                if (reservation is null)
                    return Results.Json(
                        ApiErrors.Create(context, "AI_CREDITS_INSUFFICIENT", "There are not enough AI credits for this request."),
                        statusCode: StatusCodes.Status402PaymentRequired);

                if (reservation.IsReplay)
                    return Results.Json(
                        ApiErrors.Create(
                            context,
                            reservation.ExistingStatus == "reserved"
                                ? "PROJECT_AI_REQUEST_IN_PROGRESS"
                                : "PROJECT_AI_REQUEST_REPLAYED",
                            "This project-AI request key has already been used. Start a new request to try again."),
                        statusCode: StatusCodes.Status409Conflict);

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
                    return Results.Json(
                        ApiErrors.Create(context, "PROJECT_AI_RESERVATION_CONFLICT", "The project-AI credit reservation is no longer available."),
                        statusCode: StatusCodes.Status409Conflict);
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
                    return Results.Json(
                        ApiErrors.Create(
                            context,
                            "PROJECT_AI_CONSENT_REQUIRED",
                            "Project AI consent changed before this request could be sent."),
                        statusCode: StatusCodes.Status403Forbidden);
                }

                if (assistProjectId is { } projectId)
                {
                    StudentProjectRecord? currentProject;
                    try
                    {
                        currentProject = await studentProjectStore.ReadOwnAsync(
                            accountId,
                            projectId,
                            cancellationToken);
                    }
                    catch
                    {
                        await ReleaseReservationAsync(creditLedger, accountId, reservation);
                        throw;
                    }

                    var projectError = ProjectContextError(
                        context,
                        currentProject,
                        validRequest.BaseProjectRevision);
                    if (projectError is not null)
                    {
                        await ReleaseReservationAsync(creditLedger, accountId, reservation);
                        return projectError;
                    }
                }

                ProjectAiScaffoldOutput? output;
                try
                {
                    output = await generator.GenerateAsync(providerRequest, cancellationToken);
                }
                catch (OperationCanceledException)
                {
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    if (cancellationToken.IsCancellationRequested) throw;
                    return Unavailable(context, "PROJECT_AI_PROVIDER_TIMEOUT");
                }
                catch (Exception)
                {
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    return Unavailable(context, "PROJECT_AI_PROVIDER_UNAVAILABLE");
                }

                if (output is null)
                {
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    return Results.Json(
                        ApiErrors.Create(context, "PROJECT_AI_REFUSED", "Project AI did not return a proposal."),
                        statusCode: StatusCodes.Status422UnprocessableEntity);
                }

                var outputError = ProjectAiScaffoldValidator.ValidateOutput(template, output);
                if (outputError is not null)
                {
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    return Results.Json(
                        ApiErrors.Create(context, "PROJECT_AI_INVALID_RESPONSE", "The project-AI response could not be verified."),
                        statusCode: StatusCodes.Status502BadGateway);
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
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    return Results.Json(
                        ApiErrors.Create(context, "PROJECT_AI_INVALID_USAGE", "The project-AI token usage could not be verified."),
                        statusCode: StatusCodes.Status502BadGateway);
                }
                if (settledCreditCost > maximumCreditCost)
                {
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    return Results.Json(
                        ApiErrors.Create(context, "PROJECT_AI_COST_LIMIT", "Project AI usage exceeded the reserved credit amount."),
                        statusCode: StatusCodes.Status502BadGateway);
                }

                bool reservationStillHeld;
                try
                {
                    reservationStillHeld = await settlementLedger.MarkProjectAiPreviewReadyAsync(
                        accountId,
                        reservation,
                        requestHash,
                        maximumCreditCost,
                        settledCreditCost,
                        cancellationToken);
                }
                catch
                {
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    throw;
                }
                if (!reservationStillHeld)
                {
                    await ReleaseReservationAsync(creditLedger, accountId, reservation);
                    return Results.Json(
                        ApiErrors.Create(context, "PROJECT_AI_RESERVATION_EXPIRED", "The project-AI reservation expired before a preview was ready."),
                        statusCode: StatusCodes.Status409Conflict);
                }

                return Results.Json(
                    new ProjectAiScaffoldResponse(
                        ProjectAiScaffoldValidator.Schema,
                        ProjectAiScaffoldValidator.Version,
                        "preview",
                        output,
                        validRequest.BaseProjectRevision,
                        null,
                        requestId,
                        settledCreditCost,
                        operation,
                        validRequest.ProjectId),
                    options: ResponseJsonOptions);
            })
            .RequireAuthorization()
            .RequireRateLimiting("ai");

        endpoints.MapPost(
            "/v1/project-ai/scaffold/settlement",
            async (
                HttpContext context,
                IAiCreditLedger creditLedger,
                CancellationToken cancellationToken) =>
            {
                if (!AuthenticatedUser.TryGetAccountId(context.User, out var accountId))
                    return Results.Json(
                        ApiErrors.Create(context, "AUTH_REQUIRED", "Authentication is required."),
                        statusCode: StatusCodes.Status401Unauthorized);
                if (!AuthenticatedUser.IsEmailVerified(context.User))
                    return Results.Json(
                        ApiErrors.Create(context, "FORBIDDEN", "A verified account is required for project AI."),
                        statusCode: StatusCodes.Status403Forbidden);

                var payload = await RequestJsonReader.ReadAsync(
                    context.Request,
                    "INVALID_PROJECT_AI_SETTLEMENT",
                    "The project-AI settlement is invalid.",
                    cancellationToken);
                ProjectAiScaffoldSettlementRequest? request;
                try
                {
                    request = payload.Deserialize<ProjectAiScaffoldSettlementRequest>(RequestJsonOptions);
                }
                catch (JsonException)
                {
                    return Invalid(context, "INVALID_PROJECT_AI_SETTLEMENT");
                }

                var requestError = ProjectAiScaffoldValidator.ValidateSettlementRequest(request);
                if (requestError is not null) return Invalid(context, requestError);
                var validRequest = request!;
                if (!TryGetRequestId(context, out var idempotencyKey))
                    return Invalid(context, "INVALID_IDEMPOTENCY_KEY");
                if (!string.Equals(idempotencyKey, validRequest.RequestId, StringComparison.Ordinal))
                    return Results.Json(
                        ApiErrors.Create(
                            context,
                            "PROJECT_AI_SETTLEMENT_KEY_MISMATCH",
                            "Use the same idempotency key that identifies the project-AI preview."),
                        statusCode: StatusCodes.Status409Conflict);

                if (creditLedger is not IProjectAiCreditSettlementLedger settlementLedger)
                    return Unavailable(context, "PROJECT_AI_SETTLEMENT_NOT_READY");

                var apply = validRequest.Decision == "apply";
                var settlementHash = ComputeSettlementHash(validRequest.RequestId!, validRequest.Decision!);
                var result = await settlementLedger.SettleProjectAiAsync(
                    accountId,
                    validRequest.RequestId!,
                    settlementHash,
                    apply,
                    cancellationToken);
                if (result.Status == ProjectAiCreditSettlementStatus.NotFound)
                    return Results.Json(
                        ApiErrors.Create(context, "PROJECT_AI_RESERVATION_NOT_FOUND", "The project-AI reservation was not found."),
                        statusCode: StatusCodes.Status404NotFound);
                if (result.Status == ProjectAiCreditSettlementStatus.Conflict)
                    return Results.Json(
                        ApiErrors.Create(context, "PROJECT_AI_SETTLEMENT_CONFLICT", "The reservation was settled, expired, or belongs to another operation."),
                        statusCode: StatusCodes.Status409Conflict);

                var status = result.Status switch
                {
                    ProjectAiCreditSettlementStatus.Applied => "applied",
                    ProjectAiCreditSettlementStatus.Dismissed => "dismissed",
                    _ => null,
                };
                if (status is null)
                    return Unavailable(context, "PROJECT_AI_SETTLEMENT_NOT_READY");
                return Results.Json(
                    new ProjectAiScaffoldSettlementResponse(
                        ProjectAiScaffoldValidator.SettlementSchema,
                        ProjectAiScaffoldValidator.SettlementVersion,
                        status,
                        validRequest.RequestId!,
                        result.CreditCost),
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

    private static string ComputeScaffoldRequestHash(ProjectAiScaffoldRequest request, int creditCost)
    {
        var canonical = request with
        {
            CurrentFields = request.CurrentFields is null
                ? null
                : request.CurrentFields
                    .OrderBy(pair => pair.Key, StringComparer.Ordinal)
                    .ToDictionary(pair => pair.Key, pair => pair.Value, StringComparer.Ordinal),
        };
        var bytes = JsonSerializer.SerializeToUtf8Bytes(new
        {
            Operation = "project-ai-preview:v1",
            CreditCost = creditCost,
            Request = canonical,
        }, FingerprintJsonOptions);
        return Convert.ToHexString(SHA256.HashData(bytes)).ToLowerInvariant();
    }

    private static string ComputeSettlementHash(string requestId, string decision)
    {
        var bytes = Encoding.UTF8.GetBytes($"project-ai-scaffold-settlement:v1\n{requestId}\n{decision}");
        return Convert.ToHexString(SHA256.HashData(bytes)).ToLowerInvariant();
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

    private static IResult Invalid(HttpContext context, string code) => Results.Json(
        ApiErrors.Create(context, code, "The project-AI request is invalid."),
        statusCode: StatusCodes.Status400BadRequest);

    private static IResult? ProjectContextError(
        HttpContext context,
        StudentProjectRecord? project,
        int? baseProjectRevision)
    {
        if (project is null)
            return Results.Json(
                ApiErrors.Create(context, "PROJECT_NOT_FOUND", "The student project was not found."),
                statusCode: StatusCodes.Status404NotFound);
        if (project.Version != baseProjectRevision)
            return Results.Json(
                ApiErrors.Create(context, "PROJECT_VERSION_CONFLICT", "The student project changed. Reload it before requesting assistance."),
                statusCode: StatusCodes.Status409Conflict);
        return null;
    }

    private static IResult Unavailable(HttpContext context, string code) => Results.Json(
        ApiErrors.Create(context, code, "Project AI is unavailable. Your manual project workflow remains available."),
        statusCode: StatusCodes.Status503ServiceUnavailable);
}
