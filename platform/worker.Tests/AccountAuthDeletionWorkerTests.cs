using Microsoft.Extensions.Configuration;
using Microsoft.Extensions.Logging;
using Evidrilo.Worker;
using Xunit;

namespace Evidrilo.Worker.Tests;

public sealed class AccountAuthDeletionWorkerTests
{
    private static readonly Guid AccountId = Guid.Parse("123e4567-e89b-42d3-a456-426614174000");
    private static readonly Guid OutboxId = Guid.Parse("223e4567-e89b-42d3-a456-426614174000");

    [Fact]
    public async Task Successful_provider_delete_completes_the_fenced_outbox_job()
    {
        var job = new AccountAuthDeletionJob(OutboxId, AccountId, 1, Guid.NewGuid());
        var store = new StubOutboxStore([job]);
        var provider = new StubAuthAdminClient((_, _) => Task.CompletedTask);
        using var loggerFactory = LoggerFactory.Create(_ => { });
        var worker = new AccountAuthDeletionWorker(
            loggerFactory.CreateLogger<AccountAuthDeletionWorker>(),
            Options(),
            store,
            provider);

        await worker.ProcessBatchAsync(CancellationToken.None);

        Assert.Equal([AccountId], provider.DeletedAccounts);
        Assert.Equal([job], store.Completed);
        Assert.Empty(store.Failures);
    }

    [Fact]
    public async Task Transient_provider_failure_is_recorded_for_bounded_retry()
    {
        var job = new AccountAuthDeletionJob(OutboxId, AccountId, 2, Guid.NewGuid());
        var store = new StubOutboxStore([job]);
        var provider = new StubAuthAdminClient((_, _) =>
            Task.FromException(SupabaseAuthAdminException.Create("AUTH_ADMIN_TRANSIENT", retryable: true)));
        using var loggerFactory = LoggerFactory.Create(_ => { });
        var worker = new AccountAuthDeletionWorker(
            loggerFactory.CreateLogger<AccountAuthDeletionWorker>(),
            Options(),
            store,
            provider);

        await worker.ProcessBatchAsync(CancellationToken.None);

        var failure = Assert.Single(store.Failures);
        Assert.Equal(job, failure.Job);
        Assert.Equal("AUTH_ADMIN_TRANSIENT", failure.ErrorCode);
        Assert.True(failure.Retryable);
        Assert.Empty(store.Completed);
    }

    [Fact]
    public async Task Terminal_provider_failure_is_recorded_without_retry()
    {
        var job = new AccountAuthDeletionJob(OutboxId, AccountId, 8, Guid.NewGuid());
        var store = new StubOutboxStore([job]);
        var provider = new StubAuthAdminClient((_, _) =>
            Task.FromException(SupabaseAuthAdminException.Create("AUTH_ADMIN_REJECTED", retryable: false)));
        using var loggerFactory = LoggerFactory.Create(_ => { });
        var worker = new AccountAuthDeletionWorker(
            loggerFactory.CreateLogger<AccountAuthDeletionWorker>(),
            Options(),
            store,
            provider);

        await worker.ProcessBatchAsync(CancellationToken.None);

        var failure = Assert.Single(store.Failures);
        Assert.Equal("AUTH_ADMIN_REJECTED", failure.ErrorCode);
        Assert.False(failure.Retryable);
    }

    [Fact]
    public async Task Shutdown_cancellation_leaves_job_leased_for_expiry_instead_of_marking_failure()
    {
        var job = new AccountAuthDeletionJob(OutboxId, AccountId, 1, Guid.NewGuid());
        var store = new StubOutboxStore([job]);
        using var cancellation = new CancellationTokenSource();
        var provider = new StubAuthAdminClient((_, token) =>
        {
            cancellation.Cancel();
            return Task.FromCanceled(token);
        });
        using var loggerFactory = LoggerFactory.Create(_ => { });
        var worker = new AccountAuthDeletionWorker(
            loggerFactory.CreateLogger<AccountAuthDeletionWorker>(),
            Options(),
            store,
            provider);

        await Assert.ThrowsAnyAsync<OperationCanceledException>(
            () => worker.ProcessBatchAsync(cancellation.Token));

        Assert.Empty(store.Completed);
        Assert.Empty(store.Failures);
    }

    private static WorkerOptions Options()
    {
        var configuration = new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["DATABASE_URL"] = "Host=database.invalid;Database=evidrilo",
                ["SUPABASE_AUTH_ADMIN_ENABLED"] = "true",
                ["SUPABASE_URL"] = "https://project.supabase.co",
                ["SUPABASE_SERVICE_ROLE_KEY"] = "synthetic-server-only-key",
            })
            .Build();
        return WorkerOptions.From(configuration, "Development");
    }

    private sealed class StubOutboxStore(IReadOnlyList<AccountAuthDeletionJob> jobs)
        : IAccountAuthDeletionOutboxStore
    {
        public List<AccountAuthDeletionJob> Completed { get; } = [];

        public List<(AccountAuthDeletionJob Job, string ErrorCode, bool Retryable)> Failures { get; } = [];

        public Task<IReadOnlyList<AccountAuthDeletionJob>> ClaimAsync(
            int batchSize,
            CancellationToken cancellationToken) => Task.FromResult(jobs);

        public Task CompleteAsync(AccountAuthDeletionJob job, CancellationToken cancellationToken)
        {
            Completed.Add(job);
            return Task.CompletedTask;
        }

        public Task FailAsync(
            AccountAuthDeletionJob job,
            string errorCode,
            bool retryable,
            CancellationToken cancellationToken)
        {
            Failures.Add((job, errorCode, retryable));
            return Task.CompletedTask;
        }
    }

    private sealed class StubAuthAdminClient(
        Func<Guid, CancellationToken, Task> delete) : ISupabaseAuthAdminClient
    {
        public List<Guid> DeletedAccounts { get; } = [];

        public async Task DeleteUserAsync(Guid accountId, CancellationToken cancellationToken)
        {
            await delete(accountId, cancellationToken);
            DeletedAccounts.Add(accountId);
        }
    }
}
