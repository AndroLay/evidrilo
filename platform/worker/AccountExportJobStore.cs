using System.Data;
using Npgsql;
using NpgsqlTypes;

namespace Evidrilo.Worker;

public sealed record AccountExportWorkItem(
    Guid ExportId,
    Guid AccountId,
    string RequestId,
    short Attempts,
    Guid LeaseToken,
    DateTimeOffset LeaseExpiresAt);

public interface IAccountExportGlobalLock : IAsyncDisposable
{
    Task<bool> EnsureHeldAsync(CancellationToken cancellationToken);
}

public interface IAccountExportWorkerStore
{
    Task<IAccountExportGlobalLock?> TryAcquireGlobalLockAsync(CancellationToken cancellationToken);
    Task<AccountExportWorkItem?> ClaimNextAsync(CancellationToken cancellationToken);
    Task<bool> RenewLeaseAsync(AccountExportWorkItem item, CancellationToken cancellationToken);
    Task<bool> CompleteAsync(AccountExportWorkItem item, byte[] artifact, CancellationToken cancellationToken);
    Task<bool> FailAsync(AccountExportWorkItem item, string errorCode, bool retryable, CancellationToken cancellationToken);
    Task PurgeExpiredAsync(CancellationToken cancellationToken);
}

public sealed class NpgsqlAccountExportWorkerStore(NpgsqlDataSource dataSource) : IAccountExportWorkerStore
{
    private const short MaximumAttempts = 3;
    private const long MaximumArtifactBytes = 64L * 1024 * 1024;
    private const long MaximumRetainedArtifactBytes = 512L * 1024 * 1024;
    private const string CapacityLockSql = "select pg_advisory_xact_lock(hashtextextended('evidrilo.account_exports.capacity', 0));";

    public async Task<IAccountExportGlobalLock?> TryAcquireGlobalLockAsync(CancellationToken cancellationToken)
    {
        NpgsqlConnection? connection = null;
        try
        {
            connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var command = connection.CreateCommand();
            command.CommandText = "select pg_try_advisory_lock(hashtextextended('evidrilo.account_exports.worker', 0));";
            var acquired = (bool)(await command.ExecuteScalarAsync(cancellationToken) ?? false);
            if (!acquired)
            {
                await connection.DisposeAsync();
                return null;
            }

            var result = new NpgsqlAccountExportGlobalLock(connection);
            connection = null;
            return result;
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (NpgsqlException exception)
        {
            throw DatabaseFailure("ACCOUNT_EXPORT_LOCK_FAILED", exception);
        }
        finally
        {
            if (connection is not null)
                await connection.DisposeAsync();
        }
    }

    public async Task<AccountExportWorkItem?> ClaimNextAsync(CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(IsolationLevel.ReadCommitted, cancellationToken);
            await using (var exhaust = connection.CreateCommand())
            {
                exhaust.Transaction = transaction;
                exhaust.CommandText = """
                    update public.account_export_jobs
                       set status = 'failed',
                           lease_token = null,
                           lease_expires_at = null,
                           completed_at = now(),
                           last_error_code = coalesce(last_error_code, 'EXPORT_GENERATION_FAILED')
                     where status = 'running' and lease_expires_at <= now() and attempts >= @max_attempts;
                    """;
                exhaust.Parameters.AddWithValue("max_attempts", NpgsqlDbType.Smallint, MaximumAttempts);
                await exhaust.ExecuteNonQueryAsync(cancellationToken);
            }

            await using var command = connection.CreateCommand();
            command.Transaction = transaction;
            command.CommandText = """
                with candidate as (
                    select job.export_id
                      from public.account_export_jobs job
                     where job.expires_at > now()
                       and job.attempts < @max_attempts
                       and (
                           job.status = 'queued'
                           or (job.status = 'running' and job.lease_expires_at <= now())
                       )
                       and not exists (
                           select 1 from public.account_deletion_requests deletion
                            where deletion.account_id = job.account_id
                       )
                       and not exists (
                           select 1 from public.account_deletion_tombstones tombstone
                            where tombstone.account_id = job.account_id
                       )
                     order by job.created_at, job.export_id
                     for update of job skip locked
                     limit 1
                )
                update public.account_export_jobs job
                   set status = 'running',
                       attempts = job.attempts + 1,
                       lease_token = gen_random_uuid(),
                       lease_expires_at = now() + interval '60 seconds',
                       started_at = coalesce(job.started_at, now()),
                       last_error_code = null
                  from candidate
                 where job.export_id = candidate.export_id
                returning job.export_id, job.account_id, job.request_id, job.attempts, job.lease_token, job.lease_expires_at;
                """;
            command.Parameters.AddWithValue("max_attempts", NpgsqlDbType.Smallint, MaximumAttempts);
            await using var reader = await command.ExecuteReaderAsync(CommandBehavior.SingleRow, cancellationToken);
            AccountExportWorkItem? item = null;
            if (await reader.ReadAsync(cancellationToken))
            {
                item = new AccountExportWorkItem(
                    reader.GetGuid(0),
                    reader.GetGuid(1),
                    reader.GetString(2),
                    reader.GetInt16(3),
                    reader.GetGuid(4),
                    reader.GetFieldValue<DateTimeOffset>(5));
            }
            await reader.DisposeAsync();
            await transaction.CommitAsync(cancellationToken);
            return item;
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (NpgsqlException exception)
        {
            throw DatabaseFailure("ACCOUNT_EXPORT_CLAIM_FAILED", exception);
        }
    }

    public async Task<bool> RenewLeaseAsync(AccountExportWorkItem item, CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var command = connection.CreateCommand();
            command.CommandText = """
                update public.account_export_jobs
                   set lease_expires_at = now() + interval '60 seconds'
                 where export_id = @export_id and account_id = @account_id
                   and status = 'running' and attempts = @attempts and lease_token = @lease_token
                   and lease_expires_at > now() and expires_at > now();
                """;
            AddLeaseParameters(command, item);
            return await command.ExecuteNonQueryAsync(cancellationToken) == 1;
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (NpgsqlException exception)
        {
            throw DatabaseFailure("ACCOUNT_EXPORT_LEASE_RENEWAL_FAILED", exception);
        }
    }

    public async Task<bool> CompleteAsync(
        AccountExportWorkItem item,
        byte[] artifact,
        CancellationToken cancellationToken)
    {
        if (artifact.LongLength is < 1 or > MaximumArtifactBytes)
            throw new ArgumentOutOfRangeException(nameof(artifact), "Account export artifact exceeds its byte limit.");

        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(IsolationLevel.ReadCommitted, cancellationToken);
            await using (var capacityLock = connection.CreateCommand())
            {
                capacityLock.Transaction = transaction;
                capacityLock.CommandText = CapacityLockSql;
                await capacityLock.ExecuteNonQueryAsync(cancellationToken);
            }

            await using (var capacity = connection.CreateCommand())
            {
                capacity.Transaction = transaction;
                capacity.CommandText = """
                    select coalesce(sum(artifact_bytes), 0)::bigint
                    from public.account_export_jobs
                    where status = 'ready' and expires_at > now();
                    """;
                var retainedBytes = (long)(await capacity.ExecuteScalarAsync(cancellationToken) ?? 0L);
                if (retainedBytes + artifact.LongLength > MaximumRetainedArtifactBytes)
                {
                    await FailInTransactionAsync(
                        connection,
                        transaction,
                        item,
                        "EXPORT_STORAGE_CAPACITY_LIMITED",
                        retryable: false,
                        cancellationToken);
                    await transaction.CommitAsync(cancellationToken);
                    return false;
                }
            }

            await using var command = connection.CreateCommand();
            command.Transaction = transaction;
            command.CommandText = """
                update public.account_export_jobs
                   set status = 'ready',
                       payload = @payload,
                       artifact_bytes = @artifact_bytes,
                       completed_at = now(),
                       expires_at = now() + interval '24 hours',
                       lease_token = null,
                       lease_expires_at = null,
                       last_error_code = null
                 where export_id = @export_id and account_id = @account_id
                   and status = 'running' and attempts = @attempts and lease_token = @lease_token
                   and lease_expires_at > now() and expires_at > now();
                """;
            AddLeaseParameters(command, item);
            command.Parameters.AddWithValue("payload", NpgsqlDbType.Bytea, artifact);
            command.Parameters.AddWithValue("artifact_bytes", NpgsqlDbType.Bigint, artifact.LongLength);
            var completed = await command.ExecuteNonQueryAsync(cancellationToken) == 1;
            await transaction.CommitAsync(cancellationToken);
            return completed;
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (NpgsqlException exception)
        {
            throw DatabaseFailure("ACCOUNT_EXPORT_COMPLETION_FAILED", exception);
        }
    }

    public async Task<bool> FailAsync(
        AccountExportWorkItem item,
        string errorCode,
        bool retryable,
        CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            var updated = await FailInTransactionAsync(connection, transaction, item, errorCode, retryable, cancellationToken);
            await transaction.CommitAsync(cancellationToken);
            return updated;
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (NpgsqlException exception)
        {
            throw DatabaseFailure("ACCOUNT_EXPORT_FAILURE_RECORD_FAILED", exception);
        }
    }

    public async Task PurgeExpiredAsync(CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var command = connection.CreateCommand();
            command.CommandText = "delete from public.account_export_jobs where expires_at <= now();";
            await command.ExecuteNonQueryAsync(cancellationToken);
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (NpgsqlException exception)
        {
            throw DatabaseFailure("ACCOUNT_EXPORT_PURGE_FAILED", exception);
        }
    }

    private static async Task<bool> FailInTransactionAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        AccountExportWorkItem item,
        string errorCode,
        bool retryable,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            update public.account_export_jobs
               set status = case when @retryable and attempts < @max_attempts then 'queued' else 'failed' end,
                   payload = null,
                   artifact_bytes = null,
                   lease_token = null,
                   lease_expires_at = null,
                   completed_at = case when @retryable and attempts < @max_attempts then null else now() end,
                   last_error_code = @error_code
             where export_id = @export_id and account_id = @account_id
               and status = 'running' and attempts = @attempts and lease_token = @lease_token
               and lease_expires_at > now() and expires_at > now();
            """;
        AddLeaseParameters(command, item);
        command.Parameters.AddWithValue("retryable", NpgsqlDbType.Boolean, retryable);
        command.Parameters.AddWithValue("max_attempts", NpgsqlDbType.Smallint, MaximumAttempts);
        command.Parameters.AddWithValue("error_code", NpgsqlDbType.Text, errorCode);
        return await command.ExecuteNonQueryAsync(cancellationToken) == 1;
    }

    private static void AddLeaseParameters(NpgsqlCommand command, AccountExportWorkItem item)
    {
        command.Parameters.AddWithValue("export_id", NpgsqlDbType.Uuid, item.ExportId);
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, item.AccountId);
        command.Parameters.AddWithValue("attempts", NpgsqlDbType.Smallint, item.Attempts);
        command.Parameters.AddWithValue("lease_token", NpgsqlDbType.Uuid, item.LeaseToken);
    }

    private static WorkerDatabaseException DatabaseFailure(string code, NpgsqlException exception) =>
        new(code, exception);
}

internal sealed class NpgsqlAccountExportGlobalLock(NpgsqlConnection connection) : IAccountExportGlobalLock
{
    private bool isDisposed;

    public async Task<bool> EnsureHeldAsync(CancellationToken cancellationToken)
    {
        ObjectDisposedException.ThrowIf(isDisposed, this);
        try
        {
            await using var command = connection.CreateCommand();
            command.CommandText = "select pg_backend_pid();";
            await command.ExecuteScalarAsync(cancellationToken);
            return true;
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (NpgsqlException exception)
        {
            throw new WorkerDatabaseException("ACCOUNT_EXPORT_LOCK_HEARTBEAT_FAILED", exception);
        }
    }

    public async ValueTask DisposeAsync()
    {
        if (isDisposed) return;
        isDisposed = true;
        try
        {
            await using var command = connection.CreateCommand();
            command.CommandText = "select pg_advisory_unlock(hashtextextended('evidrilo.account_exports.worker', 0));";
            await command.ExecuteScalarAsync(CancellationToken.None);
        }
        catch (NpgsqlException)
        {
            // Closing the session releases the advisory lock if the server disconnected.
        }
        finally
        {
            await connection.DisposeAsync();
        }
    }
}
