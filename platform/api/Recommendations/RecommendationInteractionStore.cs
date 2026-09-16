using Evidrilo.Api.Common;
using Npgsql;
using NpgsqlTypes;

namespace Evidrilo.Api.Recommendations;

public interface IRecommendationInteractionStore
{
    Task<string> AppendAsync(
        Guid accountId,
        RecommendationInteractionRequest request,
        CancellationToken cancellationToken);
}

public sealed class DatabaseUnavailableRecommendationInteractionStore : IRecommendationInteractionStore
{
    public Task<string> AppendAsync(
        Guid accountId,
        RecommendationInteractionRequest request,
        CancellationToken cancellationToken) => throw new ApiException(
        StatusCodes.Status503ServiceUnavailable,
        "DATABASE_NOT_CONFIGURED",
        "Recommendation interactions are not configured.");
}

public sealed class NpgsqlRecommendationInteractionStore : IRecommendationInteractionStore, IDisposable
{
    private readonly NpgsqlDataSource dataSource;

    public NpgsqlRecommendationInteractionStore(string connectionString)
    {
        dataSource = NpgsqlDataSource.Create(connectionString);
    }

    public async Task<string> AppendAsync(
        Guid accountId,
        RecommendationInteractionRequest request,
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
                insert into public.recommendation_events (
                    account_id, client_event_id, case_version_id, interaction,
                    calculation_version, reason_code
                )
                select
                    @account_id, @client_event_id, @case_version_id, @interaction,
                    @calculation_version, @reason_code
                where @case_version_id is null
                   or exists (
                       select 1 from public.case_versions
                       where case_version_id = @case_version_id and status = 'published'
                   )
                on conflict (account_id, client_event_id) do nothing
                returning client_event_id;
                """;
            command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
            command.Parameters.AddWithValue("client_event_id", NpgsqlDbType.Uuid, request.ClientEventId);
            command.Parameters.AddWithValue("case_version_id", NpgsqlDbType.Text, (object?)request.CaseVersionId ?? DBNull.Value);
            command.Parameters.AddWithValue("interaction", NpgsqlDbType.Text, request.Interaction.ToWire());
            command.Parameters.AddWithValue("calculation_version", NpgsqlDbType.Text, request.CalculationVersion);
            command.Parameters.AddWithValue("reason_code", NpgsqlDbType.Text, request.ReasonCode);
            var inserted = await command.ExecuteScalarAsync(cancellationToken);
            if (inserted is null or DBNull)
            {
                await using var duplicate = connection.CreateCommand();
                duplicate.Transaction = transaction;
                duplicate.CommandText = """
                    select case_version_id, interaction, calculation_version, reason_code
                    from public.recommendation_events
                    where account_id = @account_id and client_event_id = @client_event_id;
                    """;
                duplicate.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                duplicate.Parameters.AddWithValue("client_event_id", NpgsqlDbType.Uuid, request.ClientEventId);
                await using var duplicateReader = await duplicate.ExecuteReaderAsync(cancellationToken);
                var isDuplicate = await duplicateReader.ReadAsync(cancellationToken);
                var matches = false;
                if (isDuplicate)
                {
                    var storedCaseVersionId = duplicateReader.IsDBNull(0)
                        ? null
                        : duplicateReader.GetString(0);
                    var storedInteraction = duplicateReader.GetString(1);
                    var storedCalculationVersion = duplicateReader.GetString(2);
                    var storedReasonCode = duplicateReader.GetString(3);
                    matches = storedCaseVersionId == request.CaseVersionId
                        && storedInteraction == request.Interaction.ToWire()
                        && storedCalculationVersion == request.CalculationVersion
                        && storedReasonCode == request.ReasonCode;
                }
                await duplicateReader.DisposeAsync();
                await transaction.RollbackAsync(cancellationToken);
                if (isDuplicate && matches) return "duplicate";
                if (isDuplicate)
                    throw new ApiException(
                        StatusCodes.Status409Conflict,
                        "RECOMMENDATION_IDEMPOTENCY_KEY_REUSE",
                        "The recommendation event identifier was reused.");
                throw new ApiException(
                    StatusCodes.Status409Conflict,
                    "CASE_NOT_PUBLISHED",
                    "The recommendation case is not published or the event conflicts.");
            }

            await transaction.CommitAsync(cancellationToken);
            return "accepted";
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
                "Recommendation interactions are temporarily unavailable.",
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

public static class RecommendationInteractionWireExtensions
{
    public static string ToWire(this RecommendationInteraction value) => value switch
    {
        RecommendationInteraction.Shown => "shown",
        RecommendationInteraction.Accepted => "accepted",
        RecommendationInteraction.Dismissed => "dismissed",
        _ => throw new ArgumentOutOfRangeException(nameof(value)),
    };
}
