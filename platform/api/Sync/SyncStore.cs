using Evidrilo.Api.Common;
using Evidrilo.Api.Health;
using Npgsql;
using NpgsqlTypes;

namespace Evidrilo.Api.Sync;

public sealed record SyncPushStorageResult(
    IReadOnlyList<SyncCommandResult> Results,
    long NextCursor);

public sealed record SyncPullStorageResult(
    long Cursor,
    long NextCursor,
    bool HasMore,
    IReadOnlyList<SyncChange> Changes);

public interface ISyncStore
{
    Task<string> CheckReadinessAsync(CancellationToken cancellationToken);

    Task<SyncPushStorageResult> PushAsync(
        Guid accountId,
        IReadOnlyList<SyncCommand> commands,
        CancellationToken cancellationToken);

    Task<SyncPullStorageResult> PullAsync(
        Guid accountId,
        long cursor,
        int limit,
        CancellationToken cancellationToken);
}

public sealed class DatabaseUnavailableSyncStore : ISyncStore
{
    public Task<string> CheckReadinessAsync(CancellationToken cancellationToken) =>
        Task.FromResult("missing");

    public Task<SyncPushStorageResult> PushAsync(
        Guid accountId,
        IReadOnlyList<SyncCommand> commands,
        CancellationToken cancellationToken) => throw NotConfigured();

    public Task<SyncPullStorageResult> PullAsync(
        Guid accountId,
        long cursor,
        int limit,
        CancellationToken cancellationToken) => throw NotConfigured();

    private static ApiException NotConfigured() => new(
        StatusCodes.Status503ServiceUnavailable,
        "DATABASE_NOT_CONFIGURED",
        "Cloud sync is not configured.");
}

public sealed class NpgsqlSyncStore : ISyncStore, IDisposable
{
    private readonly NpgsqlDataSource dataSource;

    public NpgsqlSyncStore(string connectionString)
    {
        dataSource = NpgsqlDataSource.Create(connectionString);
    }

    public async Task<string> CheckReadinessAsync(CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var ledgerCommand = connection.CreateCommand();
            ledgerCommand.CommandText = "select to_regclass('public.evidrilo_schema_migrations')::text;";
            var ledger = await ledgerCommand.ExecuteScalarAsync(cancellationToken);
            var ledgerExists = ledger is not null and not DBNull;
            if (!ledgerExists)
            {
                return DatabaseSchemaReadiness.Status(
                    connectionSucceeded: true,
                    migrationLedgerExists: false,
                    currentMigrationApplied: false);
            }

            await using var versionCommand = connection.CreateCommand();
            versionCommand.CommandText = """
                select exists (
                    select 1
                    from public.evidrilo_schema_migrations
                    where version = @current_version
                );
                """;
            versionCommand.Parameters.AddWithValue(
                "current_version",
                NpgsqlDbType.Text,
                DatabaseSchemaReadiness.CurrentMigrationVersion);
            var currentMigrationApplied = await versionCommand.ExecuteScalarAsync(cancellationToken) is bool applied
                && applied;
            return DatabaseSchemaReadiness.Status(
                connectionSucceeded: true,
                migrationLedgerExists: true,
                currentMigrationApplied);
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (NpgsqlException)
        {
            return "degraded";
        }
    }

    public async Task<SyncPushStorageResult> PushAsync(
        Guid accountId,
        IReadOnlyList<SyncCommand> commands,
        CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);

            var results = new List<SyncCommandResult>(commands.Count);
            foreach (var command in commands)
            {
                results.Add(await PushCommandAsync(
                    connection,
                    transaction,
                    accountId,
                    command,
                    cancellationToken));
            }

            var nextCursor = await ReadAccountCursorAsync(connection, transaction, accountId, cancellationToken);
            await transaction.CommitAsync(cancellationToken);
            return new SyncPushStorageResult(results, nextCursor);
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (NpgsqlException exception)
        {
            throw new ApiException(
                StatusCodes.Status503ServiceUnavailable,
                "DATABASE_UNAVAILABLE",
                "The sync service is temporarily unavailable.",
                exception);
        }
    }

    public async Task<SyncPullStorageResult> PullAsync(
        Guid accountId,
        long cursor,
        int limit,
        CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);

            await using var command = connection.CreateCommand();
            command.Transaction = transaction;
            command.CommandText = """
                select server_sequence, attempt_id, case_version_id,
                       command_type, revision_number, snapshot_digest
                from public.sync_changes
                where account_id = @account_id and server_sequence > @cursor
                order by server_sequence
                limit @limit;
                """;
            command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
            command.Parameters.AddWithValue("cursor", NpgsqlDbType.Bigint, cursor);
            command.Parameters.AddWithValue("limit", NpgsqlDbType.Integer, limit + 1);

            var changes = new List<SyncChange>(limit);
            await using (var reader = await command.ExecuteReaderAsync(cancellationToken))
            {
                while (await reader.ReadAsync(cancellationToken))
                {
                    changes.Add(new SyncChange(
                        reader.GetInt64(0),
                        reader.GetGuid(1),
                        reader.GetString(2),
                        SyncCommandTypeExtensions.FromWire(reader.GetString(3)),
                        reader.GetInt32(4),
                        reader.GetString(5)));
                }
            }

            var hasMore = changes.Count > limit;
            if (hasMore) changes.RemoveAt(changes.Count - 1);
            var nextCursor = changes.Count == 0 ? cursor : changes[^1].ServerSequence;
            await transaction.CommitAsync(cancellationToken);
            return new SyncPullStorageResult(cursor, nextCursor, hasMore, changes);
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (NpgsqlException exception)
        {
            throw new ApiException(
                StatusCodes.Status503ServiceUnavailable,
                "DATABASE_UNAVAILABLE",
                "The sync service is temporarily unavailable.",
                exception);
        }
    }

    public void Dispose()
    {
        dataSource.Dispose();
    }

    private static async Task<SyncCommandResult> PushCommandAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        SyncCommand command,
        CancellationToken cancellationToken)
    {
        var existing = await ReadExistingCommandAsync(
            connection,
            transaction,
            accountId,
            command.CommandId,
            cancellationToken);
        if (existing is not null)
        {
            return await ExistingCommandResultAsync(
                connection,
                transaction,
                accountId,
                command,
                existing,
                cancellationToken);
        }

        var caseVersionStatus = await ReadCaseVersionStatusAsync(
            connection,
            transaction,
            command.CaseVersionId,
            cancellationToken);
        var rejectionReason = SyncCaseVersionPolicy.RejectionReason(caseVersionStatus);
        if (rejectionReason is not null)
        {
            return new SyncCommandResult(command.CommandId, "rejected", null, rejectionReason);
        }

        await using var insert = connection.CreateCommand();
        insert.Transaction = transaction;
        insert.CommandText = """
            insert into public.attempt_commands (
                account_id, command_id, attempt_id, case_version_id,
                command_type, revision_number, client_occurred_at, snapshot_digest
            ) values (
                @account_id, @command_id, @attempt_id, @case_version_id,
                @command_type, @revision_number, @client_occurred_at, @snapshot_digest
            )
            on conflict do nothing
            returning command_id;
            """;
        AddCommandParameters(insert, accountId, command);
        var inserted = await insert.ExecuteScalarAsync(cancellationToken);
        if (inserted is Guid)
        {
            var sequence = await ReadCommandSequenceAsync(
                connection,
                transaction,
                accountId,
                command.CommandId,
                cancellationToken);
            return new SyncCommandResult(command.CommandId, "accepted", sequence);
        }

        var racedExisting = await ReadExistingCommandAsync(
            connection,
            transaction,
            accountId,
            command.CommandId,
            cancellationToken);
        if (racedExisting is not null)
        {
            return await ExistingCommandResultAsync(
                connection,
                transaction,
                accountId,
                command,
                racedExisting,
                cancellationToken);
        }

        return new SyncCommandResult(command.CommandId, "conflict", null, "ATTEMPT_REVISION_CONFLICT");
    }

    private static async Task<SyncCommandResult> ExistingCommandResultAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        SyncCommand command,
        StoredCommand existing,
        CancellationToken cancellationToken)
    {
        var sequence = await ReadCommandSequenceAsync(
            connection,
            transaction,
            accountId,
            command.CommandId,
            cancellationToken);
        return existing.Matches(command)
            ? new SyncCommandResult(command.CommandId, "duplicate", sequence, "IDEMPOTENT_REPLAY")
            : new SyncCommandResult(command.CommandId, "rejected", null, "IDEMPOTENCY_KEY_REUSE");
    }

    private static void AddCommandParameters(NpgsqlCommand command, Guid accountId, SyncCommand syncCommand)
    {
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        command.Parameters.AddWithValue("command_id", NpgsqlDbType.Uuid, syncCommand.CommandId);
        command.Parameters.AddWithValue("attempt_id", NpgsqlDbType.Uuid, syncCommand.AttemptId);
        command.Parameters.AddWithValue("case_version_id", NpgsqlDbType.Text, syncCommand.CaseVersionId);
        command.Parameters.AddWithValue(
            "command_type",
            NpgsqlDbType.Text,
            syncCommand.CommandType.ToWire());
        command.Parameters.AddWithValue("revision_number", NpgsqlDbType.Integer, syncCommand.RevisionNumber);
        command.Parameters.AddWithValue("client_occurred_at", NpgsqlDbType.TimestampTz, syncCommand.ClientOccurredAt);
        command.Parameters.AddWithValue("snapshot_digest", NpgsqlDbType.Text, syncCommand.SnapshotDigest);
    }

    private static async Task<string?> ReadCaseVersionStatusAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        string caseVersionId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            select status
            from public.case_versions
            where case_version_id = @case_version_id
            for share;
            """;
        command.Parameters.AddWithValue("case_version_id", NpgsqlDbType.Text, caseVersionId);
        var value = await command.ExecuteScalarAsync(cancellationToken);
        return value is null or DBNull ? null : (string)value;
    }

    private static async Task SetRequestAccountAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = "select set_config('request.jwt.claim.sub', @account_id, true);";
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Text, accountId.ToString());
        await command.ExecuteNonQueryAsync(cancellationToken);
    }

    private static async Task<long> ReadAccountCursorAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            select coalesce(max(server_sequence), 0)
            from public.sync_changes
            where account_id = @account_id;
            """;
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        return Convert.ToInt64(await command.ExecuteScalarAsync(cancellationToken));
    }

    private static async Task<long> ReadCommandSequenceAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        Guid commandId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            select server_sequence
            from public.sync_changes
            where account_id = @account_id and command_id = @command_id;
            """;
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        command.Parameters.AddWithValue("command_id", NpgsqlDbType.Uuid, commandId);
        var value = await command.ExecuteScalarAsync(cancellationToken);
        if (value is null or DBNull)
        {
            throw new ApiException(
                StatusCodes.Status503ServiceUnavailable,
                "DATABASE_SCHEMA_MISSING",
                "The sync service is not ready.");
        }

        return Convert.ToInt64(value);
    }

    private static async Task<StoredCommand?> ReadExistingCommandAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        Guid commandId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            select attempt_id, case_version_id, command_type,
                   revision_number, client_occurred_at, snapshot_digest
            from public.attempt_commands
            where account_id = @account_id and command_id = @command_id;
            """;
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        command.Parameters.AddWithValue("command_id", NpgsqlDbType.Uuid, commandId);
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        if (!await reader.ReadAsync(cancellationToken)) return null;

        return new StoredCommand(
            reader.GetGuid(0),
            reader.GetString(1),
            SyncCommandTypeExtensions.FromWire(reader.GetString(2)),
            reader.GetInt32(3),
            reader.GetFieldValue<DateTimeOffset>(4),
            reader.GetString(5));
    }

    private sealed record StoredCommand(
        Guid AttemptId,
        string CaseVersionId,
        SyncCommandType CommandType,
        int RevisionNumber,
        DateTimeOffset ClientOccurredAt,
        string SnapshotDigest)
    {
        public bool Matches(SyncCommand command) =>
            AttemptId == command.AttemptId
            && CaseVersionId == command.CaseVersionId
            && CommandType == command.CommandType
            && RevisionNumber == command.RevisionNumber
            && ClientOccurredAt == command.ClientOccurredAt
            && SnapshotDigest == command.SnapshotDigest;
    }
}

public static class SyncCommandTypeExtensions
{
    public static string ToWire(this SyncCommandType commandType) => commandType switch
    {
        SyncCommandType.AttemptStarted => "attempt_started",
        SyncCommandType.AttemptSubmitted => "attempt_submitted",
        SyncCommandType.RevisionRecorded => "revision_recorded",
        _ => throw new ArgumentOutOfRangeException(nameof(commandType)),
    };

    public static SyncCommandType FromWire(string value) => value switch
    {
        "attempt_started" => SyncCommandType.AttemptStarted,
        "attempt_submitted" => SyncCommandType.AttemptSubmitted,
        "revision_recorded" => SyncCommandType.RevisionRecorded,
        _ => throw new InvalidOperationException("Stored sync command type is invalid."),
    };
}
