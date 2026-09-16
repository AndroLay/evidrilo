using Microsoft.Extensions.Configuration;
using Evidrilo.Worker;
using Xunit;

namespace Evidrilo.Worker.Tests;

public sealed class ProjectionRetryPolicyTests
{
    [Fact]
    public void Retry_delay_is_bounded_and_monotonic()
    {
        Assert.Equal(TimeSpan.FromSeconds(5), ProjectionRetryPolicy.DelayForAttempt(0));
        Assert.Equal(TimeSpan.FromSeconds(5), ProjectionRetryPolicy.DelayForAttempt(1));
        Assert.Equal(TimeSpan.FromSeconds(10), ProjectionRetryPolicy.DelayForAttempt(2));
        Assert.Equal(TimeSpan.FromMinutes(5), ProjectionRetryPolicy.DelayForAttempt(100));
    }

    [Fact]
    public void Worker_options_default_without_database_and_reject_invalid_bounds()
    {
        var configuration = new ConfigurationBuilder().Build();
        var options = WorkerOptions.From(configuration);

        Assert.False(options.DatabaseConfigured);
        Assert.Equal(5, options.PollInterval.TotalSeconds);
        Assert.Equal(10, options.BatchSize);

        var invalid = new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["WORKER_BATCH_SIZE"] = "101",
            })
            .Build();
        Assert.Throws<WorkerConfigurationException>(() => WorkerOptions.From(invalid));
    }
}
