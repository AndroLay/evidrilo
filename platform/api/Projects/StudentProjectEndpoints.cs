using System.Text.Json;
using System.Text.Json.Serialization;
using Evidrilo.Api.Auth;
using Evidrilo.Api.Common;

namespace Evidrilo.Api.Projects;

public sealed record StudentProjectMutationResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("outcome")] string Outcome,
    [property: JsonPropertyName("projectId")] Guid ProjectId,
    [property: JsonPropertyName("projectVersion")] int ProjectVersion,
    [property: JsonPropertyName("requestId")] string RequestId);

public sealed record StudentProjectDetailResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("projectId")] Guid ProjectId,
    [property: JsonPropertyName("projectVersion")] int ProjectVersion,
    [property: JsonPropertyName("project")] StudentProjectDocument Project,
    [property: JsonPropertyName("createdAt")] DateTimeOffset CreatedAt,
    [property: JsonPropertyName("updatedAt")] DateTimeOffset UpdatedAt,
    [property: JsonPropertyName("requestId")] string RequestId);

public sealed record StudentProjectCursor(
    [property: JsonPropertyName("createdAt")] DateTimeOffset CreatedAt,
    [property: JsonPropertyName("projectId")] Guid ProjectId);

public sealed record StudentProjectListResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("projects")] IReadOnlyList<StudentProjectListItem> Projects,
    [property: JsonPropertyName("nextCursor"), JsonIgnore(Condition = JsonIgnoreCondition.Never)] StudentProjectCursor? NextCursor,
    [property: JsonPropertyName("requestId")] string RequestId);

public sealed record StudentProjectRevisionPageResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("projectId")] Guid ProjectId,
    [property: JsonPropertyName("revisions")] IReadOnlyList<StudentProjectRevisionRecord> Revisions,
    [property: JsonPropertyName("nextBeforeVersion"), JsonIgnore(Condition = JsonIgnoreCondition.Never)] int? NextBeforeVersion,
    [property: JsonPropertyName("requestId")] string RequestId);

public sealed record StudentProjectCloudConsentResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("granted")] bool Granted,
    [property: JsonPropertyName("policyVersion")] string PolicyVersion,
    [property: JsonPropertyName("grantedAt"), JsonIgnore(Condition = JsonIgnoreCondition.Never)] DateTimeOffset? GrantedAt,
    [property: JsonPropertyName("revokedAt"), JsonIgnore(Condition = JsonIgnoreCondition.Never)] DateTimeOffset? RevokedAt,
    [property: JsonPropertyName("updatedAt"), JsonIgnore(Condition = JsonIgnoreCondition.Never)] DateTimeOffset? UpdatedAt,
    [property: JsonPropertyName("requestId")] string RequestId);

public static class StudentProjectEndpoints
{
    private const int DefaultPageSize = 50;
    private const int MaximumPageSize = 100;

    private static readonly JsonSerializerOptions RequestJsonOptions = new(JsonSerializerDefaults.Web)
    {
        UnmappedMemberHandling = JsonUnmappedMemberHandling.Disallow,
        Converters = { new JsonStringEnumConverter(JsonNamingPolicy.SnakeCaseLower) },
    };

    public static IEndpointRouteBuilder MapStudentProjectEndpoints(this IEndpointRouteBuilder endpoints)
    {
        endpoints.MapGet(
            "/v1/projects/cloud-consent",
            async (HttpContext context, IStudentProjectStore store, CancellationToken cancellationToken) =>
            {
                if (!TryGetVerifiedAccount(context, out var accountId, out var authFailure))
                    return authFailure!;

                var consent = await store.ReadCloudConsentOwnAsync(accountId, cancellationToken);
                return Results.Ok(CloudConsentResponse(context, consent));
            })
            .RequireAuthorization()
            .RequireRateLimiting("api");

        endpoints.MapPut(
            "/v1/projects/cloud-consent",
            async (HttpContext context, IStudentProjectStore store, CancellationToken cancellationToken) =>
            {
                if (!TryGetVerifiedAccount(context, out var accountId, out var authFailure))
                    return authFailure!;

                var request = await ReadRequestAsync<StudentProjectCloudConsentUpdateRequest>(
                    context,
                    cancellationToken,
                    "INVALID_PROJECT_CLOUD_CONSENT",
                    "The project cloud-consent request is invalid.");
                if (request is null
                    || request.Schema != "evidrilo.project-cloud-consent-update"
                    || request.Version != "1"
                    || !StudentProjectCloudConsentPolicy.IsDecision(request.Decision)
                    || !StudentProjectCloudConsentPolicy.IsValidPolicyVersion(request.PolicyVersion))
                {
                    return BadRequest(context, "INVALID_PROJECT_CLOUD_CONSENT", "The project cloud-consent request is invalid.");
                }

                if (request.Decision == "grant"
                    && !StudentProjectCloudConsentPolicy.AcceptsGrant(request.PolicyVersion))
                {
                    return Results.Json(
                        ApiErrors.Create(
                            context,
                            "PROJECT_CLOUD_CONSENT_POLICY_STALE",
                            "Review and accept the current project cloud-storage policy before enabling storage."),
                        statusCode: StatusCodes.Status409Conflict);
                }

                var consent = await store.UpdateCloudConsentOwnAsync(
                    accountId,
                    request.PolicyVersion!,
                    request.Decision!,
                    cancellationToken);
                return Results.Ok(CloudConsentResponse(context, consent));
            })
            .RequireAuthorization()
            .RequireRateLimiting("api");

        endpoints.MapPost(
            "/v1/projects",
            async (HttpContext context, IStudentProjectStore store, CancellationToken cancellationToken) =>
            {
                if (!TryGetVerifiedAccount(context, out var accountId, out var authFailure))
                    return authFailure!;
                if (!TryGetIdempotencyKey(context, out var idempotencyKey, out var keyFailure))
                    return keyFailure!;

                var request = await ReadRequestAsync<StudentProjectCreateRequest>(context, cancellationToken);
                if (request is null
                    || request.Schema != "evidrilo.student-project-create"
                    || request.Version != "1"
                    || !StudentProjectDocumentValidator.IsValid(request.Project))
                {
                    return BadRequest(context, "INVALID_STUDENT_PROJECT", "The student project is invalid.");
                }

                var fingerprint = StudentProjectRequestFingerprint.Create("create", null, request);
                var mutation = await store.CreateOwnAsync(
                    accountId,
                    idempotencyKey,
                    fingerprint,
                    request.Project!,
                    cancellationToken);
                var response = MutationResponse(context, mutation);
                return mutation.Outcome == "created"
                    ? Results.Created($"/v1/projects/{mutation.ProjectId:D}", response)
                    : Results.Ok(response);
            })
            .RequireAuthorization()
            .RequireRateLimiting("api");

        endpoints.MapGet(
            "/v1/projects",
            async (
                HttpContext context,
                int? limit,
                DateTimeOffset? beforeCreatedAt,
                Guid? beforeProjectId,
                IStudentProjectStore store,
                CancellationToken cancellationToken) =>
            {
                if (!TryGetVerifiedAccount(context, out var accountId, out var authFailure))
                    return authFailure!;

                var actualLimit = limit ?? DefaultPageSize;
                if (actualLimit is < 1 or > MaximumPageSize
                    || beforeCreatedAt.HasValue != beforeProjectId.HasValue)
                {
                    return BadRequest(context, "INVALID_PROJECT_CURSOR", "The project cursor is invalid.");
                }

                var page = await store.ListOwnAsync(
                    accountId,
                    actualLimit,
                    beforeCreatedAt,
                    beforeProjectId,
                    cancellationToken);
                StudentProjectCursor? nextCursor = null;
                if (page.HasMore && page.Projects.Count > 0)
                {
                    var last = page.Projects[^1];
                    nextCursor = new StudentProjectCursor(last.CreatedAt, last.ProjectId);
                }

                return Results.Ok(new StudentProjectListResponse(
                    "evidrilo.student-project-list",
                    "1",
                    page.Projects,
                    nextCursor,
                    RequestIdMiddleware.Get(context)));
            })
            .RequireAuthorization()
            .RequireRateLimiting("api");

        endpoints.MapGet(
            "/v1/projects/{projectId:guid}",
            async (
                HttpContext context,
                Guid projectId,
                IStudentProjectStore store,
                CancellationToken cancellationToken) =>
            {
                if (!TryGetVerifiedAccount(context, out var accountId, out var authFailure))
                    return authFailure!;

                var project = await store.ReadOwnAsync(accountId, projectId, cancellationToken);
                return project is null
                    ? NotFound(context)
                    : Results.Ok(DetailResponse(context, project));
            })
            .RequireAuthorization()
            .RequireRateLimiting("api");

        endpoints.MapPut(
            "/v1/projects/{projectId:guid}",
            async (
                HttpContext context,
                Guid projectId,
                IStudentProjectStore store,
                CancellationToken cancellationToken) =>
            {
                if (!TryGetVerifiedAccount(context, out var accountId, out var authFailure))
                    return authFailure!;
                if (!TryGetIdempotencyKey(context, out var idempotencyKey, out var keyFailure))
                    return keyFailure!;

                var request = await ReadRequestAsync<StudentProjectSaveRequest>(context, cancellationToken);
                if (request is null
                    || request.Schema != "evidrilo.student-project-save"
                    || request.Version != "1"
                    || request.ExpectedVersion < 1
                    || !StudentProjectDocumentValidator.IsValid(request.Project))
                {
                    return BadRequest(context, "INVALID_STUDENT_PROJECT", "The student project is invalid.");
                }

                var fingerprint = StudentProjectRequestFingerprint.Create("save", projectId, request);
                var mutation = await store.SaveOwnAsync(
                    accountId,
                    projectId,
                    idempotencyKey,
                    fingerprint,
                    request.ExpectedVersion,
                    request.Project!,
                    cancellationToken);
                return Results.Ok(MutationResponse(context, mutation));
            })
            .RequireAuthorization()
            .RequireRateLimiting("api");

        endpoints.MapDelete(
            "/v1/projects/{projectId:guid}/permanent",
            async (
                HttpContext context,
                Guid projectId,
                IStudentProjectStore store,
                CancellationToken cancellationToken) =>
            {
                if (!TryGetVerifiedAccount(context, out var accountId, out var authFailure))
                    return authFailure!;
                if (!TryGetIdempotencyKey(context, out var idempotencyKey, out var keyFailure))
                    return keyFailure!;

                var request = await ReadRequestAsync<StudentProjectPermanentDeleteRequest>(context, cancellationToken);
                if (request is null
                    || request.Schema != "evidrilo.student-project-permanent-delete"
                    || request.Version != "1"
                    || request.ExpectedVersion < 1)
                {
                    return BadRequest(context, "INVALID_PERMANENT_PROJECT_DELETE", "The permanent project deletion request is invalid.");
                }
                if (!request.ConfirmPermanently)
                {
                    return BadRequest(
                        context,
                        "PERMANENT_DELETE_CONFIRMATION_REQUIRED",
                        "Confirm permanent deletion before continuing.");
                }

                var fingerprint = StudentProjectRequestFingerprint.Create("delete", projectId, request);
                var mutation = await store.PermanentlyDeleteOwnAsync(
                    accountId,
                    projectId,
                    idempotencyKey,
                    fingerprint,
                    request.ExpectedVersion,
                    cancellationToken);
                return Results.Ok(MutationResponse(context, mutation));
            })
            .RequireAuthorization()
            .RequireRateLimiting("api");

        endpoints.MapGet(
            "/v1/projects/{projectId:guid}/revisions",
            async (
                HttpContext context,
                Guid projectId,
                int? limit,
                int? beforeVersion,
                IStudentProjectStore store,
                CancellationToken cancellationToken) =>
            {
                if (!TryGetVerifiedAccount(context, out var accountId, out var authFailure))
                    return authFailure!;

                var actualLimit = limit ?? DefaultPageSize;
                if (actualLimit is < 1 or > MaximumPageSize
                    || (beforeVersion.HasValue && beforeVersion.Value < 1))
                {
                    return BadRequest(context, "INVALID_PROJECT_CURSOR", "The project revision cursor is invalid.");
                }

                var page = await store.ReadRevisionsOwnAsync(
                    accountId,
                    projectId,
                    actualLimit,
                    beforeVersion,
                    cancellationToken);
                int? nextBeforeVersion = page.HasMore && page.Revisions.Count > 0
                    ? page.Revisions[^1].Version
                    : null;
                return Results.Ok(new StudentProjectRevisionPageResponse(
                    "evidrilo.student-project-revisions",
                    "1",
                    projectId,
                    page.Revisions,
                    nextBeforeVersion,
                    RequestIdMiddleware.Get(context)));
            })
            .RequireAuthorization()
            .RequireRateLimiting("api");

        endpoints.MapGet(
            "/v1/projects/{projectId:guid}/structure-report",
            async (
                HttpContext context,
                Guid projectId,
                IStudentProjectStore store,
                CancellationToken cancellationToken) =>
            {
                if (!TryGetVerifiedAccount(context, out var accountId, out var authFailure))
                    return authFailure!;

                var project = await store.ReadOwnAsync(accountId, projectId, cancellationToken);
                if (project is null) return NotFound(context);

                return Results.Ok(StudentProjectStructureReporter.Create(
                    project.ProjectId,
                    project.Version,
                    project.Document,
                    RequestIdMiddleware.Get(context)));
            })
            .RequireAuthorization()
            .RequireRateLimiting("api");

        return endpoints;
    }

    private static async Task<TRequest?> ReadRequestAsync<TRequest>(
        HttpContext context,
        CancellationToken cancellationToken,
        string errorCode = "INVALID_STUDENT_PROJECT",
        string errorMessage = "The student project request is invalid.")
    {
        var payload = await RequestJsonReader.ReadAsync(
            context.Request,
            errorCode,
            errorMessage,
            cancellationToken);
        try
        {
            return payload.Deserialize<TRequest>(RequestJsonOptions);
        }
        catch (JsonException)
        {
            return default;
        }
    }

    private static StudentProjectMutationResponse MutationResponse(
        HttpContext context,
        StudentProjectMutation mutation) =>
        new(
            "evidrilo.student-project-mutation-result",
            "1",
            mutation.Outcome,
            mutation.ProjectId,
            mutation.Version,
            RequestIdMiddleware.Get(context));

    private static StudentProjectCloudConsentResponse CloudConsentResponse(
        HttpContext context,
        StudentProjectCloudConsentState consent) =>
        new(
            "evidrilo.project-cloud-consent",
            "1",
            consent.Granted,
            consent.PolicyVersion,
            consent.GrantedAt,
            consent.RevokedAt,
            consent.UpdatedAt,
            RequestIdMiddleware.Get(context));

    private static StudentProjectDetailResponse DetailResponse(
        HttpContext context,
        StudentProjectRecord project) =>
        new(
            "evidrilo.student-project",
            "1",
            project.ProjectId,
            project.Version,
            project.Document,
            project.CreatedAt,
            project.UpdatedAt,
            RequestIdMiddleware.Get(context));

    private static bool TryGetVerifiedAccount(
        HttpContext context,
        out Guid accountId,
        out IResult? failure)
    {
        accountId = Guid.Empty;
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
                ApiErrors.Create(context, "FORBIDDEN", "A verified account is required for student projects."),
                statusCode: StatusCodes.Status403Forbidden);
            return false;
        }

        return true;
    }

    private static bool TryGetIdempotencyKey(
        HttpContext context,
        out string idempotencyKey,
        out IResult? failure)
    {
        var values = context.Request.Headers[RequestIdMiddleware.IdempotencyKeyHeaderName];
        idempotencyKey = values.Count == 1 ? values[0] ?? string.Empty : string.Empty;
        failure = null;
        if (RequestIdMiddleware.IsValid(idempotencyKey)) return true;

        failure = BadRequest(
            context,
            "IDEMPOTENCY_KEY_REQUIRED",
            "A valid Idempotency-Key header is required for project changes.");
        return false;
    }

    private static IResult NotFound(HttpContext context) =>
        Results.Json(
            ApiErrors.Create(context, "PROJECT_NOT_FOUND", "The student project was not found."),
            statusCode: StatusCodes.Status404NotFound);

    private static IResult BadRequest(HttpContext context, string code, string message) =>
        Results.Json(ApiErrors.Create(context, code, message), statusCode: StatusCodes.Status400BadRequest);
}
