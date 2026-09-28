using System.Net;
using System.Text;
using Microsoft.Extensions.Configuration;
using Evidrilo.Worker;
using Xunit;

namespace Evidrilo.Worker.Tests;

public sealed class SupabaseAuthAdminClientTests
{
    private static readonly Guid AccountId = Guid.Parse("123e4567-e89b-42d3-a456-426614174000");

    [Fact]
    public async Task Delete_uses_only_the_server_admin_endpoint_and_treats_user_not_found_as_success()
    {
        HttpRequestMessage? capturedRequest = null;
        var handler = new StubHttpMessageHandler((request, _) =>
        {
            capturedRequest = request;
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.NotFound)
            {
                Content = Json("""{"code":"user_not_found","message":"User not found"}"""),
            });
        });
        using var httpClient = new HttpClient(handler);
        var client = new SupabaseAuthAdminClient(httpClient, EnabledOptions());

        await client.DeleteUserAsync(AccountId, CancellationToken.None);

        Assert.NotNull(capturedRequest);
        Assert.Equal(HttpMethod.Delete, capturedRequest!.Method);
        Assert.Equal(
            $"https://project.supabase.co/auth/v1/admin/users/{AccountId:D}",
            capturedRequest.RequestUri?.AbsoluteUri);
        Assert.Equal("synthetic-server-only-key", capturedRequest.Headers.GetValues("apikey").Single());
        Assert.Equal("Bearer", capturedRequest.Headers.Authorization?.Scheme);
        Assert.Equal("synthetic-server-only-key", capturedRequest.Headers.Authorization?.Parameter);
        Assert.Null(capturedRequest.Content);
    }

    [Fact]
    public async Task New_secret_key_is_sent_only_in_the_apikey_header()
    {
        HttpRequestMessage? capturedRequest = null;
        var handler = new StubHttpMessageHandler((request, _) =>
        {
            capturedRequest = request;
            return Task.FromResult(new HttpResponseMessage(HttpStatusCode.NoContent));
        });
        using var httpClient = new HttpClient(handler);
        var client = new SupabaseAuthAdminClient(httpClient, EnabledSecretKeyOptions());

        await client.DeleteUserAsync(AccountId, CancellationToken.None);

        Assert.NotNull(capturedRequest);
        Assert.Equal("sb_secret_synthetic_key", capturedRequest!.Headers.GetValues("apikey").Single());
        Assert.Null(capturedRequest.Headers.Authorization);
    }

    [Fact]
    public async Task Generic_not_found_response_does_not_claim_auth_identity_was_deleted()
    {
        var handler = new StubHttpMessageHandler((_, _) => Task.FromResult(new HttpResponseMessage(HttpStatusCode.NotFound)
        {
            Content = Json("""{"code":"unexpected_not_found","message":"private provider detail"}"""),
        }));
        using var httpClient = new HttpClient(handler);
        var client = new SupabaseAuthAdminClient(httpClient, EnabledOptions());

        var exception = await Assert.ThrowsAsync<SupabaseAuthAdminException>(
            () => client.DeleteUserAsync(AccountId, CancellationToken.None));

        Assert.Equal("AUTH_ADMIN_NOT_FOUND_UNCONFIRMED", exception.ErrorCode);
        Assert.False(exception.Retryable);
        Assert.DoesNotContain("private provider detail", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public async Task Transient_provider_failure_is_retryable_without_exposing_response_body()
    {
        var handler = new StubHttpMessageHandler((_, _) => Task.FromResult(new HttpResponseMessage(HttpStatusCode.ServiceUnavailable)
        {
            Content = Json("private provider response"),
        }));
        using var httpClient = new HttpClient(handler);
        var client = new SupabaseAuthAdminClient(httpClient, EnabledOptions());

        var exception = await Assert.ThrowsAsync<SupabaseAuthAdminException>(
            () => client.DeleteUserAsync(AccountId, CancellationToken.None));

        Assert.Equal("AUTH_ADMIN_TRANSIENT", exception.ErrorCode);
        Assert.True(exception.Retryable);
        Assert.DoesNotContain("private provider response", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public async Task Unauthorized_provider_response_is_terminal_and_redacted()
    {
        var handler = new StubHttpMessageHandler((_, _) => Task.FromResult(new HttpResponseMessage(HttpStatusCode.Unauthorized)
        {
            Content = Json("synthetic secret leaked by provider"),
        }));
        using var httpClient = new HttpClient(handler);
        var client = new SupabaseAuthAdminClient(httpClient, EnabledOptions());

        var exception = await Assert.ThrowsAsync<SupabaseAuthAdminException>(
            () => client.DeleteUserAsync(AccountId, CancellationToken.None));

        Assert.Equal("AUTH_ADMIN_REJECTED", exception.ErrorCode);
        Assert.False(exception.Retryable);
        Assert.DoesNotContain("synthetic secret leaked by provider", exception.Message, StringComparison.Ordinal);
    }

    private static SupabaseAuthAdminOptions EnabledOptions()
    {
        var configuration = new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["SUPABASE_AUTH_ADMIN_ENABLED"] = "true",
                ["SUPABASE_URL"] = "https://project.supabase.co",
                ["SUPABASE_SERVICE_ROLE_KEY"] = "synthetic-server-only-key",
            })
            .Build();
        return SupabaseAuthAdminOptions.From(configuration, "Development");
    }

    private static SupabaseAuthAdminOptions EnabledSecretKeyOptions()
    {
        var configuration = new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["SUPABASE_AUTH_ADMIN_ENABLED"] = "true",
                ["SUPABASE_URL"] = "https://project.supabase.co",
                ["SUPABASE_SECRET_KEY"] = "sb_secret_synthetic_key",
            })
            .Build();
        return SupabaseAuthAdminOptions.From(configuration, "Development");
    }

    private static StringContent Json(string value) => new(value, Encoding.UTF8, "application/json");

    private sealed class StubHttpMessageHandler(
        Func<HttpRequestMessage, CancellationToken, Task<HttpResponseMessage>> send) : HttpMessageHandler
    {
        protected override Task<HttpResponseMessage> SendAsync(
            HttpRequestMessage request,
            CancellationToken cancellationToken) => send(request, cancellationToken);
    }
}
