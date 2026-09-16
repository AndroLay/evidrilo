using System.Globalization;
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
    private WorkerOptions(string? databaseConnectionString, TimeSpan pollInterval, int batchSize)
    {
        DatabaseConnectionString = databaseConnectionString;
        PollInterval = pollInterval;
        BatchSize = batchSize;
    }

    public string? DatabaseConnectionString { get; }

    public TimeSpan PollInterval { get; }

    public int BatchSize { get; }

    public bool DatabaseConfigured => !string.IsNullOrWhiteSpace(DatabaseConnectionString);

    public static WorkerOptions From(IConfiguration configuration)
    {
        var connectionString = First(
            configuration["Worker:DatabaseConnectionString"],
            configuration["DATABASE_URL"],
            configuration["SUPABASE_DB_CONNECTION_STRING"]);
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
        return new WorkerOptions(connectionString, TimeSpan.FromSeconds(pollSeconds), batchSize);
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
