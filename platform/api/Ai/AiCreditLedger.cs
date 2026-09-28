using System.Globalization;
using Evidrilo.Api.Common;
using Npgsql;
using NpgsqlTypes;

namespace Evidrilo.Api.Ai;

public enum ProjectAiCreditSettlementStatus
{
    Applied,
    Dismissed,
    NotFound,
    Conflict,
}

public sealed record ProjectAiCreditSettlementResult(
    ProjectAiCreditSettlementStatus Status,
    int CreditCost = 0);

/// <summary>
/// Durable project-preview binding and one-shot student settlement layered on
/// the account-scoped AI credit reservation ledger.
/// </summary>
public interface IProjectAiCreditSettlementLedger
{
    Task<bool> BindProjectAiReservationAsync(
        Guid accountId,
        AiCreditReservation reservation,
        string requestHash,
        int creditCost,
        CancellationToken cancellationToken);

    Task<bool> MarkProjectAiPreviewReadyAsync(
        Guid accountId,
        AiCreditReservation reservation,
        string requestHash,
        int creditCost,
        CancellationToken cancellationToken);

    Task<ProjectAiCreditSettlementResult> SettleProjectAiAsync(
        Guid accountId,
        string requestId,
        string settlementHash,
        bool apply,
        CancellationToken cancellationToken);
}

public sealed class DatabaseUnavailableAiCreditLedger : IAiCreditLedger, IProjectAiCreditSettlementLedger
{
    public Task EnsureConsentAsync(
        Guid accountId,
        string consentVersion,
        CancellationToken cancellationToken) => throw NotConfigured();

    public Task<AiCreditReservation?> TryReserveAsync(
        Guid accountId,
        string requestId,
        string requestHash,
        int creditCost,
        CancellationToken cancellationToken) => throw NotConfigured();

    public Task<AiCreditReservation?> TryReserveAsync(
        Guid accountId,
        string requestId,
        string requestHash,
        CancellationToken cancellationToken) =>
        TryReserveAsync(accountId, requestId, requestHash, 1, cancellationToken);

    public Task<bool> CompleteAsync(
        Guid accountId,
        AiCreditReservation reservation,
        bool accepted,
        CancellationToken cancellationToken) => throw NotConfigured();

    public Task<AiCreditBalance> GetBalanceAsync(
        Guid accountId,
        CancellationToken cancellationToken) => throw NotConfigured();

    public Task<bool> BindProjectAiReservationAsync(
        Guid accountId,
        AiCreditReservation reservation,
        string requestHash,
        int creditCost,
        CancellationToken cancellationToken) => throw NotConfigured();

    public Task<bool> MarkProjectAiPreviewReadyAsync(
        Guid accountId,
        AiCreditReservation reservation,
        string requestHash,
        int creditCost,
        CancellationToken cancellationToken) => throw NotConfigured();

    public Task<ProjectAiCreditSettlementResult> SettleProjectAiAsync(
        Guid accountId,
        string requestId,
        string settlementHash,
        bool apply,
        CancellationToken cancellationToken) => throw NotConfigured();

    private static ApiException NotConfigured() => new(
        StatusCodes.Status503ServiceUnavailable,
        "DATABASE_NOT_CONFIGURED",
        "AI credits are not configured.");
}

public sealed class NpgsqlAiCreditLedger : IAiCreditLedger, IProjectAiCreditSettlementLedger, IDisposable
{
    private const string FreeGrantKind = "free_once";
    private const string SubscriptionGrantKind = "subscription_month";
    private const string Entitlement = "evidrilo_pro";
    private static readonly string LegacyRequestHash = new('0', 64);
    private const int FreeGrantCredits = 10;
    private const int SubscriptionGrantCredits = 100;
    private const string ProjectAiDispatchingMarkerPrefix = "project-ai-dispatching:v1:";
    private const string ProjectAiPreviewMarkerPrefix = "project-ai-preview:v1:";
    private const string ProjectAiSettledMarkerPrefix = "project-ai-settled:v1:";
    private static readonly TimeSpan ReservationLease = TimeSpan.FromMinutes(2);
    // Keep a successful preview's hold alive while the student reviews it;
    // ordinary in-flight provider reservations retain the shorter lease.
    private static readonly TimeSpan ProjectPreviewLease = TimeSpan.FromHours(24);
    private readonly NpgsqlDataSource dataSource;

    public NpgsqlAiCreditLedger(string connectionString)
    {
        dataSource = NpgsqlDataSource.Create(connectionString);
    }

    public async Task EnsureConsentAsync(
        Guid accountId,
        string consentVersion,
        CancellationToken cancellationToken)
    {
        if (accountId == Guid.Empty || string.IsNullOrWhiteSpace(consentVersion))
            throw new ApiException(
                StatusCodes.Status400BadRequest,
                "INVALID_AI_CONSENT",
                "The AI consent is invalid.");

        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);
            await LockAccountRowAsync(connection, transaction, accountId, cancellationToken);
            var now = DateTimeOffset.UtcNow;

            await using (var consentCommand = connection.CreateCommand())
            {
                consentCommand.Transaction = transaction;
                consentCommand.CommandText = """
                    insert into public.ai_credit_consents (
                        account_id, consent_version, consented_at, revoked_at
                    ) values (@account_id, @consent_version, @consented_at, null)
                    on conflict (account_id) do update
                    set consent_version = excluded.consent_version,
                        consented_at = excluded.consented_at,
                        revoked_at = null;
                    """;
                consentCommand.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                consentCommand.Parameters.AddWithValue("consent_version", NpgsqlDbType.Text, consentVersion);
                consentCommand.Parameters.AddWithValue("consented_at", NpgsqlDbType.TimestampTz, now);
                await consentCommand.ExecuteNonQueryAsync(cancellationToken);
            }

            await InsertGrantIfMissingAsync(
                connection,
                transaction,
                accountId,
                FreeGrantKind,
                "once",
                FreeGrantCredits,
                now,
                expiresAt: null,
                cancellationToken);

            var entitlementPeriod = await GetActiveEntitlementPeriodAsync(
                connection,
                transaction,
                accountId,
                cancellationToken);
            if (entitlementPeriod is not null)
            {
                var (periodStart, periodExpiresAt) = CurrentEntitlementMonth(entitlementPeriod);
                await InsertGrantIfMissingAsync(
                    connection,
                    transaction,
                    accountId,
                    SubscriptionGrantKind,
                    PeriodGrantKey(periodStart),
                    SubscriptionGrantCredits,
                    periodStart,
                    periodExpiresAt,
                    cancellationToken);
            }

            await transaction.CommitAsync(cancellationToken);
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
                "AI credit consent is temporarily unavailable.",
                exception);
        }
    }

    public Task<AiCreditReservation?> TryReserveAsync(
        Guid accountId,
        string requestId,
        string requestHash,
        CancellationToken cancellationToken) =>
        TryReserveAsync(accountId, requestId, requestHash, 1, cancellationToken);

    public async Task<AiCreditReservation?> TryReserveAsync(
        Guid accountId,
        string requestId,
        string requestHash,
        int creditCost,
        CancellationToken cancellationToken)
    {
        if (creditCost is < 1 or > 100)
            throw new ApiException(
                StatusCodes.Status400BadRequest,
                "INVALID_AI_CREDIT_COST",
                "The AI credit cost is invalid.");

        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);
            // Serialize reservation creation per account. A primary-key check
            // alone is not enough: two transactions can both observe that a
            // new request key is absent before either one commits. Locking the
            // authenticated account row makes the replay decision and credit
            // reservation one atomic account-scoped operation.
            await LockAccountRowAsync(connection, transaction, accountId, cancellationToken);
            await ReleaseExpiredReservationsAsync(connection, transaction, accountId, cancellationToken);

            string? existingStatus = null;
            string? existingHash = null;
            string? existingReleaseReason = null;
            int? existingCreditCost = null;
            await using (var existingCommand = connection.CreateCommand())
            {
                existingCommand.Transaction = transaction;
                existingCommand.CommandText = """
                    select request_hash, status, credit_cost, release_reason
                    from public.ai_credit_reservations
                    where account_id = @account_id and request_id = @request_id
                    for update;
                    """;
                existingCommand.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                existingCommand.Parameters.AddWithValue("request_id", NpgsqlDbType.Text, requestId);
                await using var reader = await existingCommand.ExecuteReaderAsync(cancellationToken);
                if (await reader.ReadAsync(cancellationToken))
                {
                    existingHash = reader.GetString(0);
                    existingStatus = reader.GetString(1);
                    existingCreditCost = reader.GetInt32(2);
                    existingReleaseReason = reader.IsDBNull(3) ? null : reader.GetString(3);
                }
            }

            // Reservations created before migration 033 have a zero sentinel
            // because their original payload fingerprint was never persisted.
            // They remain terminal replays, but cannot be safely compared to a
            // new payload. A new key is required for every new attempt.
            // A settled Project AI reservation stores the settlement digest in
            // request_hash and retains the original scaffold digest in its
            // project-ai-settled marker, preventing a replay from dispatching.
            var originalHashHasProjectAiSettlementReceipt = existingStatus is "consumed" or "released"
                && string.Equals(
                    existingReleaseReason,
                    ProjectAiSettledRequestMarker(requestHash),
                    StringComparison.Ordinal);
            if (existingStatus is not null
                && (existingCreditCost != creditCost
                    || (!string.Equals(existingHash, LegacyRequestHash, StringComparison.Ordinal)
                        && !string.Equals(existingHash, requestHash, StringComparison.Ordinal)
                        && !originalHashHasProjectAiSettlementReceipt)))
            {
                throw new ApiException(
                    StatusCodes.Status409Conflict,
                    "AI_IDEMPOTENCY_KEY_REUSE",
                    "The AI operation identifier was reused with different input.");
            }

            if (existingStatus is "reserved" or "consumed" or "released")
            {
                await transaction.CommitAsync(cancellationToken);
                return new AiCreditReservation(requestId, IsReplay: true, ExistingStatus: existingStatus);
            }

            var entitlementPeriod = await GetActiveEntitlementPeriodAsync(
                connection,
                transaction,
                accountId,
                cancellationToken);
            var subscriptionGrantKey = entitlementPeriod is null
                ? null
                : PeriodGrantKey(CurrentEntitlementMonth(entitlementPeriod).StartsAt);
            var grantId = await SelectAvailableGrantAsync(
                connection,
                transaction,
                accountId,
                subscriptionGrantKey,
                creditCost,
                cancellationToken);
            if (grantId is null)
            {
                await transaction.CommitAsync(cancellationToken);
                return null;
            }

            await using (var grantCommand = connection.CreateCommand())
            {
                grantCommand.Transaction = transaction;
                grantCommand.CommandText = """
                    update public.ai_credit_grants
                    set reserved_credits = reserved_credits + @credit_cost
                    where grant_id = @grant_id
                      and credits >= reserved_credits + consumed_credits + @credit_cost;
                    """;
                grantCommand.Parameters.AddWithValue("grant_id", NpgsqlDbType.Uuid, grantId.Value);
                grantCommand.Parameters.AddWithValue("credit_cost", NpgsqlDbType.Integer, creditCost);
                if (await grantCommand.ExecuteNonQueryAsync(cancellationToken) != 1)
                    throw new ApiException(
                        StatusCodes.Status503ServiceUnavailable,
                        "AI_CREDIT_LEDGER_CORRUPT",
                        "The AI credit ledger could not reserve the requested amount.");
            }

            await using var insertReservation = connection.CreateCommand();
            insertReservation.Transaction = transaction;
            insertReservation.CommandText = """
                insert into public.ai_credit_reservations (
                    account_id, request_id, request_hash, grant_id, credit_cost,
                    status, reserved_at, lease_expires_at
                ) values (
                    @account_id, @request_id, @request_hash, @grant_id, @credit_cost,
                    'reserved', now(), now() + @lease_duration
                );
                """;
            insertReservation.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
            insertReservation.Parameters.AddWithValue("request_id", NpgsqlDbType.Text, requestId);
            insertReservation.Parameters.AddWithValue("request_hash", NpgsqlDbType.Text, requestHash);
            insertReservation.Parameters.AddWithValue("grant_id", NpgsqlDbType.Uuid, grantId.Value);
            insertReservation.Parameters.AddWithValue("credit_cost", NpgsqlDbType.Integer, creditCost);
            insertReservation.Parameters.AddWithValue("lease_duration", NpgsqlDbType.Interval, ReservationLease);
            await insertReservation.ExecuteNonQueryAsync(cancellationToken);

            await transaction.CommitAsync(cancellationToken);
            return new AiCreditReservation(requestId);
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
                "AI credit reservation is temporarily unavailable.",
                exception);
        }
    }

    public async Task<bool> CompleteAsync(
        Guid accountId,
        AiCreditReservation reservation,
        bool accepted,
        CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);
            await LockAccountRowAsync(connection, transaction, accountId, cancellationToken);
            await ReleaseExpiredReservationsAsync(connection, transaction, accountId, cancellationToken);

            Guid? grantId = null;
            string? status = null;
            await using (var reservationCommand = connection.CreateCommand())
            {
                reservationCommand.Transaction = transaction;
                reservationCommand.CommandText = """
                    select grant_id, status
                    from public.ai_credit_reservations
                    where account_id = @account_id and request_id = @request_id
                    for update;
                    """;
                reservationCommand.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                reservationCommand.Parameters.AddWithValue("request_id", NpgsqlDbType.Text, reservation.RequestId);
                await using var reader = await reservationCommand.ExecuteReaderAsync(cancellationToken);
                if (await reader.ReadAsync(cancellationToken))
                {
                    grantId = reader.GetGuid(0);
                    status = reader.GetString(1);
                }
            }

            if (grantId is null || status != "reserved")
            {
                await transaction.CommitAsync(cancellationToken);
                return false;
            }

            Guid? settledGrantId = null;
            int settledCreditCost = 0;
            await using (var reservationCommand = connection.CreateCommand())
            {
                reservationCommand.Transaction = transaction;
                reservationCommand.CommandText = """
                    update public.ai_credit_reservations
                    set status = @status, completed_at = now(), release_reason = null
                    where account_id = @account_id
                      and request_id = @request_id
                      and status = 'reserved'
                      and lease_expires_at > clock_timestamp()
                    returning grant_id, credit_cost;
                    """;
                reservationCommand.Parameters.AddWithValue(
                    "status",
                    NpgsqlDbType.Text,
                    accepted ? "consumed" : "released");
                reservationCommand.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                reservationCommand.Parameters.AddWithValue("request_id", NpgsqlDbType.Text, reservation.RequestId);
                await using var reader = await reservationCommand.ExecuteReaderAsync(cancellationToken);
                if (await reader.ReadAsync(cancellationToken))
                {
                    settledGrantId = reader.GetGuid(0);
                    settledCreditCost = reader.GetInt32(1);
                }
            }

            if (settledGrantId is null)
            {
                await ReleaseExpiredReservationsAsync(connection, transaction, accountId, cancellationToken);
                await transaction.CommitAsync(cancellationToken);
                return false;
            }

            await using (var grantCommand = connection.CreateCommand())
            {
                grantCommand.Transaction = transaction;
                grantCommand.CommandText = accepted
                    ? """
                      update public.ai_credit_grants
                      set reserved_credits = reserved_credits - @credit_cost,
                          consumed_credits = consumed_credits + @credit_cost
                      where grant_id = @grant_id and reserved_credits >= @credit_cost;
                      """
                    : """
                      update public.ai_credit_grants
                      set reserved_credits = reserved_credits - @credit_cost
                      where grant_id = @grant_id and reserved_credits >= @credit_cost;
                      """;
                grantCommand.Parameters.AddWithValue("grant_id", NpgsqlDbType.Uuid, settledGrantId.Value);
                grantCommand.Parameters.AddWithValue("credit_cost", NpgsqlDbType.Integer, settledCreditCost);
                if (await grantCommand.ExecuteNonQueryAsync(cancellationToken) != 1)
                    throw new ApiException(
                        StatusCodes.Status503ServiceUnavailable,
                        "AI_CREDIT_LEDGER_CORRUPT",
                        "The AI credit ledger could not settle the reservation.");
            }

            await transaction.CommitAsync(cancellationToken);
            return true;
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
                "AI credit settlement is temporarily unavailable.",
                exception);
        }
    }

    public async Task<bool> BindProjectAiReservationAsync(
        Guid accountId,
        AiCreditReservation reservation,
        string requestHash,
        int creditCost,
        CancellationToken cancellationToken)
    {
        if (accountId == Guid.Empty
            || !IsHash(requestHash)
            || creditCost is not (1 or 3)
            || !IsValidRequestId(reservation.RequestId))
        {
            throw new ApiException(
                StatusCodes.Status400BadRequest,
                "INVALID_PROJECT_AI_RESERVATION",
                "The project-AI reservation is invalid.");
        }

        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);
            await LockAccountRowAsync(connection, transaction, accountId, cancellationToken);
            await ReleaseExpiredReservationsAsync(connection, transaction, accountId, cancellationToken);

            string? storedHash = null;
            string? status = null;
            string? releaseReason = null;
            int storedCreditCost = 0;
            await using (var read = connection.CreateCommand())
            {
                read.Transaction = transaction;
                read.CommandText = """
                    select request_hash, status, credit_cost, release_reason
                    from public.ai_credit_reservations
                    where account_id = @account_id and request_id = @request_id
                    for update;
                    """;
                read.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                read.Parameters.AddWithValue("request_id", NpgsqlDbType.Text, reservation.RequestId);
                await using var reader = await read.ExecuteReaderAsync(cancellationToken);
                if (await reader.ReadAsync(cancellationToken))
                {
                    storedHash = reader.GetString(0);
                    status = reader.GetString(1);
                    storedCreditCost = reader.GetInt32(2);
                    releaseReason = reader.IsDBNull(3) ? null : reader.GetString(3);
                }
            }

            var dispatchingMarker = ProjectAiDispatchingMarker(requestHash);
            if (status != "reserved"
                || storedCreditCost != creditCost
                || !string.Equals(storedHash, requestHash, StringComparison.Ordinal)
                || (releaseReason is not null && releaseReason != dispatchingMarker))
            {
                await transaction.CommitAsync(cancellationToken);
                return false;
            }

            if (releaseReason == dispatchingMarker)
            {
                await transaction.CommitAsync(cancellationToken);
                return true;
            }

            await using var update = connection.CreateCommand();
            update.Transaction = transaction;
            update.CommandText = """
                update public.ai_credit_reservations
                set release_reason = @dispatching_marker
                where account_id = @account_id
                  and request_id = @request_id
                  and request_hash = @request_hash
                  and credit_cost = @credit_cost
                  and status = 'reserved'
                  and release_reason is null
                  and lease_expires_at > clock_timestamp();
                """;
            update.Parameters.AddWithValue("dispatching_marker", NpgsqlDbType.Text, dispatchingMarker);
            update.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
            update.Parameters.AddWithValue("request_id", NpgsqlDbType.Text, reservation.RequestId);
            update.Parameters.AddWithValue("request_hash", NpgsqlDbType.Text, requestHash);
            update.Parameters.AddWithValue("credit_cost", NpgsqlDbType.Integer, creditCost);
            var bound = await update.ExecuteNonQueryAsync(cancellationToken) == 1;
            await transaction.CommitAsync(cancellationToken);
            return bound;
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
                "The project-AI reservation is temporarily unavailable.",
                exception);
        }
    }

    public async Task<bool> MarkProjectAiPreviewReadyAsync(
        Guid accountId,
        AiCreditReservation reservation,
        string requestHash,
        int creditCost,
        CancellationToken cancellationToken)
    {
        if (accountId == Guid.Empty
            || !IsHash(requestHash)
            || creditCost is not (1 or 3)
            || !IsValidRequestId(reservation.RequestId))
        {
            throw new ApiException(
                StatusCodes.Status400BadRequest,
                "INVALID_PROJECT_AI_RESERVATION",
                "The project-AI reservation is invalid.");
        }

        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);
            await LockAccountRowAsync(connection, transaction, accountId, cancellationToken);
            await ReleaseExpiredReservationsAsync(connection, transaction, accountId, cancellationToken);

            string? storedHash = null;
            string? status = null;
            string? releaseReason = null;
            int storedCreditCost = 0;
            await using (var read = connection.CreateCommand())
            {
                read.Transaction = transaction;
                read.CommandText = """
                    select request_hash, status, credit_cost, release_reason
                    from public.ai_credit_reservations
                    where account_id = @account_id and request_id = @request_id
                    for update;
                    """;
                read.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                read.Parameters.AddWithValue("request_id", NpgsqlDbType.Text, reservation.RequestId);
                await using var reader = await read.ExecuteReaderAsync(cancellationToken);
                if (await reader.ReadAsync(cancellationToken))
                {
                    storedHash = reader.GetString(0);
                    status = reader.GetString(1);
                    storedCreditCost = reader.GetInt32(2);
                    releaseReason = reader.IsDBNull(3) ? null : reader.GetString(3);
                }
            }

            var dispatchingMarker = ProjectAiDispatchingMarker(requestHash);
            var previewMarker = ProjectAiPreviewMarker(requestHash);
            if (status != "reserved"
                || storedCreditCost != creditCost
                || !string.Equals(storedHash, requestHash, StringComparison.Ordinal)
                || (releaseReason != dispatchingMarker && releaseReason != previewMarker))
            {
                await transaction.CommitAsync(cancellationToken);
                return false;
            }

            var expectedMarker = releaseReason == previewMarker ? previewMarker : dispatchingMarker;
            await using var update = connection.CreateCommand();
            update.Transaction = transaction;
            update.CommandText = """
                update public.ai_credit_reservations
                set release_reason = @preview_marker,
                    lease_expires_at = clock_timestamp() + @preview_lease
                where account_id = @account_id
                  and request_id = @request_id
                  and request_hash = @request_hash
                  and credit_cost = @credit_cost
                  and status = 'reserved'
                  and release_reason = @expected_marker
                  and lease_expires_at > clock_timestamp();
                """;
            update.Parameters.AddWithValue("preview_marker", NpgsqlDbType.Text, previewMarker);
            update.Parameters.AddWithValue("preview_lease", NpgsqlDbType.Interval, ProjectPreviewLease);
            update.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
            update.Parameters.AddWithValue("request_id", NpgsqlDbType.Text, reservation.RequestId);
            update.Parameters.AddWithValue("request_hash", NpgsqlDbType.Text, requestHash);
            update.Parameters.AddWithValue("credit_cost", NpgsqlDbType.Integer, creditCost);
            update.Parameters.AddWithValue("expected_marker", NpgsqlDbType.Text, expectedMarker);
            var marked = await update.ExecuteNonQueryAsync(cancellationToken) == 1;
            await transaction.CommitAsync(cancellationToken);
            return marked;
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
                "The project-AI reservation is temporarily unavailable.",
                exception);
        }
    }

    public async Task<ProjectAiCreditSettlementResult> SettleProjectAiAsync(
        Guid accountId,
        string requestId,
        string settlementHash,
        bool apply,
        CancellationToken cancellationToken)
    {
        if (accountId == Guid.Empty
            || !IsValidRequestId(requestId)
            || !IsHash(settlementHash))
        {
            throw new ApiException(
                StatusCodes.Status400BadRequest,
                "INVALID_PROJECT_AI_SETTLEMENT",
                "The project-AI settlement is invalid.");
        }

        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);
            await LockAccountRowAsync(connection, transaction, accountId, cancellationToken);
            await ReleaseExpiredReservationsAsync(connection, transaction, accountId, cancellationToken);

            Guid? grantId = null;
            string? requestHash = null;
            string? status = null;
            string? releaseReason = null;
            int creditCost = 0;
            await using (var read = connection.CreateCommand())
            {
                read.Transaction = transaction;
                read.CommandText = """
                    select grant_id, request_hash, status, credit_cost, release_reason
                    from public.ai_credit_reservations
                    where account_id = @account_id and request_id = @request_id
                    for update;
                    """;
                read.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                read.Parameters.AddWithValue("request_id", NpgsqlDbType.Text, requestId);
                await using var reader = await read.ExecuteReaderAsync(cancellationToken);
                if (await reader.ReadAsync(cancellationToken))
                {
                    grantId = reader.GetGuid(0);
                    requestHash = reader.GetString(1);
                    status = reader.GetString(2);
                    creditCost = reader.GetInt32(3);
                    releaseReason = reader.IsDBNull(4) ? null : reader.GetString(4);
                }
            }

            if (grantId is null || requestHash is null)
            {
                await transaction.CommitAsync(cancellationToken);
                return new ProjectAiCreditSettlementResult(ProjectAiCreditSettlementStatus.NotFound);
            }

            var finalStatus = apply ? "consumed" : "released";
            if (status is "consumed" or "released")
            {
                var sameSettlement = status == finalStatus
                    && string.Equals(requestHash, settlementHash, StringComparison.Ordinal)
                    && IsProjectAiSettledRequestMarker(releaseReason);
                await transaction.CommitAsync(cancellationToken);
                return sameSettlement
                    ? new ProjectAiCreditSettlementResult(
                        apply ? ProjectAiCreditSettlementStatus.Applied : ProjectAiCreditSettlementStatus.Dismissed,
                        creditCost)
                    : new ProjectAiCreditSettlementResult(ProjectAiCreditSettlementStatus.Conflict);
            }

            if (status != "reserved"
                || releaseReason != ProjectAiPreviewMarker(requestHash)
                || creditCost is not (1 or 3))
            {
                await transaction.CommitAsync(cancellationToken);
                return new ProjectAiCreditSettlementResult(ProjectAiCreditSettlementStatus.Conflict);
            }

            Guid? settledGrantId = null;
            await using (var updateReservation = connection.CreateCommand())
            {
                updateReservation.Transaction = transaction;
                updateReservation.CommandText = """
                    update public.ai_credit_reservations
                    set status = @status,
                        request_hash = @settlement_hash,
                        completed_at = clock_timestamp(),
                        release_reason = @request_marker
                    where account_id = @account_id
                      and request_id = @request_id
                      and status = 'reserved'
                      and release_reason = @preview_marker
                      and lease_expires_at > clock_timestamp()
                    returning grant_id;
                    """;
                updateReservation.Parameters.AddWithValue("status", NpgsqlDbType.Text, finalStatus);
                updateReservation.Parameters.AddWithValue("settlement_hash", NpgsqlDbType.Text, settlementHash);
                updateReservation.Parameters.AddWithValue(
                    "request_marker",
                    NpgsqlDbType.Text,
                    ProjectAiSettledRequestMarker(requestHash));
                updateReservation.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                updateReservation.Parameters.AddWithValue("request_id", NpgsqlDbType.Text, requestId);
                updateReservation.Parameters.AddWithValue(
                    "preview_marker",
                    NpgsqlDbType.Text,
                    ProjectAiPreviewMarker(requestHash));
                var value = await updateReservation.ExecuteScalarAsync(cancellationToken);
                settledGrantId = value is Guid id ? id : null;
            }

            if (settledGrantId is null)
            {
                await ReleaseExpiredReservationsAsync(connection, transaction, accountId, cancellationToken);
                await transaction.CommitAsync(cancellationToken);
                return new ProjectAiCreditSettlementResult(ProjectAiCreditSettlementStatus.Conflict);
            }

            await using (var updateGrant = connection.CreateCommand())
            {
                updateGrant.Transaction = transaction;
                updateGrant.CommandText = apply
                    ? """
                      update public.ai_credit_grants
                      set reserved_credits = reserved_credits - @credit_cost,
                          consumed_credits = consumed_credits + @credit_cost
                      where grant_id = @grant_id and reserved_credits >= @credit_cost;
                      """
                    : """
                      update public.ai_credit_grants
                      set reserved_credits = reserved_credits - @credit_cost
                      where grant_id = @grant_id and reserved_credits >= @credit_cost;
                      """;
                updateGrant.Parameters.AddWithValue("grant_id", NpgsqlDbType.Uuid, settledGrantId.Value);
                updateGrant.Parameters.AddWithValue("credit_cost", NpgsqlDbType.Integer, creditCost);
                if (await updateGrant.ExecuteNonQueryAsync(cancellationToken) != 1)
                    throw new ApiException(
                        StatusCodes.Status503ServiceUnavailable,
                        "AI_CREDIT_LEDGER_CORRUPT",
                        "The project-AI reservation could not be settled.");
            }

            await transaction.CommitAsync(cancellationToken);
            return new ProjectAiCreditSettlementResult(
                apply ? ProjectAiCreditSettlementStatus.Applied : ProjectAiCreditSettlementStatus.Dismissed,
                creditCost);
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
                "Project-AI credit settlement is temporarily unavailable.",
                exception);
        }
    }

    public async Task<AiCreditBalance> GetBalanceAsync(
        Guid accountId,
        CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);
            await LockAccountRowAsync(connection, transaction, accountId, cancellationToken);
            await ReleaseExpiredReservationsAsync(connection, transaction, accountId, cancellationToken);
            var grants = new List<AiCreditGrantBalance>();
            var now = await GetDatabaseTimeAsync(connection, transaction, cancellationToken);
            var entitlementPeriod = await GetActiveEntitlementPeriodAsync(
                connection,
                transaction,
                accountId,
                cancellationToken);
            var subscriptionGrantKey = entitlementPeriod is null
                ? null
                : PeriodGrantKey(CurrentEntitlementMonth(entitlementPeriod).StartsAt);
            bool consentRecorded;

            await using (var consentCommand = connection.CreateCommand())
            {
                consentCommand.Transaction = transaction;
                consentCommand.CommandText = """
                    select exists (
                        select 1
                        from public.ai_credit_consents
                        where account_id = @account_id and revoked_at is null
                    );
                    """;
                consentCommand.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                consentRecorded = (bool)(await consentCommand.ExecuteScalarAsync(cancellationToken) ?? false);
            }

            await using (var grantCommand = connection.CreateCommand())
            {
                grantCommand.Transaction = transaction;
                grantCommand.CommandText = """
                    select grant_kind, grant_key, credits, reserved_credits,
                           consumed_credits, expires_at
                    from public.ai_credit_grants
                    where account_id = @account_id
                      and starts_at <= @now
                      and (expires_at is null or expires_at > @now)
                      and (
                          grant_kind = 'free_once'
                          or (
                              grant_kind = 'subscription_month'
                              and grant_key = @subscription_grant_key
                              and exists (
                              select 1
                              from public.entitlements entitlement
                              where entitlement.account_id = @account_id
                                and entitlement.entitlement = 'evidrilo_pro'
                                and entitlement.status = 'active'
                                and entitlement.period_started_at <= @now
                                and entitlement.period_expires_at > @now
                              )
                          )
                      )
                    order by expires_at nulls last, grant_kind, grant_key;
                    """;
                grantCommand.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                grantCommand.Parameters.AddWithValue("now", NpgsqlDbType.TimestampTz, now);
                grantCommand.Parameters.AddWithValue(
                    "subscription_grant_key",
                    NpgsqlDbType.Text,
                    (object?)subscriptionGrantKey ?? DBNull.Value);
                await using var reader = await grantCommand.ExecuteReaderAsync(cancellationToken);
                while (await reader.ReadAsync(cancellationToken))
                {
                    grants.Add(new AiCreditGrantBalance(
                        reader.GetString(0),
                        reader.GetString(1),
                        reader.GetInt32(2),
                        reader.GetInt32(3),
                        reader.GetInt32(4),
                        reader.IsDBNull(5) ? null : reader.GetFieldValue<DateTimeOffset>(5)));
                }
            }

            await transaction.CommitAsync(cancellationToken);
            return new AiCreditBalance(consentRecorded, grants);
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
                "AI credit balance is temporarily unavailable.",
                exception);
        }
    }

    public void Dispose() => dataSource.Dispose();

    private static async Task InsertGrantIfMissingAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        string grantKind,
        string grantKey,
        int credits,
        DateTimeOffset startsAt,
        DateTimeOffset? expiresAt,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            insert into public.ai_credit_grants (
                account_id, grant_kind, grant_key, credits,
                reserved_credits, consumed_credits, starts_at, expires_at
            ) values (
                @account_id, @grant_kind, @grant_key, @credits,
                0, 0, @starts_at, @expires_at
            ) on conflict (account_id, grant_kind, grant_key) do nothing;
            """;
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        command.Parameters.AddWithValue("grant_kind", NpgsqlDbType.Text, grantKind);
        command.Parameters.AddWithValue("grant_key", NpgsqlDbType.Text, grantKey);
        command.Parameters.AddWithValue("credits", NpgsqlDbType.Integer, credits);
        command.Parameters.AddWithValue("starts_at", NpgsqlDbType.TimestampTz, startsAt);
        command.Parameters.AddWithValue("expires_at", NpgsqlDbType.TimestampTz, (object?)expiresAt ?? DBNull.Value);
        await command.ExecuteNonQueryAsync(cancellationToken);
    }

    private static async Task<EntitlementPeriod?> GetActiveEntitlementPeriodAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            select entitlement.period_started_at,
                   entitlement.period_expires_at,
                   instant.now_at
            from public.entitlements entitlement
            cross join (select clock_timestamp() as now_at) instant
            where entitlement.account_id = @account_id
              and entitlement.entitlement = @entitlement
              and entitlement.status = 'active'
              and entitlement.period_started_at <= instant.now_at
              and entitlement.period_expires_at > instant.now_at;
            """;
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        command.Parameters.AddWithValue("entitlement", NpgsqlDbType.Text, Entitlement);
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        if (!await reader.ReadAsync(cancellationToken))
            return null;
        return new EntitlementPeriod(
            reader.GetFieldValue<DateTimeOffset>(0),
            reader.GetFieldValue<DateTimeOffset>(1),
            reader.GetFieldValue<DateTimeOffset>(2));
    }

    private static (DateTimeOffset StartsAt, DateTimeOffset ExpiresAt) CurrentEntitlementMonth(
        EntitlementPeriod entitlementPeriod)
    {
        var periodStart = entitlementPeriod.StartedAt.ToUniversalTime();
        var periodExpiresAt = entitlementPeriod.ExpiresAt.ToUniversalTime();
        var currentAt = entitlementPeriod.CurrentAt.ToUniversalTime();
        var monthOffset = (currentAt.Year - periodStart.Year) * 12
            + currentAt.Month - periodStart.Month;
        var startsAt = periodStart.AddMonths(monthOffset);
        if (startsAt > currentAt)
        {
            monthOffset--;
            startsAt = periodStart.AddMonths(monthOffset);
        }

        var expiresAt = periodStart.AddMonths(monthOffset + 1);
        if (expiresAt > periodExpiresAt)
            expiresAt = periodExpiresAt;
        return (startsAt, expiresAt);
    }

    private static string PeriodGrantKey(DateTimeOffset periodStart) =>
        "period-" + periodStart.ToUnixTimeMilliseconds().ToString(CultureInfo.InvariantCulture);

    private static bool IsValidRequestId(string requestId) =>
        requestId.Length is >= 8 and <= 128
        && requestId.All(character =>
            character is >= 'A' and <= 'Z'
                or >= 'a' and <= 'z'
                or >= '0' and <= '9'
                or '_' or '-');

    private static bool IsHash(string value) =>
        value.Length == 64
        && value.All(character => character is >= '0' and <= '9' or >= 'a' and <= 'f');

    private static string ProjectAiDispatchingMarker(string requestHash) =>
        ProjectAiDispatchingMarkerPrefix + requestHash;

    private static string ProjectAiPreviewMarker(string requestHash) =>
        ProjectAiPreviewMarkerPrefix + requestHash;

    private static string ProjectAiSettledRequestMarker(string originalRequestHash) =>
        ProjectAiSettledMarkerPrefix + originalRequestHash;

    private static bool IsProjectAiSettledRequestMarker(string? marker) =>
        marker is not null
        && marker.StartsWith(ProjectAiSettledMarkerPrefix, StringComparison.Ordinal)
        && IsHash(marker[ProjectAiSettledMarkerPrefix.Length..]);

    private static async Task<DateTimeOffset> GetDatabaseTimeAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = "select clock_timestamp();";
        var value = await command.ExecuteScalarAsync(cancellationToken);
        return value switch
        {
            DateTimeOffset timestamp => timestamp,
            DateTime timestamp => new DateTimeOffset(DateTime.SpecifyKind(timestamp, DateTimeKind.Utc)),
            _ => throw new ApiException(
                StatusCodes.Status503ServiceUnavailable,
                "AI_CREDIT_LEDGER_CORRUPT",
                "The AI credit ledger could not read its database clock."),
        };
    }

    private static async Task ReleaseExpiredReservationsAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            with expired as materialized (
                select account_id, request_id, grant_id, credit_cost
                from public.ai_credit_reservations
                where account_id = @account_id
                  and status = 'reserved'
                  and lease_expires_at <= clock_timestamp()
                for update
            ),
            released as (
                update public.ai_credit_reservations reservation
                set status = 'released',
                    completed_at = clock_timestamp(),
                    release_reason = 'lease_expired'
                from expired
                where reservation.account_id = expired.account_id
                  and reservation.request_id = expired.request_id
                  and reservation.status = 'reserved'
                  and reservation.lease_expires_at <= clock_timestamp()
                returning reservation.grant_id, reservation.credit_cost
            ),
            grant_costs as (
                select grant_id, sum(credit_cost)::integer as released_credit_cost
                from released
                group by grant_id
            ),
            adjusted_grants as (
                update public.ai_credit_grants credit_grant
                set reserved_credits = credit_grant.reserved_credits - grant_costs.released_credit_cost
                from grant_costs
                where credit_grant.grant_id = grant_costs.grant_id
                  and credit_grant.reserved_credits >= grant_costs.released_credit_cost
                returning credit_grant.grant_id
            )
            select
                (select count(*) from expired),
                (select count(*) from released),
                (select count(*) from grant_costs),
                (select count(*) from adjusted_grants);
            """;
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        if (!await reader.ReadAsync(cancellationToken))
            throw new ApiException(
                StatusCodes.Status503ServiceUnavailable,
                "AI_CREDIT_LEDGER_CORRUPT",
                "The AI credit ledger could not recover expired reservations.");

        var expiredCount = reader.GetInt64(0);
        var releasedCount = reader.GetInt64(1);
        var grantCount = reader.GetInt64(2);
        var adjustedGrantCount = reader.GetInt64(3);
        if (expiredCount != releasedCount || grantCount != adjustedGrantCount)
            throw new ApiException(
                StatusCodes.Status503ServiceUnavailable,
                "AI_CREDIT_LEDGER_CORRUPT",
                "The AI credit ledger could not recover expired reservations.");
    }

    private sealed record EntitlementPeriod(
        DateTimeOffset StartedAt,
        DateTimeOffset ExpiresAt,
        DateTimeOffset CurrentAt);

    private static async Task<Guid?> SelectAvailableGrantAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        string? subscriptionGrantKey,
        int creditCost,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            select grant_id
            from public.ai_credit_grants
            where account_id = @account_id
              and starts_at <= now()
              and (expires_at is null or expires_at > now())
              and credits >= reserved_credits + consumed_credits + @credit_cost
              and (
                  grant_kind = 'free_once'
                  or (
                    grant_kind = 'subscription_month'
                    and grant_key = @subscription_grant_key
                    and exists (
                      select 1
                      from public.entitlements entitlement
                      where entitlement.account_id = @account_id
                        and entitlement.entitlement = 'evidrilo_pro'
                        and entitlement.status = 'active'
                        and entitlement.period_started_at <= now()
                        and entitlement.period_expires_at > now()
                    )
                  )
              )
            order by expires_at nulls last, grant_kind, grant_key
            limit 1
            for update;
            """;
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        command.Parameters.AddWithValue("credit_cost", NpgsqlDbType.Integer, creditCost);
        command.Parameters.AddWithValue(
            "subscription_grant_key",
            NpgsqlDbType.Text,
            (object?)subscriptionGrantKey ?? DBNull.Value);
        var value = await command.ExecuteScalarAsync(cancellationToken);
        return value is Guid grantId ? grantId : null;
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

    private static async Task LockAccountRowAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = "select id from auth.users where id = @account_id for update;";
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        if (await command.ExecuteScalarAsync(cancellationToken) is null)
        {
            throw new ApiException(
                StatusCodes.Status404NotFound,
                "ACCOUNT_NOT_FOUND",
                "The account could not be found.");
        }
    }
}
