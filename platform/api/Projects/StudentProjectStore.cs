using Evidrilo.Api.Common;

namespace Evidrilo.Api.Projects;

public interface IStudentProjectStore
{
    Task<StudentProjectCloudConsentState> ReadCloudConsentOwnAsync(
        Guid accountId,
        CancellationToken cancellationToken);

    Task<StudentProjectCloudConsentState> UpdateCloudConsentOwnAsync(
        Guid accountId,
        string policyVersion,
        string decision,
        CancellationToken cancellationToken);

    Task<StudentProjectListResult> ListOwnAsync(
        Guid accountId,
        int limit,
        DateTimeOffset? beforeCreatedAt,
        Guid? beforeProjectId,
        CancellationToken cancellationToken);

    Task<StudentProjectRecord?> ReadOwnAsync(
        Guid accountId,
        Guid projectId,
        CancellationToken cancellationToken);

    Task<StudentProjectRevisionPage> ReadRevisionsOwnAsync(
        Guid accountId,
        Guid projectId,
        int limit,
        int? beforeVersion,
        CancellationToken cancellationToken);

    Task<StudentProjectMutation> CreateOwnAsync(
        Guid accountId,
        string idempotencyKey,
        string requestFingerprint,
        StudentProjectDocument document,
        CancellationToken cancellationToken);

    Task<StudentProjectMutation> SaveOwnAsync(
        Guid accountId,
        Guid projectId,
        string idempotencyKey,
        string requestFingerprint,
        int expectedVersion,
        StudentProjectDocument document,
        CancellationToken cancellationToken);

    Task<StudentProjectMutation> PermanentlyDeleteOwnAsync(
        Guid accountId,
        Guid projectId,
        string idempotencyKey,
        string requestFingerprint,
        int expectedVersion,
        CancellationToken cancellationToken);
}

public sealed class DatabaseUnavailableStudentProjectStore : IStudentProjectStore
{
    public Task<StudentProjectCloudConsentState> ReadCloudConsentOwnAsync(
        Guid accountId,
        CancellationToken cancellationToken) => throw NotConfigured();

    public Task<StudentProjectCloudConsentState> UpdateCloudConsentOwnAsync(
        Guid accountId,
        string policyVersion,
        string decision,
        CancellationToken cancellationToken) => throw NotConfigured();

    public Task<StudentProjectListResult> ListOwnAsync(
        Guid accountId,
        int limit,
        DateTimeOffset? beforeCreatedAt,
        Guid? beforeProjectId,
        CancellationToken cancellationToken) => throw NotConfigured();

    public Task<StudentProjectRecord?> ReadOwnAsync(
        Guid accountId,
        Guid projectId,
        CancellationToken cancellationToken) => throw NotConfigured();

    public Task<StudentProjectRevisionPage> ReadRevisionsOwnAsync(
        Guid accountId,
        Guid projectId,
        int limit,
        int? beforeVersion,
        CancellationToken cancellationToken) => throw NotConfigured();

    public Task<StudentProjectMutation> CreateOwnAsync(
        Guid accountId,
        string idempotencyKey,
        string requestFingerprint,
        StudentProjectDocument document,
        CancellationToken cancellationToken) => throw NotConfigured();

    public Task<StudentProjectMutation> SaveOwnAsync(
        Guid accountId,
        Guid projectId,
        string idempotencyKey,
        string requestFingerprint,
        int expectedVersion,
        StudentProjectDocument document,
        CancellationToken cancellationToken) => throw NotConfigured();

    public Task<StudentProjectMutation> PermanentlyDeleteOwnAsync(
        Guid accountId,
        Guid projectId,
        string idempotencyKey,
        string requestFingerprint,
        int expectedVersion,
        CancellationToken cancellationToken) => throw NotConfigured();

    private static ApiException NotConfigured() => new(
        StatusCodes.Status503ServiceUnavailable,
        "DATABASE_NOT_CONFIGURED",
        "Student project storage is not configured.");
}
