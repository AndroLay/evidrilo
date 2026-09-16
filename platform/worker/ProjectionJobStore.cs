using Npgsql;
using NpgsqlTypes;

namespace Evidrilo.Worker;

public sealed record ProjectionJob(
    Guid JobId,
    Guid AccountId,
    int Attempts);

internal static class ProjectionJobSql
{
    public const string FencedRunningJob = """
        status = 'running'
        and attempts = @attempts
        """;

    public const string OwnedLease = """
        status = 'running'
        and attempts = @attempts
        and leased_until > now()
        """;
}

public interface IProjectionJobStore
{
    Task<IReadOnlyList<ProjectionJob>> ClaimAsync(
        int batchSize,
        CancellationToken cancellationToken);

    Task ProcessAsync(
        ProjectionJob job,
        CancellationToken cancellationToken);

    Task FailAsync(
        ProjectionJob job,
        string errorCode,
        CancellationToken cancellationToken);
}

public sealed class EmptyProjectionJobStore : IProjectionJobStore
{
    public Task<IReadOnlyList<ProjectionJob>> ClaimAsync(
        int batchSize,
        CancellationToken cancellationToken) =>
        Task.FromResult<IReadOnlyList<ProjectionJob>>(Array.Empty<ProjectionJob>());

    public Task ProcessAsync(ProjectionJob job, CancellationToken cancellationToken) => Task.CompletedTask;

    public Task FailAsync(ProjectionJob job, string errorCode, CancellationToken cancellationToken) => Task.CompletedTask;
}

public sealed class NpgsqlProjectionJobStore : IProjectionJobStore, IDisposable
{
    private readonly NpgsqlDataSource dataSource;

    public NpgsqlProjectionJobStore(string connectionString)
    {
        dataSource = NpgsqlDataSource.Create(connectionString);
    }

    public async Task<IReadOnlyList<ProjectionJob>> ClaimAsync(
        int batchSize,
        CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await using var command = connection.CreateCommand();
            command.Transaction = transaction;
            command.CommandText = """
                with candidate as (
                    select job_id
                    from public.worker_jobs
                    where job_type = 'analytics_projection'
                      and account_id is not null
                      and available_at <= now()
                      and attempts < 10
                      and (
                          status = 'queued'
                          or (status = 'running' and leased_until < now())
                      )
                    order by available_at, created_at, job_id
                    for update skip locked
                    limit @batch_size
                )
                update public.worker_jobs jobs
                set status = 'running',
                    attempts = jobs.attempts + 1,
                    leased_until = now() + interval '60 seconds',
                    last_error_code = null
                from candidate
                where jobs.job_id = candidate.job_id
                returning jobs.job_id, jobs.account_id, jobs.attempts;
                """;
            command.Parameters.AddWithValue("batch_size", NpgsqlDbType.Integer, batchSize);
            var jobs = new List<ProjectionJob>(batchSize);
            await using (var reader = await command.ExecuteReaderAsync(cancellationToken))
            {
                while (await reader.ReadAsync(cancellationToken))
                {
                    jobs.Add(new ProjectionJob(reader.GetGuid(0), reader.GetGuid(1), reader.GetInt32(2)));
                }
            }

            await transaction.CommitAsync(cancellationToken);
            return jobs;
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (NpgsqlException exception)
        {
            throw new WorkerDatabaseException("PROJECTION_CLAIM_FAILED", exception);
        }
    }

    public async Task ProcessAsync(ProjectionJob job, CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);

            await using var lease = connection.CreateCommand();
            lease.Transaction = transaction;
            lease.CommandText = $"""
                select 1
                from public.worker_jobs
                where job_id = @job_id
                  and {ProjectionJobSql.OwnedLease}
                for update;
                """;
            lease.Parameters.AddWithValue("job_id", NpgsqlDbType.Uuid, job.JobId);
            lease.Parameters.AddWithValue("attempts", NpgsqlDbType.Integer, job.Attempts);
            if (await lease.ExecuteScalarAsync(cancellationToken) is null)
            {
                throw new WorkerLeaseLostException();
            }

            await using var upsert = connection.CreateCommand();
            upsert.Transaction = transaction;
            upsert.CommandText = """
                with aggregate as (
                    select
                        count(*) filter (where event_name = 'attempt_completed')::integer as attempts_observed,
                        count(*) filter (where event_name = 'attempt_completed')::integer as completed_attempts,
                        count(*) filter (where event_name = 'revision_recorded')::integer as revisions_observed,
                        count(*) filter (where event_name = 'attempt_completed' and properties ->> 'outcome' = 'PASS')::integer as pass_count,
                        count(*) filter (where event_name = 'attempt_completed' and properties ->> 'outcome' = 'ACTION_REQUIRED')::integer as action_required_count,
                        count(*) filter (where event_name = 'attempt_completed' and properties ->> 'outcome' in ('CANNOT_ASSESS', 'INCOMPLETE'))::integer as abstention_count
                    from public.analytics_events
                    where account_id = @account_id
                )
                insert into public.progress_projections (
                    account_id, calculation_version, attempts_observed, completed_attempts,
                    revisions_observed, pass_count, action_required_count,
                    abstention_count, coverage, rebuilt_at
                )
                select
                    @account_id,
                    'progress.v1',
                    attempts_observed,
                    completed_attempts,
                    revisions_observed,
                    pass_count,
                    action_required_count,
                    abstention_count,
                    case when attempts_observed = 0 then 0
                         else least(1.0, greatest(0.0, completed_attempts::double precision / attempts_observed))
                    end,
                    now()
                from aggregate
                on conflict (account_id) do update set
                    calculation_version = excluded.calculation_version,
                    attempts_observed = excluded.attempts_observed,
                    completed_attempts = excluded.completed_attempts,
                    revisions_observed = excluded.revisions_observed,
                    pass_count = excluded.pass_count,
                    action_required_count = excluded.action_required_count,
                    abstention_count = excluded.abstention_count,
                    coverage = excluded.coverage,
                    rebuilt_at = excluded.rebuilt_at;
                """;
            upsert.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, job.AccountId);
            await upsert.ExecuteNonQueryAsync(cancellationToken);

            await using var daily = connection.CreateCommand();
            daily.Transaction = transaction;
            daily.CommandText = """
                delete from public.progress_daily_projections
                where account_id = @account_id;

                with aggregate as (
                    select
                        (occurred_at at time zone 'UTC')::date as projection_date,
                        count(*) filter (where event_name = 'attempt_completed')::integer as attempts_observed,
                        count(*) filter (where event_name = 'attempt_completed')::integer as completed_attempts,
                        count(*) filter (where event_name = 'revision_recorded')::integer as revisions_observed,
                        count(*) filter (where event_name = 'attempt_completed' and properties ->> 'outcome' = 'PASS')::integer as pass_count,
                        count(*) filter (where event_name = 'attempt_completed' and properties ->> 'outcome' = 'ACTION_REQUIRED')::integer as action_required_count,
                        count(*) filter (where event_name = 'attempt_completed' and properties ->> 'outcome' in ('CANNOT_ASSESS', 'INCOMPLETE'))::integer as abstention_count
                    from public.analytics_events
                    where account_id = @account_id
                    group by (occurred_at at time zone 'UTC')::date
                )
                insert into public.progress_daily_projections (
                    account_id, projection_date, calculation_version,
                    attempts_observed, completed_attempts, revisions_observed,
                    pass_count, action_required_count, abstention_count,
                    coverage, rebuilt_at
                )
                select
                    @account_id,
                    projection_date,
                    'progress.v1',
                    attempts_observed,
                    completed_attempts,
                    revisions_observed,
                    pass_count,
                    action_required_count,
                    abstention_count,
                    case when attempts_observed = 0 then 0
                         else least(1.0, greatest(0.0, completed_attempts::double precision / attempts_observed))
                    end,
                    now()
                from aggregate;
                """;
            daily.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, job.AccountId);
            await daily.ExecuteNonQueryAsync(cancellationToken);

            await using var complete = connection.CreateCommand();
            complete.Transaction = transaction;
            complete.CommandText = $"""
                update public.worker_jobs
                set status = 'succeeded', leased_until = null, last_error_code = null
                where job_id = @job_id
                  and {ProjectionJobSql.FencedRunningJob};
                """;
            complete.Parameters.AddWithValue("job_id", NpgsqlDbType.Uuid, job.JobId);
            complete.Parameters.AddWithValue("attempts", NpgsqlDbType.Integer, job.Attempts);
            if (await complete.ExecuteNonQueryAsync(cancellationToken) != 1)
            {
                throw new WorkerLeaseLostException();
            }
            await transaction.CommitAsync(cancellationToken);
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (NpgsqlException exception)
        {
            throw new WorkerDatabaseException("PROJECTION_PROCESS_FAILED", exception);
        }
    }

    public async Task FailAsync(
        ProjectionJob job,
        string errorCode,
        CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var command = connection.CreateCommand();
            command.CommandText = $"""
                update public.worker_jobs
                set status = case when attempts >= 10 then 'failed' else 'queued' end,
                    available_at = now() + make_interval(secs => least(300, power(2, greatest(0, attempts - 1)) * 5)),
                    leased_until = null,
                    last_error_code = @error_code
                where job_id = @job_id
                  and {ProjectionJobSql.FencedRunningJob};
                """;
            command.Parameters.AddWithValue("job_id", NpgsqlDbType.Uuid, job.JobId);
            command.Parameters.AddWithValue("attempts", NpgsqlDbType.Integer, job.Attempts);
            command.Parameters.AddWithValue("error_code", NpgsqlDbType.Text, SafeErrorCode(errorCode));
            await command.ExecuteNonQueryAsync(cancellationToken);
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (NpgsqlException exception)
        {
            throw new WorkerDatabaseException("PROJECTION_FAILURE_RECORD_FAILED", exception);
        }
    }

    public void Dispose() => dataSource.Dispose();

    private static string SafeErrorCode(string value)
    {
        var normalized = new string(value
            .Where(character => char.IsLetterOrDigit(character) || character == '_')
            .Take(64)
            .ToArray())
            .ToUpperInvariant();
        return string.IsNullOrWhiteSpace(normalized) ? "UNKNOWN_FAILURE" : normalized;
    }
}

public class WorkerDatabaseException : Exception
{
    public WorkerDatabaseException(string code, Exception innerException)
        : base(code, innerException)
    {
        Code = code;
    }

    public string Code { get; }
}

public sealed class WorkerLeaseLostException : WorkerDatabaseException
{
    public WorkerLeaseLostException()
        : base("PROJECTION_LEASE_LOST", new InvalidOperationException("Projection job lease is no longer owned."))
    {
    }
}

public static class ProjectionRetryPolicy
{
    public static TimeSpan DelayForAttempt(int attempts)
    {
        if (attempts <= 0) return TimeSpan.FromSeconds(5);
        var seconds = Math.Min(300, Math.Pow(2, Math.Min(8, attempts - 1)) * 5);
        return TimeSpan.FromSeconds(seconds);
    }
}
