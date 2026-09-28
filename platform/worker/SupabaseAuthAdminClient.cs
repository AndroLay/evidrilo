using System.Net;
using System.Net.Http.Headers;
using System.Text.Json;

namespace Evidrilo.Worker;

public interface ISupabaseAuthAdminClient
{
    Task DeleteUserAsync(Guid accountId, CancellationToken cancellationToken);
}

public sealed class SupabaseAuthAdminException : Exception
{
    private static readonly HashSet<string> AllowedErrorCodes =
    [
        "AUTH_ADMIN_TIMEOUT",
        "AUTH_ADMIN_TRANSIENT",
        "AUTH_ADMIN_NOT_FOUND_UNCONFIRMED",
        "AUTH_ADMIN_REJECTED",
    ];

    private SupabaseAuthAdminException(string errorCode, bool retryable)
        : base(errorCode)
    {
        ErrorCode = errorCode;
        Retryable = retryable;
    }

    public string ErrorCode { get; }

    public bool Retryable { get; }

    public static SupabaseAuthAdminException Create(string errorCode, bool retryable) =>
        AllowedErrorCodes.Contains(errorCode)
            ? new SupabaseAuthAdminException(errorCode, retryable)
            : throw new ArgumentException("The Auth Admin error code is not an approved safe code.", nameof(errorCode));
}

public sealed class SupabaseAuthAdminClient : ISupabaseAuthAdminClient
{
    private const int MaxErrorBodyBytes = 8192;
    private readonly HttpClient httpClient;
    private readonly SupabaseAuthAdminOptions options;

    public SupabaseAuthAdminClient(HttpClient httpClient, SupabaseAuthAdminOptions options)
    {
        this.httpClient = httpClient;
        this.options = options;
    }

    public async Task DeleteUserAsync(Guid accountId, CancellationToken cancellationToken)
    {
        if (!options.Enabled || options.BaseUri is null || options.ApiKey is null)
            throw new InvalidOperationException("Supabase Auth Admin deletion is not configured.");
        if (accountId == Guid.Empty)
            throw new ArgumentException("A non-empty account id is required.", nameof(accountId));

        using var request = new HttpRequestMessage(
            HttpMethod.Delete,
            new Uri(options.BaseUri, $"auth/v1/admin/users/{accountId:D}"));
        request.Headers.TryAddWithoutValidation("apikey", options.ApiKey);
        if (options.LegacyBearerAuthorization)
            request.Headers.Authorization = new AuthenticationHeaderValue("Bearer", options.ApiKey);

        using var timeout = CancellationTokenSource.CreateLinkedTokenSource(cancellationToken);
        timeout.CancelAfter(options.RequestTimeout);
        try
        {
            using var response = await httpClient.SendAsync(
                request,
                HttpCompletionOption.ResponseHeadersRead,
                timeout.Token);

            if (response.IsSuccessStatusCode)
                return;

            if (response.StatusCode == HttpStatusCode.NotFound
                && await IsValidatedUserNotFoundAsync(response.Content, timeout.Token))
            {
                return;
            }

            if (response.StatusCode is HttpStatusCode.RequestTimeout or (HttpStatusCode)429
                || (int)response.StatusCode >= 500)
            {
                throw SupabaseAuthAdminException.Create("AUTH_ADMIN_TRANSIENT", retryable: true);
            }

            throw SupabaseAuthAdminException.Create(
                response.StatusCode == HttpStatusCode.NotFound
                    ? "AUTH_ADMIN_NOT_FOUND_UNCONFIRMED"
                    : "AUTH_ADMIN_REJECTED",
                retryable: false);
        }
        catch (OperationCanceledException) when (!cancellationToken.IsCancellationRequested)
        {
            throw SupabaseAuthAdminException.Create("AUTH_ADMIN_TIMEOUT", retryable: true);
        }
        catch (Exception exception) when (exception is HttpRequestException or IOException)
        {
            throw SupabaseAuthAdminException.Create("AUTH_ADMIN_TRANSIENT", retryable: true);
        }
    }

    private static async Task<bool> IsValidatedUserNotFoundAsync(
        HttpContent content,
        CancellationToken cancellationToken)
    {
        if (content.Headers.ContentLength is > MaxErrorBodyBytes)
            return false;

        try
        {
            await using var stream = await content.ReadAsStreamAsync(cancellationToken);
            var buffer = new byte[MaxErrorBodyBytes + 1];
            var length = 0;
            while (length < buffer.Length)
            {
                var read = await stream.ReadAsync(buffer.AsMemory(length), cancellationToken);
                if (read == 0)
                    break;
                length += read;
            }

            if (length > MaxErrorBodyBytes)
                return false;

            using var document = JsonDocument.Parse(buffer.AsMemory(0, length));
            return document.RootElement.ValueKind == JsonValueKind.Object
                && document.RootElement.TryGetProperty("code", out var code)
                && code.ValueKind == JsonValueKind.String
                && string.Equals(code.GetString(), "user_not_found", StringComparison.Ordinal);
        }
        catch (JsonException)
        {
            return false;
        }
        catch (NotSupportedException)
        {
            return false;
        }
    }
}

public static class SupabaseAuthAdminHttpClientFactory
{
    public static HttpClient Create() => new(new SocketsHttpHandler
    {
        AllowAutoRedirect = false,
        ConnectTimeout = TimeSpan.FromSeconds(5),
        PooledConnectionLifetime = TimeSpan.FromMinutes(5),
    })
    {
        Timeout = Timeout.InfiniteTimeSpan,
    };
}
