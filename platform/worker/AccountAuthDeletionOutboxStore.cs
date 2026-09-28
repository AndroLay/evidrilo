using Npgsql;
using NpgsqlTypes;

namespace Evidrilo.Worker;

public sealed record AccountAuthDeletionJob(
    Guid OutboxId,
    Guid AccountId,
    int Attempts,
    Guid LeaseToken);

public interface IAccountAuthDeletionOutboxStore
{
    Task<IReadOnlyList<AccountAuthDeletionJob>> ClaimAsync(
        int batchSize,
        CancellationToken cancellationToken);

    Task CompleteAsync(AccountAuthDeletionJob job, CancellationToken cancellationToken);

    Task FailAsync(
        AccountAuthDeletionJob job,
        string errorCode,
        bool retryable,
        CancellationToken cancellationToken);
}

public sealed class NpgsqlAccountAuthDeletionOutboxStore : IAccountAuthDeletionOutboxStore, IDisposable
{
    private const int MaxAttempts = 8;
    private const int LeaseSeconds = 60;
    private readonly NpgsqlDataSource dataSource;

    public NpgsqlAccountAuthDeletionOutboxStore(string connectionString)
    {
        dataSource = NpgsqlDataSource.Create(connectionString);
    }

    public async Task<IReadOnlyList<AccountAuthDeletionJob>> ClaimAsync(
        int batchSize,
        CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await using (var exhaust = connection.CreateCommand())
            {
                exhaust.Transaction = transaction;
                exhaust.CommandText = """
                update public.account_auth_deletion_outbox
                   set status = 'dead_letter',
                       lease_token = null,
                       leased_until = null,
                       last_error_code = coalesce(last_error_code, 'AUTH_ADMIN_RETRY_EXHAUSTED'),
                       updated_at = now()
                 where status = 'running'
                   and leased_until <= now()
                   and attempts >= @max_attempts;
                """;
                exhaust.Parameters.AddWithValue("max_attempts", NpgsqlDbType.Integer, MaxAttempts);
                await exhaust.ExecuteNonQueryAsync(cancellationToken);
            }

            await using var command = connection.CreateCommand();
            command.Transaction = transaction;
            command.CommandText = """
                with candidate as (
                    select outbox_id
                     from public.account_auth_deletion_outbox
                     where account_id is not null
                       and attempts < @max_attempts
                       and available_at <= now()
                       and (
                           status = 'queued'
                           or (status = 'running' and leased_until <= now())
                       )
                     order by available_at, created_at, outbox_id
                     for update skip locked
                     limit @batch_size
                )
                update public.account_auth_deletion_outbox jobs
                   set status = 'running',
                       attempts = jobs.attempts + 1,
                       lease_token = gen_random_uuid(),
                       leased_until = now() + make_interval(secs => @lease_seconds),
                       last_error_code = null,
                       updated_at = now()
                  from candidate
                 where jobs.outbox_id = candidate.outbox_id
                returning jobs.outbox_id, jobs.account_id, jobs.attempts, jobs.lease_token;
                """;
            command.Parameters.AddWithValue("max_attempts", NpgsqlDbType.Integer, MaxAttempts);
            command.Parameters.AddWithValue("lease_seconds", NpgsqlDbType.Integer, LeaseSeconds);
            command.Parameters.AddWithValue("batch_size", NpgsqlDbType.Integer, Math.Clamp(batchSize, 1, 100));

            var jobs = new List<AccountAuthDeletionJob>(Math.Clamp(batchSize, 1, 100));
            await using (var reader = await command.ExecuteReaderAsync(cancellationToken))
            {
                while (await reader.ReadAsync(cancellationToken))
                {
                    jobs.Add(new AccountAuthDeletionJob(
                        reader.GetGuid(0),
                        reader.GetGuid(1),
                        reader.GetInt32(2),
                        reader.GetGuid(3)));
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
            throw new WorkerDatabaseException("AUTH_DELETION_CLAIM_FAILED", exception);
        }
    }

    public async Task CompleteAsync(AccountAuthDeletionJob job, CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var command = connection.CreateCommand();
            command.CommandText = """
                update public.account_auth_deletion_outbox
                   set status = 'completed',
                       account_id = null,
                       lease_token = null,
                       leased_until = null,
                       completed_at = now(),
                       last_error_code = null,
                       updated_at = now()
                 where outbox_id = @outbox_id
                   and status = 'running'
                   and attempts = @attempts
                   and lease_token = @lease_token
                   and leased_until > now();
                """;
            AddLeaseParameters(command, job);
            if (await command.ExecuteNonQueryAsync(cancellationToken) != 1)
                throw new AccountAuthDeletionLeaseLostException();
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (WorkerDatabaseException)
        {
            throw;
        }
        catch (NpgsqlException exception)
        {
            throw new WorkerDatabaseException("AUTH_DELETION_COMPLETE_FAILED", exception);
        }
    }

    public async Task FailAsync(
        AccountAuthDeletionJob job,
        string errorCode,
        bool retryable,
        CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var command = connection.CreateCommand();
            command.CommandText = """
                update public.account_auth_deletion_outbox
                   set status = case
                           when @retryable and attempts < @max_attempts then 'queued'
                           else 'dead_letter'
                       end,
                       available_at = case
                           when @retryable and attempts < @max_attempts
                           then now() + make_interval(secs => @retry_seconds)
                           else available_at
                       end,
                       lease_token = null,
                       leased_until = null,
                       last_error_code = @error_code,
                       updated_at = now()
                 where outbox_id = @outbox_id
                   and status = 'running'
                   and attempts = @attempts
                   and lease_token = @lease_token
                   and leased_until > now();
                """;
            AddLeaseParameters(command, job);
            command.Parameters.AddWithValue("max_attempts", NpgsqlDbType.Integer, MaxAttempts);
            command.Parameters.AddWithValue("retryable", NpgsqlDbType.Boolean, retryable);
            command.Parameters.AddWithValue(
                "retry_seconds",
                NpgsqlDbType.Integer,
                (int)AccountAuthDeletionRetryPolicy.DelayForAttempt(job.Attempts).TotalSeconds);
            command.Parameters.AddWithValue("error_code", NpgsqlDbType.Text, SafeErrorCode(errorCode));
            await command.ExecuteNonQueryAsync(cancellationToken);
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (NpgsqlException exception)
        {
            throw new WorkerDatabaseException("AUTH_DELETION_FAILURE_RECORD_FAILED", exception);
        }
    }

    public void Dispose() => dataSource.Dispose();

    private static void AddLeaseParameters(NpgsqlCommand command, AccountAuthDeletionJob job)
    {
        command.Parameters.AddWithValue("outbox_id", NpgsqlDbType.Uuid, job.OutboxId);
        command.Parameters.AddWithValue("attempts", NpgsqlDbType.Integer, job.Attempts);
        command.Parameters.AddWithValue("lease_token", NpgsqlDbType.Uuid, job.LeaseToken);
    }

    private static string SafeErrorCode(string errorCode)
    {
        var normalized = new string(errorCode.ToUpperInvariant()
            .Where(character => character is >= 'A' and <= 'Z' or >= '0' and <= '9' or '_')
            .Take(64)
            .ToArray());
        return string.IsNullOrEmpty(normalized) ? "AUTH_ADMIN_FAILURE" : normalized;
    }
}

public sealed class AccountAuthDeletionLeaseLostException()
    : WorkerDatabaseException("AUTH_DELETION_LEASE_LOST", new InvalidOperationException())
{
}

public static class AccountAuthDeletionRetryPolicy
{
    public const int MaxAttempts = 8;

    public static TimeSpan DelayForAttempt(int attempts)
    {
        var exponent = Math.Clamp(attempts - 1, 0, 12);
        return TimeSpan.FromSeconds(Math.Min(900, 5 * Math.Pow(2, exponent)));
    }
}
