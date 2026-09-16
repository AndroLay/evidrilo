using Evidrilo.Api.Common;
using Npgsql;
using NpgsqlTypes;

namespace Evidrilo.Api.Authorization;

public interface ICohortStore
{
    Task<CohortSummaryLookup> GetSummaryAsync(
        Guid accountId,
        Guid cohortId,
        CancellationToken cancellationToken);
}

public sealed class DatabaseUnavailableCohortStore : ICohortStore
{
    public Task<CohortSummaryLookup> GetSummaryAsync(
        Guid accountId,
        Guid cohortId,
        CancellationToken cancellationToken) => throw new ApiException(
        StatusCodes.Status503ServiceUnavailable,
        "DATABASE_NOT_CONFIGURED",
        "Cohort summaries are not configured.");
}

public sealed class NpgsqlCohortStore : ICohortStore, IDisposable
{
    private readonly NpgsqlDataSource dataSource;

    public NpgsqlCohortStore(string connectionString)
    {
        dataSource = NpgsqlDataSource.Create(connectionString);
    }

    public async Task<CohortSummaryLookup> GetSummaryAsync(
        Guid accountId,
        Guid cohortId,
        CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);

            var scope = await ReadAggregateAsync(connection, transaction, cohortId, cancellationToken);
            if (scope is null)
                return CohortSummaryLookup.Denied("MEMBERSHIP_REQUIRED");

            var decision = AccessPolicy.CanReadCohortAggregate(
                new Membership(accountId, scope.OrganizationId, scope.Role, true),
                new CohortScope(scope.OrganizationId, cohortId, scope.ActiveLearnerCount));
            if (!decision.Allowed)
                return CohortSummaryLookup.Denied(decision.Code);

            await transaction.CommitAsync(cancellationToken);
            return CohortSummaryLookup.Found(new CohortSummaryData(
                cohortId,
                scope.ActiveLearnerCount,
                scope.CompletionRate,
                scope.PassRate));
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
                "Cohort summaries are temporarily unavailable.",
                exception);
        }
    }

    public void Dispose() => dataSource.Dispose();

    private static async Task<CohortScopeRecord?> ReadAggregateAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid cohortId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            select organization_id, role, learner_count, completion_rate, pass_rate
            from public.read_cohort_aggregate(@cohort_id);
            """;
        command.Parameters.AddWithValue("cohort_id", NpgsqlDbType.Uuid, cohortId);
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        if (!await reader.ReadAsync(cancellationToken)) return null;

        var role = reader.GetString(1) switch
        {
            "learner" => PlatformRole.Learner,
            "author" => PlatformRole.Author,
            "teacher" => PlatformRole.Teacher,
            "reviewer" => PlatformRole.Reviewer,
            "maintainer" => PlatformRole.Maintainer,
            "owner" => PlatformRole.Owner,
            _ => throw new ApiException(
                StatusCodes.Status503ServiceUnavailable,
                "DATABASE_SCHEMA_MISSING",
                "The authorization schema is not ready."),
        };
        return new CohortScopeRecord(
            reader.GetGuid(0),
            role,
            reader.GetInt32(2),
            reader.IsDBNull(3) ? null : reader.GetDouble(3),
            reader.IsDBNull(4) ? null : reader.GetDouble(4));
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

    private sealed record CohortScopeRecord(
        Guid OrganizationId,
        PlatformRole Role,
        int ActiveLearnerCount,
        double? CompletionRate,
        double? PassRate);
}
