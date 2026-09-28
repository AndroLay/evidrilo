using Evidrilo.Api.Common;
using Npgsql;
using NpgsqlTypes;

namespace Evidrilo.Api.Ai;

public sealed record AiProviderSpendReservation(
    Guid AccountId,
    string RequestId,
    string Model,
    decimal ReservedCostUsd,
    decimal MonthlySpendLimitUsd,
    TimeSpan LeaseDuration);

public enum AiProviderBudgetReservationStatus
{
    Reserved,
    InProgress,
    Replay,
    BudgetExceeded,
}

public interface IAiProviderSpendBudgetStore
{
    Task<AiProviderBudgetReservationStatus> TryReserveAsync(
        AiProviderSpendReservation request,
        CancellationToken cancellationToken);

    Task<bool> CompleteAsync(
        Guid accountId,
        string requestId,
        decimal? actualCostUsd,
        int? inputTokens,
        int? outputTokens,
        bool uncertain,
        bool released,
        CancellationToken cancellationToken);
}

public sealed class DatabaseUnavailableAiProviderSpendBudgetStore : IAiProviderSpendBudgetStore
{
    public Task<AiProviderBudgetReservationStatus> TryReserveAsync(
        AiProviderSpendReservation request,
        CancellationToken cancellationToken) => throw new ApiException(
        StatusCodes.Status503ServiceUnavailable,
        "DATABASE_NOT_CONFIGURED",
        "AI provider spending is not configured.");

    public Task<bool> CompleteAsync(
        Guid accountId,
        string requestId,
        decimal? actualCostUsd,
        int? inputTokens,
        int? outputTokens,
        bool uncertain,
        bool released,
        CancellationToken cancellationToken) => throw new ApiException(
        StatusCodes.Status503ServiceUnavailable,
        "DATABASE_NOT_CONFIGURED",
        "AI provider spending is not configured.");
}

public sealed class NpgsqlAiProviderSpendBudgetStore : IAiProviderSpendBudgetStore, IDisposable
{
    private const string Provider = "openai";
    private readonly NpgsqlDataSource dataSource;

    public NpgsqlAiProviderSpendBudgetStore(string connectionString)
    {
        dataSource = NpgsqlDataSource.Create(connectionString);
    }

    public async Task<AiProviderBudgetReservationStatus> TryReserveAsync(
        AiProviderSpendReservation request,
        CancellationToken cancellationToken)
    {
        ValidateReservation(request);
        await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
        await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
        try
        {
            var existingPeriod = await FindPeriodAsync(connection, transaction, request, cancellationToken);
            var period = existingPeriod
                ?? await EnsureCurrentBudgetAsync(connection, transaction, request.MonthlySpendLimitUsd, cancellationToken);

            await LockBudgetAsync(connection, transaction, period, cancellationToken);
            await SettleExpiredReservationsAsync(connection, transaction, period, cancellationToken);

            if (existingPeriod.HasValue)
            {
                var existingStatus = await FindReservationStatusAsync(
                    connection,
                    transaction,
                    request,
                    cancellationToken);
                if (existingStatus is not null)
                {
                    await transaction.CommitAsync(cancellationToken);
                    return existingStatus == "reserved"
                        ? AiProviderBudgetReservationStatus.InProgress
                        : AiProviderBudgetReservationStatus.Replay;
                }
            }

            var budget = await ReadBudgetAsync(connection, transaction, period, cancellationToken);
            if (budget.SpentUsd + budget.ReservedUsd + request.ReservedCostUsd > budget.LimitUsd)
            {
                await transaction.CommitAsync(cancellationToken);
                return AiProviderBudgetReservationStatus.BudgetExceeded;
            }

            await using (var insert = connection.CreateCommand())
            {
                insert.Transaction = transaction;
                insert.CommandText = """
                    insert into public.ai_provider_spend_reservations (
                        provider, period_start, account_id, request_id, model,
                        reserved_cost_usd, status, lease_expires_at
                    ) values (
                        @provider, @period_start, @account_id, @request_id, @model,
                        @reserved_cost_usd, 'reserved', now() + @lease_duration
                    ) on conflict (provider, account_id, request_id) do nothing;
                    """;
                insert.Parameters.AddWithValue("provider", NpgsqlDbType.Text, Provider);
                insert.Parameters.AddWithValue("period_start", NpgsqlDbType.Date, period);
                insert.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, request.AccountId);
                insert.Parameters.AddWithValue("request_id", NpgsqlDbType.Text, request.RequestId);
                insert.Parameters.AddWithValue("model", NpgsqlDbType.Text, request.Model);
                insert.Parameters.AddWithValue("reserved_cost_usd", NpgsqlDbType.Numeric, request.ReservedCostUsd);
                insert.Parameters.AddWithValue("lease_duration", NpgsqlDbType.Interval, request.LeaseDuration);
                var inserted = await insert.ExecuteNonQueryAsync(cancellationToken);
                if (inserted == 0)
                {
                    var status = await FindReservationStatusAsync(connection, transaction, request, cancellationToken);
                    await transaction.CommitAsync(cancellationToken);
                    return status == "reserved"
                        ? AiProviderBudgetReservationStatus.InProgress
                        : AiProviderBudgetReservationStatus.Replay;
                }
            }

            await using (var update = connection.CreateCommand())
            {
                update.Transaction = transaction;
                update.CommandText = """
                    update public.ai_provider_monthly_spend
                    set reserved_usd = reserved_usd + @reserved_cost_usd,
                        updated_at = now()
                    where provider = @provider and period_start = @period_start;
                    """;
                update.Parameters.AddWithValue("reserved_cost_usd", NpgsqlDbType.Numeric, request.ReservedCostUsd);
                update.Parameters.AddWithValue("provider", NpgsqlDbType.Text, Provider);
                update.Parameters.AddWithValue("period_start", NpgsqlDbType.Date, period);
                if (await update.ExecuteNonQueryAsync(cancellationToken) != 1)
                    throw new InvalidOperationException("AI provider monthly budget row disappeared during reservation.");
            }

            await transaction.CommitAsync(cancellationToken);
            return AiProviderBudgetReservationStatus.Reserved;
        }
        catch
        {
            await transaction.RollbackAsync(CancellationToken.None);
            throw;
        }
    }

    public async Task<bool> CompleteAsync(
        Guid accountId,
        string requestId,
        decimal? actualCostUsd,
        int? inputTokens,
        int? outputTokens,
        bool uncertain,
        bool released,
        CancellationToken cancellationToken)
    {
        if (accountId == Guid.Empty
            || !IsValidRequestId(requestId)
            || actualCostUsd is < 0
            || inputTokens is < 0
            || outputTokens is < 0
            || (inputTokens.HasValue != outputTokens.HasValue)
            || (uncertain && (actualCostUsd.HasValue || released))
            || (released && (!actualCostUsd.HasValue || actualCostUsd.Value != 0 || inputTokens.HasValue))
            || (!uncertain && !released && !actualCostUsd.HasValue))
            throw new ArgumentException("AI provider spend settlement is invalid.");

        await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
        await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
        try
        {
            var period = await FindPeriodAsync(connection, transaction, accountId, requestId, cancellationToken);
            if (!period.HasValue)
            {
                await transaction.CommitAsync(cancellationToken);
                return false;
            }

            await LockBudgetAsync(connection, transaction, period.Value, cancellationToken);
            decimal reservedCost;
            string currentStatus;
            await using (var lookup = connection.CreateCommand())
            {
                lookup.Transaction = transaction;
                lookup.CommandText = """
                    select reserved_cost_usd, status
                    from public.ai_provider_spend_reservations
                    where provider = @provider and period_start = @period_start
                      and account_id = @account_id and request_id = @request_id
                    for update;
                    """;
                lookup.Parameters.AddWithValue("provider", NpgsqlDbType.Text, Provider);
                lookup.Parameters.AddWithValue("period_start", NpgsqlDbType.Date, period.Value);
                lookup.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                lookup.Parameters.AddWithValue("request_id", NpgsqlDbType.Text, requestId);
                await using var reader = await lookup.ExecuteReaderAsync(cancellationToken);
                if (!await reader.ReadAsync(cancellationToken))
                {
                    await reader.DisposeAsync();
                    await transaction.CommitAsync(cancellationToken);
                    return false;
                }
                reservedCost = reader.GetDecimal(0);
                currentStatus = reader.GetString(1);
            }

            if (currentStatus != "reserved")
            {
                await transaction.CommitAsync(cancellationToken);
                return true;
            }

            var settledCost = uncertain ? reservedCost : actualCostUsd!.Value;
            var status = released ? "released" : uncertain ? "uncertain" : "settled";
            await using (var updateReservation = connection.CreateCommand())
            {
                updateReservation.Transaction = transaction;
                updateReservation.CommandText = """
                    update public.ai_provider_spend_reservations
                    set actual_cost_usd = @actual_cost_usd,
                        input_tokens = @input_tokens,
                        output_tokens = @output_tokens,
                        status = @status,
                        settled_at = now()
                    where provider = @provider and period_start = @period_start
                      and account_id = @account_id and request_id = @request_id
                      and status = 'reserved';
                    """;
                updateReservation.Parameters.AddWithValue("actual_cost_usd", NpgsqlDbType.Numeric, settledCost);
                updateReservation.Parameters.AddWithValue("input_tokens", NpgsqlDbType.Integer, (object?)inputTokens ?? DBNull.Value);
                updateReservation.Parameters.AddWithValue("output_tokens", NpgsqlDbType.Integer, (object?)outputTokens ?? DBNull.Value);
                updateReservation.Parameters.AddWithValue("status", NpgsqlDbType.Text, status);
                updateReservation.Parameters.AddWithValue("provider", NpgsqlDbType.Text, Provider);
                updateReservation.Parameters.AddWithValue("period_start", NpgsqlDbType.Date, period.Value);
                updateReservation.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                updateReservation.Parameters.AddWithValue("request_id", NpgsqlDbType.Text, requestId);
                if (await updateReservation.ExecuteNonQueryAsync(cancellationToken) != 1)
                {
                    await transaction.RollbackAsync(CancellationToken.None);
                    return false;
                }
            }

            await using (var updateBudget = connection.CreateCommand())
            {
                updateBudget.Transaction = transaction;
                updateBudget.CommandText = """
                    update public.ai_provider_monthly_spend
                    set reserved_usd = reserved_usd - @reserved_cost_usd,
                        spent_usd = spent_usd + @actual_cost_usd,
                        updated_at = now()
                    where provider = @provider and period_start = @period_start;
                    """;
                updateBudget.Parameters.AddWithValue("reserved_cost_usd", NpgsqlDbType.Numeric, reservedCost);
                updateBudget.Parameters.AddWithValue("actual_cost_usd", NpgsqlDbType.Numeric, settledCost);
                updateBudget.Parameters.AddWithValue("provider", NpgsqlDbType.Text, Provider);
                updateBudget.Parameters.AddWithValue("period_start", NpgsqlDbType.Date, period.Value);
                if (await updateBudget.ExecuteNonQueryAsync(cancellationToken) != 1)
                    throw new InvalidOperationException("AI provider monthly budget row disappeared during settlement.");
            }

            await transaction.CommitAsync(cancellationToken);
            return true;
        }
        catch
        {
            await transaction.RollbackAsync(CancellationToken.None);
            throw;
        }
    }

    private static async Task<DateOnly?> FindPeriodAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        AiProviderSpendReservation request,
        CancellationToken cancellationToken) =>
        await FindPeriodAsync(connection, transaction, request.AccountId, request.RequestId, cancellationToken);

    private static async Task<DateOnly?> FindPeriodAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        string requestId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            select period_start from public.ai_provider_spend_reservations
            where provider = @provider and account_id = @account_id and request_id = @request_id;
            """;
        command.Parameters.AddWithValue("provider", NpgsqlDbType.Text, Provider);
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        command.Parameters.AddWithValue("request_id", NpgsqlDbType.Text, requestId);
        var result = await command.ExecuteScalarAsync(cancellationToken);
        return result is DateOnly date ? date : null;
    }

    private static async Task<DateOnly> EnsureCurrentBudgetAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        decimal monthlyLimitUsd,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            insert into public.ai_provider_monthly_spend (
                provider, period_start, spend_limit_usd
            ) values (
                @provider, (date_trunc('month', now() at time zone 'UTC'))::date, @spend_limit_usd
            )
            on conflict (provider, period_start) do update
            set spend_limit_usd = excluded.spend_limit_usd, updated_at = now()
            returning period_start;
            """;
        command.Parameters.AddWithValue("provider", NpgsqlDbType.Text, Provider);
        command.Parameters.AddWithValue("spend_limit_usd", NpgsqlDbType.Numeric, monthlyLimitUsd);
        var result = await command.ExecuteScalarAsync(cancellationToken);
        return result is DateOnly date
            ? date
            : throw new InvalidOperationException("AI provider monthly budget could not be created.");
    }

    private static async Task LockBudgetAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        DateOnly period,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            select period_start from public.ai_provider_monthly_spend
            where provider = @provider and period_start = @period_start for update;
            """;
        command.Parameters.AddWithValue("provider", NpgsqlDbType.Text, Provider);
        command.Parameters.AddWithValue("period_start", NpgsqlDbType.Date, period);
        if (await command.ExecuteScalarAsync(cancellationToken) is null)
            throw new InvalidOperationException("AI provider monthly budget row is missing.");
    }

    private static async Task SettleExpiredReservationsAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        DateOnly period,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            with expired as (
                update public.ai_provider_spend_reservations
                set status = 'uncertain',
                    actual_cost_usd = reserved_cost_usd,
                    settled_at = now()
                where provider = @provider and period_start = @period_start
                  and status = 'reserved' and lease_expires_at <= now()
                returning reserved_cost_usd
            )
            update public.ai_provider_monthly_spend
            set reserved_usd = reserved_usd - coalesce((select sum(reserved_cost_usd) from expired), 0),
                spent_usd = spent_usd + coalesce((select sum(reserved_cost_usd) from expired), 0),
                updated_at = now()
            where provider = @provider and period_start = @period_start;
            """;
        command.Parameters.AddWithValue("provider", NpgsqlDbType.Text, Provider);
        command.Parameters.AddWithValue("period_start", NpgsqlDbType.Date, period);
        await command.ExecuteNonQueryAsync(cancellationToken);
    }

    private static async Task<(decimal LimitUsd, decimal ReservedUsd, decimal SpentUsd)> ReadBudgetAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        DateOnly period,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            select spend_limit_usd, reserved_usd, spent_usd
            from public.ai_provider_monthly_spend
            where provider = @provider and period_start = @period_start;
            """;
        command.Parameters.AddWithValue("provider", NpgsqlDbType.Text, Provider);
        command.Parameters.AddWithValue("period_start", NpgsqlDbType.Date, period);
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        if (!await reader.ReadAsync(cancellationToken))
            throw new InvalidOperationException("AI provider monthly budget row is missing.");
        return (reader.GetDecimal(0), reader.GetDecimal(1), reader.GetDecimal(2));
    }

    private static async Task<string?> FindReservationStatusAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        AiProviderSpendReservation request,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            select status from public.ai_provider_spend_reservations
            where provider = @provider and account_id = @account_id and request_id = @request_id;
            """;
        command.Parameters.AddWithValue("provider", NpgsqlDbType.Text, Provider);
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, request.AccountId);
        command.Parameters.AddWithValue("request_id", NpgsqlDbType.Text, request.RequestId);
        var result = await command.ExecuteScalarAsync(cancellationToken);
        return result is string status ? status : null;
    }

    private static void ValidateReservation(AiProviderSpendReservation request)
    {
        if (request.AccountId == Guid.Empty
            || !IsValidRequestId(request.RequestId)
            || string.IsNullOrWhiteSpace(request.Model)
            || request.Model.Length > 128
            || request.ReservedCostUsd <= 0
            || request.MonthlySpendLimitUsd <= 0
            || request.ReservedCostUsd > request.MonthlySpendLimitUsd
            || request.LeaseDuration < TimeSpan.FromSeconds(5)
            || request.LeaseDuration > TimeSpan.FromMinutes(2))
            throw new ArgumentException("AI provider spend reservation is invalid.", nameof(request));
    }

    private static bool IsValidRequestId(string value) =>
        value.Length is >= 8 and <= 128
        && value.All(character =>
            character is >= 'A' and <= 'Z'
                or >= 'a' and <= 'z'
                or >= '0' and <= '9'
                or '_' or '-');

    public void Dispose() => dataSource.Dispose();
}
