using System.Text.Json.Serialization;
using Evidrilo.Api.Common;
using Npgsql;
using NpgsqlTypes;

namespace Evidrilo.Api.Billing;

public sealed record EntitlementRecord(
    [property: JsonPropertyName("entitlement")] string Entitlement,
    [property: JsonPropertyName("status")] string Status,
    [property: JsonPropertyName("updatedAt")] DateTimeOffset UpdatedAt);

public interface IEntitlementStore
{
    Task<IReadOnlyList<EntitlementRecord>> GetOwnAsync(
        Guid accountId,
        CancellationToken cancellationToken);
}

public sealed class DatabaseUnavailableEntitlementStore : IEntitlementStore
{
    public Task<IReadOnlyList<EntitlementRecord>> GetOwnAsync(
        Guid accountId,
        CancellationToken cancellationToken) => throw new ApiException(
        StatusCodes.Status503ServiceUnavailable,
        "DATABASE_NOT_CONFIGURED",
        "Entitlements are not configured.");
}

public sealed class NpgsqlEntitlementStore : IEntitlementStore, IDisposable
{
    private readonly NpgsqlDataSource dataSource;

    public NpgsqlEntitlementStore(string connectionString)
    {
        dataSource = NpgsqlDataSource.Create(connectionString);
    }

    public async Task<IReadOnlyList<EntitlementRecord>> GetOwnAsync(
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
                select entitlement, status, updated_at
                from public.entitlements
                where account_id = @account_id
                order by entitlement;
                """;
            command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
            var records = new List<EntitlementRecord>();
            await using (var reader = await command.ExecuteReaderAsync(cancellationToken))
            {
                while (await reader.ReadAsync(cancellationToken))
                {
                    records.Add(new EntitlementRecord(
                        reader.GetString(0),
                        reader.GetString(1),
                        reader.GetFieldValue<DateTimeOffset>(2)));
                }
            }

            await transaction.CommitAsync(cancellationToken);
            return records;
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
                "Entitlements are temporarily unavailable.",
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
