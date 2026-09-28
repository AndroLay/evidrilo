using Evidrilo.Api.Common;
using Npgsql;
using NpgsqlTypes;

namespace Evidrilo.Api.Notifications;

public interface INotificationPreferencesStore
{
    Task<NotificationPreferencesSnapshot> GetOwnAsync(
        Guid accountId,
        CancellationToken cancellationToken);

    Task<NotificationPreferencesWriteResult> PutOwnAsync(
        Guid accountId,
        NotificationPreferencesUpdateRequest request,
        CancellationToken cancellationToken);
}

public sealed class DatabaseUnavailableNotificationPreferencesStore : INotificationPreferencesStore
{
    public Task<NotificationPreferencesSnapshot> GetOwnAsync(
        Guid accountId,
        CancellationToken cancellationToken) => throw NotConfigured();

    public Task<NotificationPreferencesWriteResult> PutOwnAsync(
        Guid accountId,
        NotificationPreferencesUpdateRequest request,
        CancellationToken cancellationToken) => throw NotConfigured();

    private static ApiException NotConfigured() => new(
        StatusCodes.Status503ServiceUnavailable,
        "DATABASE_NOT_CONFIGURED",
        "Notification preferences are not configured.");
}

public sealed class NpgsqlNotificationPreferencesStore : INotificationPreferencesStore, IDisposable
{
    private readonly NpgsqlDataSource dataSource;

    public NpgsqlNotificationPreferencesStore(string connectionString)
    {
        dataSource = NpgsqlDataSource.Create(connectionString);
    }

    public async Task<NotificationPreferencesSnapshot> GetOwnAsync(
        Guid accountId,
        CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);
            var snapshot = await ReadAsync(connection, transaction, accountId, lockRow: false, cancellationToken);
            await transaction.CommitAsync(cancellationToken);
            return snapshot ?? NotificationPreferencesSnapshot.Defaults();
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
                "Notification preferences are temporarily unavailable.",
                exception);
        }
    }

    public async Task<NotificationPreferencesWriteResult> PutOwnAsync(
        Guid accountId,
        NotificationPreferencesUpdateRequest request,
        CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);
            await LockAccountDeletionFenceAsync(connection, transaction, accountId, cancellationToken);
            await EnsureAccountNotDeletedAsync(connection, transaction, accountId, cancellationToken);

            var current = await ReadAsync(connection, transaction, accountId, lockRow: true, cancellationToken);
            if (current is null)
            {
                if (request.ExpectedRevision != 0)
                    throw Conflict();

                var inserted = await InsertAsync(connection, transaction, accountId, request, cancellationToken);
                if (inserted is not null)
                {
                    await transaction.CommitAsync(cancellationToken);
                    return new NotificationPreferencesWriteResult("accepted", inserted);
                }

                // Another writer inserted the row between the read and insert.
                // Re-read it under the same transaction and require an exact
                // state match; never silently overwrite a concurrent update.
                current = await ReadAsync(connection, transaction, accountId, lockRow: true, cancellationToken);
                if (current is null || !current.Matches(request))
                    throw Conflict();

                await transaction.CommitAsync(cancellationToken);
                return new NotificationPreferencesWriteResult("unchanged", current);
            }

            if (current.Matches(request))
            {
                await transaction.CommitAsync(cancellationToken);
                return new NotificationPreferencesWriteResult("unchanged", current);
            }

            if (current.Revision != request.ExpectedRevision)
                throw Conflict();

            var updated = await UpdateAsync(connection, transaction, accountId, request, current.Revision, cancellationToken);
            if (updated is null)
                throw Conflict();

            await transaction.CommitAsync(cancellationToken);
            return new NotificationPreferencesWriteResult("accepted", updated);
        }
        catch (ApiException)
        {
            throw;
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
                "Notification preferences are temporarily unavailable.",
                exception);
        }
    }

    public void Dispose() => dataSource.Dispose();

    private static async Task<NotificationPreferencesSnapshot?> ReadAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        bool lockRow,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = $"""
            select enabled, continue_unfinished_enabled, review_completed_enabled,
                   cadence, local_hour, local_minute, revision, updated_at
            from public.notification_preferences
            where account_id = @account_id
            {(lockRow ? "for update" : string.Empty)};
            """;
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        if (!await reader.ReadAsync(cancellationToken))
            return null;

        return new NotificationPreferencesSnapshot(
            reader.GetBoolean(0),
            reader.GetBoolean(1),
            reader.GetBoolean(2),
            reader.GetString(3),
            reader.GetInt16(4),
            reader.GetInt16(5),
            reader.GetInt64(6),
            reader.GetFieldValue<DateTimeOffset>(7));
    }

    private static async Task<NotificationPreferencesSnapshot?> InsertAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        NotificationPreferencesUpdateRequest request,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            insert into public.notification_preferences (
                account_id, enabled, continue_unfinished_enabled,
                review_completed_enabled, cadence, local_hour, local_minute,
                revision
            ) values (
                @account_id, @enabled, @continue_unfinished_enabled,
                @review_completed_enabled, @cadence, @local_hour, @local_minute,
                1
            )
            on conflict (account_id) do nothing
            returning enabled, continue_unfinished_enabled, review_completed_enabled,
                      cadence, local_hour, local_minute, revision, updated_at;
            """;
        AddRequestParameters(command, accountId, request);
        return await ReadSnapshotFromCommandAsync(command, cancellationToken);
    }

    private static async Task<NotificationPreferencesSnapshot?> UpdateAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        NotificationPreferencesUpdateRequest request,
        long expectedCurrentRevision,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            update public.notification_preferences
               set enabled = @enabled,
                   continue_unfinished_enabled = @continue_unfinished_enabled,
                   review_completed_enabled = @review_completed_enabled,
                   cadence = @cadence,
                   local_hour = @local_hour,
                   local_minute = @local_minute,
                   revision = revision + 1,
                   updated_at = now()
             where account_id = @account_id
               and revision = @expected_current_revision
            returning enabled, continue_unfinished_enabled, review_completed_enabled,
                      cadence, local_hour, local_minute, revision, updated_at;
            """;
        AddRequestParameters(command, accountId, request);
        command.Parameters.AddWithValue(
            "expected_current_revision",
            NpgsqlDbType.Bigint,
            expectedCurrentRevision);
        return await ReadSnapshotFromCommandAsync(command, cancellationToken);
    }

    private static async Task<NotificationPreferencesSnapshot?> ReadSnapshotFromCommandAsync(
        NpgsqlCommand command,
        CancellationToken cancellationToken)
    {
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        if (!await reader.ReadAsync(cancellationToken))
            return null;

        return new NotificationPreferencesSnapshot(
            reader.GetBoolean(0),
            reader.GetBoolean(1),
            reader.GetBoolean(2),
            reader.GetString(3),
            reader.GetInt16(4),
            reader.GetInt16(5),
            reader.GetInt64(6),
            reader.GetFieldValue<DateTimeOffset>(7));
    }

    private static void AddRequestParameters(
        NpgsqlCommand command,
        Guid accountId,
        NotificationPreferencesUpdateRequest request)
    {
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        command.Parameters.AddWithValue("enabled", NpgsqlDbType.Boolean, request.Enabled);
        command.Parameters.AddWithValue(
            "continue_unfinished_enabled",
            NpgsqlDbType.Boolean,
            request.ContinueUnfinishedEnabled);
        command.Parameters.AddWithValue(
            "review_completed_enabled",
            NpgsqlDbType.Boolean,
            request.ReviewCompletedEnabled);
        command.Parameters.AddWithValue("cadence", NpgsqlDbType.Text, request.Cadence);
        command.Parameters.AddWithValue("local_hour", NpgsqlDbType.Smallint, (short)request.LocalHour);
        command.Parameters.AddWithValue("local_minute", NpgsqlDbType.Smallint, (short)request.LocalMinute);
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

    private static async Task EnsureAccountNotDeletedAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            select exists (
                select 1 from public.account_deletion_tombstones where account_id = @account_id
                union all
                select 1 from public.account_deletion_requests
                 where account_id = @account_id and status = 'completed'
            );
            """;
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        if (await command.ExecuteScalarAsync(cancellationToken) is true)
        {
            throw new ApiException(
                StatusCodes.Status410Gone,
                "ACCOUNT_DELETED",
                "This account has been deleted.");
        }
    }

    private static ApiException Conflict() => new(
        StatusCodes.Status409Conflict,
        "NOTIFICATION_PREFERENCES_CONFLICT",
        "Notification preferences changed elsewhere. Refresh and try again.");
}
