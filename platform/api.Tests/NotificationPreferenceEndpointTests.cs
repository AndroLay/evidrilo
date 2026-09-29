using System.Net;
using System.Net.Http.Json;
using System.Text.Json;
using Evidrilo.Api.Notifications;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;

namespace Evidrilo.Api.Tests;

public sealed class NotificationPreferenceEndpointTests : IClassFixture<ApiFactory>
{
    private static readonly Guid UserId = Guid.Parse("123e4567-e89b-42d3-a456-426614174000");
    private readonly HttpClient client;

    public NotificationPreferenceEndpointTests(ApiFactory factory)
    {
        client = factory.CreateClient();
    }

    [Fact]
    public async Task Default_and_update_responses_keep_nullable_updated_at_key()
    {
        using var factory = ApiFactory.WithAdditionalServices(services =>
        {
            services.RemoveAll<INotificationPreferencesStore>();
            services.AddSingleton<INotificationPreferencesStore>(new DefaultNotificationPreferencesStore());
        });
        using var testClient = factory.CreateClient();

        using var getRequest = new HttpRequestMessage(HttpMethod.Get, "/v1/notifications/preferences");
        getRequest.Headers.Add("X-Test-User", $"{UserId}|true");
        using var getResponse = await testClient.SendAsync(getRequest, cancellationToken: TestContext.Current.CancellationToken);
        using var getBody = JsonDocument.Parse(await getResponse.Content.ReadAsStringAsync(cancellationToken: TestContext.Current.CancellationToken));

        Assert.Equal(HttpStatusCode.OK, getResponse.StatusCode);
        AssertExactKeys(getBody.RootElement,
            "schema", "version", "enabled", "continueUnfinishedEnabled", "reviewCompletedEnabled",
            "cadence", "localHour", "localMinute", "revision", "updatedAt", "requestId");
        Assert.Equal(JsonValueKind.Null, getBody.RootElement.GetProperty("updatedAt").ValueKind);

        using var putRequest = new HttpRequestMessage(HttpMethod.Put, "/v1/notifications/preferences")
        {
            Content = JsonContent.Create(ValidRequest()),
        };
        putRequest.Headers.Add("X-Test-User", $"{UserId}|true");
        using var putResponse = await testClient.SendAsync(putRequest, cancellationToken: TestContext.Current.CancellationToken);
        using var putBody = JsonDocument.Parse(await putResponse.Content.ReadAsStringAsync(cancellationToken: TestContext.Current.CancellationToken));

        Assert.Equal(HttpStatusCode.OK, putResponse.StatusCode);
        AssertExactKeys(putBody.RootElement,
            "schema", "version", "outcome", "enabled", "continueUnfinishedEnabled", "reviewCompletedEnabled",
            "cadence", "localHour", "localMinute", "revision", "updatedAt", "requestId");
        Assert.Equal(JsonValueKind.String, putBody.RootElement.GetProperty("updatedAt").ValueKind);
    }

    [Fact]
    public async Task Notification_preferences_require_authenticated_identity()
    {
        using var response = await client.GetAsync("/v1/notifications/preferences", cancellationToken: TestContext.Current.CancellationToken);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>(cancellationToken: TestContext.Current.CancellationToken);

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal("AUTH_REQUIRED", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Notification_preferences_require_verified_identity()
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, "/v1/notifications/preferences");
        request.Headers.Add("X-Test-User", $"{UserId}|false");

        using var response = await client.SendAsync(request, cancellationToken: TestContext.Current.CancellationToken);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>(cancellationToken: TestContext.Current.CancellationToken);

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
        Assert.Equal("FORBIDDEN", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Notification_preferences_fail_closed_when_database_is_not_configured()
    {
        using var request = new HttpRequestMessage(HttpMethod.Get, "/v1/notifications/preferences");
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request, cancellationToken: TestContext.Current.CancellationToken);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>(cancellationToken: TestContext.Current.CancellationToken);

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal("DATABASE_NOT_CONFIGURED", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Notification_preferences_update_requires_authenticated_identity()
    {
        using var response = await client.PutAsJsonAsync(
            "/v1/notifications/preferences",
            ValidRequest(), cancellationToken: TestContext.Current.CancellationToken);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>(cancellationToken: TestContext.Current.CancellationToken);

        Assert.Equal(HttpStatusCode.Unauthorized, response.StatusCode);
        Assert.Equal("AUTH_REQUIRED", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Notification_preferences_update_rejects_invalid_schedule()
    {
        using var request = new HttpRequestMessage(HttpMethod.Put, "/v1/notifications/preferences")
        {
            Content = JsonContent.Create(new
            {
                schema = "evidrilo.notification-preferences-update",
                version = "1",
                enabled = true,
                continueUnfinishedEnabled = true,
                reviewCompletedEnabled = false,
                cadence = "daily",
                localHour = 24,
                localMinute = 0,
            }),
        };
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request, cancellationToken: TestContext.Current.CancellationToken);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>(cancellationToken: TestContext.Current.CancellationToken);

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal("INVALID_NOTIFICATION_PREFERENCES", body.GetProperty("code").GetString());
    }

    [Fact]
    public async Task Notification_preferences_update_requires_an_expected_revision()
    {
        var requests = new object[]
        {
            new
            {
                schema = "evidrilo.notification-preferences-update",
                version = "1",
                enabled = true,
                continueUnfinishedEnabled = true,
                reviewCompletedEnabled = false,
                cadence = "weekly",
                localHour = 9,
                localMinute = 30,
            },
            new
            {
                schema = "evidrilo.notification-preferences-update",
                version = "1",
                enabled = true,
                continueUnfinishedEnabled = true,
                reviewCompletedEnabled = false,
                cadence = "weekly",
                localHour = 9,
                localMinute = 30,
                expectedRevision = (long?)null,
            },
        };

        foreach (var payload in requests)
        {
            using var request = new HttpRequestMessage(HttpMethod.Put, "/v1/notifications/preferences")
            {
                Content = JsonContent.Create(payload),
            };
            request.Headers.Add("X-Test-User", $"{UserId}|true");

            using var response = await client.SendAsync(request, cancellationToken: TestContext.Current.CancellationToken);
            var body = await response.Content.ReadFromJsonAsync<JsonElement>(cancellationToken: TestContext.Current.CancellationToken);

            Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
            Assert.Equal("INVALID_NOTIFICATION_PREFERENCES", body.GetProperty("code").GetString());
        }
    }

    [Fact]
    public async Task Notification_preferences_update_fails_closed_when_database_is_not_configured()
    {
        using var request = new HttpRequestMessage(HttpMethod.Put, "/v1/notifications/preferences")
        {
            Content = JsonContent.Create(ValidRequest()),
        };
        request.Headers.Add("X-Test-User", $"{UserId}|true");

        using var response = await client.SendAsync(request, cancellationToken: TestContext.Current.CancellationToken);
        var body = await response.Content.ReadFromJsonAsync<JsonElement>(cancellationToken: TestContext.Current.CancellationToken);

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal("DATABASE_NOT_CONFIGURED", body.GetProperty("code").GetString());
    }

    private static object ValidRequest() => new
    {
        schema = "evidrilo.notification-preferences-update",
        version = "1",
        enabled = true,
        continueUnfinishedEnabled = true,
        reviewCompletedEnabled = false,
        cadence = "weekly",
        localHour = 9,
        localMinute = 30,
        expectedRevision = 0,
    };

    private static void AssertExactKeys(JsonElement element, params string[] expectedKeys)
    {
        var actualKeys = element.EnumerateObject()
            .Select(property => property.Name)
            .OrderBy(name => name, StringComparer.Ordinal)
            .ToArray();
        Assert.Equal(expectedKeys.OrderBy(name => name, StringComparer.Ordinal), actualKeys);
    }

    private sealed class DefaultNotificationPreferencesStore : INotificationPreferencesStore
    {
        public Task<NotificationPreferencesSnapshot> GetOwnAsync(
            Guid accountId,
            CancellationToken cancellationToken) =>
            Task.FromResult(NotificationPreferencesSnapshot.Defaults());

        public Task<NotificationPreferencesWriteResult> PutOwnAsync(
            Guid accountId,
            NotificationPreferencesUpdateRequest request,
            CancellationToken cancellationToken) =>
            Task.FromResult(new NotificationPreferencesWriteResult(
                "accepted",
                new NotificationPreferencesSnapshot(
                    request.Enabled,
                    request.ContinueUnfinishedEnabled,
                    request.ReviewCompletedEnabled,
                    request.Cadence,
                    request.LocalHour,
                    request.LocalMinute,
                    1,
                    new DateTimeOffset(2026, 9, 27, 0, 0, 0, TimeSpan.Zero))));
    }
}
