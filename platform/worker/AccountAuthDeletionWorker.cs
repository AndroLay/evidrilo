using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;

namespace Evidrilo.Worker;

public sealed class AccountAuthDeletionWorker : BackgroundService
{
    private readonly ILogger<AccountAuthDeletionWorker> logger;
    private readonly WorkerOptions options;
    private readonly IAccountAuthDeletionOutboxStore store;
    private readonly ISupabaseAuthAdminClient authAdminClient;

    public AccountAuthDeletionWorker(
        ILogger<AccountAuthDeletionWorker> logger,
        WorkerOptions options,
        IAccountAuthDeletionOutboxStore store,
        ISupabaseAuthAdminClient authAdminClient)
    {
        this.logger = logger;
        this.options = options;
        this.store = store;
        this.authAdminClient = authAdminClient;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        logger.LogInformation("Account-auth deletion worker started with bounded retries");
        try
        {
            using var timer = new PeriodicTimer(options.PollInterval);
            while (await timer.WaitForNextTickAsync(stoppingToken))
                await ProcessBatchAsync(stoppingToken);
        }
        catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
        {
            // An interrupted provider request leaves its lease to expire and be safely retried.
        }
        finally
        {
            logger.LogInformation("Account-auth deletion worker stopped");
        }
    }

    public async Task ProcessBatchAsync(CancellationToken cancellationToken)
    {
        IReadOnlyList<AccountAuthDeletionJob> jobs;
        try
        {
            jobs = await store.ClaimAsync(options.BatchSize, cancellationToken);
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            throw;
        }
        catch (WorkerDatabaseException exception)
        {
            logger.LogError("Account-auth deletion outbox is unavailable with code {ErrorCode}", exception.Code);
            return;
        }

        foreach (var job in jobs)
        {
            try
            {
                await authAdminClient.DeleteUserAsync(job.AccountId, cancellationToken);
                await store.CompleteAsync(job, cancellationToken);
            }
            catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
            {
                throw;
            }
            catch (SupabaseAuthAdminException exception)
            {
                logger.LogWarning(
                    "Supabase Auth deletion attempt {Attempt} failed with safe code {ErrorCode}; retryable={Retryable}",
                    job.Attempts,
                    exception.ErrorCode,
                    exception.Retryable);
                await RecordFailureAsync(job, exception.ErrorCode, exception.Retryable, cancellationToken);
            }
            catch (WorkerDatabaseException exception)
            {
                logger.LogError(
                    "Account-auth deletion state could not be recorded with code {ErrorCode} at attempt {Attempt}",
                    exception.Code,
                    job.Attempts);
            }
        }
    }

    private async Task RecordFailureAsync(
        AccountAuthDeletionJob job,
        string errorCode,
        bool retryable,
        CancellationToken cancellationToken)
    {
        try
        {
            await store.FailAsync(job, errorCode, retryable, cancellationToken);
            if (!retryable || job.Attempts >= AccountAuthDeletionRetryPolicy.MaxAttempts)
            {
                logger.LogError(
                    "Account-auth deletion entered terminal state with safe code {ErrorCode} at attempt {Attempt}",
                    errorCode,
                    job.Attempts);
            }
        }
        catch (OperationCanceledException) when (cancellationToken.IsCancellationRequested)
        {
            throw;
        }
        catch (WorkerDatabaseException exception)
        {
            logger.LogError("Account-auth deletion failure state could not be recorded with code {ErrorCode}", exception.Code);
        }
    }
}
