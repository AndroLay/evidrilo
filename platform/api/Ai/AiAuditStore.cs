using Evidrilo.Api.Common;
using Npgsql;
using NpgsqlTypes;

namespace Evidrilo.Api.Ai;

public sealed class DatabaseUnavailableAiAuditStore : IAiAuditStore
{
    public Task RecordAsync(
        Guid accountId,
        string requestId,
        AiAuditMetadata metadata,
        string? reasonCode,
        CancellationToken cancellationToken) => throw new ApiException(
        StatusCodes.Status503ServiceUnavailable,
        "DATABASE_NOT_CONFIGURED",
        "AI assistance is not configured.");
}

public sealed class NpgsqlAiAuditStore : IAiAuditStore, IDisposable
{
    private readonly NpgsqlDataSource dataSource;

    public NpgsqlAiAuditStore(string connectionString)
    {
        dataSource = NpgsqlDataSource.Create(connectionString);
    }

    public async Task RecordAsync(
        Guid accountId,
        string requestId,
        AiAuditMetadata metadata,
        string? reasonCode,
        CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var command = connection.CreateCommand();
            command.CommandText = """
                insert into public.ai_audit_events (
                    account_id, request_id, prompt_version, input_hash,
                    provider, outcome, reason_code
                ) values (
                    @account_id, @request_id, @prompt_version, @input_hash,
                    @provider, @outcome, @reason_code
                ) on conflict (account_id, request_id) do nothing;
                """;
            command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
            command.Parameters.AddWithValue("request_id", NpgsqlDbType.Text, requestId);
            command.Parameters.AddWithValue("prompt_version", NpgsqlDbType.Text, metadata.PromptVersion);
            command.Parameters.AddWithValue("input_hash", NpgsqlDbType.Text, metadata.InputHash);
            command.Parameters.AddWithValue("provider", NpgsqlDbType.Text, (object?)metadata.Provider ?? DBNull.Value);
            command.Parameters.AddWithValue("outcome", NpgsqlDbType.Text, metadata.Outcome);
            command.Parameters.AddWithValue("reason_code", NpgsqlDbType.Text, (object?)reasonCode ?? DBNull.Value);
            await command.ExecuteNonQueryAsync(cancellationToken);
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
                "AI assistance audit is temporarily unavailable.",
                exception);
        }
    }

    public void Dispose() => dataSource.Dispose();
}
