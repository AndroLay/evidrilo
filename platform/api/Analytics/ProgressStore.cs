using Evidrilo.Api.Common;
using Npgsql;
using NpgsqlTypes;

namespace Evidrilo.Api.Analytics;

public interface IProgressStore
{
    Task<ProgressProjection?> GetAsync(
        Guid accountId,
        CancellationToken cancellationToken);

    Task<IReadOnlyList<DailyProgressProjection>> GetDailyAsync(
        Guid accountId,
        DateOnly from,
        DateOnly to,
        CancellationToken cancellationToken);
}

public sealed class DatabaseUnavailableProgressStore : IProgressStore
{
    public Task<ProgressProjection?> GetAsync(
        Guid accountId,
        CancellationToken cancellationToken) => throw new ApiException(
        StatusCodes.Status503ServiceUnavailable,
        "DATABASE_NOT_CONFIGURED",
        "Progress is not configured.");

    public Task<IReadOnlyList<DailyProgressProjection>> GetDailyAsync(
        Guid accountId,
        DateOnly from,
        DateOnly to,
        CancellationToken cancellationToken) => throw new ApiException(
        StatusCodes.Status503ServiceUnavailable,
        "DATABASE_NOT_CONFIGURED",
        "Daily progress is not configured.");
}

public sealed class NpgsqlProgressStore : IProgressStore, IDisposable
{
    private readonly NpgsqlDataSource dataSource;

    public NpgsqlProgressStore(string connectionString)
    {
        dataSource = NpgsqlDataSource.Create(connectionString);
    }

    public async Task<ProgressProjection?> GetAsync(
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
                select calculation_version, attempts_observed, completed_attempts,
                       revisions_observed, pass_count, action_required_count,
                       abstention_count, coverage
                from public.progress_projections
                where account_id = @account_id;
                """;
            command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);

            ProgressProjection? projection = null;
            await using (var reader = await command.ExecuteReaderAsync(cancellationToken))
            {
                if (await reader.ReadAsync(cancellationToken))
                {
                    projection = new ProgressProjection
                    {
                        CalculationVersion = reader.GetString(0),
                        AttemptsObserved = reader.GetInt32(1),
                        CompletedAttempts = reader.GetInt32(2),
                        RevisionsObserved = reader.GetInt32(3),
                        PassCount = reader.GetInt32(4),
                        ActionRequiredCount = reader.GetInt32(5),
                        AbstentionCount = reader.GetInt32(6),
                        Coverage = reader.GetDouble(7),
                    };
                }
            }

            await transaction.CommitAsync(cancellationToken);
            return projection;
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
                "Progress is temporarily unavailable.",
                exception);
        }
    }

    public async Task<IReadOnlyList<DailyProgressProjection>> GetDailyAsync(
        Guid accountId,
        DateOnly from,
        DateOnly to,
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
                select projection_date, calculation_version, attempts_observed,
                       completed_attempts, revisions_observed, pass_count,
                       action_required_count, abstention_count, coverage
                from public.progress_daily_projections
                where account_id = @account_id
                  and projection_date between @from_date and @to_date
                order by projection_date;
                """;
            command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
            command.Parameters.AddWithValue("from_date", NpgsqlDbType.Date, from);
            command.Parameters.AddWithValue("to_date", NpgsqlDbType.Date, to);

            var projections = new List<DailyProgressProjection>();
            await using (var reader = await command.ExecuteReaderAsync(cancellationToken))
            {
                while (await reader.ReadAsync(cancellationToken))
                {
                    projections.Add(new DailyProgressProjection(
                        reader.GetFieldValue<DateOnly>(0),
                        reader.GetString(1),
                        reader.GetInt32(2),
                        reader.GetInt32(3),
                        reader.GetInt32(4),
                        reader.GetInt32(5),
                        reader.GetInt32(6),
                        reader.GetInt32(7),
                        reader.GetDouble(8)));
                }
            }

            await transaction.CommitAsync(cancellationToken);
            return projections;
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
                "Daily progress is temporarily unavailable.",
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
