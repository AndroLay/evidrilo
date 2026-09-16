using System.Globalization;
using System.Net;
using Microsoft.Extensions.Configuration;

namespace Evidrilo.Api.Configuration;

public sealed class PlatformConfigurationException : Exception
{
    public PlatformConfigurationException(string message)
        : base(message)
    {
    }
}

public sealed record ReadinessDependencies(string Config, string Auth, string Database);

public sealed class PlatformOptions
{
    public const string SectionName = "Platform";
    public const string ExpectedRevenueCatEntitlementId = "evidrilo_pro";

    private PlatformOptions(
        string environment,
        int port,
        IReadOnlyList<string> corsAllowedOrigins,
        IReadOnlyList<IPAddress> trustedProxyAddresses,
        string? supabaseUrl,
        string? supabasePublishableKey,
        string? databaseConnectionString,
        string? revenueCatWebhookSecret,
        string? revenueCatWebhookAuthorization,
        string? revenueCatEntitlementId)
    {
        Environment = environment;
        Port = port;
        CorsAllowedOrigins = corsAllowedOrigins;
        TrustedProxyAddresses = trustedProxyAddresses;
        SupabaseUrl = supabaseUrl;
        SupabasePublishableKey = supabasePublishableKey;
        DatabaseConnectionString = databaseConnectionString;
        RevenueCatWebhookSecret = revenueCatWebhookSecret;
        RevenueCatWebhookAuthorization = revenueCatWebhookAuthorization;
        RevenueCatEntitlementId = revenueCatEntitlementId;
    }

    public string Environment { get; }

    public int Port { get; }

    public IReadOnlyList<string> CorsAllowedOrigins { get; }

    /// <summary>
    /// Explicit proxy addresses allowed to provide X-Forwarded-* headers.
    /// An empty list means forwarded headers are ignored by the API.
    /// </summary>
    public IReadOnlyList<IPAddress> TrustedProxyAddresses { get; }

    public string? SupabaseUrl { get; }

    public string? SupabasePublishableKey { get; }

    public string? DatabaseConnectionString { get; }

    public string? RevenueCatWebhookSecret { get; }

    /// <summary>
    /// Optional exact Authorization header value configured in RevenueCat.
    /// This is a server-only secret and is never included in safe summaries.
    /// </summary>
    public string? RevenueCatWebhookAuthorization { get; }

    /// <summary>
    /// The server-owned entitlement that is projected from RevenueCat events.
    /// </summary>
    public string? RevenueCatEntitlementId { get; }

    public bool SupabaseConfigured => !string.IsNullOrWhiteSpace(SupabaseUrl)
        && !string.IsNullOrWhiteSpace(SupabasePublishableKey);

    public bool DatabaseConfigured => !string.IsNullOrWhiteSpace(DatabaseConnectionString);

    public bool BillingConfigured => (!string.IsNullOrWhiteSpace(RevenueCatWebhookSecret)
        || !string.IsNullOrWhiteSpace(RevenueCatWebhookAuthorization))
        && string.Equals(
            RevenueCatEntitlementId,
            ExpectedRevenueCatEntitlementId,
            StringComparison.Ordinal);

    public ReadinessDependencies Readiness => SupabaseConfigured
        ? new ReadinessDependencies("ready", "ready", DatabaseConfigured ? "ready" : "missing")
        : new ReadinessDependencies("missing", "missing", DatabaseConfigured ? "ready" : "missing");

    public static PlatformOptions From(IConfiguration configuration, string hostingEnvironment)
    {
        var environment = NormalizeEnvironment(
            First(configuration["Platform:Environment"], configuration["NODE_ENV"], hostingEnvironment));
        var port = ParsePort(First(configuration["Platform:Port"], configuration["PORT"]));
        var configuredOrigins = First(
            configuration["Platform:CorsAllowedOrigins"],
            configuration["CORS_ALLOWED_ORIGINS"]);
        if (environment is "staging" or "production" && configuredOrigins is null)
        {
            throw new PlatformConfigurationException(
                "CORS_ALLOWED_ORIGINS must be explicitly configured in staging and production.");
        }

        var origins = ParseOrigins(configuredOrigins, environment);
        var trustedProxyAddresses = ParseTrustedProxyAddresses(
            First(configuration["Platform:TrustedProxyAddresses"], configuration["TRUSTED_PROXY_ADDRESSES"]));
        var supabaseUrl = Optional(First(configuration["Platform:SupabaseUrl"], configuration["SUPABASE_URL"]));
        var publishableKey = Optional(
            First(configuration["Platform:SupabasePublishableKey"], configuration["SUPABASE_PUBLISHABLE_KEY"]));
        var databaseConnectionString = Optional(
            First(
                configuration["Platform:DatabaseConnectionString"],
                configuration["DATABASE_URL"],
                configuration["SUPABASE_DB_CONNECTION_STRING"]));
        var revenueCatWebhookSecret = Optional(
            First(configuration["Platform:RevenueCatWebhookSecret"], configuration["REVENUECAT_WEBHOOK_SECRET"]));
        var revenueCatWebhookAuthorization = Optional(
            First(
                configuration["Platform:RevenueCatWebhookAuthorization"],
                configuration["REVENUECAT_WEBHOOK_AUTHORIZATION"]));
        var revenueCatEntitlementId = Optional(
            First(configuration["Platform:RevenueCatEntitlementId"], configuration["REVENUECAT_ENTITLEMENT_ID"]));

        if (supabaseUrl is not null
            && (!Uri.TryCreate(supabaseUrl, UriKind.Absolute, out var parsed)
                || parsed.Scheme != Uri.UriSchemeHttps))
        {
            throw new PlatformConfigurationException("SUPABASE_URL must be an HTTPS URL.");
        }

        var options = new PlatformOptions(
            environment,
            port,
            origins,
            trustedProxyAddresses,
            supabaseUrl,
            publishableKey,
            databaseConnectionString,
            revenueCatWebhookSecret,
            revenueCatWebhookAuthorization,
            revenueCatEntitlementId);
        options.ValidateForStartup();
        return options;
    }

    public void ValidateForStartup()
    {
        if (Environment.Equals("production", StringComparison.OrdinalIgnoreCase)
            && (!SupabaseConfigured || !DatabaseConfigured))
        {
            throw new PlatformConfigurationException("Required platform configuration is missing.");
        }
    }

    public string ToSafeString()
    {
        return $"PlatformOptions(environment={Environment}, port={Port}, origins={CorsAllowedOrigins.Count}, trustedProxies={TrustedProxyAddresses.Count}, supabaseConfigured={SupabaseConfigured}, databaseConfigured={DatabaseConfigured}, billingConfigured={BillingConfigured})";
    }

    private static string NormalizeEnvironment(string? value)
    {
        var normalized = Optional(value)?.ToLowerInvariant() ?? "development";
        return normalized switch
        {
            "development" or "dev" => "development",
            "test" or "testing" => "test",
            "staging" or "stage" => "staging",
            "production" or "prod" => "production",
            _ => throw new PlatformConfigurationException(
                "Platform environment must be development, test, staging, or production."),
        };
    }

    private static int ParsePort(string? value)
    {
        if (string.IsNullOrWhiteSpace(value)) return 5080;
        if (!int.TryParse(value, NumberStyles.None, CultureInfo.InvariantCulture, out var port)
            || port is < 1 or > 65535)
        {
            throw new PlatformConfigurationException("PORT must be an integer between 1 and 65535.");
        }

        return port;
    }

    private static IReadOnlyList<string> ParseOrigins(string? value, string environment)
    {
        var origins = (value ?? "http://localhost:3000")
            .Split(',', StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries);
        if (origins.Length == 0)
        {
            throw new PlatformConfigurationException("CORS_ALLOWED_ORIGINS must contain at least one origin.");
        }

        if (origins.Contains("*", StringComparer.Ordinal))
        {
            throw new PlatformConfigurationException("Wildcard CORS is not allowed.");
        }

        foreach (var origin in origins)
        {
            if (!Uri.TryCreate(origin, UriKind.Absolute, out var parsed)
                || parsed.Host.Length == 0
                || (parsed.Scheme != Uri.UriSchemeHttp && parsed.Scheme != Uri.UriSchemeHttps)
                || parsed.AbsolutePath != "/"
                || parsed.Query.Length != 0
                || parsed.Fragment.Length != 0
                || parsed.UserInfo.Length != 0)
            {
                throw new PlatformConfigurationException("CORS_ALLOWED_ORIGINS contains an invalid origin.");
            }

            if (environment is "staging" or "production" && parsed.Scheme != Uri.UriSchemeHttps)
            {
                throw new PlatformConfigurationException("CORS_ALLOWED_ORIGINS must use HTTPS in staging and production.");
            }
        }

        return origins;
    }

    private static IReadOnlyList<IPAddress> ParseTrustedProxyAddresses(string? value)
    {
        if (string.IsNullOrWhiteSpace(value)) return Array.Empty<IPAddress>();

        var addresses = new List<IPAddress>();
        foreach (var candidate in value.Split(',', StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries))
        {
            if (!IPAddress.TryParse(candidate, out var address)
                || address.Equals(IPAddress.Any)
                || address.Equals(IPAddress.IPv6Any))
            {
                throw new PlatformConfigurationException(
                    "TRUSTED_PROXY_ADDRESSES must contain only explicit IP addresses.");
            }

            if (!addresses.Contains(address)) addresses.Add(address);
        }

        return addresses;
    }

    private static string? First(params string?[] values)
    {
        return values.FirstOrDefault(value => !string.IsNullOrWhiteSpace(value));
    }

    private static string? Optional(string? value)
    {
        var trimmed = value?.Trim();
        return string.IsNullOrWhiteSpace(trimmed) ? null : trimmed;
    }
}
