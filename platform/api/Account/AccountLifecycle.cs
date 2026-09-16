using Evidrilo.Api.Common;
using Npgsql;
using NpgsqlTypes;

namespace Evidrilo.Api.Account;

public sealed record AccountDeletionOperation(string Outcome);

public static class AccountDeletionStatus
{
    public static bool IsDeleted(
        bool deletionRequestCompleted,
        bool profileTombstoned) => deletionRequestCompleted || profileTombstoned;
}

public interface IAccountLifecycleStore
{
    Task<bool> IsDeletedAsync(
        Guid accountId,
        CancellationToken cancellationToken);

    Task<AccountDeletionOperation> DeleteOwnAsync(
        Guid accountId,
        CancellationToken cancellationToken);
}

public sealed class DatabaseUnavailableAccountLifecycleStore : IAccountLifecycleStore
{
    public Task<bool> IsDeletedAsync(
        Guid accountId,
        CancellationToken cancellationToken) => throw NotConfigured();

    public Task<AccountDeletionOperation> DeleteOwnAsync(
        Guid accountId,
        CancellationToken cancellationToken) => throw NotConfigured();

    private static ApiException NotConfigured() => new(
        StatusCodes.Status503ServiceUnavailable,
        "DATABASE_NOT_CONFIGURED",
        "Account lifecycle is not configured.");
}

public sealed class NpgsqlAccountLifecycleStore : IAccountLifecycleStore, IDisposable
{
    private readonly NpgsqlDataSource dataSource;

    public NpgsqlAccountLifecycleStore(string connectionString)
    {
        dataSource = NpgsqlDataSource.Create(connectionString);
    }

    public async Task<AccountDeletionOperation> DeleteOwnAsync(
        Guid accountId,
        CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);

            await using var command = connection.CreateCommand();
            command.Transaction = transaction;
            command.CommandText = "select public.prepare_account_deletion(@account_id);";
            command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
            var outcome = await command.ExecuteScalarAsync(cancellationToken) as string;
            if (outcome is not ("accepted" or "already_completed"))
                throw new ApiException(
                    StatusCodes.Status503ServiceUnavailable,
                    "DATABASE_SCHEMA_MISSING",
                    "The account lifecycle schema is not ready.");

            await transaction.CommitAsync(cancellationToken);
            return new AccountDeletionOperation(outcome);
        }
        catch (ApiException)
        {
            throw;
        }
        catch (PostgresException exception) when (
            exception.SqlState == "P0001"
            && exception.MessageText == "owner_transfer_required")
        {
            throw new ApiException(
                StatusCodes.Status409Conflict,
                "OWNER_TRANSFER_REQUIRED",
                "Transfer ownership before deleting this account.",
                exception);
        }
        catch (PostgresException exception) when (exception.SqlState == "42501")
        {
            throw new ApiException(
                StatusCodes.Status403Forbidden,
                "ACCOUNT_SCOPE_REQUIRED",
                "The account deletion request is not scoped to the authenticated account.",
                exception);
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
                "Account deletion is temporarily unavailable.",
                exception);
        }
    }

    public async Task<bool> IsDeletedAsync(
        Guid accountId,
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
                select
                    exists (
                        select 1
                        from public.account_deletion_requests
                        where account_id = @account_id
                          and status = 'completed'
                    ),
                    exists (
                        select 1
                        from public.account_profiles
                        where account_id = @account_id
                          and deleted_at is not null
                    );
                """;
            command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
            bool deletionRequestCompleted;
            bool profileTombstoned;
            await using (var reader = await command.ExecuteReaderAsync(cancellationToken))
            {
                if (!await reader.ReadAsync(cancellationToken))
                {
                    await transaction.CommitAsync(cancellationToken);
                    return false;
                }

                deletionRequestCompleted = reader.GetBoolean(0);
                profileTombstoned = reader.GetBoolean(1);
            }
            await transaction.CommitAsync(cancellationToken);
            return AccountDeletionStatus.IsDeleted(deletionRequestCompleted, profileTombstoned);
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
                "Account lifecycle status is temporarily unavailable.",
                exception);
        }
    }

    public void Dispose() => dataSource.Dispose();

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
}
