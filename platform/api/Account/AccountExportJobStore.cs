using System.Data;
using Evidrilo.Api.Common;
using Npgsql;
using NpgsqlTypes;

namespace Evidrilo.Api.Account;

public sealed record AccountExportJob(
    Guid ExportId,
    string Status,
    string RequestId,
    DateTimeOffset CreatedAt,
    DateTimeOffset? CompletedAt,
    DateTimeOffset ExpiresAt,
    long? ArtifactBytes,
    string? ErrorCode);

public interface IAccountExportArtifactLease : IAsyncDisposable
{
    long Length { get; }
    Task CopyToAsync(Stream destination, CancellationToken cancellationToken);
}

public interface IAccountExportJobStore
{
    Task<AccountExportJob> CreateOwnAsync(
        Guid accountId,
        string idempotencyKeyHash,
        string requestId,
        CancellationToken cancellationToken);

    Task<AccountExportJob?> ReadOwnAsync(Guid accountId, Guid exportId, CancellationToken cancellationToken);

    Task<IAccountExportArtifactLease?> OpenReadyArtifactOwnAsync(
        Guid accountId,
        Guid exportId,
        CancellationToken cancellationToken);

    Task<AccountExportJob?> CancelOwnAsync(Guid accountId, Guid exportId, CancellationToken cancellationToken);
}

public sealed class AccountExportCapacityException() : Exception("Account export capacity is full.")
{
}

public sealed class DatabaseUnavailableAccountExportJobStore : IAccountExportJobStore
{
    private static ApiException NotConfigured() => new(
        StatusCodes.Status503ServiceUnavailable,
        "DATABASE_NOT_CONFIGURED",
        "Account export is not configured.");

    public Task<AccountExportJob> CreateOwnAsync(
        Guid accountId,
        string idempotencyKeyHash,
        string requestId,
        CancellationToken cancellationToken) => throw NotConfigured();

    public Task<AccountExportJob?> ReadOwnAsync(Guid accountId, Guid exportId, CancellationToken cancellationToken) =>
        throw NotConfigured();

    public Task<IAccountExportArtifactLease?> OpenReadyArtifactOwnAsync(
        Guid accountId,
        Guid exportId,
        CancellationToken cancellationToken) => throw NotConfigured();

    public Task<AccountExportJob?> CancelOwnAsync(Guid accountId, Guid exportId, CancellationToken cancellationToken) =>
        throw NotConfigured();
}

public sealed class NpgsqlAccountExportJobStore : IAccountExportJobStore, IDisposable
{
    private const long MaximumQueuedJobs = 100;
    private const string CapacityLockSql = "select pg_advisory_xact_lock(hashtextextended('evidrilo.account_exports.capacity', 0));";
    private readonly NpgsqlDataSource dataSource;

    public NpgsqlAccountExportJobStore(string connectionString)
    {
        dataSource = NpgsqlDataSource.Create(connectionString);
    }

    public async Task<AccountExportJob> CreateOwnAsync(
        Guid accountId,
        string idempotencyKeyHash,
        string requestId,
        CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(IsolationLevel.ReadCommitted, cancellationToken);
            await LockAccountDeletionFenceAsync(connection, transaction, accountId, cancellationToken);
            await EnsureAccountIsActiveAsync(connection, transaction, accountId, cancellationToken);
            await using (var capacityLock = connection.CreateCommand())
            {
                capacityLock.Transaction = transaction;
                capacityLock.CommandText = CapacityLockSql;
                await capacityLock.ExecuteNonQueryAsync(cancellationToken);
            }

            await using (var purge = connection.CreateCommand())
            {
                purge.Transaction = transaction;
                purge.CommandText = "delete from public.account_export_jobs where expires_at <= now();";
                await purge.ExecuteNonQueryAsync(cancellationToken);
            }

            await using (var replay = connection.CreateCommand())
            {
                replay.Transaction = transaction;
                replay.CommandText = """
                    select export_id, status, request_id, created_at, completed_at, expires_at, artifact_bytes, last_error_code
                    from public.account_export_jobs
                    where account_id = @account_id and idempotency_key_hash = @key_hash and expires_at > now();
                    """;
                replay.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                replay.Parameters.AddWithValue("key_hash", NpgsqlDbType.Text, idempotencyKeyHash);
                await using var reader = await replay.ExecuteReaderAsync(CommandBehavior.SingleRow, cancellationToken);
                if (await reader.ReadAsync(cancellationToken))
                {
                    var existing = ReadJob(reader);
                    await reader.DisposeAsync();
                    await transaction.CommitAsync(cancellationToken);
                    return existing;
                }
            }

            await using (var active = connection.CreateCommand())
            {
                active.Transaction = transaction;
                active.CommandText = """
                    select exists (
                        select 1 from public.account_export_jobs
                        where account_id = @account_id and status in ('queued', 'running', 'ready')
                    );
                    """;
                active.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                if ((bool)(await active.ExecuteScalarAsync(cancellationToken) ?? false))
                    throw new AccountExportCapacityException();
            }

            await using (var queueCount = connection.CreateCommand())
            {
                queueCount.Transaction = transaction;
                queueCount.CommandText = """
                    select count(*) from public.account_export_jobs
                    where status in ('queued', 'running') and expires_at > now();
                    """;
                var queued = (long)(await queueCount.ExecuteScalarAsync(cancellationToken) ?? 0L);
                if (queued >= MaximumQueuedJobs)
                    throw new AccountExportCapacityException();
            }

            await using var insert = connection.CreateCommand();
            insert.Transaction = transaction;
            insert.CommandText = """
                insert into public.account_export_jobs (account_id, idempotency_key_hash, request_id)
                values (@account_id, @key_hash, @request_id)
                returning export_id, status, request_id, created_at, completed_at, expires_at, artifact_bytes, last_error_code;
                """;
            insert.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
            insert.Parameters.AddWithValue("key_hash", NpgsqlDbType.Text, idempotencyKeyHash);
            insert.Parameters.AddWithValue("request_id", NpgsqlDbType.Text, requestId);
            await using var insertedReader = await insert.ExecuteReaderAsync(CommandBehavior.SingleRow, cancellationToken);
            if (!await insertedReader.ReadAsync(cancellationToken))
                throw new InvalidOperationException("The export job insert returned no row.");
            var created = ReadJob(insertedReader);
            await insertedReader.DisposeAsync();
            await transaction.CommitAsync(cancellationToken);
            return created;
        }
        catch (AccountExportCapacityException)
        {
            throw;
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (NpgsqlException exception)
        {
            throw MapDatabaseException(exception);
        }
    }

    public async Task<AccountExportJob?> ReadOwnAsync(
        Guid accountId,
        Guid exportId,
        CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var command = connection.CreateCommand();
            command.CommandText = """
                select export_id, status, request_id, created_at, completed_at, expires_at, artifact_bytes, last_error_code
                from public.account_export_jobs
                where account_id = @account_id and export_id = @export_id and expires_at > now();
                """;
            AddOwnerParameters(command, accountId, exportId);
            await using var reader = await command.ExecuteReaderAsync(CommandBehavior.SingleRow, cancellationToken);
            return await reader.ReadAsync(cancellationToken) ? ReadJob(reader) : null;
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (NpgsqlException exception)
        {
            throw MapDatabaseException(exception);
        }
    }

    public async Task<IAccountExportArtifactLease?> OpenReadyArtifactOwnAsync(
        Guid accountId,
        Guid exportId,
        CancellationToken cancellationToken)
    {
        NpgsqlConnection? connection = null;
        NpgsqlCommand? command = null;
        NpgsqlDataReader? reader = null;
        try
        {
            connection = await dataSource.OpenConnectionAsync(cancellationToken);
            command = connection.CreateCommand();
            command.CommandText = """
                select artifact_bytes, payload
                from public.account_export_jobs
                where account_id = @account_id and export_id = @export_id
                  and status = 'ready' and expires_at > now();
                """;
            AddOwnerParameters(command, accountId, exportId);
            reader = await command.ExecuteReaderAsync(CommandBehavior.SequentialAccess | CommandBehavior.SingleRow, cancellationToken);
            if (!await reader.ReadAsync(cancellationToken))
            {
                await reader.DisposeAsync();
                await command.DisposeAsync();
                await connection.DisposeAsync();
                return null;
            }

            var length = reader.GetInt64(0);
            var payload = reader.GetStream(1);
            var lease = new NpgsqlAccountExportArtifactLease(connection, command, reader, payload, length);
            connection = null;
            command = null;
            reader = null;
            return lease;
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (NpgsqlException exception)
        {
            throw MapDatabaseException(exception);
        }
        finally
        {
            if (reader is not null) await reader.DisposeAsync();
            if (command is not null) await command.DisposeAsync();
            if (connection is not null) await connection.DisposeAsync();
        }
    }

    public async Task<AccountExportJob?> CancelOwnAsync(
        Guid accountId,
        Guid exportId,
        CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var command = connection.CreateCommand();
            command.CommandText = """
                update public.account_export_jobs
                   set status = 'cancelled',
                       payload = null,
                       artifact_bytes = null,
                       last_error_code = null,
                       lease_token = null,
                       lease_expires_at = null,
                       completed_at = coalesce(completed_at, now())
                 where account_id = @account_id and export_id = @export_id and expires_at > now()
                returning export_id, status, request_id, created_at, completed_at, expires_at, artifact_bytes, last_error_code;
                """;
            AddOwnerParameters(command, accountId, exportId);
            await using var reader = await command.ExecuteReaderAsync(CommandBehavior.SingleRow, cancellationToken);
            return await reader.ReadAsync(cancellationToken) ? ReadJob(reader) : null;
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (NpgsqlException exception)
        {
            throw MapDatabaseException(exception);
        }
    }

    public void Dispose() => dataSource.Dispose();

    private static void AddOwnerParameters(NpgsqlCommand command, Guid accountId, Guid exportId)
    {
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        command.Parameters.AddWithValue("export_id", NpgsqlDbType.Uuid, exportId);
    }

    private static async Task LockAccountDeletionFenceAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = "select pg_advisory_xact_lock(hashtextextended(@account_id::text, 0));";
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        await command.ExecuteNonQueryAsync(cancellationToken);
    }

    private static async Task EnsureAccountIsActiveAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            select exists (
                select 1 from public.account_deletion_requests
                where account_id = @account_id
            ) or exists (
                select 1 from public.account_deletion_tombstones
                where account_id = @account_id
            );
            """;
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        if ((bool)(await command.ExecuteScalarAsync(cancellationToken) ?? false))
            throw new ApiException(
                StatusCodes.Status410Gone,
                "ACCOUNT_DELETED",
                "This account has been deleted.");
    }

    private static AccountExportJob ReadJob(NpgsqlDataReader reader) => new(
        reader.GetGuid(0),
        reader.GetString(1),
        reader.GetString(2),
        reader.GetFieldValue<DateTimeOffset>(3),
        reader.IsDBNull(4) ? null : reader.GetFieldValue<DateTimeOffset>(4),
        reader.GetFieldValue<DateTimeOffset>(5),
        reader.IsDBNull(6) ? null : reader.GetInt64(6),
        reader.IsDBNull(7) ? null : reader.GetString(7));

    private static ApiException MapDatabaseException(NpgsqlException exception)
    {
        var code = exception is PostgresException postgresException
            && postgresException.SqlState is PostgresErrorCodes.UndefinedTable or PostgresErrorCodes.UndefinedColumn
            ? "DATABASE_SCHEMA_MISSING"
            : "DATABASE_UNAVAILABLE";
        var message = code == "DATABASE_SCHEMA_MISSING"
            ? "The account export schema is not ready."
            : "Account export is temporarily unavailable.";
        return new ApiException(StatusCodes.Status503ServiceUnavailable, code, message, exception);
    }
}

internal sealed class NpgsqlAccountExportArtifactLease(
    NpgsqlConnection connection,
    NpgsqlCommand command,
    NpgsqlDataReader reader,
    Stream payload,
    long length) : IAccountExportArtifactLease
{
    private bool isDisposed;
    public long Length { get; } = length;

    public async Task CopyToAsync(Stream destination, CancellationToken cancellationToken)
    {
        ObjectDisposedException.ThrowIf(isDisposed, this);
        await payload.CopyToAsync(destination, 81920, cancellationToken);
    }

    public async ValueTask DisposeAsync()
    {
        if (isDisposed) return;
        isDisposed = true;
        await payload.DisposeAsync();
        await reader.DisposeAsync();
        await command.DisposeAsync();
        await connection.DisposeAsync();
    }
}
