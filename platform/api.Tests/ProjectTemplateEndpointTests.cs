using System.Net;
using System.Net.Http.Json;
using System.Text;
using System.Text.Json;
using Evidrilo.Api.ProjectTemplates;
using Microsoft.AspNetCore.Mvc.Testing;
using Microsoft.AspNetCore.TestHost;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;

namespace Evidrilo.Api.Tests;

public sealed class ProjectTemplateEndpointTests
{
    private const string UserId = "123e4567-e89b-42d3-a456-426614174000";

    [Fact]
    public async Task Anonymous_student_can_browse_five_fixed_families()
    {
        using var factory = CreateFactory(new TestProjectTemplateStore());
        using var client = factory.CreateClient();

        using var response = await client.GetAsync("/v1/project-template-families");
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal("evidrilo.project-template-families", body.GetProperty("schema").GetString());
        Assert.Equal(5, body.GetProperty("families").GetArrayLength());
        Assert.Equal(0, body.GetProperty("families")[0].GetProperty("selectableTemplateCount").GetInt32());
    }

    [Fact]
    public async Task Anonymous_catalog_returns_only_published_public_fields()
    {
        using var factory = CreateFactory(new TestProjectTemplateStore(includePublished: true));
        using var client = factory.CreateClient();

        using var response = await client.GetAsync("/v1/project-templates?family=literature_review&limit=20");
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();
        var template = body.GetProperty("templates")[0];

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal("template-1", template.GetProperty("id").GetString());
        Assert.Equal("published", template.GetProperty("publication").GetString());
        Assert.False(template.TryGetProperty("organizationId", out _));
        Assert.False(template.TryGetProperty("authorId", out _));
        Assert.False(template.TryGetProperty("reviewerId", out _));
    }

    [Fact]
    public async Task Published_detail_includes_server_owned_review_metadata_without_org_identity()
    {
        using var factory = CreateFactory(new TestProjectTemplateStore(includePublished: true));
        using var client = factory.CreateClient();

        using var response = await client.GetAsync("/v1/project-templates/template-1/versions/1");
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();
        var template = body.GetProperty("template");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.True(template.GetProperty("examples")[0].GetProperty("reviewed").GetBoolean());
        Assert.False(template.TryGetProperty("organizationId", out _));
        Assert.False(template.TryGetProperty("authorId", out _));
        Assert.False(template.TryGetProperty("reviewerId", out _));
    }

    [Fact]
    public async Task Exact_read_of_unpublished_version_is_not_found()
    {
        using var factory = CreateFactory(new TestProjectTemplateStore());
        using var client = factory.CreateClient();

        using var response = await client.GetAsync("/v1/project-templates/template-1/versions/1");

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
    }

    [Theory]
    [InlineData("/v1/project-templates?family=universal_assignment")]
    [InlineData("/v1/project-templates?limit=0")]
    [InlineData("/v1/project-templates?limit=101")]
    [InlineData("/v1/project-templates?afterTemplateId=invalid%20cursor")]
    public async Task Catalog_query_rejects_unknown_family_and_unbounded_page_size(string path)
    {
        using var factory = CreateFactory(new TestProjectTemplateStore());
        using var client = factory.CreateClient();

        using var response = await client.GetAsync(path);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Contains(body.GetProperty("code").GetString(), new[]
        {
            "INVALID_PROJECT_TEMPLATE_FAMILY",
            "INVALID_PROJECT_TEMPLATE_CATALOG_QUERY",
        });
    }

    [Fact]
    public async Task Authoring_requires_verified_identity_and_fails_closed_without_database()
    {
        using var factory = new ApiFactory();
        using var client = factory.CreateClient();

        using var anonymous = await client.PostAsync(
            "/v1/authoring/project-templates",
            new StringContent("{}", Encoding.UTF8, "application/json"));
        using var unverifiedRequest = new HttpRequestMessage(
            HttpMethod.Post,
            "/v1/authoring/project-templates")
        {
            Content = new StringContent("{}", Encoding.UTF8, "application/json"),
        };
        unverifiedRequest.Headers.Add("X-Test-User", $"{UserId}|false");
        using var unverified = await client.SendAsync(unverifiedRequest);

        Assert.Equal(HttpStatusCode.Unauthorized, anonymous.StatusCode);
        Assert.Equal(HttpStatusCode.Forbidden, unverified.StatusCode);
    }

    [Fact]
    public async Task Lifecycle_transition_route_requires_authentication()
    {
        using var factory = new ApiFactory();
        using var client = factory.CreateClient();

        using var response = await client.PostAsync(
            "/v1/authoring/project-templates/template-1/versions/1/transition",
            new StringContent("{}", Encoding.UTF8, "application/json"));

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
    }

    [Fact]
    public async Task Invalid_draft_is_rejected_before_database_access()
    {
        using var factory = new ApiFactory();
        using var client = factory.CreateClient();
        using var request = new HttpRequestMessage(HttpMethod.Post, "/v1/authoring/project-templates")
        {
            Content = new StringContent(
                """{"schema":"evidrilo.project-template-draft","version":"1","organizationId":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","templateId":"template-1","templateVersion":1,"family":"literature_review","template":{"title":"Draft","summary":"Summary","intendedOutput":"Output","inputFields":[{"id":"question","kind":"research_question","label":"Question","required":true}],"steps":[{"id":"step-1","title":"Step","inputFieldIds":["question"]}],"methodSpecificLimitations":["Limit"],"provenanceRequirements":["Source"],"accessibilityExpectations":["Accessible"],"examples":[{"id":"example-1","summary":"Example","reviewed":true}]}}""",
                Encoding.UTF8,
                "application/json"),
        };
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal("INVALID_PROJECT_TEMPLATE_DRAFT", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Public_catalog_fails_closed_when_database_is_unavailable()
    {
        using var factory = new ApiFactory();
        using var client = factory.CreateClient();

        using var response = await client.GetAsync("/v1/project-templates");
        var body = await response.Content.ReadFromJsonAsync<JsonElement>();

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal("DATABASE_NOT_CONFIGURED", body.GetProperty("code").GetString());
    }

    private static WebApplicationFactory<Program> CreateFactory(IProjectTemplateStore store) =>
        new ApiFactory().WithWebHostBuilder(builder => builder.ConfigureTestServices(services =>
        {
            services.RemoveAll<IProjectTemplateStore>();
            services.AddSingleton(store);
        }));

    private sealed class TestProjectTemplateStore(bool includePublished = false) : IProjectTemplateStore
    {
        private static readonly ProjectTemplateDocument Template = new(
            "A bounded literature review",
            "Compare findings across a defined set of sources.",
            "A source-to-claim outline with limitations.",
            [new ProjectTemplateInputField("question", ProjectTemplateInputKind.ResearchQuestion, "Research question", true)],
            [new ProjectTemplateStep("frame-question", "Frame the question", ["question"])],
            ["Do not treat selected sources as a complete census of the field."],
            ["Record each source identifier and retrieval location."],
            ["Use descriptive labels and preserve reading order."],
            [
                new ProjectTemplateExample(
                    "example-normal",
                    "Synthetic normal structure example.",
                    true,
                    ProjectTemplateExampleKind.Normal),
                new ProjectTemplateExample(
                    "example-edge",
                    "Synthetic edge or conflicting structure example.",
                    true,
                    ProjectTemplateExampleKind.EdgeOrConflicting),
            ]);

        private readonly ProjectTemplateCatalogEntry? published = includePublished
            ? new ProjectTemplateCatalogEntry(
                "template-1",
                1,
                "literature_review",
                Template,
                DateTimeOffset.Parse("2026-09-01T00:00:00Z"))
            : null;

        public Task<IReadOnlyList<ProjectTemplateFamilyOffering>> ListFamiliesAsync(CancellationToken cancellationToken) =>
            Task.FromResult<IReadOnlyList<ProjectTemplateFamilyOffering>>(ProjectTemplateFamilies.All
                .Select(family => new ProjectTemplateFamilyOffering(
                    family,
                    published?.Family == family.Id ? 1 : 0))
                .ToArray());

        public Task<ProjectTemplateCatalogPage> ListPublishedAsync(
            string? family,
            int limit,
            string? afterTemplateId,
            CancellationToken cancellationToken)
        {
            ProjectTemplateCatalogEntry[] items = published is null
                || family is not null && published.Family != family
                || afterTemplateId is not null && string.CompareOrdinal(published.TemplateId, afterTemplateId) <= 0
                ? Array.Empty<ProjectTemplateCatalogEntry>()
                : [published!];
            return Task.FromResult(new ProjectTemplateCatalogPage(items.Take(limit).ToArray(), null));
        }

        public Task<ProjectTemplateCatalogEntry?> GetPublishedAsync(
            string templateId,
            int templateVersion,
            CancellationToken cancellationToken) =>
            Task.FromResult(published is { } entry && entry.TemplateId == templateId && entry.TemplateVersion == templateVersion
                ? entry
                : null);

        public Task<ProjectTemplateOperation> CreateDraftAsync(
            Guid actorAccountId,
            ProjectTemplateDraftCreateRequest request,
            CancellationToken cancellationToken) =>
            Task.FromResult(new ProjectTemplateOperation(
                "accepted",
                request.TemplateId!,
                request.TemplateVersion,
                ProjectTemplateLifecycleState.Draft));

        public Task<ProjectTemplateOperation> TransitionAsync(
            Guid actorAccountId,
            string templateId,
            int templateVersion,
            ProjectTemplateTransitionRequest request,
            CancellationToken cancellationToken) =>
            Task.FromResult(new ProjectTemplateOperation(
                "accepted",
                templateId,
                templateVersion,
                request.TargetState));
    }
}
