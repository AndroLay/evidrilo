using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;

namespace Evidrilo.Worker;

public sealed class ProjectionWorker : BackgroundService
{
    private readonly ILogger<ProjectionWorker> logger;
    private readonly WorkerOptions options;
    private readonly IProjectionJobStore store;

    public ProjectionWorker(
        ILogger<ProjectionWorker> logger,
        WorkerOptions options,
        IProjectionJobStore store)
    {
        this.logger = logger;
        this.options = options;
        this.store = store;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        logger.LogInformation(
            "Evidrilo worker started with bounded projection boundary; databaseConfigured={DatabaseConfigured}, batchSize={BatchSize}",
            options.DatabaseConfigured,
            options.BatchSize);
        try
        {
            using var timer = new PeriodicTimer(options.PollInterval);
            while (await timer.WaitForNextTickAsync(stoppingToken))
            {
                IReadOnlyList<ProjectionJob> jobs;
                try
                {
                    jobs = await store.ClaimAsync(options.BatchSize, stoppingToken);
                }
                catch (WorkerDatabaseException exception)
                {
                    logger.LogError(
                        "Projection worker dependency failure with code {ErrorCode}",
                        exception.Code);
                    continue;
                }

                foreach (var job in jobs)
                {
                    try
                    {
                        await store.ProcessAsync(job, stoppingToken);
                    }
                    catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
                    {
                        throw;
                    }
                    catch (WorkerDatabaseException exception)
                    {
                        logger.LogError(
                            "Projection job failed with code {ErrorCode} and attempt {Attempt}",
                            exception.Code,
                            job.Attempts);
                        try
                        {
                            await store.FailAsync(job, exception.Code, stoppingToken);
                        }
                        catch (WorkerDatabaseException failureException)
                        {
                            logger.LogError(
                                "Projection failure state could not be recorded with code {ErrorCode}",
                                failureException.Code);
                        }
                    }
                }
            }
        }
        catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
        {
            // Graceful shutdown is expected; no in-flight job is claimed here.
        }
        finally
        {
            logger.LogInformation("Evidrilo worker stopped");
        }
    }
}
