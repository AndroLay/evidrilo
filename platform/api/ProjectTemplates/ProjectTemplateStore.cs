using Evidrilo.Api.Common;

namespace Evidrilo.Api.ProjectTemplates;

public interface IProjectTemplateStore
{
    Task<IReadOnlyList<ProjectTemplateFamilyOffering>> ListFamiliesAsync(CancellationToken cancellationToken);

    Task<ProjectTemplateCatalogPage> ListPublishedAsync(
        string? family,
        int limit,
        string? afterTemplateId,
        CancellationToken cancellationToken);

    Task<ProjectTemplateCatalogEntry?> GetPublishedAsync(
        string templateId,
        int templateVersion,
        CancellationToken cancellationToken);

    Task<ProjectTemplateOperation> CreateDraftAsync(
        Guid actorAccountId,
        ProjectTemplateDraftCreateRequest request,
        CancellationToken cancellationToken);

    Task<ProjectTemplateOperation> TransitionAsync(
        Guid actorAccountId,
        string templateId,
        int templateVersion,
        ProjectTemplateTransitionRequest request,
        CancellationToken cancellationToken);
}

public sealed class DatabaseUnavailableProjectTemplateStore : IProjectTemplateStore
{
    public Task<IReadOnlyList<ProjectTemplateFamilyOffering>> ListFamiliesAsync(
        CancellationToken cancellationToken) => throw NotConfigured();

    public Task<ProjectTemplateCatalogPage> ListPublishedAsync(
        string? family,
        int limit,
        string? afterTemplateId,
        CancellationToken cancellationToken) => throw NotConfigured();

    public Task<ProjectTemplateCatalogEntry?> GetPublishedAsync(
        string templateId,
        int templateVersion,
        CancellationToken cancellationToken) => throw NotConfigured();

    public Task<ProjectTemplateOperation> CreateDraftAsync(
        Guid actorAccountId,
        ProjectTemplateDraftCreateRequest request,
        CancellationToken cancellationToken) => throw NotConfigured();

    public Task<ProjectTemplateOperation> TransitionAsync(
        Guid actorAccountId,
        string templateId,
        int templateVersion,
        ProjectTemplateTransitionRequest request,
        CancellationToken cancellationToken) => throw NotConfigured();

    private static ApiException NotConfigured() => new(
        StatusCodes.Status503ServiceUnavailable,
        "DATABASE_NOT_CONFIGURED",
        "The project template catalog is not configured.");
}
