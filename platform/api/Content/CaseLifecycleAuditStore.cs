using System.Text.Json.Serialization;
using Evidrilo.Api.Authorization;
using Evidrilo.Api.Common;
using Npgsql;
using NpgsqlTypes;

namespace Evidrilo.Api.Content;

public static class CaseLifecycleAuditLimits
{
    public const int MaxEvents = 128;
}

public sealed record CaseLifecycleAuditEvent(
    Guid AuditEventId,
    Guid? ActorAccountId,
    string EventType,
    CaseLifecycleState? FromState,
    CaseLifecycleState ToState,
    string? Reason,
    DateTimeOffset CreatedAt);

public sealed record CaseLifecycleAuditRead(
    string CaseVersionId,
    IReadOnlyList<CaseLifecycleAuditEvent> Events,
    bool Truncated);

public sealed record CaseLifecycleAuditEventResponse(
    [property: JsonPropertyName("auditEventId")] Guid AuditEventId,
    [property: JsonPropertyName("actorAccountId"), JsonIgnore(Condition = JsonIgnoreCondition.Never)] Guid? ActorAccountId,
    [property: JsonPropertyName("eventType")] string EventType,
    [property: JsonPropertyName("fromState"), JsonIgnore(Condition = JsonIgnoreCondition.Never)] string? FromState,
    [property: JsonPropertyName("toState")] string ToState,
    [property: JsonPropertyName("reason"), JsonIgnore(Condition = JsonIgnoreCondition.Never)] string? Reason,
    [property: JsonPropertyName("createdAt")] DateTimeOffset CreatedAt);

public sealed record CaseLifecycleAuditResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("caseVersionId")] string CaseVersionId,
    [property: JsonPropertyName("events")] IReadOnlyList<CaseLifecycleAuditEventResponse> Events,
    [property: JsonPropertyName("truncated")] bool Truncated,
    [property: JsonPropertyName("requestId")] string RequestId);

public interface ICaseLifecycleAuditStore
{
    Task<CaseLifecycleAuditRead?> GetAsync(
        Guid accountId,
        string caseVersionId,
        CancellationToken cancellationToken);
}

public sealed class DatabaseUnavailableCaseLifecycleAuditStore : ICaseLifecycleAuditStore
{
    public Task<CaseLifecycleAuditRead?> GetAsync(
        Guid accountId,
        string caseVersionId,
        CancellationToken cancellationToken) => throw new ApiException(
        StatusCodes.Status503ServiceUnavailable,
        "DATABASE_NOT_CONFIGURED",
        "Case lifecycle audit is not configured.");
}

public sealed class NpgsqlCaseLifecycleAuditStore : ICaseLifecycleAuditStore, IDisposable
{
    private readonly NpgsqlDataSource dataSource;

    public NpgsqlCaseLifecycleAuditStore(string connectionString)
    {
        dataSource = NpgsqlDataSource.Create(connectionString);
    }

    public async Task<CaseLifecycleAuditRead?> GetAsync(
        Guid accountId,
        string caseVersionId,
        CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);

            var scope = await ReadCaseScopeAsync(
                connection,
                transaction,
                accountId,
                caseVersionId,
                cancellationToken);
            if (scope is null)
            {
                await transaction.CommitAsync(cancellationToken);
                return null;
            }

            var membership = scope.Role is { } role && scope.OrganizationId is { } organizationId
                ? new Membership(accountId, organizationId, role, true)
                : null;
            var decision = AccessPolicy.CanReadContentAudit(
                membership,
                scope.OrganizationId ?? Guid.Empty);
            if (!decision.Allowed) throw Forbidden(decision.Code);

            await using var command = connection.CreateCommand();
            command.Transaction = transaction;
            command.CommandText = """
                select audit_event_id, actor_account_id, event_type,
                       from_state, to_state, reason, created_at
                from public.case_lifecycle_audit_events
                where case_version_id = @case_version_id
                order by created_at asc, audit_event_id asc
                limit @event_limit;
                """;
            command.Parameters.AddWithValue("case_version_id", NpgsqlDbType.Text, caseVersionId);
            command.Parameters.AddWithValue(
                "event_limit",
                NpgsqlDbType.Integer,
                CaseLifecycleAuditLimits.MaxEvents + 1);

            var events = new List<CaseLifecycleAuditEvent>(CaseLifecycleAuditLimits.MaxEvents + 1);
            await using (var reader = await command.ExecuteReaderAsync(cancellationToken))
            {
                while (await reader.ReadAsync(cancellationToken))
                {
                    events.Add(new CaseLifecycleAuditEvent(
                        reader.GetGuid(0),
                        reader.IsDBNull(1) ? null : reader.GetGuid(1),
                        ParseEventType(reader.GetString(2)),
                        reader.IsDBNull(3) ? null : CaseLifecycleStateExtensions.FromWire(reader.GetString(3)),
                        CaseLifecycleStateExtensions.FromWire(reader.GetString(4)),
                        reader.IsDBNull(5) ? null : reader.GetString(5),
                        reader.GetFieldValue<DateTimeOffset>(6)));
                }
            }

            var truncated = events.Count > CaseLifecycleAuditLimits.MaxEvents;
            if (truncated) events.RemoveRange(CaseLifecycleAuditLimits.MaxEvents, events.Count - CaseLifecycleAuditLimits.MaxEvents);
            await transaction.CommitAsync(cancellationToken);
            return new CaseLifecycleAuditRead(caseVersionId, events, truncated);
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
                "Case lifecycle audit is temporarily unavailable.",
                exception);
        }
    }

    public void Dispose() => dataSource.Dispose();

    private static async Task<CaseScope?> ReadCaseScopeAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        string caseVersionId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            select case_version.organization_id, membership.role
            from public.case_versions case_version
            left join public.organization_memberships membership
              on membership.organization_id = case_version.organization_id
             and membership.account_id = @account_id
             and membership.active = true
            where case_version.case_version_id = @case_version_id;
            """;
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        command.Parameters.AddWithValue("case_version_id", NpgsqlDbType.Text, caseVersionId);

        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        if (!await reader.ReadAsync(cancellationToken)) return null;

        Guid? organizationId = reader.IsDBNull(0) ? null : reader.GetGuid(0);
        PlatformRole? role = reader.IsDBNull(1) ? null : ParseRole(reader.GetString(1));
        return new CaseScope(organizationId, role);
    }

    private static PlatformRole ParseRole(string role) => role switch
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

    private static string ParseEventType(string eventType) => eventType switch
    {
        "created" or "transitioned" => eventType,
        _ => throw new ApiException(
            StatusCodes.Status503ServiceUnavailable,
            "DATABASE_SCHEMA_MISSING",
            "The content audit schema is not ready."),
    };

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

    private static ApiException Forbidden(string code) => new(
        StatusCodes.Status403Forbidden,
        code,
        "You are not allowed to access case lifecycle history.");

    private sealed record CaseScope(Guid? OrganizationId, PlatformRole? Role);
}
