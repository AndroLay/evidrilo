using Evidrilo.Api.Common;
using Npgsql;
using NpgsqlTypes;

namespace Evidrilo.Api.Ai;

public sealed record AiConversationSession(
    Guid SessionId,
    string CaseVersionId,
    string ContextFingerprint,
    int TurnsUsed,
    DateTimeOffset CreatedAt,
    DateTimeOffset ExpiresAt);

public sealed record AiConversationTurnReservation(
    Guid SessionId,
    string RequestId,
    string RequestHash,
    int TurnIndex);

public interface IAiConversationStore
{
    Task<AiConversationSession> CreateAsync(
        Guid accountId,
        string creationRequestId,
        string caseVersionId,
        string contextFingerprint,
        CancellationToken cancellationToken);

    Task<AiConversationTurnReservation> ReserveTurnAsync(
        Guid accountId,
        Guid sessionId,
        string requestId,
        string requestHash,
        string contextFingerprint,
        CancellationToken cancellationToken);

    Task<bool> CompleteTurnAsync(
        Guid accountId,
        AiConversationTurnReservation reservation,
        bool accepted,
        CancellationToken cancellationToken);

    Task<bool> ClearAsync(Guid accountId, Guid sessionId, CancellationToken cancellationToken);
}

public sealed class DatabaseUnavailableAiConversationStore : IAiConversationStore
{
    public Task<AiConversationSession> CreateAsync(
        Guid accountId,
        string creationRequestId,
        string caseVersionId,
        string contextFingerprint,
        CancellationToken cancellationToken) => throw NotConfigured();

    public Task<AiConversationTurnReservation> ReserveTurnAsync(
        Guid accountId,
        Guid sessionId,
        string requestId,
        string requestHash,
        string contextFingerprint,
        CancellationToken cancellationToken) => throw NotConfigured();

    public Task<bool> CompleteTurnAsync(
        Guid accountId,
        AiConversationTurnReservation reservation,
        bool accepted,
        CancellationToken cancellationToken) => throw NotConfigured();

    public Task<bool> ClearAsync(Guid accountId, Guid sessionId, CancellationToken cancellationToken) =>
        throw NotConfigured();

    private static ApiException NotConfigured() => new(
        StatusCodes.Status503ServiceUnavailable,
        "DATABASE_NOT_CONFIGURED",
        "AI conversations are not configured.");
}

public sealed class NpgsqlAiConversationStore : IAiConversationStore, IDisposable
{
    public const int TurnLimit = 5;
    public static readonly TimeSpan SessionLifetime = TimeSpan.FromMinutes(30);
    private static readonly TimeSpan TurnLease = TimeSpan.FromMinutes(2);
    private readonly NpgsqlDataSource dataSource;

    public NpgsqlAiConversationStore(string connectionString)
    {
        dataSource = NpgsqlDataSource.Create(connectionString);
    }

    public async Task<AiConversationSession> CreateAsync(
        Guid accountId,
        string creationRequestId,
        string caseVersionId,
        string contextFingerprint,
        CancellationToken cancellationToken)
    {
        ValidateIdentifiers(accountId, creationRequestId, caseVersionId, contextFingerprint);
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);
            await LockAccountRowAsync(connection, transaction, accountId, cancellationToken);

            await using (var cleanup = connection.CreateCommand())
            {
                cleanup.Transaction = transaction;
                cleanup.CommandText = """
                    with expired_sessions as (
                        select session_id
                        from public.ai_conversation_sessions
                        where account_id = @account_id and expires_at <= now()
                        order by expires_at
                        limit 100
                        for update skip locked
                    )
                    delete from public.ai_conversation_sessions as session
                    using expired_sessions
                    where session.session_id = expired_sessions.session_id
                      and session.account_id = @account_id;
                    """;
                cleanup.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                await cleanup.ExecuteNonQueryAsync(cancellationToken);
            }

            var newSessionId = Guid.NewGuid();
            await using (var insert = connection.CreateCommand())
            {
                insert.Transaction = transaction;
                insert.CommandText = """
                    insert into public.ai_conversation_sessions (
                        session_id, account_id, creation_request_id, case_version_id,
                        context_fingerprint, created_at, expires_at
                    ) values (
                        @session_id, @account_id, @request_id, @case_version_id,
                        @context_fingerprint, now(), now() + @session_lifetime
                    ) on conflict (account_id, creation_request_id) do nothing;
                    """;
                insert.Parameters.AddWithValue("session_id", NpgsqlDbType.Uuid, newSessionId);
                insert.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                insert.Parameters.AddWithValue("request_id", NpgsqlDbType.Text, creationRequestId);
                insert.Parameters.AddWithValue("case_version_id", NpgsqlDbType.Text, caseVersionId);
                insert.Parameters.AddWithValue("context_fingerprint", NpgsqlDbType.Text, contextFingerprint);
                insert.Parameters.AddWithValue("session_lifetime", NpgsqlDbType.Interval, SessionLifetime);
                await insert.ExecuteNonQueryAsync(cancellationToken);
            }

            AiConversationSession? session;
            await using (var select = connection.CreateCommand())
            {
                select.Transaction = transaction;
                select.CommandText = """
                    select session_id, case_version_id, context_fingerprint, turn_count,
                           created_at, expires_at
                    from public.ai_conversation_sessions
                    where account_id = @account_id and creation_request_id = @request_id
                    for update;
                    """;
                select.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                select.Parameters.AddWithValue("request_id", NpgsqlDbType.Text, creationRequestId);
                await using var reader = await select.ExecuteReaderAsync(cancellationToken);
                session = await reader.ReadAsync(cancellationToken)
                    ? new AiConversationSession(
                        reader.GetGuid(0),
                        reader.GetString(1),
                        reader.GetString(2),
                        reader.GetInt16(3),
                        reader.GetFieldValue<DateTimeOffset>(4),
                        reader.GetFieldValue<DateTimeOffset>(5))
                    : null;
            }

            if (session is null)
                throw new ApiException(StatusCodes.Status503ServiceUnavailable, "AI_CONVERSATION_UNAVAILABLE", "The conversation could not be started.");
            if (!string.Equals(session.CaseVersionId, caseVersionId, StringComparison.Ordinal)
                || !string.Equals(session.ContextFingerprint, contextFingerprint, StringComparison.Ordinal))
                throw new ApiException(StatusCodes.Status409Conflict, "AI_IDEMPOTENCY_KEY_REUSE", "The operation identifier was reused with different context.");

            await transaction.CommitAsync(cancellationToken);
            return session;
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
            throw DatabaseUnavailable("AI conversation session is temporarily unavailable.", exception);
        }
    }

    public async Task<AiConversationTurnReservation> ReserveTurnAsync(
        Guid accountId,
        Guid sessionId,
        string requestId,
        string requestHash,
        string contextFingerprint,
        CancellationToken cancellationToken)
    {
        ValidateRequestIdentifier(accountId, requestId);
        if (sessionId == Guid.Empty || !IsHash(requestHash) || !IsHash(contextFingerprint))
            throw new ApiException(StatusCodes.Status400BadRequest, "INVALID_AI_CONVERSATION", "The conversation request is invalid.");

        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);
            await LockAccountRowAsync(connection, transaction, accountId, cancellationToken);

            string? activeRequestId;
            string? activeRequestHash;
            short? activeTurnIndex;
            DateTimeOffset? activeLeaseExpiresAt;
            short turnCount;
            string storedFingerprint;
            DateTimeOffset expiresAt;
            await using (var select = connection.CreateCommand())
            {
                select.Transaction = transaction;
                select.CommandText = """
                    select context_fingerprint, turn_count, expires_at,
                           active_request_id, active_request_hash,
                           active_turn_index, active_lease_expires_at
                    from public.ai_conversation_sessions
                    where account_id = @account_id and session_id = @session_id
                    for update;
                    """;
                select.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                select.Parameters.AddWithValue("session_id", NpgsqlDbType.Uuid, sessionId);
                await using var reader = await select.ExecuteReaderAsync(cancellationToken);
                if (!await reader.ReadAsync(cancellationToken))
                    throw new ApiException(StatusCodes.Status404NotFound, "AI_CONVERSATION_NOT_FOUND", "The conversation is no longer available.");
                storedFingerprint = reader.GetString(0);
                turnCount = reader.GetInt16(1);
                expiresAt = reader.GetFieldValue<DateTimeOffset>(2);
                activeRequestId = reader.IsDBNull(3) ? null : reader.GetString(3);
                activeRequestHash = reader.IsDBNull(4) ? null : reader.GetString(4);
                activeTurnIndex = reader.IsDBNull(5) ? null : reader.GetInt16(5);
                activeLeaseExpiresAt = reader.IsDBNull(6) ? null : reader.GetFieldValue<DateTimeOffset>(6);
            }

            if (expiresAt <= DateTimeOffset.UtcNow)
                throw new ApiException(StatusCodes.Status410Gone, "AI_CONVERSATION_EXPIRED", "The conversation has expired.");
            if (!string.Equals(storedFingerprint, contextFingerprint, StringComparison.Ordinal))
                throw new ApiException(StatusCodes.Status409Conflict, "AI_CONTEXT_STALE", "The project context changed. Start a new conversation.");

            var priorRequest = await ReadTurnRequestAsync(connection, transaction, accountId, requestId, cancellationToken);
            if (priorRequest is not null)
            {
                if (!string.Equals(priorRequest.Value.RequestHash, requestHash, StringComparison.Ordinal)
                    || priorRequest.Value.SessionId != sessionId)
                    throw new ApiException(StatusCodes.Status409Conflict, "AI_IDEMPOTENCY_KEY_REUSE", "The operation identifier was reused with different input.");
                if (priorRequest.Value.Status == "reserved")
                    throw new ApiException(StatusCodes.Status409Conflict, "AI_REQUEST_IN_PROGRESS", "This AI request is already being processed.");
                throw new ApiException(StatusCodes.Status409Conflict, "AI_IDEMPOTENCY_REPLAY", "This AI request has already been processed. Start a new request to try again.");
            }

            if (activeRequestId is not null)
            {
                if (activeLeaseExpiresAt > DateTimeOffset.UtcNow)
                    throw new ApiException(StatusCodes.Status409Conflict, "AI_REQUEST_IN_PROGRESS", "Another conversation turn is being processed.");
                if (activeTurnIndex is null)
                    throw new ApiException(StatusCodes.Status503ServiceUnavailable, "AI_CONVERSATION_UNAVAILABLE", "The conversation could not be continued.");
                await ReleaseTurnRequestAsync(connection, transaction, accountId, activeRequestId, cancellationToken);
                await ClearActiveTurnAsync(connection, transaction, accountId, sessionId, cancellationToken);
            }

            if (turnCount >= TurnLimit)
                throw new ApiException(StatusCodes.Status409Conflict, "AI_CONVERSATION_TURN_LIMIT", "This conversation has reached its turn limit.");

            var nextTurn = checked((short)(turnCount + 1));
            await using (var insert = connection.CreateCommand())
            {
                insert.Transaction = transaction;
                insert.CommandText = """
                    insert into public.ai_conversation_turn_requests (
                        account_id, session_id, request_id, request_hash,
                        turn_index, status, started_at, lease_expires_at
                    ) values (
                        @account_id, @session_id, @request_id, @request_hash,
                        @turn_index, 'reserved', now(), now() + @turn_lease
                    );
                    """;
                insert.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                insert.Parameters.AddWithValue("session_id", NpgsqlDbType.Uuid, sessionId);
                insert.Parameters.AddWithValue("request_id", NpgsqlDbType.Text, requestId);
                insert.Parameters.AddWithValue("request_hash", NpgsqlDbType.Text, requestHash);
                insert.Parameters.AddWithValue("turn_index", NpgsqlDbType.Smallint, nextTurn);
                insert.Parameters.AddWithValue("turn_lease", NpgsqlDbType.Interval, TurnLease);
                await insert.ExecuteNonQueryAsync(cancellationToken);
            }

            await using (var update = connection.CreateCommand())
            {
                update.Transaction = transaction;
                update.CommandText = """
                    update public.ai_conversation_sessions
                    set active_request_id = @request_id,
                        active_request_hash = @request_hash,
                        active_turn_index = @turn_index,
                        active_lease_expires_at = now() + @turn_lease,
                        updated_at = now()
                    where account_id = @account_id and session_id = @session_id;
                    """;
                update.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                update.Parameters.AddWithValue("session_id", NpgsqlDbType.Uuid, sessionId);
                update.Parameters.AddWithValue("request_id", NpgsqlDbType.Text, requestId);
                update.Parameters.AddWithValue("request_hash", NpgsqlDbType.Text, requestHash);
                update.Parameters.AddWithValue("turn_index", NpgsqlDbType.Smallint, nextTurn);
                update.Parameters.AddWithValue("turn_lease", NpgsqlDbType.Interval, TurnLease);
                await update.ExecuteNonQueryAsync(cancellationToken);
            }

            await transaction.CommitAsync(cancellationToken);
            return new AiConversationTurnReservation(sessionId, requestId, requestHash, nextTurn);
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
            throw DatabaseUnavailable("AI conversation turn is temporarily unavailable.", exception);
        }
    }

    public async Task<bool> CompleteTurnAsync(
        Guid accountId,
        AiConversationTurnReservation reservation,
        bool accepted,
        CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);
            await LockAccountRowAsync(connection, transaction, accountId, cancellationToken);

            bool isActive;
            await using (var select = connection.CreateCommand())
            {
                select.Transaction = transaction;
                select.CommandText = """
                    select exists (
                        select 1 from public.ai_conversation_sessions
                        where account_id = @account_id and session_id = @session_id
                          and active_request_id = @request_id
                          and active_request_hash = @request_hash
                          and active_turn_index = @turn_index
                          and active_lease_expires_at > now()
                          and expires_at > now()
                    ) and exists (
                        select 1 from public.ai_conversation_turn_requests
                        where account_id = @account_id and session_id = @session_id
                          and request_id = @request_id and request_hash = @request_hash
                          and turn_index = @turn_index and status = 'reserved'
                    );
                    """;
                select.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                select.Parameters.AddWithValue("session_id", NpgsqlDbType.Uuid, reservation.SessionId);
                select.Parameters.AddWithValue("request_id", NpgsqlDbType.Text, reservation.RequestId);
                select.Parameters.AddWithValue("request_hash", NpgsqlDbType.Text, reservation.RequestHash);
                select.Parameters.AddWithValue("turn_index", NpgsqlDbType.Smallint, reservation.TurnIndex);
                isActive = (bool)(await select.ExecuteScalarAsync(cancellationToken) ?? false);
            }

            if (!isActive)
            {
                await transaction.CommitAsync(cancellationToken);
                return false;
            }

            await using (var requestUpdate = connection.CreateCommand())
            {
                requestUpdate.Transaction = transaction;
                requestUpdate.CommandText = """
                    update public.ai_conversation_turn_requests
                    set status = @status, completed_at = now()
                    where account_id = @account_id and request_id = @request_id
                      and request_hash = @request_hash and status = 'reserved';
                    """;
                requestUpdate.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                requestUpdate.Parameters.AddWithValue("request_id", NpgsqlDbType.Text, reservation.RequestId);
                requestUpdate.Parameters.AddWithValue("request_hash", NpgsqlDbType.Text, reservation.RequestHash);
                requestUpdate.Parameters.AddWithValue("status", NpgsqlDbType.Text, accepted ? "completed" : "released");
                await requestUpdate.ExecuteNonQueryAsync(cancellationToken);
            }

            await using (var sessionUpdate = connection.CreateCommand())
            {
                sessionUpdate.Transaction = transaction;
                sessionUpdate.CommandText = """
                    update public.ai_conversation_sessions
                    set turn_count = case when @accepted then @turn_index else turn_count end,
                        active_request_id = null,
                        active_request_hash = null,
                        active_turn_index = null,
                        active_lease_expires_at = null,
                        updated_at = now()
                    where account_id = @account_id and session_id = @session_id
                      and active_request_id = @request_id and active_request_hash = @request_hash;
                    """;
                sessionUpdate.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                sessionUpdate.Parameters.AddWithValue("session_id", NpgsqlDbType.Uuid, reservation.SessionId);
                sessionUpdate.Parameters.AddWithValue("request_id", NpgsqlDbType.Text, reservation.RequestId);
                sessionUpdate.Parameters.AddWithValue("request_hash", NpgsqlDbType.Text, reservation.RequestHash);
                sessionUpdate.Parameters.AddWithValue("turn_index", NpgsqlDbType.Smallint, reservation.TurnIndex);
                sessionUpdate.Parameters.AddWithValue("accepted", NpgsqlDbType.Boolean, accepted);
                await sessionUpdate.ExecuteNonQueryAsync(cancellationToken);
            }

            await transaction.CommitAsync(cancellationToken);
            return true;
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
            throw DatabaseUnavailable("AI conversation settlement is temporarily unavailable.", exception);
        }
    }

    public async Task<bool> ClearAsync(Guid accountId, Guid sessionId, CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);
            await LockAccountRowAsync(connection, transaction, accountId, cancellationToken);

            await using (var activeTurn = connection.CreateCommand())
            {
                activeTurn.Transaction = transaction;
                activeTurn.CommandText = """
                    select exists (
                        select 1 from public.ai_conversation_sessions
                        where account_id = @account_id and session_id = @session_id
                          and active_request_id is not null
                    );
                    """;
                activeTurn.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                activeTurn.Parameters.AddWithValue("session_id", NpgsqlDbType.Uuid, sessionId);
                if ((bool)(await activeTurn.ExecuteScalarAsync(cancellationToken) ?? false))
                    throw new ApiException(
                        StatusCodes.Status409Conflict,
                        "AI_REQUEST_IN_PROGRESS",
                        "Wait for the current conversation turn to finish before clearing it.");
            }

            await using var command = connection.CreateCommand();
            command.Transaction = transaction;
            command.CommandText = "delete from public.ai_conversation_sessions where account_id = @account_id and session_id = @session_id;";
            command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
            command.Parameters.AddWithValue("session_id", NpgsqlDbType.Uuid, sessionId);
            var deleted = await command.ExecuteNonQueryAsync(cancellationToken) == 1;
            await transaction.CommitAsync(cancellationToken);
            return deleted;
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
            throw DatabaseUnavailable("AI conversation data is temporarily unavailable.", exception);
        }
    }

    public void Dispose() => dataSource.Dispose();

    private static async Task<(Guid SessionId, string RequestHash, string Status)?> ReadTurnRequestAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        string requestId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            select session_id, request_hash, status
            from public.ai_conversation_turn_requests
            where account_id = @account_id and request_id = @request_id
            for update;
            """;
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        command.Parameters.AddWithValue("request_id", NpgsqlDbType.Text, requestId);
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        return await reader.ReadAsync(cancellationToken)
            ? (reader.GetGuid(0), reader.GetString(1), reader.GetString(2))
            : null;
    }

    private static async Task ReleaseTurnRequestAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        string requestId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            update public.ai_conversation_turn_requests
            set status = 'released', completed_at = now()
            where account_id = @account_id and request_id = @request_id and status = 'reserved';
            """;
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        command.Parameters.AddWithValue("request_id", NpgsqlDbType.Text, requestId);
        await command.ExecuteNonQueryAsync(cancellationToken);
    }

    private static async Task ClearActiveTurnAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        Guid sessionId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            update public.ai_conversation_sessions
            set active_request_id = null, active_request_hash = null,
                active_turn_index = null, active_lease_expires_at = null,
                updated_at = now()
            where account_id = @account_id and session_id = @session_id;
            """;
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        command.Parameters.AddWithValue("session_id", NpgsqlDbType.Uuid, sessionId);
        await command.ExecuteNonQueryAsync(cancellationToken);
    }

    private static void ValidateIdentifiers(
        Guid accountId,
        string requestId,
        string caseVersionId,
        string fingerprint)
    {
        ValidateRequestIdentifier(accountId, requestId);
        if (string.IsNullOrWhiteSpace(caseVersionId)
            || caseVersionId.Length > 128
            || !IsHash(fingerprint))
            throw new ApiException(StatusCodes.Status400BadRequest, "INVALID_AI_CONVERSATION", "The conversation request is invalid.");
    }

    private static void ValidateRequestIdentifier(Guid accountId, string requestId)
    {
        if (accountId == Guid.Empty
            || string.IsNullOrEmpty(requestId)
            || requestId.Length is < 8 or > 128
            || requestId.Any(character => !(char.IsAsciiLetterOrDigit(character) || character is '_' or '-')))
            throw new ApiException(StatusCodes.Status400BadRequest, "INVALID_AI_CONVERSATION", "The conversation request is invalid.");
    }

    private static bool IsHash(string? value) =>
        value is { Length: 64 }
        && value.All(character => character is >= '0' and <= '9' or >= 'a' and <= 'f');

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
            throw new ApiException(StatusCodes.Status404NotFound, "ACCOUNT_NOT_FOUND", "The account could not be found.");
    }

    private static ApiException DatabaseUnavailable(string message, Exception exception) => new(
        StatusCodes.Status503ServiceUnavailable,
        "DATABASE_UNAVAILABLE",
        message,
        exception);
}
