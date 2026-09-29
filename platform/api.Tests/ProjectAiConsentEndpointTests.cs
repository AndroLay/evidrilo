using System.Net;
using System.Text;
using System.Text.Json;
using Evidrilo.Api.ProjectAi;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;

namespace Evidrilo.Api.Tests;

public sealed class ProjectAiConsentEndpointTests
{
    private static readonly Guid FirstAccount = Guid.Parse("b72a3ddd-fec0-4b6a-9b63-12724da76fa6");
    private static readonly Guid SecondAccount = Guid.Parse("3fa85f64-5717-4562-b3fc-2c963f66afa6");

    [Fact]
    public async Task Consent_grant_is_account_scoped_and_revocation_is_idempotent()
    {
        var store = new MemoryProjectAiConsentStore();
        using var factory = CreateFactory(store);
        using var client = factory.CreateClient();

        using var grant = await SendAsAsync(client, HttpMethod.Put, "/v1/project-ai/consent", FirstAccount,
            ConsentBody(ProjectAiConsentPolicy.CurrentPolicyVersion));
        using var ownRead = await SendAsAsync(client, HttpMethod.Get, "/v1/project-ai/consent", FirstAccount);
        using var otherRead = await SendAsAsync(client, HttpMethod.Get, "/v1/project-ai/consent", SecondAccount);
        using var revoke = await SendAsAsync(client, HttpMethod.Delete, "/v1/project-ai/consent", FirstAccount);
        using var repeatedRevoke = await SendAsAsync(client, HttpMethod.Delete, "/v1/project-ai/consent", FirstAccount);

        Assert.Equal(HttpStatusCode.OK, grant.StatusCode);
        Assert.True(await ReadGrantedAsync(ownRead));
        Assert.False(await ReadGrantedAsync(otherRead));
        Assert.Equal(HttpStatusCode.OK, revoke.StatusCode);
        Assert.Equal(HttpStatusCode.OK, repeatedRevoke.StatusCode);
        Assert.False(await ReadGrantedAsync(repeatedRevoke));
        Assert.Equal(2, store.EventCount);
    }

    [Fact]
    public async Task Consent_responses_include_nullable_timestamp_keys_for_every_state()
    {
        var store = new MemoryProjectAiConsentStore();
        using var factory = CreateFactory(store);
        using var client = factory.CreateClient();

        using var notGranted = await SendAsAsync(client, HttpMethod.Get, "/v1/project-ai/consent", FirstAccount);
        using var notGrantedBody = JsonDocument.Parse(await notGranted.Content.ReadAsStringAsync(cancellationToken: TestContext.Current.CancellationToken));
        Assert.Equal(HttpStatusCode.OK, notGranted.StatusCode);
        AssertExactKeys(notGrantedBody.RootElement,
            "schema", "version", "granted", "policyVersion", "grantedAt", "revokedAt", "generation");
        Assert.Equal(JsonValueKind.Null, notGrantedBody.RootElement.GetProperty("grantedAt").ValueKind);
        Assert.Equal(JsonValueKind.Null, notGrantedBody.RootElement.GetProperty("revokedAt").ValueKind);

        using var grant = await SendAsAsync(client, HttpMethod.Put, "/v1/project-ai/consent", FirstAccount,
            ConsentBody(ProjectAiConsentPolicy.CurrentPolicyVersion));
        using var grantedBody = JsonDocument.Parse(await grant.Content.ReadAsStringAsync(cancellationToken: TestContext.Current.CancellationToken));
        Assert.Equal(HttpStatusCode.OK, grant.StatusCode);
        AssertExactKeys(grantedBody.RootElement,
            "schema", "version", "granted", "policyVersion", "grantedAt", "revokedAt", "generation");
        Assert.Equal(JsonValueKind.String, grantedBody.RootElement.GetProperty("grantedAt").ValueKind);
        Assert.Equal(JsonValueKind.Null, grantedBody.RootElement.GetProperty("revokedAt").ValueKind);

        using var revoke = await SendAsAsync(client, HttpMethod.Delete, "/v1/project-ai/consent", FirstAccount);
        using var revokedBody = JsonDocument.Parse(await revoke.Content.ReadAsStringAsync(cancellationToken: TestContext.Current.CancellationToken));
        Assert.Equal(HttpStatusCode.OK, revoke.StatusCode);
        AssertExactKeys(revokedBody.RootElement,
            "schema", "version", "granted", "policyVersion", "grantedAt", "revokedAt", "generation");
        Assert.Equal(JsonValueKind.String, revokedBody.RootElement.GetProperty("grantedAt").ValueKind);
        Assert.Equal(JsonValueKind.String, revokedBody.RootElement.GetProperty("revokedAt").ValueKind);
    }

    [Fact]
    public async Task Grant_requires_the_current_policy_version()
    {
        var store = new MemoryProjectAiConsentStore();
        using var factory = CreateFactory(store);
        using var client = factory.CreateClient();

        using var response = await SendAsAsync(client, HttpMethod.Put, "/v1/project-ai/consent", FirstAccount,
            ConsentBody("project-ai-data.v0"));
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync(cancellationToken: TestContext.Current.CancellationToken));

        Assert.Equal(HttpStatusCode.Conflict, response.StatusCode);
        Assert.Equal("PROJECT_AI_CONSENT_POLICY_STALE", body.RootElement.GetProperty("code").GetString());
        Assert.False((await store.ReadOwnAsync(FirstAccount, CancellationToken.None)).Granted);
    }

    [Fact]
    public async Task Client_boolean_claims_neither_grant_nor_replace_stored_consent()
    {
        var store = new MemoryProjectAiConsentStore();
        using var factory = CreateFactory(store);
        using var client = factory.CreateClient();

        using var unconsented = await SendAsAsync(
            client,
            HttpMethod.Post,
            "/v1/project-ai/scaffold",
            FirstAccount,
            ScaffoldBody(optedIn: true, projectDataConsent: true, consentVersion: ProjectAiConsentPolicy.CurrentPolicyVersion));
        using var missingConsentBody = JsonDocument.Parse(await unconsented.Content.ReadAsStringAsync(cancellationToken: TestContext.Current.CancellationToken));

        Assert.Equal(HttpStatusCode.Forbidden, unconsented.StatusCode);
        Assert.Equal("PROJECT_AI_CONSENT_REQUIRED", missingConsentBody.RootElement.GetProperty("code").GetString());

        using var grant = await SendAsAsync(client, HttpMethod.Put, "/v1/project-ai/consent", FirstAccount,
            ConsentBody(ProjectAiConsentPolicy.CurrentPolicyVersion));
        using var legacyFalseClaims = await SendAsAsync(
            client,
            HttpMethod.Post,
            "/v1/project-ai/scaffold",
            FirstAccount,
            ScaffoldBody(optedIn: false, projectDataConsent: false, consentVersion: "stale-client-value"),
            idempotencyKey: "project-ai-consent-0001");
        using var revoked = await SendAsAsync(client, HttpMethod.Delete, "/v1/project-ai/consent", FirstAccount);
        using var afterRevocation = await SendAsAsync(
            client,
            HttpMethod.Post,
            "/v1/project-ai/scaffold",
            FirstAccount,
            ScaffoldBody(optedIn: true, projectDataConsent: true, consentVersion: ProjectAiConsentPolicy.CurrentPolicyVersion));

        Assert.Equal(HttpStatusCode.OK, grant.StatusCode);
        Assert.Equal(HttpStatusCode.ServiceUnavailable, legacyFalseClaims.StatusCode);
        using (var disabledBody = JsonDocument.Parse(await legacyFalseClaims.Content.ReadAsStringAsync(cancellationToken: TestContext.Current.CancellationToken)))
            Assert.Equal("PROJECT_AI_NOT_READY", disabledBody.RootElement.GetProperty("code").GetString());
        Assert.Equal(HttpStatusCode.OK, revoked.StatusCode);
        Assert.Equal(HttpStatusCode.Forbidden, afterRevocation.StatusCode);
    }

    [Fact]
    public async Task Consent_routes_require_a_verified_authenticated_account()
    {
        var store = new MemoryProjectAiConsentStore();
        using var factory = CreateFactory(store);
        using var client = factory.CreateClient();

        using var anonymous = await client.GetAsync("/v1/project-ai/consent", cancellationToken: TestContext.Current.CancellationToken);
        using var unverified = await SendAsAsync(
            client,
            HttpMethod.Put,
            "/v1/project-ai/consent",
            FirstAccount,
            ConsentBody(ProjectAiConsentPolicy.CurrentPolicyVersion),
            verified: false);

        Assert.Equal(HttpStatusCode.Unauthorized, anonymous.StatusCode);
        Assert.Equal(HttpStatusCode.Forbidden, unverified.StatusCode);
        Assert.Equal(0, store.EventCount);
    }

    [Fact]
    public void Scaffold_dispatch_requires_the_same_active_consent_generation()
    {
        var initial = ProjectAiConsentPolicy.NotGranted with
        {
            Granted = true,
            GrantedAt = DateTimeOffset.UtcNow,
            Generation = 4,
        };

        Assert.True(ProjectAiConsentPolicy.StillAuthorizesDispatch(initial, initial));
        Assert.False(ProjectAiConsentPolicy.StillAuthorizesDispatch(
            initial,
            initial with { Granted = false, RevokedAt = DateTimeOffset.UtcNow, Generation = 5 }));
        Assert.False(ProjectAiConsentPolicy.StillAuthorizesDispatch(
            initial,
            initial with { Generation = 6 }));
    }

    private static ApiFactory CreateFactory(MemoryProjectAiConsentStore store) => ApiFactory.WithAdditionalServices(services =>
    {
        services.RemoveAll<IProjectAiConsentStore>();
        services.AddSingleton<IProjectAiConsentStore>(store);
    });

    private static async Task<HttpResponseMessage> SendAsAsync(
        HttpClient client,
        HttpMethod method,
        string path,
        Guid accountId,
        string? body = null,
        bool verified = true,
        string? idempotencyKey = null)
    {
        using var request = new HttpRequestMessage(method, path);
        request.Headers.Add("X-Test-User", $"{accountId}|{verified.ToString().ToLowerInvariant()}");
        if (idempotencyKey is not null) request.Headers.Add("Idempotency-Key", idempotencyKey);
        if (body is not null) request.Content = new StringContent(body, Encoding.UTF8, "application/json");
        return await client.SendAsync(request);
    }

    private static async Task<bool> ReadGrantedAsync(HttpResponseMessage response)
    {
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        return body.RootElement.GetProperty("granted").GetBoolean();
    }

    private static void AssertExactKeys(JsonElement element, params string[] expectedKeys)
    {
        var actualKeys = element.EnumerateObject()
            .Select(property => property.Name)
            .OrderBy(name => name, StringComparer.Ordinal)
            .ToArray();
        Assert.Equal(expectedKeys.OrderBy(name => name, StringComparer.Ordinal), actualKeys);
    }

    private static string ConsentBody(string policyVersion) => JsonSerializer.Serialize(new
    {
        schema = ProjectAiConsentPolicy.Schema,
        version = ProjectAiConsentPolicy.Version,
        policyVersion,
    });

    private static string ScaffoldBody(bool optedIn, bool projectDataConsent, string consentVersion) => JsonSerializer.Serialize(new
    {
        schema = "evidrilo.project-ai-scaffold",
        version = "1",
        operation = "create_project",
        projectId = (string?)null,
        templateId = "reviewed-template",
        templateVersion = 1,
        baseProjectRevision = (int?)null,
        assignmentBrief = "Compare two measurements and explain the reasoning.",
        researchQuestion = "How does the measured outcome vary?",
        studentQuestion = "Help me define a manageable first step.",
        currentFields = new Dictionary<string, string>(),
        constraints = new[] { "Limited time" },
        locale = "en",
        optedIn,
        projectDataConsent,
        projectDataConsentVersion = consentVersion,
    });

    private sealed class MemoryProjectAiConsentStore : IProjectAiConsentStore
    {
        private readonly Dictionary<Guid, ProjectAiConsentState> states = [];
        public int EventCount { get; private set; }

        public Task<ProjectAiConsentState> ReadOwnAsync(Guid accountId, CancellationToken cancellationToken) =>
            Task.FromResult(states.GetValueOrDefault(accountId) ?? ProjectAiConsentPolicy.NotGranted);

        public Task<ProjectAiConsentState> GrantOwnAsync(
            Guid accountId,
            string policyVersion,
            CancellationToken cancellationToken)
        {
            var current = states.GetValueOrDefault(accountId) ?? ProjectAiConsentPolicy.NotGranted;
            if (current.Granted && current.PolicyVersion == policyVersion) return Task.FromResult(current);
            var now = DateTimeOffset.UtcNow;
            var updated = current with
            {
                Granted = true,
                PolicyVersion = policyVersion,
                GrantedAt = now,
                RevokedAt = null,
                Generation = current.Generation + 1,
            };
            states[accountId] = updated;
            EventCount++;
            return Task.FromResult(updated);
        }

        public Task<ProjectAiConsentState> RevokeOwnAsync(Guid accountId, CancellationToken cancellationToken)
        {
            var current = states.GetValueOrDefault(accountId) ?? ProjectAiConsentPolicy.NotGranted;
            if (!current.Granted) return Task.FromResult(current);
            var updated = current with
            {
                Granted = false,
                RevokedAt = DateTimeOffset.UtcNow,
                Generation = current.Generation + 1,
            };
            states[accountId] = updated;
            EventCount++;
            return Task.FromResult(updated);
        }
    }
}
