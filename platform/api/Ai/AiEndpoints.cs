using System.Text.Json;
using System.Text.Json.Serialization;
using Evidrilo.Api.Auth;
using Evidrilo.Api.Common;
using Evidrilo.Api.Content;

namespace Evidrilo.Api.Ai;

public sealed record AiAssistResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("status")] string Status,
    [property: JsonPropertyName("text"), JsonIgnore(Condition = JsonIgnoreCondition.Never)] string? Text,
    [property: JsonPropertyName("reasonCode"), JsonIgnore(Condition = JsonIgnoreCondition.Never)] string? ReasonCode,
    [property: JsonPropertyName("promptVersion")] string PromptVersion,
    [property: JsonPropertyName("groundedAnchorIds")] IReadOnlyList<string> GroundedAnchorIds,
    [property: JsonPropertyName("requestId")] string RequestId);

public sealed record AiCreditGrantResponse(
    [property: JsonPropertyName("grantKind")] string GrantKind,
    [property: JsonPropertyName("grantKey")] string GrantKey,
    [property: JsonPropertyName("granted")] int Granted,
    [property: JsonPropertyName("reserved")] int Reserved,
    [property: JsonPropertyName("consumed")] int Consumed,
    [property: JsonPropertyName("available")] int Available,
    [property: JsonPropertyName("expiresAt")] DateTimeOffset? ExpiresAt);

public sealed record AiCreditsResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("consentRecorded")] bool ConsentRecorded,
    [property: JsonPropertyName("available")] int Available,
    [property: JsonPropertyName("grants")] IReadOnlyList<AiCreditGrantResponse> Grants,
    [property: JsonPropertyName("requestId")] string RequestId);

public sealed record AiConversationStartRequest(
    [property: JsonRequired, JsonPropertyName("optedIn")] bool OptedIn,
    [property: JsonRequired, JsonPropertyName("locale")] string Locale,
    [property: JsonRequired, JsonPropertyName("context")] AiAssistContextRequest Context,
    [property: JsonPropertyName("learnerLimitation")] string? LearnerLimitation);

public sealed record AiConversationTurnRequest(
    [property: JsonRequired, JsonPropertyName("purpose")] AiAssistPurpose Purpose,
    [property: JsonRequired, JsonPropertyName("input")] string Input,
    [property: JsonRequired, JsonPropertyName("locale")] string Locale,
    [property: JsonRequired, JsonPropertyName("optedIn")] bool OptedIn,
    [property: JsonRequired, JsonPropertyName("context")] AiAssistContextRequest Context,
    [property: JsonPropertyName("learnerLimitation")] string? LearnerLimitation,
    [property: JsonRequired, JsonPropertyName("history")] IReadOnlyList<AiConversationMessage> History);

public sealed record AiConversationSessionResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("status")] string Status,
    [property: JsonPropertyName("sessionId")] string SessionId,
    [property: JsonPropertyName("caseVersionId")] string CaseVersionId,
    [property: JsonPropertyName("contextFingerprint")] string ContextFingerprint,
    [property: JsonPropertyName("turnLimit")] int TurnLimit,
    [property: JsonPropertyName("turnsUsed")] int TurnsUsed,
    [property: JsonPropertyName("expiresAt")] DateTimeOffset ExpiresAt,
    [property: JsonPropertyName("requestId")] string RequestId);

public sealed record AiConversationProposalResponse(
    [property: JsonPropertyName("field")] string Field,
    [property: JsonPropertyName("beforeValue"), JsonIgnore(Condition = JsonIgnoreCondition.Never)] string? BeforeValue,
    [property: JsonPropertyName("suggestedValue")] string SuggestedValue,
    [property: JsonPropertyName("anchorIds")] IReadOnlyList<string> AnchorIds);

public sealed record AiConversationTurnResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("status")] string Status,
    [property: JsonPropertyName("kind")] string? Kind,
    [property: JsonPropertyName("text")] string? Text,
    [property: JsonPropertyName("reasonCode")] string? ReasonCode,
    [property: JsonPropertyName("groundedAnchorIds")] IReadOnlyList<string> GroundedAnchorIds,
    [property: JsonPropertyName("proposal")] AiConversationProposalResponse? Proposal,
    [property: JsonPropertyName("autoApplied")] bool AutoApplied,
    [property: JsonPropertyName("turnsUsed")] int TurnsUsed,
    [property: JsonPropertyName("turnsRemaining")] int TurnsRemaining,
    [property: JsonPropertyName("requestId")] string RequestId);

public sealed record AiConversationClearResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("status")] string Status,
    [property: JsonPropertyName("sessionId")] string SessionId,
    [property: JsonPropertyName("requestId")] string RequestId);

public static class AiEndpoints
{
    private static readonly JsonSerializerOptions RequestJsonOptions = new(JsonSerializerDefaults.Web)
    {
        PropertyNamingPolicy = JsonNamingPolicy.CamelCase,
        UnmappedMemberHandling = JsonUnmappedMemberHandling.Disallow,
    };

    static AiEndpoints()
    {
        RequestJsonOptions.Converters.Add(new JsonStringEnumConverter(JsonNamingPolicy.SnakeCaseLower));
    }

    public static IEndpointRouteBuilder MapAiEndpoints(this IEndpointRouteBuilder endpoints)
    {
        endpoints.MapGet(
            "/v1/ai/credits",
            async (
                HttpContext context,
                IAiCreditLedger creditLedger,
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
                        ApiErrors.Create(context, "FORBIDDEN", "You are not allowed to view AI credits."),
                        statusCode: StatusCodes.Status403Forbidden);
                }

                var balance = await creditLedger.GetBalanceAsync(accountId, cancellationToken);
                return Results.Ok(ToResponse(balance, RequestIdMiddleware.Get(context)));
            })
            .RequireAuthorization()
            .RequireRateLimiting("api");

        endpoints.MapPost(
            "/v1/ai/assist",
            async (
                HttpContext context,
                AiGateway gateway,
                IAiAuditStore auditStore,
                ICaseStore caseStore,
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
                        ApiErrors.Create(context, "FORBIDDEN", "You are not allowed to request AI assistance."),
                        statusCode: StatusCodes.Status403Forbidden);
                }

                var payload = await RequestJsonReader.ReadAsync(
                    context.Request,
                    "INVALID_AI_REQUEST",
                    "The AI request is invalid.",
                    cancellationToken);
                AiAssistRequest? request;
                try
                {
                    request = payload.Deserialize<AiAssistRequest>(RequestJsonOptions);
                }
                catch (JsonException)
                {
                    return Results.Json(
                        ApiErrors.Create(context, "INVALID_AI_REQUEST", "The AI request is invalid."),
                        statusCode: StatusCodes.Status400BadRequest);
                }

                if (request is null)
                {
                    return Results.Json(
                        ApiErrors.Create(context, "INVALID_AI_REQUEST", "The AI request is invalid."),
                        statusCode: StatusCodes.Status400BadRequest);
                }

                if (request.OptedIn)
                {
                    if (request.Context is null)
                    {
                        return Results.Json(
                            ApiErrors.Create(context, "INVALID_AI_CONTEXT", "A grounded case context is required."),
                            statusCode: StatusCodes.Status400BadRequest);
                    }

                    var publishedCase = await caseStore.GetPublishedAsync(
                        accountId,
                        request.Context.CaseVersionId,
                        cancellationToken);
                    if (publishedCase is null)
                    {
                        return Results.Json(
                            ApiErrors.Create(context, "AI_CONTEXT_CASE_NOT_FOUND", "The grounded case is not available."),
                            statusCode: StatusCodes.Status404NotFound);
                    }

                    var grounded = AiContextBuilder.TryBuild(
                        publishedCase,
                        request.Context,
                        request.Input);
                    if (!grounded.IsValid || grounded.Context is null)
                    {
                        return Results.Json(
                            ApiErrors.Create(
                                context,
                                grounded.ReasonCode ?? "INVALID_AI_CONTEXT",
                                "The AI context does not match the published case."),
                            statusCode: StatusCodes.Status400BadRequest);
                    }

                    request = request with { Input = grounded.Context.Prompt };
                }

                var operationId = context.Request.Headers["Idempotency-Key"].FirstOrDefault()
                    ?? RequestIdMiddleware.Get(context);
                var result = await gateway.GenerateAsync(accountId, request, operationId, cancellationToken);
                if (!string.Equals(result.ReasonCode, "AI_OPT_IN_REQUIRED", StringComparison.Ordinal))
                {
                    await auditStore.RecordAsync(
                        accountId,
                        operationId,
                        result.Audit,
                        result.ReasonCode,
                        cancellationToken);
                }
                return Results.Ok(new AiAssistResponse(
                    "evidrilo.ai-assist-result",
                    "1",
                    result.Status,
                    result.Text,
                    result.ReasonCode,
                    result.Audit.PromptVersion,
                    result.GroundedAnchorIds,
                    RequestIdMiddleware.Get(context)));
            })
            .RequireAuthorization()
            .RequireRateLimiting("ai");

        endpoints.MapPost(
            "/v1/ai/conversations",
            async (
                HttpContext context,
                IAiConversationStore conversationStore,
                ICaseStore caseStore,
                CancellationToken cancellationToken) =>
            {
                if (!TryGetVerifiedAccount(context, "start an AI conversation", out var accountId, out var error))
                    return error!;

                if (!TryReadIdempotencyKey(context, out var idempotencyKey))
                    return Results.Json(
                        ApiErrors.Create(context, "INVALID_IDEMPOTENCY_KEY", "A valid Idempotency-Key header is required."),
                        statusCode: StatusCodes.Status400BadRequest);

                var payload = await RequestJsonReader.ReadAsync(
                    context.Request,
                    "INVALID_AI_CONVERSATION",
                    "The conversation request is invalid.",
                    cancellationToken);
                AiConversationStartRequest? request;
                try
                {
                    request = payload.Deserialize<AiConversationStartRequest>(RequestJsonOptions);
                }
                catch (JsonException)
                {
                    request = null;
                }

                if (request is null
                    || !request.OptedIn
                    || request.Context is null
                    || !IsValidLocale(request.Locale))
                    return Results.Json(
                        ApiErrors.Create(context, request is { OptedIn: false } ? "AI_OPT_IN_REQUIRED" : "INVALID_AI_CONVERSATION", "A valid, opted-in project context is required."),
                        statusCode: request is { OptedIn: false } ? StatusCodes.Status403Forbidden : StatusCodes.Status400BadRequest);

                var publishedCase = await caseStore.GetPublishedAsync(
                    accountId,
                    request.Context.CaseVersionId,
                    cancellationToken);
                if (publishedCase is null)
                    return Results.Json(
                        ApiErrors.Create(context, "AI_CONTEXT_CASE_NOT_FOUND", "The grounded case is not available."),
                        statusCode: StatusCodes.Status404NotFound);

                var grounded = AiContextBuilder.TryBuild(
                    publishedCase,
                    request.Context,
                    "Start a bounded, case-specific conversation.",
                    request.LearnerLimitation);
                if (!grounded.IsValid || grounded.Context is null)
                    return Results.Json(
                        ApiErrors.Create(context, grounded.ReasonCode ?? "INVALID_AI_CONTEXT", "The AI context does not match the published case."),
                        statusCode: StatusCodes.Status400BadRequest);

                var fingerprint = AiConversationFingerprint.Create(
                    publishedCase,
                    request.Context,
                    request.LearnerLimitation);
                var session = await conversationStore.CreateAsync(
                    accountId,
                    idempotencyKey,
                    publishedCase.CaseVersionId,
                    fingerprint,
                    cancellationToken);

                return Results.Ok(new AiConversationSessionResponse(
                    "evidrilo.ai-conversation-session",
                    "1",
                    "active",
                    session.SessionId.ToString("D"),
                    session.CaseVersionId,
                    session.ContextFingerprint,
                    NpgsqlAiConversationStore.TurnLimit,
                    session.TurnsUsed,
                    session.ExpiresAt,
                    RequestIdMiddleware.Get(context)));
            })
            .RequireAuthorization()
            .RequireRateLimiting("ai");

        endpoints.MapPost(
            "/v1/ai/conversations/{sessionId:guid}/turns",
            async (
                HttpContext context,
                Guid sessionId,
                IAiConversationStore conversationStore,
                AiConversationGateway conversationGateway,
                IAiAuditStore auditStore,
                ICaseStore caseStore,
                CancellationToken cancellationToken) =>
            {
                if (!TryGetVerifiedAccount(context, "request AI conversation assistance", out var accountId, out var error))
                    return error!;

                if (!TryReadIdempotencyKey(context, out var idempotencyKey))
                    return Results.Json(
                        ApiErrors.Create(context, "INVALID_IDEMPOTENCY_KEY", "A valid Idempotency-Key header is required."),
                        statusCode: StatusCodes.Status400BadRequest);

                var payload = await RequestJsonReader.ReadAsync(
                    context.Request,
                    "INVALID_AI_CONVERSATION",
                    "The conversation request is invalid.",
                    cancellationToken);
                AiConversationTurnRequest? request;
                try
                {
                    request = payload.Deserialize<AiConversationTurnRequest>(RequestJsonOptions);
                }
                catch (JsonException)
                {
                    request = null;
                }

                if (request is null
                    || request.Context is null
                    || request.History is null
                    || !Enum.IsDefined(request.Purpose)
                    || !IsValidLocale(request.Locale)
                    || string.IsNullOrWhiteSpace(request.Input)
                    || request.Input.Length > AiConversationPrompt.MaxMessageLength)
                    return Results.Json(
                        ApiErrors.Create(context, "INVALID_AI_CONVERSATION", "The conversation request is invalid."),
                        statusCode: StatusCodes.Status400BadRequest);
                if (!request.OptedIn)
                    return Results.Json(
                        ApiErrors.Create(context, "AI_OPT_IN_REQUIRED", "Explicit AI consent is required for each conversation turn."),
                        statusCode: StatusCodes.Status403Forbidden);

                var publishedCase = await caseStore.GetPublishedAsync(
                    accountId,
                    request.Context.CaseVersionId,
                    cancellationToken);
                if (publishedCase is null)
                    return Results.Json(
                        ApiErrors.Create(context, "AI_CONTEXT_CASE_NOT_FOUND", "The grounded case is not available."),
                        statusCode: StatusCodes.Status404NotFound);

                var grounded = AiContextBuilder.TryBuild(
                    publishedCase,
                    request.Context,
                    "The current student question is provided in the bounded dialogue below.",
                    request.LearnerLimitation);
                if (!grounded.IsValid || grounded.Context is null)
                    return Results.Json(
                        ApiErrors.Create(context, grounded.ReasonCode ?? "INVALID_AI_CONTEXT", "The AI context does not match the published case."),
                        statusCode: StatusCodes.Status400BadRequest);

                var allowedAnchors = grounded.Context.AnchorIds
                    .Concat(grounded.Context.LimitationIds)
                    .ToHashSet(StringComparer.Ordinal);
                var prompt = AiConversationPrompt.TryBuild(
                    grounded.Context.Prompt,
                    request.History,
                    request.Input,
                    allowedAnchors);
                if (!prompt.IsValid || prompt.Prompt is null)
                    return Results.Json(
                        ApiErrors.Create(context, prompt.ReasonCode ?? "INVALID_AI_CONVERSATION", "The conversation history is invalid or too large."),
                        statusCode: StatusCodes.Status400BadRequest);

                var contextFingerprint = AiConversationFingerprint.Create(
                    publishedCase,
                    request.Context,
                    request.LearnerLimitation);
                var operationId = $"chat_{sessionId:N}_{idempotencyKey}";
                var requestHash = AiConversationFingerprint.RequestHash(request.Purpose, request.Locale, prompt.Prompt);
                var reservation = await conversationStore.ReserveTurnAsync(
                    accountId,
                    sessionId,
                    operationId,
                    requestHash,
                    contextFingerprint,
                    cancellationToken);

                AiConversationGatewayResult result;
                try
                {
                    result = await conversationGateway.GenerateAsync(
                        accountId,
                        new AiConversationGatewayRequest(
                            request.Purpose,
                            prompt.Prompt,
                            request.Locale,
                            allowedAnchors,
                            new AiConversationDraftSnapshot(
                                request.Context.ClaimText,
                                request.Context.ClaimScope,
                                request.LearnerLimitation,
                                request.Context.NextAction)),
                        operationId,
                        cancellationToken);
                }
                catch
                {
                    await conversationStore.CompleteTurnAsync(
                        accountId,
                        reservation,
                        accepted: false,
                        CancellationToken.None);
                    throw;
                }

                var accepted = string.Equals(result.Status, "success", StringComparison.Ordinal);
                var settled = await conversationStore.CompleteTurnAsync(
                    accountId,
                    reservation,
                    accepted,
                    cancellationToken);
                if (!settled)
                {
                    result = new AiConversationGatewayResult(
                        "fallback",
                        null,
                        null,
                        "AI_CONVERSATION_SESSION_EXPIRED",
                        Array.Empty<string>(),
                        null,
                        result.Audit with { Provider = null, Outcome = "session_expired" });
                    accepted = false;
                }

                await auditStore.RecordAsync(
                    accountId,
                    operationId,
                    result.Audit,
                    result.ReasonCode,
                    cancellationToken);

                var turnsUsed = accepted ? reservation.TurnIndex : reservation.TurnIndex - 1;
                return Results.Ok(new AiConversationTurnResponse(
                    "evidrilo.ai-conversation-turn",
                    "1",
                    result.Status,
                    result.Kind,
                    result.Text,
                    result.ReasonCode,
                    result.GroundedAnchorIds,
                    result.Proposal is null
                        ? null
                        : new AiConversationProposalResponse(
                            result.Proposal.Field,
                            result.Proposal.BeforeValue,
                            result.Proposal.SuggestedValue,
                            result.Proposal.AnchorIds),
                    AutoApplied: false,
                    turnsUsed,
                    NpgsqlAiConversationStore.TurnLimit - turnsUsed,
                    RequestIdMiddleware.Get(context)));
            })
            .RequireAuthorization()
            .RequireRateLimiting("ai");

        endpoints.MapDelete(
            "/v1/ai/conversations/{sessionId:guid}",
            async (
                HttpContext context,
                Guid sessionId,
                IAiConversationStore conversationStore,
                CancellationToken cancellationToken) =>
            {
                if (!TryGetVerifiedAccount(context, "clear an AI conversation", out var accountId, out var error))
                    return error!;

                if (!await conversationStore.ClearAsync(accountId, sessionId, cancellationToken))
                    return Results.Json(
                        ApiErrors.Create(context, "AI_CONVERSATION_NOT_FOUND", "The conversation is no longer available."),
                        statusCode: StatusCodes.Status404NotFound);

                return Results.Ok(new AiConversationClearResponse(
                    "evidrilo.ai-conversation-clear",
                    "1",
                    "cleared",
                    sessionId.ToString("D"),
                    RequestIdMiddleware.Get(context)));
            })
            .RequireAuthorization()
            .RequireRateLimiting("api");

        return endpoints;
    }

    private static bool TryGetVerifiedAccount(
        HttpContext context,
        string operation,
        out Guid accountId,
        out IResult? error)
    {
        if (!AuthenticatedUser.TryGetAccountId(context.User, out accountId))
        {
            error = Results.Json(
                ApiErrors.Create(context, "AUTH_REQUIRED", "Authentication is required."),
                statusCode: StatusCodes.Status401Unauthorized);
            return false;
        }
        if (!AuthenticatedUser.IsEmailVerified(context.User))
        {
            error = Results.Json(
                ApiErrors.Create(context, "FORBIDDEN", $"You are not allowed to {operation}."),
                statusCode: StatusCodes.Status403Forbidden);
            return false;
        }
        error = null;
        return true;
    }

    private static bool TryReadIdempotencyKey(HttpContext context, out string value)
    {
        value = context.Request.Headers["Idempotency-Key"].FirstOrDefault() ?? string.Empty;
        return value.Length is >= 8 and <= 80
            && value.All(character =>
                character is >= 'A' and <= 'Z'
                    or >= 'a' and <= 'z'
                    or >= '0' and <= '9'
                    or '_' or '-');
    }

    private static bool IsValidLocale(string? locale) =>
        !string.IsNullOrWhiteSpace(locale)
        && locale.Length <= 32
        && locale.All(character => char.IsAsciiLetterOrDigit(character) || character == '-');

    private static AiCreditsResponse ToResponse(AiCreditBalance balance, string requestId) =>
        new(
            "evidrilo.ai-credits",
            "1",
            balance.ConsentRecorded,
            balance.Available,
            balance.Grants
                .Select(grant => new AiCreditGrantResponse(
                    grant.GrantKind,
                    grant.GrantKey,
                    grant.Granted,
                    grant.Reserved,
                    grant.Consumed,
                    grant.Available,
                    grant.ExpiresAt))
                .ToArray(),
            requestId);
}
