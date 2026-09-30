using Evidrilo.Worker;
using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;
using Xunit;

namespace Evidrilo.Worker.Tests;

public sealed class AccountExportWorkerTests
{
    private static readonly AccountExportWorkItem WorkItem = new(
        Guid.Parse("123e4567-e89b-42d3-a456-426614174000"),
        Guid.Parse("223e4567-e89b-42d3-a456-426614174000"),
        "export_req_0001",
        1,
        Guid.Parse("323e4567-e89b-42d3-a456-426614174000"),
        DateTimeOffset.UtcNow.AddSeconds(60));

    [Fact]
    public async Task Export_worker_requires_the_global_lock_before_claiming()
    {
        var store = new StubExportStore { LockAvailable = false, Next = WorkItem };
        using var loggerFactory = LoggerFactory.Create(_ => { });
        using var worker = new AccountExportWorker(
            loggerFactory.CreateLogger<AccountExportWorker>(),
            Options(),
            store,
            new StubSnapshotBuilder((_, _) => Task.FromResult(new byte[] { 1, 2, 3 })));

        var processed = await worker.ProcessNextAsync(CancellationToken.None);

        Assert.False(processed);
        Assert.Equal(0, store.ClaimCalls);
        Assert.Equal(0, store.PurgeCalls);
    }

    [Fact]
    public async Task Export_worker_claims_one_job_and_completes_its_artifact()
    {
        var store = new StubExportStore { Next = WorkItem };
        var snapshot = new StubSnapshotBuilder((_, _) => Task.FromResult(new byte[] { 9, 8, 7 }));
        using var loggerFactory = LoggerFactory.Create(_ => { });
        using var worker = new AccountExportWorker(
            loggerFactory.CreateLogger<AccountExportWorker>(),
            Options(),
            store,
            snapshot);

        var processed = await worker.ProcessNextAsync(CancellationToken.None);

        Assert.True(processed);
        Assert.Equal(1, store.ClaimCalls);
        Assert.Equal(1, snapshot.BuildCalls);
        Assert.Equal(new byte[] { 9, 8, 7 }, Assert.Single(store.Completed).Artifact);
        Assert.Empty(store.Failures);
        Assert.Equal(1, store.PurgeCalls);
    }

    [Fact]
    public async Task Oversized_export_is_failed_without_persisting_partial_bytes()
    {
        var store = new StubExportStore { Next = WorkItem };
        var snapshot = new StubSnapshotBuilder((_, _) =>
            Task.FromException<byte[]>(new AccountExportArtifactTooLargeException()));
        using var loggerFactory = LoggerFactory.Create(_ => { });
        using var worker = new AccountExportWorker(
            loggerFactory.CreateLogger<AccountExportWorker>(),
            Options(),
            store,
            snapshot);

        await worker.ProcessNextAsync(CancellationToken.None);

        Assert.Empty(store.Completed);
        var failure = Assert.Single(store.Failures);
        Assert.Equal("ACCOUNT_EXPORT_TOO_LARGE", failure.ErrorCode);
        Assert.False(failure.Retryable);
    }

    [Fact]
    public async Task Shutdown_cancellation_leaves_the_export_leased_for_safe_recovery()
    {
        var store = new StubExportStore { Next = WorkItem };
        using var cancellation = new CancellationTokenSource();
        var snapshot = new StubSnapshotBuilder(async (_, token) =>
        {
            cancellation.Cancel();
            await Task.Delay(Timeout.InfiniteTimeSpan, token);
            return [];
        });
        using var loggerFactory = LoggerFactory.Create(_ => { });
        using var worker = new AccountExportWorker(
            loggerFactory.CreateLogger<AccountExportWorker>(),
            Options(),
            store,
            snapshot);

        await Assert.ThrowsAnyAsync<OperationCanceledException>(
            () => worker.ProcessNextAsync(cancellation.Token));

        Assert.Empty(store.Completed);
        Assert.Empty(store.Failures);
    }

    [Fact]
    public async Task Artifact_stream_enforces_its_exact_byte_ceiling()
    {
        await using var stream = new BoundedAccountExportArtifactStream(4);
        await stream.WriteAsync(new byte[] { 1, 2, 3, 4 }, CancellationToken.None);

        Assert.Equal(4, stream.Length);
        Assert.Throws<AccountExportArtifactTooLargeException>(() => stream.WriteByte(5));
    }

    private static WorkerOptions Options() => WorkerOptions.From(
        new ConfigurationBuilder().AddInMemoryCollection(new Dictionary<string, string?>()).Build(),
        "Development");

    private sealed class StubSnapshotBuilder(Func<AccountExportWorkItem, CancellationToken, Task<byte[]>> build)
        : IAccountExportSnapshotBuilder
    {
        public int BuildCalls { get; private set; }

        public Task<byte[]> BuildAsync(AccountExportWorkItem item, CancellationToken cancellationToken)
        {
            BuildCalls++;
            return build(item, cancellationToken);
        }
    }

    private sealed class StubExportStore : IAccountExportWorkerStore
    {
        public bool LockAvailable { get; init; } = true;
        public AccountExportWorkItem? Next { get; set; }
        public int ClaimCalls { get; private set; }
        public int PurgeCalls { get; private set; }
        public List<(AccountExportWorkItem Item, byte[] Artifact)> Completed { get; } = [];
        public List<(AccountExportWorkItem Item, string ErrorCode, bool Retryable)> Failures { get; } = [];

        public Task<IAccountExportGlobalLock?> TryAcquireGlobalLockAsync(CancellationToken cancellationToken) =>
            Task.FromResult<IAccountExportGlobalLock?>(LockAvailable ? new StubGlobalLock() : null);

        public Task<AccountExportWorkItem?> ClaimNextAsync(CancellationToken cancellationToken)
        {
            ClaimCalls++;
            var item = Next;
            Next = null;
            return Task.FromResult(item);
        }

        public Task<bool> RenewLeaseAsync(AccountExportWorkItem item, CancellationToken cancellationToken) =>
            Task.FromResult(true);

        public Task<bool> CompleteAsync(AccountExportWorkItem item, byte[] artifact, CancellationToken cancellationToken)
        {
            Completed.Add((item, artifact.ToArray()));
            return Task.FromResult(true);
        }

        public Task<bool> FailAsync(
            AccountExportWorkItem item,
            string errorCode,
            bool retryable,
            CancellationToken cancellationToken)
        {
            Failures.Add((item, errorCode, retryable));
            return Task.FromResult(true);
        }

        public Task PurgeExpiredAsync(CancellationToken cancellationToken)
        {
            PurgeCalls++;
            return Task.CompletedTask;
        }
    }

    private sealed class StubGlobalLock : IAccountExportGlobalLock
    {
        public Task<bool> EnsureHeldAsync(CancellationToken cancellationToken) => Task.FromResult(true);

        public ValueTask DisposeAsync() => ValueTask.CompletedTask;
    }
}
