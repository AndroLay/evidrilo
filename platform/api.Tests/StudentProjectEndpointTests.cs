using System.Net;
using System.Text;
using System.Text.Json;

namespace Evidrilo.Api.Tests;

public sealed class StudentProjectEndpointTests : IClassFixture<ApiFactory>
{
    private static readonly Guid AccountId = Guid.Parse("c2f345e7-5674-4695-9ac3-210e7cbd1929");
    private static readonly Guid ProjectId = Guid.Parse("31c0d18c-943a-49d0-8e45-d6d693f8f6c8");
    private readonly HttpClient client;

    public StudentProjectEndpointTests(ApiFactory factory)
    {
        client = factory.CreateClient();
    }

    [Fact]
    public async Task Project_list_requires_authentication()
    {
        using var response = await client.GetAsync("/v1/projects");
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal("AUTH_REQUIRED", body.RootElement.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Project_access_requires_a_verified_account()
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, "/v1/projects");
        request.Headers.Add("X-Test-User", $"{AccountId}|false");

        using var response = await client.SendAsync(request);
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
        Assert.Equal("FORBIDDEN", body.RootElement.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Project_cloud_consent_requires_authentication()
    {
        using var response = await client.GetAsync("/v1/projects/cloud-consent");
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal("AUTH_REQUIRED", body.RootElement.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Project_cloud_consent_read_fails_closed_without_database_configuration()
    {
        using var request = AuthenticatedRequest(HttpMethod.Get, "/v1/projects/cloud-consent", "{}");

        using var response = await client.SendAsync(request);
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal("DATABASE_NOT_CONFIGURED", body.RootElement.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Project_cloud_consent_rejects_an_unknown_decision_before_database_access()
    {
        using var request = AuthenticatedRequest(
            HttpMethod.Put,
            "/v1/projects/cloud-consent",
            CloudConsentBody("allow"));

        using var response = await client.SendAsync(request);
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal("INVALID_PROJECT_CLOUD_CONSENT", body.RootElement.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Project_cloud_consent_requires_acceptance_of_the_current_policy_version()
    {
        using var request = AuthenticatedRequest(
            HttpMethod.Put,
            "/v1/projects/cloud-consent",
            CloudConsentBody("grant", "student-project-cloud.v0"));

        using var response = await client.SendAsync(request);
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());

        Assert.Equal(HttpStatusCode.Conflict, response.StatusCode);
        Assert.Equal("PROJECT_CLOUD_CONSENT_POLICY_STALE", body.RootElement.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Valid_project_cloud_consent_update_fails_closed_without_database_configuration()
    {
        using var request = AuthenticatedRequest(
            HttpMethod.Put,
            "/v1/projects/cloud-consent",
            CloudConsentBody("grant"));

        using var response = await client.SendAsync(request);
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal("DATABASE_NOT_CONFIGURED", body.RootElement.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Project_create_requires_an_idempotency_key()
    {
        using var request = AuthenticatedRequest(HttpMethod.Post, "/v1/projects", CreateBody());

        using var response = await client.SendAsync(request);
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal("IDEMPOTENCY_KEY_REQUIRED", body.RootElement.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Valid_project_create_fails_closed_without_database_configuration()
    {
        using var request = AuthenticatedRequest(HttpMethod.Post, "/v1/projects", CreateBody());
        request.Headers.Add("Idempotency-Key", "project-create-local-001");

        using var response = await client.SendAsync(request);
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal("DATABASE_NOT_CONFIGURED", body.RootElement.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Project_create_deserializes_snake_case_evidence_kind_from_contract()
    {
        using var request = AuthenticatedRequest(HttpMethod.Post, "/v1/projects", CreateBodyWithEvidence());
        request.Headers.Add("Idempotency-Key", "project-create-evidence-001");

        using var response = await client.SendAsync(request);
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal("DATABASE_NOT_CONFIGURED", body.RootElement.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Valid_project_list_fails_closed_without_database_configuration()
    {
        using var request = AuthenticatedRequest(HttpMethod.Get, "/v1/projects", body: "{}");

        using var response = await client.SendAsync(request);
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal("DATABASE_NOT_CONFIGURED", body.RootElement.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Valid_project_detail_and_report_fail_closed_without_database_configuration()
    {
        foreach (var path in new[]
        {
            $"/v1/projects/{ProjectId:D}",
            $"/v1/projects/{ProjectId:D}/structure-report",
        })
        {
            using var request = AuthenticatedRequest(HttpMethod.Get, path, body: "{}");

            using var response = await client.SendAsync(request);
            using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());

            Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
            Assert.Equal("DATABASE_NOT_CONFIGURED", body.RootElement.GetProperty("code").GetString());
        }
    }

    [Fact]
    public async Task Valid_project_mutations_fail_closed_without_database_configuration()
    {
        using (var save = AuthenticatedRequest(
            HttpMethod.Put,
            $"/v1/projects/{ProjectId:D}",
            SaveBody()))
        {
            save.Headers.Add("Idempotency-Key", "project-save-local-001");
            using var response = await client.SendAsync(save);
            using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());

            Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
            Assert.Equal("DATABASE_NOT_CONFIGURED", body.RootElement.GetProperty("code").GetString());
        }
    }

    [Fact]
    public async Task Ordinary_project_delete_route_does_not_permanently_delete_data()
    {
        using var request = AuthenticatedRequest(
            HttpMethod.Delete,
            $"/v1/projects/{ProjectId:D}",
            DeleteBody(confirmPermanently: true));

        using var response = await client.SendAsync(request);

        Assert.Equal(HttpStatusCode.MethodNotAllowed, response.StatusCode);
    }

    [Fact]
    public async Task Permanent_project_delete_requires_a_second_explicit_confirmation()
    {
        foreach (var bodyText in new[]
        {
            DeleteBody(confirmPermanently: false),
            """{"schema":"evidrilo.student-project-permanent-delete","version":"1","expectedVersion":1}""",
        })
        {
            using var request = AuthenticatedRequest(
                HttpMethod.Delete,
                $"/v1/projects/{ProjectId:D}/permanent",
                bodyText);
            request.Headers.Add("Idempotency-Key", "project-delete-confirm-001");

            using var response = await client.SendAsync(request);
            using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());

            Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
            Assert.Equal(
                "PERMANENT_DELETE_CONFIRMATION_REQUIRED",
                body.RootElement.GetProperty("code").GetString());
        }
    }

    [Fact]
    public async Task Confirmed_permanent_project_delete_fails_closed_without_database_configuration()
    {
        using var request = AuthenticatedRequest(
            HttpMethod.Delete,
            $"/v1/projects/{ProjectId:D}/permanent",
            DeleteBody(confirmPermanently: true));
        request.Headers.Add("Idempotency-Key", "project-delete-confirmed-001");

        using var response = await client.SendAsync(request);
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal("DATABASE_NOT_CONFIGURED", body.RootElement.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Project_revisions_fail_closed_without_database_configuration()
    {
        using var request = AuthenticatedRequest(
            HttpMethod.Get,
            $"/v1/projects/{ProjectId:D}/revisions?limit=1",
            body: "{}");

        using var response = await client.SendAsync(request);
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal("DATABASE_NOT_CONFIGURED", body.RootElement.GetProperty("code").GetString());
    }

    private static HttpRequestMessage AuthenticatedRequest(HttpMethod method, string path, string body)
    {
        var request = new HttpRequestMessage(method, path)
        {
            Content = new StringContent(body, Encoding.UTF8, "application/json"),
        };
        request.Headers.Add("X-Test-User", $"{AccountId}|true");
        return request;
    }

    private static string CreateBody() => """
        {
          "schema": "evidrilo.student-project-create",
          "version": "1",
          "project": {
            "title": "A local inquiry project",
            "taskBrief": "Compare one measured condition with another.",
            "question": "How does the selected condition relate to the observed outcome?",
            "method": "Student-described method",
            "hypothesis": null,
            "criteria": [],
            "evidenceItems": [],
            "criterionEvidenceLinks": [],
            "analysis": null,
            "claim": null,
            "claimEvidenceIds": [],
            "limitations": [],
            "nextAction": null
          }
        }
        """;

    private static string CreateBodyWithEvidence() => """
        {
          "schema": "evidrilo.student-project-create",
          "version": "1",
          "project": {
            "title": "A local inquiry project",
            "taskBrief": "Compare one measured condition with another.",
            "question": "How does the selected condition relate to the observed outcome?",
            "method": "Student-described method",
            "hypothesis": null,
            "criteria": [],
            "evidenceItems": [
              {
                "id": "observation-1",
                "kind": "observation",
                "label": "First observation",
                "summary": "A manually entered result.",
                "origin": "Student notes"
              }
            ],
            "criterionEvidenceLinks": [],
            "analysis": null,
            "claim": null,
            "claimEvidenceIds": [],
            "limitations": [],
            "nextAction": null
          }
        }
        """;

    private static string SaveBody() => """
        {
          "schema": "evidrilo.student-project-save",
          "version": "1",
          "expectedVersion": 1,
          "project": {
            "title": "A local inquiry project",
            "taskBrief": "Compare one measured condition with another.",
            "question": "How does the selected condition relate to the observed outcome?",
            "method": "Student-described method",
            "hypothesis": null,
            "criteria": [],
            "evidenceItems": [],
            "criterionEvidenceLinks": [],
            "analysis": null,
            "claim": null,
            "claimEvidenceIds": [],
            "limitations": [],
            "nextAction": null
          }
        }
        """;

    private static string DeleteBody(bool confirmPermanently) => $$"""
        {
          "schema": "evidrilo.student-project-permanent-delete",
          "version": "1",
          "expectedVersion": 1,
          "confirmPermanently": {{confirmPermanently.ToString().ToLowerInvariant()}}
        }
        """;

    private static string CloudConsentBody(string decision, string policyVersion = "student-project-cloud.v1") => $$"""
        {
          "schema": "evidrilo.project-cloud-consent-update",
          "version": "1",
          "policyVersion": "{{policyVersion}}",
          "decision": "{{decision}}"
        }
        """;
}
