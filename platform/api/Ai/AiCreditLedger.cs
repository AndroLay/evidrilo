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
        int settledCreditCost,
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
        int settledCreditCost,
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
        int settledCreditCost,
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
    private const int FreeGrantCredits = 20;
    private const int SubscriptionGrantCredits = 200;
    private const string ProjectAiDispatchingMarkerPrefix = "project-ai-dispatching:v1:";
    private const string ProjectAiPreviewMarkerPrefix = "project-ai-preview:v1:";
    private const string ProjectAiSettledMarkerPrefix = "project-ai-settled:v1:";
    private const string ProjectAiSettledOutcomeMarkerPrefix = "project-ai-settled:v2:";
    private static readonly TimeSpan ReservationLease = TimeSpan.FromMinutes(2);
    // Keep a successful preview's hold alive while the student reviews it;
    // ordinary in-flight provider reservations retain the shorter lease.
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

            await ReconcileSubscriptionCreditPeriodsAsync(
                connection,
                transaction,
                accountId,
                now,
                cancellationToken);

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
        if (creditCost is < 1 or > AiCreditPricing.MaximumCreditsPerRequest)
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
                && IsProjectAiSettlementReceiptForRequest(existingReleaseReason, requestHash);
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

            var grantAllocations = await SelectAvailableGrantsAsync(
                connection,
                transaction,
                accountId,
                creditCost,
                cancellationToken);
            if (grantAllocations.Count == 0)
            {
                await transaction.CommitAsync(cancellationToken);
                return null;
            }

            foreach (var allocation in grantAllocations)
            {
                await using var grantCommand = connection.CreateCommand();
                grantCommand.Transaction = transaction;
                grantCommand.CommandText = """
                    update public.ai_credit_grants
                    set reserved_credits = reserved_credits + @allocation_credits
                    where grant_id = @grant_id
                      and credits >= reserved_credits + consumed_credits + @allocation_credits;
                    """;
                grantCommand.Parameters.AddWithValue("grant_id", NpgsqlDbType.Uuid, allocation.GrantId);
                grantCommand.Parameters.AddWithValue("allocation_credits", NpgsqlDbType.Integer, allocation.Credits);
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
            insertReservation.Parameters.AddWithValue("grant_id", NpgsqlDbType.Uuid, grantAllocations[0].GrantId);
            insertReservation.Parameters.AddWithValue("credit_cost", NpgsqlDbType.Integer, creditCost);
            insertReservation.Parameters.AddWithValue("lease_duration", NpgsqlDbType.Interval, ReservationLease);
            await insertReservation.ExecuteNonQueryAsync(cancellationToken);

            for (var index = 0; index < grantAllocations.Count; index++)
            {
                var allocation = grantAllocations[index];
                await using var allocationCommand = connection.CreateCommand();
                allocationCommand.Transaction = transaction;
                allocationCommand.CommandText = """
                    insert into public.ai_credit_reservation_allocations (
                        account_id, request_id, allocation_index, grant_id,
                        reserved_credits, settled_credits
                    ) values (
                        @account_id, @request_id, @allocation_index, @grant_id,
                        @reserved_credits, null
                    );
                    """;
                allocationCommand.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                allocationCommand.Parameters.AddWithValue("request_id", NpgsqlDbType.Text, requestId);
                allocationCommand.Parameters.AddWithValue("allocation_index", NpgsqlDbType.Integer, index);
                allocationCommand.Parameters.AddWithValue("grant_id", NpgsqlDbType.Uuid, allocation.GrantId);
                allocationCommand.Parameters.AddWithValue("reserved_credits", NpgsqlDbType.Integer, allocation.Credits);
                await allocationCommand.ExecuteNonQueryAsync(cancellationToken);
            }

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
        int settledCreditCost,
        CancellationToken cancellationToken)
    {
        if (settledCreditCost is < 0 or > AiCreditPricing.MaximumCreditsPerRequest
            || (!accepted && settledCreditCost != 0))
            throw new ApiException(
                StatusCodes.Status400BadRequest,
                "INVALID_AI_CREDIT_SETTLEMENT",
                "The AI credit settlement is invalid.");

        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);
            await LockAccountRowAsync(connection, transaction, accountId, cancellationToken);
            await ReleaseExpiredReservationsAsync(connection, transaction, accountId, cancellationToken);

            Guid? grantId = null;
            string? status = null;
            int reservedCreditCost = 0;
            await using (var reservationCommand = connection.CreateCommand())
            {
                reservationCommand.Transaction = transaction;
                reservationCommand.CommandText = """
                    select grant_id, status, credit_cost
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
                    reservedCreditCost = reader.GetInt32(2);
                }
            }

            if (grantId is null || status != "reserved")
            {
                await transaction.CommitAsync(cancellationToken);
                return false;
            }
            if (accepted && settledCreditCost > reservedCreditCost)
            {
                await transaction.CommitAsync(cancellationToken);
                return false;
            }

            int? updatedReservedCost = null;
            await using (var reservationCommand = connection.CreateCommand())
            {
                reservationCommand.Transaction = transaction;
                reservationCommand.CommandText = """
                    update public.ai_credit_reservations
                    set status = @status,
                        settled_credit_cost = @settled_credit_cost,
                        completed_at = now(),
                        release_reason = null
                    where account_id = @account_id
                      and request_id = @request_id
                      and status = 'reserved'
                      and lease_expires_at > clock_timestamp()
                    returning credit_cost;
                    """;
                reservationCommand.Parameters.AddWithValue(
                    "status",
                    NpgsqlDbType.Text,
                    accepted ? "consumed" : "released");
                reservationCommand.Parameters.AddWithValue(
                    "settled_credit_cost",
                    NpgsqlDbType.Integer,
                    accepted ? settledCreditCost : 0);
                reservationCommand.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                reservationCommand.Parameters.AddWithValue("request_id", NpgsqlDbType.Text, reservation.RequestId);
                await using var reader = await reservationCommand.ExecuteReaderAsync(cancellationToken);
                if (await reader.ReadAsync(cancellationToken))
                    updatedReservedCost = reader.GetInt32(0);
            }

            if (updatedReservedCost is null)
            {
                await ReleaseExpiredReservationsAsync(connection, transaction, accountId, cancellationToken);
                await transaction.CommitAsync(cancellationToken);
                return false;
            }

            await SettleReservationAllocationsAsync(
                connection,
                transaction,
                accountId,
                reservation.RequestId,
                updatedReservedCost.Value,
                accepted ? settledCreditCost : 0,
                cancellationToken);

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
            || creditCost is < 1 or > AiCreditPricing.MaximumCreditsPerRequest
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
        int settledCreditCost,
        CancellationToken cancellationToken)
    {
        if (accountId == Guid.Empty
            || !IsHash(requestHash)
            || creditCost is < 1 or > AiCreditPricing.MaximumCreditsPerRequest
            || settledCreditCost is < 1 or > AiCreditPricing.MaximumCreditsPerRequest
            || settledCreditCost > creditCost
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
            int? storedSettledCreditCost = null;
            await using (var read = connection.CreateCommand())
            {
                read.Transaction = transaction;
                read.CommandText = """
                    select request_hash, status, credit_cost, settled_credit_cost, release_reason
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
                    storedSettledCreditCost = reader.IsDBNull(3) ? null : reader.GetInt32(3);
                    releaseReason = reader.IsDBNull(4) ? null : reader.GetString(4);
                }
            }

            var dispatchingMarker = ProjectAiDispatchingMarker(requestHash);
            var previewMarker = ProjectAiPreviewMarker(requestHash);
            if (status == "consumed"
                && storedCreditCost == creditCost
                && storedSettledCreditCost == settledCreditCost
                && string.Equals(storedHash, requestHash, StringComparison.Ordinal)
                && releaseReason == previewMarker)
            {
                await transaction.CommitAsync(cancellationToken);
                return true;
            }
            if (status != "reserved"
                || storedCreditCost != creditCost
                || !string.Equals(storedHash, requestHash, StringComparison.Ordinal)
                || (releaseReason != dispatchingMarker && releaseReason != previewMarker))
            {
                await transaction.CommitAsync(cancellationToken);
                return false;
            }

            var expectedMarker = releaseReason == previewMarker ? previewMarker : dispatchingMarker;
            var reservationSettled = false;
            await using (var updateReservation = connection.CreateCommand())
            {
                updateReservation.Transaction = transaction;
                updateReservation.CommandText = """
                update public.ai_credit_reservations
                set status = 'consumed',
                    settled_credit_cost = @settled_credit_cost,
                    completed_at = clock_timestamp(),
                    release_reason = @preview_marker
                where account_id = @account_id
                  and request_id = @request_id
                  and request_hash = @request_hash
                  and credit_cost = @credit_cost
                  and status = 'reserved'
                  and release_reason = @expected_marker
                  and lease_expires_at > clock_timestamp()
                returning request_id;
                """;
                updateReservation.Parameters.AddWithValue("settled_credit_cost", NpgsqlDbType.Integer, settledCreditCost);
                updateReservation.Parameters.AddWithValue("preview_marker", NpgsqlDbType.Text, previewMarker);
                updateReservation.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                updateReservation.Parameters.AddWithValue("request_id", NpgsqlDbType.Text, reservation.RequestId);
                updateReservation.Parameters.AddWithValue("request_hash", NpgsqlDbType.Text, requestHash);
                updateReservation.Parameters.AddWithValue("credit_cost", NpgsqlDbType.Integer, creditCost);
                updateReservation.Parameters.AddWithValue("expected_marker", NpgsqlDbType.Text, expectedMarker);
                var value = await updateReservation.ExecuteScalarAsync(cancellationToken);
                reservationSettled = value is string;
            }

            if (!reservationSettled)
            {
                await ReleaseExpiredReservationsAsync(connection, transaction, accountId, cancellationToken);
                await transaction.CommitAsync(cancellationToken);
                return false;
            }

            await SettleReservationAllocationsAsync(
                connection,
                transaction,
                accountId,
                reservation.RequestId,
                creditCost,
                settledCreditCost,
                cancellationToken);

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
            int? settledCreditCost = null;
            await using (var read = connection.CreateCommand())
            {
                read.Transaction = transaction;
                read.CommandText = """
                    select grant_id, request_hash, status, credit_cost, settled_credit_cost, release_reason
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
                    settledCreditCost = reader.IsDBNull(4) ? null : reader.GetInt32(4);
                    releaseReason = reader.IsDBNull(5) ? null : reader.GetString(5);
                }
            }

            if (grantId is null || requestHash is null)
            {
                await transaction.CommitAsync(cancellationToken);
                return new ProjectAiCreditSettlementResult(ProjectAiCreditSettlementStatus.NotFound);
            }

            if (status == "consumed"
                && settledCreditCost is > 0
                && releaseReason == ProjectAiPreviewMarker(requestHash))
            {
                await using var updatePreview = connection.CreateCommand();
                updatePreview.Transaction = transaction;
                updatePreview.CommandText = """
                    update public.ai_credit_reservations
                    set request_hash = @settlement_hash,
                        release_reason = @settled_marker
                    where account_id = @account_id
                      and request_id = @request_id
                      and request_hash = @original_request_hash
                      and status = 'consumed'
                      and release_reason = @preview_marker;
                    """;
                updatePreview.Parameters.AddWithValue("settlement_hash", NpgsqlDbType.Text, settlementHash);
                updatePreview.Parameters.AddWithValue("settled_marker", NpgsqlDbType.Text, ProjectAiSettledRequestMarker(requestHash, apply));
                updatePreview.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                updatePreview.Parameters.AddWithValue("request_id", NpgsqlDbType.Text, requestId);
                updatePreview.Parameters.AddWithValue("original_request_hash", NpgsqlDbType.Text, requestHash);
                updatePreview.Parameters.AddWithValue("preview_marker", NpgsqlDbType.Text, ProjectAiPreviewMarker(requestHash));
                if (await updatePreview.ExecuteNonQueryAsync(cancellationToken) != 1)
                {
                    await transaction.CommitAsync(cancellationToken);
                    return new ProjectAiCreditSettlementResult(ProjectAiCreditSettlementStatus.Conflict);
                }

                await transaction.CommitAsync(cancellationToken);
                return new ProjectAiCreditSettlementResult(
                    apply ? ProjectAiCreditSettlementStatus.Applied : ProjectAiCreditSettlementStatus.Dismissed,
                    settledCreditCost.Value);
            }

            if (status is "consumed" or "released")
            {
                var sameSettlement = string.Equals(requestHash, settlementHash, StringComparison.Ordinal)
                    && ((status == (apply ? "consumed" : "released")
                            && IsProjectAiSettledRequestMarker(releaseReason))
                        || (status == "consumed"
                            && IsProjectAiSettledOutcomeMarker(releaseReason, apply)));
                await transaction.CommitAsync(cancellationToken);
                return sameSettlement
                    ? new ProjectAiCreditSettlementResult(
                        apply ? ProjectAiCreditSettlementStatus.Applied : ProjectAiCreditSettlementStatus.Dismissed,
                        settledCreditCost ?? creditCost)
                    : new ProjectAiCreditSettlementResult(ProjectAiCreditSettlementStatus.Conflict);
            }

            if (creditCost is < 1 or > AiCreditPricing.MaximumCreditsPerRequest)
            {
                await transaction.CommitAsync(cancellationToken);
                return new ProjectAiCreditSettlementResult(ProjectAiCreditSettlementStatus.Conflict);
            }

            // Finish a preview created before token-based settlement was
            // deployed. New previews are already charged before they are sent.
            if (status != "reserved" || releaseReason != ProjectAiPreviewMarker(requestHash))
            {
                await transaction.CommitAsync(cancellationToken);
                return new ProjectAiCreditSettlementResult(ProjectAiCreditSettlementStatus.Conflict);
            }

            await using (var updateReservation = connection.CreateCommand())
            {
                updateReservation.Transaction = transaction;
                updateReservation.CommandText = """
                    update public.ai_credit_reservations
                    set status = 'consumed',
                        settled_credit_cost = credit_cost,
                        request_hash = @settlement_hash,
                        completed_at = clock_timestamp(),
                        release_reason = @settled_marker
                    where account_id = @account_id
                      and request_id = @request_id
                      and status = 'reserved'
                      and request_hash = @original_request_hash
                      and release_reason = @preview_marker
                      and lease_expires_at > clock_timestamp()
                    returning request_id;
                    """;
                updateReservation.Parameters.AddWithValue("settlement_hash", NpgsqlDbType.Text, settlementHash);
                updateReservation.Parameters.AddWithValue("settled_marker", NpgsqlDbType.Text, ProjectAiSettledRequestMarker(requestHash, apply));
                updateReservation.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                updateReservation.Parameters.AddWithValue("request_id", NpgsqlDbType.Text, requestId);
                updateReservation.Parameters.AddWithValue("original_request_hash", NpgsqlDbType.Text, requestHash);
                updateReservation.Parameters.AddWithValue("preview_marker", NpgsqlDbType.Text, ProjectAiPreviewMarker(requestHash));
                var value = await updateReservation.ExecuteScalarAsync(cancellationToken);
                if (value is not string)
                {
                    await ReleaseExpiredReservationsAsync(connection, transaction, accountId, cancellationToken);
                    await transaction.CommitAsync(cancellationToken);
                    return new ProjectAiCreditSettlementResult(ProjectAiCreditSettlementStatus.Conflict);
                }
            }

            await SettleReservationAllocationsAsync(
                connection,
                transaction,
                accountId,
                requestId,
                creditCost,
                creditCost,
                cancellationToken);

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

            // Paid credit entitlement is earned by subscription. AI data consent
            // still gates spending/provider requests, not accounting for purchases.
            await ReconcileSubscriptionCreditPeriodsAsync(
                connection,
                transaction,
                accountId,
                now,
                cancellationToken);

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
                    order by starts_at, grant_kind, grant_key;
                    """;
                grantCommand.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                grantCommand.Parameters.AddWithValue("now", NpgsqlDbType.TimestampTz, now);
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

    private static async Task ReconcileSubscriptionCreditPeriodsAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        DateTimeOffset now,
        CancellationToken cancellationToken)
    {
        var events = new List<SubscriptionCreditPeriod>();
        await using (var command = connection.CreateCommand())
        {
            command.Transaction = transaction;
            command.CommandText = """
                select status, occurred_at, period_started_at, period_expires_at
                from public.entitlement_events
                where account_id = @account_id
                  and entitlement = @entitlement
                order by occurred_at, provider_event_id;
                """;
            command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
            command.Parameters.AddWithValue("entitlement", NpgsqlDbType.Text, Entitlement);
            await using var reader = await command.ExecuteReaderAsync(cancellationToken);
            while (await reader.ReadAsync(cancellationToken))
            {
                events.Add(new SubscriptionCreditPeriod(
                    reader.GetString(0),
                    reader.GetFieldValue<DateTimeOffset>(1),
                    reader.IsDBNull(2) ? null : reader.GetFieldValue<DateTimeOffset>(2),
                    reader.IsDBNull(3) ? null : reader.GetFieldValue<DateTimeOffset>(3)));
            }
        }

        var orderedStarts = AiSubscriptionCreditSchedule.GrantStarts(events, now).ToArray();
        if (orderedStarts.Length == 0) return;

        var grantKeys = orderedStarts.Select(PeriodGrantKey).ToArray();
        await using var insert = connection.CreateCommand();
        insert.Transaction = transaction;
        insert.CommandText = """
            insert into public.ai_credit_grants (
                account_id, grant_kind, grant_key, credits,
                reserved_credits, consumed_credits, starts_at, expires_at
            )
            select @account_id, @grant_kind, earned.grant_key, @credits,
                   0, 0, earned.starts_at, null
            from unnest(@grant_keys, @starts_at) as earned(grant_key, starts_at)
            on conflict (account_id, grant_kind, grant_key) do nothing;
            """;
        insert.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        insert.Parameters.AddWithValue("grant_kind", NpgsqlDbType.Text, SubscriptionGrantKind);
        insert.Parameters.AddWithValue("credits", NpgsqlDbType.Integer, SubscriptionGrantCredits);
        insert.Parameters.Add(new NpgsqlParameter
        {
            ParameterName = "grant_keys",
            NpgsqlDbType = NpgsqlDbType.Array | NpgsqlDbType.Text,
            Value = grantKeys,
        });
        insert.Parameters.Add(new NpgsqlParameter
        {
            ParameterName = "starts_at",
            NpgsqlDbType = NpgsqlDbType.Array | NpgsqlDbType.TimestampTz,
            Value = orderedStarts,
        });
        await insert.ExecuteNonQueryAsync(cancellationToken);
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

    private static string ProjectAiSettledRequestMarker(string originalRequestHash, bool applied) =>
        ProjectAiSettledOutcomeMarkerPrefix
        + originalRequestHash
        + (applied ? ":applied" : ":dismissed");

    private static bool IsProjectAiSettledRequestMarker(string? marker) =>
        marker is not null
        && marker.StartsWith(ProjectAiSettledMarkerPrefix, StringComparison.Ordinal)
        && IsHash(marker[ProjectAiSettledMarkerPrefix.Length..]);

    private static bool IsProjectAiSettledOutcomeMarker(string? marker, bool applied)
    {
        var outcomeSuffix = applied ? ":applied" : ":dismissed";
        return marker is not null
            && marker.StartsWith(ProjectAiSettledOutcomeMarkerPrefix, StringComparison.Ordinal)
            && marker.Length == ProjectAiSettledOutcomeMarkerPrefix.Length + 64 + outcomeSuffix.Length
            && marker.EndsWith(outcomeSuffix, StringComparison.Ordinal)
            && IsHash(marker[
                ProjectAiSettledOutcomeMarkerPrefix.Length..
                    (ProjectAiSettledOutcomeMarkerPrefix.Length + 64)]);
    }

    private static bool IsProjectAiSettlementReceiptForRequest(string? marker, string requestHash) =>
        string.Equals(marker, ProjectAiSettledRequestMarker(requestHash), StringComparison.Ordinal)
        || string.Equals(marker, ProjectAiSettledRequestMarker(requestHash, applied: true), StringComparison.Ordinal)
        || string.Equals(marker, ProjectAiSettledRequestMarker(requestHash, applied: false), StringComparison.Ordinal);

    private static async Task SettleReservationAllocationsAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        string requestId,
        int expectedReservedCredits,
        int settledCredits,
        CancellationToken cancellationToken)
    {
        var allocations = new List<ReservedCreditAllocation>();
        await using (var read = connection.CreateCommand())
        {
            read.Transaction = transaction;
            read.CommandText = """
                select allocation_index, grant_id, reserved_credits, settled_credits
                from public.ai_credit_reservation_allocations
                where account_id = @account_id and request_id = @request_id
                order by allocation_index
                for update;
                """;
            read.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
            read.Parameters.AddWithValue("request_id", NpgsqlDbType.Text, requestId);
            await using var reader = await read.ExecuteReaderAsync(cancellationToken);
            while (await reader.ReadAsync(cancellationToken))
            {
                if (!reader.IsDBNull(3))
                    throw InvalidCreditLedger();
                allocations.Add(new ReservedCreditAllocation(
                    reader.GetInt32(0),
                    reader.GetGuid(1),
                    reader.GetInt32(2)));
            }
        }

        if (allocations.Count == 0
            || allocations.Sum(allocation => allocation.Credits) != expectedReservedCredits
            || settledCredits < 0
            || settledCredits > expectedReservedCredits)
            throw InvalidCreditLedger();

        var remaining = settledCredits;
        var settledByGrant = allocations.Select(allocation =>
        {
            var settled = Math.Min(allocation.Credits, remaining);
            remaining -= settled;
            return settled;
        }).ToArray();
        if (remaining != 0)
            throw InvalidCreditLedger();

        await using var update = connection.CreateCommand();
        update.Transaction = transaction;
        update.CommandText = """
            with planned as (
                select *
                from unnest(
                    @allocation_indices,
                    @grant_ids,
                    @reserved_credits,
                    @settled_credits
                ) as item(allocation_index, grant_id, reserved_credits, settled_credits)
            ),
            updated_allocations as (
                update public.ai_credit_reservation_allocations allocation
                set settled_credits = planned.settled_credits
                from planned
                where allocation.account_id = @account_id
                  and allocation.request_id = @request_id
                  and allocation.allocation_index = planned.allocation_index
                  and allocation.grant_id = planned.grant_id
                  and allocation.reserved_credits = planned.reserved_credits
                  and allocation.settled_credits is null
                returning allocation.grant_id, allocation.reserved_credits, allocation.settled_credits
            ),
            adjusted_grants as (
                update public.ai_credit_grants credit_grant
                set reserved_credits = credit_grant.reserved_credits - allocation.reserved_credits,
                    consumed_credits = credit_grant.consumed_credits + allocation.settled_credits
                from updated_allocations allocation
                where credit_grant.grant_id = allocation.grant_id
                  and credit_grant.reserved_credits >= allocation.reserved_credits
                returning credit_grant.grant_id
            )
            select
                (select count(*) from updated_allocations),
                (select count(*) from adjusted_grants);
            """;
        update.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        update.Parameters.AddWithValue("request_id", NpgsqlDbType.Text, requestId);
        update.Parameters.Add(new NpgsqlParameter
        {
            ParameterName = "allocation_indices",
            NpgsqlDbType = NpgsqlDbType.Array | NpgsqlDbType.Integer,
            Value = allocations.Select(allocation => allocation.Index).ToArray(),
        });
        update.Parameters.Add(new NpgsqlParameter
        {
            ParameterName = "grant_ids",
            NpgsqlDbType = NpgsqlDbType.Array | NpgsqlDbType.Uuid,
            Value = allocations.Select(allocation => allocation.GrantId).ToArray(),
        });
        update.Parameters.Add(new NpgsqlParameter
        {
            ParameterName = "reserved_credits",
            NpgsqlDbType = NpgsqlDbType.Array | NpgsqlDbType.Integer,
            Value = allocations.Select(allocation => allocation.Credits).ToArray(),
        });
        update.Parameters.Add(new NpgsqlParameter
        {
            ParameterName = "settled_credits",
            NpgsqlDbType = NpgsqlDbType.Array | NpgsqlDbType.Integer,
            Value = settledByGrant,
        });
        await using var result = await update.ExecuteReaderAsync(cancellationToken);
        if (!await result.ReadAsync(cancellationToken)
            || result.GetInt64(0) != allocations.Count
            || result.GetInt64(1) != allocations.Count)
            throw InvalidCreditLedger();
    }

    private static ApiException InvalidCreditLedger() => new(
        StatusCodes.Status503ServiceUnavailable,
        "AI_CREDIT_LEDGER_CORRUPT",
        "The AI credit ledger could not reconcile a reservation.");

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
                select account_id, request_id
                from public.ai_credit_reservations
                where account_id = @account_id
                  and status = 'reserved'
                  and lease_expires_at <= clock_timestamp()
                for update
            ),
            expired_allocations as materialized (
                select allocation.account_id, allocation.request_id,
                       allocation.allocation_index, allocation.grant_id,
                       allocation.reserved_credits
                from public.ai_credit_reservation_allocations allocation
                join expired using (account_id, request_id)
                for update of allocation
            ),
            released as (
                update public.ai_credit_reservations reservation
                set status = 'released',
                    settled_credit_cost = 0,
                    completed_at = clock_timestamp(),
                    release_reason = 'lease_expired'
                from expired
                where reservation.account_id = expired.account_id
                  and reservation.request_id = expired.request_id
                  and reservation.status = 'reserved'
                  and reservation.lease_expires_at <= clock_timestamp()
                returning reservation.account_id, reservation.request_id
            ),
            released_allocations as (
                update public.ai_credit_reservation_allocations allocation
                set settled_credits = 0
                from expired_allocations expected
                join released using (account_id, request_id)
                where allocation.account_id = expected.account_id
                  and allocation.request_id = expected.request_id
                  and allocation.allocation_index = expected.allocation_index
                  and allocation.settled_credits is null
                returning allocation.grant_id, allocation.reserved_credits
            ),
            grant_costs as (
                select grant_id, sum(reserved_credits)::integer as released_credit_cost
                from released_allocations
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
                (select count(*) from expired_allocations),
                (select count(*) from released_allocations),
                (select count(*) from released
                  where not exists (
                      select 1 from public.ai_credit_reservation_allocations allocation
                      where allocation.account_id = released.account_id
                        and allocation.request_id = released.request_id
                  )),
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
        var expectedAllocationCount = reader.GetInt64(2);
        var releasedAllocationCount = reader.GetInt64(3);
        var missingReservationAllocationCount = reader.GetInt64(4);
        var grantCount = reader.GetInt64(5);
        var adjustedGrantCount = reader.GetInt64(6);
        if (expiredCount != releasedCount
            || expectedAllocationCount != releasedAllocationCount
            || missingReservationAllocationCount != 0
            || grantCount != adjustedGrantCount)
            throw new ApiException(
                StatusCodes.Status503ServiceUnavailable,
                "AI_CREDIT_LEDGER_CORRUPT",
                "The AI credit ledger could not recover expired reservations.");
    }

    private sealed record CreditGrantAllocation(Guid GrantId, int Credits);
    private sealed record ReservedCreditAllocation(int Index, Guid GrantId, int Credits);

    private static async Task<IReadOnlyList<CreditGrantAllocation>> SelectAvailableGrantsAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        int creditCost,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            select grant_id, credits - reserved_credits - consumed_credits as available_credits
            from public.ai_credit_grants
            where account_id = @account_id
              and starts_at <= now()
              and (expires_at is null or expires_at > now())
              and credits > reserved_credits + consumed_credits
            order by starts_at, grant_kind, grant_key
            for update;
            """;
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        var allocations = new List<CreditGrantAllocation>();
        var remaining = creditCost;
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        while (remaining > 0 && await reader.ReadAsync(cancellationToken))
        {
            var grantId = reader.GetGuid(0);
            var available = reader.GetInt32(1);
            var reserved = Math.Min(remaining, available);
            if (reserved <= 0)
                continue;
            allocations.Add(new CreditGrantAllocation(grantId, reserved));
            remaining -= reserved;
        }

        return remaining == 0 ? allocations : Array.Empty<CreditGrantAllocation>();
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
        command.CommandText = "select public.lock_runtime_account(@account_id);";
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        if (await command.ExecuteScalarAsync(cancellationToken) is not true)
        {
            throw new ApiException(
                StatusCodes.Status404NotFound,
                "ACCOUNT_NOT_FOUND",
                "The account could not be found.");
        }
    }
}
