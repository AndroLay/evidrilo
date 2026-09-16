using Evidrilo.Worker;
using Xunit;

namespace Evidrilo.Worker.Tests;

public sealed class ProjectionLeaseFencingTests
{
    [Fact]
    public void Process_lease_predicate_requires_current_attempt_and_unexpired_lease()
    {
        Assert.Contains("status = 'running'", ProjectionJobSql.OwnedLease);
        Assert.Contains("attempts = @attempts", ProjectionJobSql.OwnedLease);
        Assert.Contains("leased_until > now()", ProjectionJobSql.OwnedLease);
    }

    [Fact]
    public void Failure_and_completion_predicate_requires_current_attempt()
    {
        Assert.Contains("status = 'running'", ProjectionJobSql.FencedRunningJob);
        Assert.Contains("attempts = @attempts", ProjectionJobSql.FencedRunningJob);
        Assert.DoesNotContain("leased_until", ProjectionJobSql.FencedRunningJob);
    }

    [Fact]
    public void Lease_loss_is_reported_with_a_safe_stable_code()
    {
        var exception = new WorkerLeaseLostException();

        Assert.Equal("PROJECTION_LEASE_LOST", exception.Code);
        Assert.Equal("PROJECTION_LEASE_LOST", exception.Message);
    }
}
