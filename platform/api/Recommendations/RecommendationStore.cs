using Evidrilo.Api.Common;
using Npgsql;
using NpgsqlTypes;

namespace Evidrilo.Api.Recommendations;

public interface IRecommendationStore
{
    Task<RecommendationInput> GetInputAsync(
        Guid accountId,
        CancellationToken cancellationToken);
}

public sealed class DatabaseUnavailableRecommendationStore : IRecommendationStore
{
    public Task<RecommendationInput> GetInputAsync(
        Guid accountId,
        CancellationToken cancellationToken) => throw new ApiException(
        StatusCodes.Status503ServiceUnavailable,
        "DATABASE_NOT_CONFIGURED",
        "Recommendations are not configured.");
}

public sealed class NpgsqlRecommendationStore : IRecommendationStore, IDisposable
{
    private readonly NpgsqlDataSource dataSource;

    public NpgsqlRecommendationStore(string connectionString)
    {
        dataSource = NpgsqlDataSource.Create(connectionString);
    }

    public async Task<RecommendationInput> GetInputAsync(
        Guid accountId,
        CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);

            var projection = await ReadProjectionAsync(connection, transaction, accountId, cancellationToken);
            var candidates = await ReadCandidatesAsync(connection, transaction, cancellationToken);
            await transaction.CommitAsync(cancellationToken);

            return new RecommendationInput(
                projection.CalculationVersion,
                projection.AttemptsObserved,
                projection.ActionRequiredCount,
                projection.PassCount,
                projection.AbstentionCount,
                candidates);
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
                "Recommendations are temporarily unavailable.",
                exception);
        }
    }

    public void Dispose() => dataSource.Dispose();

    private static async Task<RecommendationProjection> ReadProjectionAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            select calculation_version, attempts_observed, action_required_count,
                   pass_count, abstention_count
            from public.progress_projections
            where account_id = @account_id;
            """;
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        if (!await reader.ReadAsync(cancellationToken))
        {
            return new RecommendationProjection(
                ProgressProjectionVersion,
                0,
                0,
                0,
                0);
        }

        var projection = new RecommendationProjection(
            reader.GetString(0),
            reader.GetInt32(1),
            reader.GetInt32(2),
            reader.GetInt32(3),
            reader.GetInt32(4));
        if (projection.CalculationVersion != ProgressProjectionVersion
            || projection.AttemptsObserved < 0
            || projection.ActionRequiredCount < 0
            || projection.PassCount < 0
            || projection.AbstentionCount < 0
            || (long)projection.PassCount + projection.ActionRequiredCount + projection.AbstentionCount
                > projection.AttemptsObserved)
            throw new ApiException(
                StatusCodes.Status503ServiceUnavailable,
                "PROJECTION_UNAVAILABLE",
                "Recommendations are temporarily unavailable.");
        return projection;
    }

    private static async Task<IReadOnlyList<RecommendationCandidate>> ReadCandidatesAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            select case_version_id, objective, skill_tags[1], difficulty,
                   evidence_references
            from public.case_versions
            where status = 'published'
              and objective is not null
              and skill_tags is not null
              and cardinality(skill_tags) > 0
              and skill_tags[1] is not null
              and difficulty is not null
              and evidence_references is not null
              and cardinality(evidence_references) > 0
            order by difficulty, skill_tags[1], case_version_id;
            """;
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        var candidates = new List<RecommendationCandidate>();
        while (await reader.ReadAsync(cancellationToken))
        {
            candidates.Add(new RecommendationCandidate(
                reader.GetString(0),
                reader.GetString(1),
                reader.GetString(2),
                reader.GetInt32(3),
                true,
                reader.GetFieldValue<string[]>(4)));
        }

        return candidates;
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

    private const string ProgressProjectionVersion = "progress.v1";

    private sealed record RecommendationProjection(
        string CalculationVersion,
        int AttemptsObserved,
        int ActionRequiredCount,
        int PassCount,
        int AbstentionCount);
}
