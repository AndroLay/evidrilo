using System.Globalization;
using Evidrilo.Platform;
using Microsoft.Extensions.Configuration;

namespace Evidrilo.Worker;

public sealed class WorkerConfigurationException : Exception
{
    public WorkerConfigurationException(string message)
        : base(message)
    {
    }
}

public sealed class WorkerOptions
{
    private WorkerOptions(
        string? databaseConnectionString,
        TimeSpan pollInterval,
        int batchSize,
        SupabaseAuthAdminOptions supabaseAuthAdmin)
    {
        DatabaseConnectionString = databaseConnectionString;
        PollInterval = pollInterval;
        BatchSize = batchSize;
        SupabaseAuthAdmin = supabaseAuthAdmin;
    }

    public string? DatabaseConnectionString { get; }

    public TimeSpan PollInterval { get; }

    public int BatchSize { get; }

    public bool DatabaseConfigured => !string.IsNullOrWhiteSpace(DatabaseConnectionString);

    public SupabaseAuthAdminOptions SupabaseAuthAdmin { get; }

    public bool AccountDeletionEnabled => SupabaseAuthAdmin.Enabled;

    public static WorkerOptions From(
        IConfiguration configuration,
        string hostingEnvironment = "Development")
    {
        var rawConnectionString = First(
            configuration["Worker:DatabaseConnectionString"],
            configuration["DATABASE_URL"],
            configuration["SUPABASE_DB_CONNECTION_STRING"]);
        var isDeployedEnvironment = hostingEnvironment.Equals("Production", StringComparison.OrdinalIgnoreCase)
            || hostingEnvironment.Equals("Staging", StringComparison.OrdinalIgnoreCase);
        string? connectionString;
        try
        {
            connectionString = DatabaseConnectionStringParser.Normalize(
                rawConnectionString,
                isDeployedEnvironment);
        }
        catch (FormatException)
        {
            throw new WorkerConfigurationException(
                "DATABASE_URL or SUPABASE_DB_CONNECTION_STRING is invalid; deployed environments require encrypted PostgreSQL transport and an explicit username.");
        }
        var pollSeconds = ParsePositiveInt(
            First(configuration["Worker:PollIntervalSeconds"], configuration["WORKER_POLL_INTERVAL_SECONDS"]),
            5,
            1,
            300,
            "WORKER_POLL_INTERVAL_SECONDS");
        var batchSize = ParsePositiveInt(
            First(configuration["Worker:BatchSize"], configuration["WORKER_BATCH_SIZE"]),
            10,
            1,
            100,
            "WORKER_BATCH_SIZE");
        var supabaseAuthAdmin = SupabaseAuthAdminOptions.From(configuration, hostingEnvironment);
        if (isDeployedEnvironment && string.IsNullOrWhiteSpace(connectionString))
            throw new WorkerConfigurationException("A deployed worker requires DATABASE_URL.");
        if (isDeployedEnvironment && !supabaseAuthAdmin.Enabled)
            throw new WorkerConfigurationException("A deployed worker requires account-auth deletion to be enabled.");

        return new WorkerOptions(
            connectionString,
            TimeSpan.FromSeconds(pollSeconds),
            batchSize,
            supabaseAuthAdmin);
    }

    private static int ParsePositiveInt(string? value, int fallback, int minimum, int maximum, string name)
    {
        if (string.IsNullOrWhiteSpace(value)) return fallback;
        if (!int.TryParse(value, NumberStyles.None, CultureInfo.InvariantCulture, out var parsed)
            || parsed < minimum
            || parsed > maximum)
            throw new WorkerConfigurationException($"{name} must be between {minimum} and {maximum}.");
        return parsed;
    }

    private static string? First(params string?[] values) =>
        values.FirstOrDefault(value => !string.IsNullOrWhiteSpace(value))?.Trim();
}

public sealed class SupabaseAuthAdminOptions
{
    private SupabaseAuthAdminOptions(
        bool enabled,
        Uri? baseUri,
        string? apiKey,
        bool legacyBearerAuthorization)
    {
        Enabled = enabled;
        BaseUri = baseUri;
        ApiKey = apiKey;
        LegacyBearerAuthorization = legacyBearerAuthorization;
    }

    public bool Enabled { get; }

    public Uri? BaseUri { get; }

    public string? ApiKey { get; }

    public bool LegacyBearerAuthorization { get; }

    public TimeSpan RequestTimeout { get; } = TimeSpan.FromSeconds(10);

    public static SupabaseAuthAdminOptions From(IConfiguration configuration, string hostingEnvironment)
    {
        var enabledValue = FirstConfigured(
            configuration["Worker:SupabaseAuthAdminEnabled"],
            configuration["SUPABASE_AUTH_ADMIN_ENABLED"]);
        if (!string.IsNullOrWhiteSpace(enabledValue)
            && !bool.TryParse(enabledValue, out _))
        {
            throw new WorkerConfigurationException("SUPABASE_AUTH_ADMIN_ENABLED must be true or false.");
        }

        var enabled = bool.TryParse(enabledValue, out var parsedEnabled) && parsedEnabled;
        if (!enabled)
            return new SupabaseAuthAdminOptions(false, null, null, false);

        var rawUrl = FirstConfigured(configuration["Worker:SupabaseUrl"], configuration["SUPABASE_URL"]);
        if (!Uri.TryCreate(rawUrl, UriKind.Absolute, out var baseUri)
            || baseUri.Scheme != Uri.UriSchemeHttps
            || string.IsNullOrWhiteSpace(baseUri.Host)
            || !string.IsNullOrEmpty(baseUri.UserInfo)
            || baseUri.AbsolutePath != "/"
            || !string.IsNullOrEmpty(baseUri.Query)
            || !string.IsNullOrEmpty(baseUri.Fragment))
        {
            throw new WorkerConfigurationException(
                "Enabled account-auth deletion requires an HTTPS SUPABASE_URL origin without credentials, path, query, or fragment.");
        }

        var configuredSecretKey = configuration["Worker:SupabaseSecretKey"];
        var secretKey = !string.IsNullOrEmpty(configuredSecretKey)
            ? configuredSecretKey
            : configuration["SUPABASE_SECRET_KEY"];
        var legacyServiceRoleKey = FirstConfigured(
            configuration["Worker:SupabaseServiceRoleKey"],
            configuration["SUPABASE_SERVICE_ROLE_KEY"]);
        var hasSecretKey = !string.IsNullOrEmpty(secretKey);
        var apiKey = hasSecretKey ? secretKey : legacyServiceRoleKey;
        if (string.IsNullOrWhiteSpace(apiKey)
            || apiKey.Length > 4096
            || apiKey.Any(character => character < '!' || character > '~')
            || (hasSecretKey && !apiKey.StartsWith("sb_secret_", StringComparison.Ordinal)))
        {
            throw new WorkerConfigurationException(
                "Enabled account-auth deletion requires a valid SUPABASE_SECRET_KEY (preferred) or legacy SUPABASE_SERVICE_ROLE_KEY.");
        }

        return new SupabaseAuthAdminOptions(true, baseUri, apiKey, legacyBearerAuthorization: !hasSecretKey);
    }

    private static string? FirstConfigured(params string?[] values) =>
        values.FirstOrDefault(value => !string.IsNullOrWhiteSpace(value))?.Trim();
}
