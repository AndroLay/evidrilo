using Evidrilo.Api.Common;
using Npgsql;
using NpgsqlTypes;
using System.Text.Json.Serialization;

namespace Evidrilo.Api.ProjectAi;

public sealed record ProjectAiConsentGrantRequest(
    [property: JsonRequired, JsonPropertyName("schema")] string? Schema,
    [property: JsonRequired, JsonPropertyName("version")] string? Version,
    [property: JsonRequired, JsonPropertyName("policyVersion")] string? PolicyVersion);

public sealed record ProjectAiConsentState(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("granted")] bool Granted,
    [property: JsonPropertyName("policyVersion")] string PolicyVersion,
    [property: JsonPropertyName("grantedAt"), JsonIgnore(Condition = JsonIgnoreCondition.Never)] DateTimeOffset? GrantedAt,
    [property: JsonPropertyName("revokedAt"), JsonIgnore(Condition = JsonIgnoreCondition.Never)] DateTimeOffset? RevokedAt,
    [property: JsonPropertyName("generation")] int Generation);

public static class ProjectAiConsentPolicy
{
    public const string Schema = "evidrilo.project-ai-consent";
    public const string Version = "1";
    public const string CurrentPolicyVersion = "project-ai-data.v1";

    public static string? ValidateGrant(ProjectAiConsentGrantRequest? request)
    {
        if (request is null
            || !string.Equals(request.Schema, Schema, StringComparison.Ordinal)
            || !string.Equals(request.Version, Version, StringComparison.Ordinal))
            return "INVALID_PROJECT_AI_CONSENT";
        if (!string.Equals(request.PolicyVersion, CurrentPolicyVersion, StringComparison.Ordinal))
            return "PROJECT_AI_CONSENT_POLICY_STALE";
        return null;
    }

    public static bool StillAuthorizesDispatch(
        ProjectAiConsentState initial,
        ProjectAiConsentState current) =>
        initial.Granted
        && current.Granted
        && string.Equals(initial.PolicyVersion, CurrentPolicyVersion, StringComparison.Ordinal)
        && string.Equals(current.PolicyVersion, CurrentPolicyVersion, StringComparison.Ordinal)
        && initial.Generation == current.Generation;

    public static ProjectAiConsentState NotGranted { get; } = new(
        Schema,
        Version,
        false,
        CurrentPolicyVersion,
        null,
        null,
        0);
}

public interface IProjectAiConsentStore
{
    Task<ProjectAiConsentState> ReadOwnAsync(Guid accountId, CancellationToken cancellationToken);

    Task<ProjectAiConsentState> GrantOwnAsync(
        Guid accountId,
        string policyVersion,
        CancellationToken cancellationToken);

    Task<ProjectAiConsentState> RevokeOwnAsync(Guid accountId, CancellationToken cancellationToken);
}

public sealed class DatabaseUnavailableProjectAiConsentStore : IProjectAiConsentStore
{
    public Task<ProjectAiConsentState> ReadOwnAsync(Guid accountId, CancellationToken cancellationToken) =>
        throw NotConfigured();

    public Task<ProjectAiConsentState> GrantOwnAsync(
        Guid accountId,
        string policyVersion,
        CancellationToken cancellationToken) => throw NotConfigured();

    public Task<ProjectAiConsentState> RevokeOwnAsync(Guid accountId, CancellationToken cancellationToken) =>
        throw NotConfigured();

    private static ApiException NotConfigured() => new(
        StatusCodes.Status503ServiceUnavailable,
        "DATABASE_NOT_CONFIGURED",
        "Project AI consent storage is not configured.");
}

public sealed class NpgsqlProjectAiConsentStore : IProjectAiConsentStore, IDisposable
{
    private readonly NpgsqlDataSource dataSource;

    public NpgsqlProjectAiConsentStore(string connectionString) =>
        dataSource = NpgsqlDataSource.Create(connectionString);

    public async Task<ProjectAiConsentState> ReadOwnAsync(
        Guid accountId,
        CancellationToken cancellationToken)
    {
        ValidateAccount(accountId);
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);
            var state = await ReadStateAsync(connection, transaction, accountId, forUpdate: false, cancellationToken);
            await transaction.CommitAsync(cancellationToken);
            return state ?? ProjectAiConsentPolicy.NotGranted;
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (NpgsqlException exception)
        {
            throw DatabaseUnavailable(exception);
        }
    }

    public async Task<ProjectAiConsentState> GrantOwnAsync(
        Guid accountId,
        string policyVersion,
        CancellationToken cancellationToken)
    {
        ValidateAccount(accountId);
        if (!string.Equals(policyVersion, ProjectAiConsentPolicy.CurrentPolicyVersion, StringComparison.Ordinal))
            throw new ApiException(
                StatusCodes.Status409Conflict,
                "PROJECT_AI_CONSENT_POLICY_STALE",
                "Review the current Project AI data-use policy before enabling assistance.");

        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);
            await LockAccountAsync(connection, transaction, accountId, cancellationToken);
            await EnsureAccountNotDeletedAsync(connection, transaction, accountId, cancellationToken);

            var current = await ReadStateAsync(connection, transaction, accountId, forUpdate: true, cancellationToken);
            if (current is { Granted: true } && current.PolicyVersion == ProjectAiConsentPolicy.CurrentPolicyVersion)
            {
                await transaction.CommitAsync(cancellationToken);
                return current;
            }

            var generation = (current?.Generation ?? 0) + 1;
            ProjectAiConsentState updated;
            await using (var command = connection.CreateCommand())
            {
                command.Transaction = transaction;
                command.CommandText = """
                    insert into public.project_ai_consents (
                        account_id, policy_version, granted, granted_at, revoked_at,
                        consent_generation, updated_at
                    ) values (
                        @account_id, @policy_version, true, clock_timestamp(), null,
                        @generation, clock_timestamp()
                    )
                    on conflict (account_id) do update
                       set policy_version = excluded.policy_version,
                           granted = true,
                           granted_at = clock_timestamp(),
                           revoked_at = null,
                           consent_generation = excluded.consent_generation,
                           updated_at = clock_timestamp()
                    returning granted, policy_version, granted_at, revoked_at, consent_generation;
                    """;
                command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                command.Parameters.AddWithValue("policy_version", NpgsqlDbType.Text, ProjectAiConsentPolicy.CurrentPolicyVersion);
                command.Parameters.AddWithValue("generation", NpgsqlDbType.Integer, generation);
                await using var reader = await command.ExecuteReaderAsync(cancellationToken);
                if (!await reader.ReadAsync(cancellationToken)) throw SchemaMissing();
                updated = ReadState(reader);
            }

            await AppendEventAsync(connection, transaction, accountId, updated, "grant", cancellationToken);
            await transaction.CommitAsync(cancellationToken);
            return updated;
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (ApiException)
        {
            throw;
        }
        catch (NpgsqlException exception)
        {
            throw DatabaseUnavailable(exception);
        }
    }

    public async Task<ProjectAiConsentState> RevokeOwnAsync(
        Guid accountId,
        CancellationToken cancellationToken)
    {
        ValidateAccount(accountId);
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);
            await LockAccountAsync(connection, transaction, accountId, cancellationToken);

            var current = await ReadStateAsync(connection, transaction, accountId, forUpdate: true, cancellationToken);
            if (current is null || !current.Granted)
            {
                await transaction.CommitAsync(cancellationToken);
                return current ?? ProjectAiConsentPolicy.NotGranted;
            }

            ProjectAiConsentState updated;
            await using (var command = connection.CreateCommand())
            {
                command.Transaction = transaction;
                command.CommandText = """
                    update public.project_ai_consents
                    set granted = false,
                        revoked_at = clock_timestamp(),
                        consent_generation = consent_generation + 1,
                        updated_at = clock_timestamp()
                    where account_id = @account_id
                    returning granted, policy_version, granted_at, revoked_at, consent_generation;
                    """;
                command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                await using var reader = await command.ExecuteReaderAsync(cancellationToken);
                if (!await reader.ReadAsync(cancellationToken)) throw SchemaMissing();
                updated = ReadState(reader);
            }

            await AppendEventAsync(connection, transaction, accountId, updated, "revoke", cancellationToken);
            await transaction.CommitAsync(cancellationToken);
            return updated;
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (ApiException)
        {
            throw;
        }
        catch (NpgsqlException exception)
        {
            throw DatabaseUnavailable(exception);
        }
    }

    public void Dispose() => dataSource.Dispose();

    private static async Task<ProjectAiConsentState?> ReadStateAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        bool forUpdate,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            select granted, policy_version, granted_at, revoked_at, consent_generation
            from public.project_ai_consents
            where account_id = @account_id
            """ + (forUpdate ? " for update;" : ";");
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        return await reader.ReadAsync(cancellationToken) ? ReadState(reader) : null;
    }

    private static ProjectAiConsentState ReadState(NpgsqlDataReader reader) => new(
        ProjectAiConsentPolicy.Schema,
        ProjectAiConsentPolicy.Version,
        reader.GetBoolean(0),
        reader.GetString(1),
        reader.IsDBNull(2) ? null : AsUtcOffset(reader.GetDateTime(2)),
        reader.IsDBNull(3) ? null : AsUtcOffset(reader.GetDateTime(3)),
        reader.GetInt32(4));

    private static async Task AppendEventAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        ProjectAiConsentState state,
        string decision,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            insert into public.project_ai_consent_events (
                account_id, policy_version, decision, consent_generation
            ) values (@account_id, @policy_version, @decision, @generation);
            """;
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        command.Parameters.AddWithValue("policy_version", NpgsqlDbType.Text, state.PolicyVersion);
        command.Parameters.AddWithValue("decision", NpgsqlDbType.Text, decision);
        command.Parameters.AddWithValue("generation", NpgsqlDbType.Integer, state.Generation);
        await command.ExecuteNonQueryAsync(cancellationToken);
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

    private static async Task LockAccountAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = "select pg_advisory_xact_lock(hashtextextended(@account_id, 0));";
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Text, accountId.ToString());
        await command.ExecuteNonQueryAsync(cancellationToken);
    }

    private static async Task EnsureAccountNotDeletedAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            select exists (
                select 1 from public.account_deletion_requests
                where account_id = @account_id and status = 'completed'
            ) or exists (
                select 1 from public.account_deletion_tombstones
                where account_id = @account_id
            ) or exists (
                select 1 from public.account_profiles
                where account_id = @account_id and deleted_at is not null
            );
            """;
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        if (await command.ExecuteScalarAsync(cancellationToken) is true)
            throw new ApiException(
                StatusCodes.Status410Gone,
                "ACCOUNT_DELETED",
                "This account has been deleted.");
    }

    private static DateTimeOffset AsUtcOffset(DateTime timestamp)
    {
        var utc = DateTime.SpecifyKind(timestamp, DateTimeKind.Utc);
        return new DateTimeOffset(utc);
    }

    private static void ValidateAccount(Guid accountId)
    {
        if (accountId == Guid.Empty)
            throw new ApiException(StatusCodes.Status401Unauthorized, "AUTH_REQUIRED", "Authentication is required.");
    }

    private static ApiException SchemaMissing() => new(
        StatusCodes.Status503ServiceUnavailable,
        "DATABASE_SCHEMA_NOT_READY",
        "Project AI consent storage is not ready.");

    private static ApiException DatabaseUnavailable(Exception exception) => new(
        StatusCodes.Status503ServiceUnavailable,
        "DATABASE_UNAVAILABLE",
        "Project AI consent is temporarily unavailable.",
        exception);
}
