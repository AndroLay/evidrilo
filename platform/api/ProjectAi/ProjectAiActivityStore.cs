using System.Globalization;
using System.Text;
using Evidrilo.Api.Common;
using Npgsql;
using NpgsqlTypes;

namespace Evidrilo.Api.ProjectAi;

public static class ProjectAiActivityOutcomes
{
    public const string Pending = "PENDING";
    public const string Applied = "APPLIED";
    public const string Edited = "EDITED";
    public const string Dismissed = "DISMISSED";
    public const string Stale = "STALE";
    public const string Failed = "FAILED";
    public const string Completed = "COMPLETED";

    public static bool IsFinal(string? outcome) => outcome is Applied or Edited or Dismissed or Stale or Failed or Completed;
}

public sealed record ProjectAiActivityCreate(
    Guid InstallationId,
    string RequestId,
    string Mode,
    Guid? ProjectId,
    string? StageId,
    string? OperationId,
    int? BaseProjectRevision,
    int? ConsentGeneration);

public sealed record ProjectAiActivityRecord(
    Guid ActivityId,
    Guid InstallationId,
    string RequestId,
    string Mode,
    Guid? ProjectId,
    string? StageId,
    string? OperationId,
    int? BaseProjectRevision,
    int? ConsentGeneration,
    int? ResultProjectRevision,
    string Outcome,
    string? RequestedSettlementOutcome,
    string? SettlementHash,
    DateTimeOffset CreatedAt,
    DateTimeOffset UpdatedAt);

public sealed record ProjectAiActivityCursor(DateTimeOffset CreatedAt, Guid ActivityId);

public sealed record ProjectAiActivityPage(
    IReadOnlyList<ProjectAiActivityRecord> Activities,
    string? NextCursor);

public enum ProjectAiActivityCompletionStatus
{
    Updated,
    AlreadyCompleted,
    NotFound,
    Conflict,
}

public interface IProjectAiActivityStore
{
    Task<bool> CreatePendingOwnAsync(
        Guid accountId,
        ProjectAiActivityCreate activity,
        CancellationToken cancellationToken);

    Task<ProjectAiActivityRecord?> ReadOwnAsync(
        Guid accountId,
        Guid installationId,
        string requestId,
        CancellationToken cancellationToken);

    Task<ProjectAiActivityPage> ListOwnAsync(
        Guid accountId,
        Guid installationId,
        Guid? projectId,
        int limit,
        ProjectAiActivityCursor? before,
        CancellationToken cancellationToken);

    Task<ProjectAiActivityCompletionStatus> CompleteOwnAsync(
        Guid accountId,
        Guid installationId,
        string requestId,
        string outcome,
        int? resultProjectRevision,
        string? requestedSettlementOutcome,
        string? settlementHash,
        CancellationToken cancellationToken);

    Task<int> ClearGeneralOwnAsync(
        Guid accountId,
        Guid installationId,
        CancellationToken cancellationToken);
}

public sealed class DatabaseUnavailableProjectAiActivityStore : IProjectAiActivityStore
{
    public Task<bool> CreatePendingOwnAsync(Guid accountId, ProjectAiActivityCreate activity, CancellationToken cancellationToken) =>
        throw NotConfigured();

    public Task<ProjectAiActivityRecord?> ReadOwnAsync(Guid accountId, Guid installationId, string requestId, CancellationToken cancellationToken) =>
        throw NotConfigured();

    public Task<ProjectAiActivityPage> ListOwnAsync(Guid accountId, Guid installationId, Guid? projectId, int limit, ProjectAiActivityCursor? before, CancellationToken cancellationToken) =>
        throw NotConfigured();

    public Task<ProjectAiActivityCompletionStatus> CompleteOwnAsync(Guid accountId, Guid installationId, string requestId, string outcome, int? resultProjectRevision, string? requestedSettlementOutcome, string? settlementHash, CancellationToken cancellationToken) =>
        throw NotConfigured();

    public Task<int> ClearGeneralOwnAsync(Guid accountId, Guid installationId, CancellationToken cancellationToken) =>
        throw NotConfigured();

    private static ApiException NotConfigured() => new(
        StatusCodes.Status503ServiceUnavailable,
        "DATABASE_NOT_CONFIGURED",
        "Project AI activity storage is not configured.");
}

public sealed class NpgsqlProjectAiActivityStore : IProjectAiActivityStore, IDisposable
{
    private readonly NpgsqlDataSource dataSource;

    public NpgsqlProjectAiActivityStore(string connectionString) =>
        dataSource = NpgsqlDataSource.Create(connectionString);

    public async Task<bool> CreatePendingOwnAsync(
        Guid accountId,
        ProjectAiActivityCreate activity,
        CancellationToken cancellationToken)
    {
        ValidateAccount(accountId);
        ValidateActivity(activity);
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);
            await LockAccountAsync(connection, transaction, accountId, cancellationToken);
            await EnsureAccountNotDeletedAsync(connection, transaction, accountId, cancellationToken);

            await using var command = connection.CreateCommand();
            command.Transaction = transaction;
            command.CommandText = """
                insert into public.project_ai_activity (
                    account_id, installation_id, request_id, mode, project_id,
                    stage_id, operation_id, base_project_revision, consent_generation, outcome
                ) values (
                    @account_id, @installation_id, @request_id, @mode, @project_id,
                    @stage_id, @operation_id, @base_project_revision, @consent_generation, 'PENDING'
                )
                on conflict (account_id, request_id) do nothing
                returning activity_id;
                """;
            AddActivityParameters(command, accountId, activity);
            var inserted = await command.ExecuteScalarAsync(cancellationToken) is Guid;
            await transaction.CommitAsync(cancellationToken);
            return inserted;
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

    public async Task<ProjectAiActivityRecord?> ReadOwnAsync(
        Guid accountId,
        Guid installationId,
        string requestId,
        CancellationToken cancellationToken)
    {
        ValidateScope(accountId, installationId, requestId);
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);
            await using var command = connection.CreateCommand();
            command.Transaction = transaction;
            command.CommandText = SelectColumns + """
                where account_id = @account_id
                  and installation_id = @installation_id
                  and request_id = @request_id;
                """;
            AddScopeParameters(command, accountId, installationId, requestId);
            await using var reader = await command.ExecuteReaderAsync(cancellationToken);
            var result = await reader.ReadAsync(cancellationToken) ? ReadActivity(reader) : null;
            await reader.CloseAsync();
            await transaction.CommitAsync(cancellationToken);
            return result;
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

    public async Task<ProjectAiActivityPage> ListOwnAsync(
        Guid accountId,
        Guid installationId,
        Guid? projectId,
        int limit,
        ProjectAiActivityCursor? before,
        CancellationToken cancellationToken)
    {
        ValidateAccount(accountId);
        if (installationId == Guid.Empty || limit is < 1 or > 100)
            throw InvalidActivityScope();
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);
            await using var command = connection.CreateCommand();
            command.Transaction = transaction;
            command.CommandText = SelectColumns + """
                where account_id = @account_id
                  and installation_id = @installation_id
                  and (@project_id is null or project_id = @project_id)
                  and (
                      @before_created_at is null
                      or (created_at, activity_id) < (@before_created_at, @before_activity_id)
                  )
                order by created_at desc, activity_id desc
                limit @page_size;
                """;
            command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
            command.Parameters.AddWithValue("installation_id", NpgsqlDbType.Uuid, installationId);
            command.Parameters.Add(new NpgsqlParameter("project_id", NpgsqlDbType.Uuid)
            {
                Value = projectId is null ? DBNull.Value : projectId.Value,
            });
            command.Parameters.Add(new NpgsqlParameter("before_created_at", NpgsqlDbType.TimestampTz)
            {
                Value = before is null ? DBNull.Value : before.CreatedAt,
            });
            command.Parameters.Add(new NpgsqlParameter("before_activity_id", NpgsqlDbType.Uuid)
            {
                Value = before is null ? DBNull.Value : before.ActivityId,
            });
            command.Parameters.AddWithValue("page_size", NpgsqlDbType.Integer, limit + 1);
            var entries = new List<ProjectAiActivityRecord>(limit + 1);
            await using (var reader = await command.ExecuteReaderAsync(cancellationToken))
            {
                while (await reader.ReadAsync(cancellationToken)) entries.Add(ReadActivity(reader));
            }

            var hasMore = entries.Count > limit;
            if (hasMore) entries.RemoveAt(entries.Count - 1);
            var cursor = hasMore && entries.Count > 0 ? EncodeCursor(entries[^1]) : null;
            await transaction.CommitAsync(cancellationToken);
            return new ProjectAiActivityPage(entries, cursor);
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

    public async Task<ProjectAiActivityCompletionStatus> CompleteOwnAsync(
        Guid accountId,
        Guid installationId,
        string requestId,
        string outcome,
        int? resultProjectRevision,
        string? requestedSettlementOutcome,
        string? settlementHash,
        CancellationToken cancellationToken)
    {
        ValidateScope(accountId, installationId, requestId);
        if (!ProjectAiActivityOutcomes.IsFinal(outcome)
            || (outcome is ProjectAiActivityOutcomes.Applied or ProjectAiActivityOutcomes.Edited)
                != resultProjectRevision.HasValue
            || (outcome is ProjectAiActivityOutcomes.Applied or ProjectAiActivityOutcomes.Edited
                or ProjectAiActivityOutcomes.Dismissed)
                != (requestedSettlementOutcome is not null && settlementHash is not null)
            || (requestedSettlementOutcome is null) != (settlementHash is null)
            || (requestedSettlementOutcome is not null
                && requestedSettlementOutcome is not (ProjectAiActivityOutcomes.Applied or ProjectAiActivityOutcomes.Edited
                    or ProjectAiActivityOutcomes.Dismissed or ProjectAiActivityOutcomes.Stale))
            || (outcome != ProjectAiActivityOutcomes.Stale
                && requestedSettlementOutcome is not null
                && outcome != requestedSettlementOutcome)
            || (settlementHash is not null && !System.Text.RegularExpressions.Regex.IsMatch(settlementHash, "\\A[a-f0-9]{64}\\z", System.Text.RegularExpressions.RegexOptions.CultureInvariant)))
            throw InvalidActivityOutcome();
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);
            await using var update = connection.CreateCommand();
            update.Transaction = transaction;
            update.CommandText = """
                update public.project_ai_activity
                   set outcome = @outcome,
                       result_project_revision = @result_project_revision,
                       requested_settlement_outcome = @requested_settlement_outcome,
                       settlement_hash = @settlement_hash,
                       updated_at = clock_timestamp()
                 where account_id = @account_id
                   and installation_id = @installation_id
                   and request_id = @request_id
                   and outcome = 'PENDING'
                returning activity_id;
                """;
            AddScopeParameters(update, accountId, installationId, requestId);
            update.Parameters.AddWithValue("outcome", NpgsqlDbType.Text, outcome);
            update.Parameters.Add(new NpgsqlParameter("result_project_revision", NpgsqlDbType.Integer)
            {
                Value = resultProjectRevision is null ? DBNull.Value : resultProjectRevision.Value,
            });
            update.Parameters.Add(new NpgsqlParameter("requested_settlement_outcome", NpgsqlDbType.Text)
            {
                Value = requestedSettlementOutcome is null ? DBNull.Value : requestedSettlementOutcome,
            });
            update.Parameters.Add(new NpgsqlParameter("settlement_hash", NpgsqlDbType.Text)
            {
                Value = settlementHash is null ? DBNull.Value : settlementHash,
            });
            if (await update.ExecuteScalarAsync(cancellationToken) is Guid)
            {
                await transaction.CommitAsync(cancellationToken);
                return ProjectAiActivityCompletionStatus.Updated;
            }

            await using var read = connection.CreateCommand();
            read.Transaction = transaction;
            read.CommandText = SelectColumns + """
                where account_id = @account_id
                  and installation_id = @installation_id
                  and request_id = @request_id;
                """;
            AddScopeParameters(read, accountId, installationId, requestId);
            await using var reader = await read.ExecuteReaderAsync(cancellationToken);
            if (!await reader.ReadAsync(cancellationToken))
            {
                await reader.CloseAsync();
                await transaction.CommitAsync(cancellationToken);
                return ProjectAiActivityCompletionStatus.NotFound;
            }
            var existing = ReadActivity(reader);
            await reader.CloseAsync();
            await transaction.CommitAsync(cancellationToken);
            return existing.Outcome == outcome
                   && existing.ResultProjectRevision == resultProjectRevision
                   && existing.RequestedSettlementOutcome == requestedSettlementOutcome
                   && existing.SettlementHash == settlementHash
                ? ProjectAiActivityCompletionStatus.AlreadyCompleted
                : ProjectAiActivityCompletionStatus.Conflict;
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

    public async Task<int> ClearGeneralOwnAsync(
        Guid accountId,
        Guid installationId,
        CancellationToken cancellationToken)
    {
        ValidateAccount(accountId);
        if (installationId == Guid.Empty) throw InvalidActivityScope();
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);
            await using var command = connection.CreateCommand();
            command.Transaction = transaction;
            command.CommandText = """
                delete from public.project_ai_activity
                 where account_id = @account_id
                   and installation_id = @installation_id
                   and mode = 'GENERAL'
                returning activity_id;
                """;
            command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
            command.Parameters.AddWithValue("installation_id", NpgsqlDbType.Uuid, installationId);
            var deleted = 0;
            await using (var reader = await command.ExecuteReaderAsync(cancellationToken))
            {
                while (await reader.ReadAsync(cancellationToken)) deleted++;
            }
            await transaction.CommitAsync(cancellationToken);
            return deleted;
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

    public static string EncodeCursor(ProjectAiActivityRecord activity)
    {
        var raw = string.Concat(activity.CreatedAt.ToUniversalTime().ToString("O", CultureInfo.InvariantCulture), "\n", activity.ActivityId.ToString("N"));
        return Convert.ToBase64String(Encoding.UTF8.GetBytes(raw)).TrimEnd('=').Replace('+', '-').Replace('/', '_');
    }

    public static bool TryDecodeCursor(string? value, out ProjectAiActivityCursor? cursor)
    {
        cursor = null;
        if (string.IsNullOrWhiteSpace(value) || value.Length > 256) return false;
        try
        {
            var base64 = value.Replace('-', '+').Replace('_', '/');
            base64 = base64.PadRight(base64.Length + ((4 - base64.Length % 4) % 4), '=');
            var parts = Encoding.UTF8.GetString(Convert.FromBase64String(base64)).Split('\n');
            if (parts.Length != 2
                || !DateTimeOffset.TryParseExact(parts[0], "O", CultureInfo.InvariantCulture, DateTimeStyles.RoundtripKind, out var createdAt)
                || !Guid.TryParseExact(parts[1], "N", out var activityId)
                || activityId == Guid.Empty)
                return false;
            cursor = new ProjectAiActivityCursor(createdAt, activityId);
            return true;
        }
        catch (FormatException)
        {
            return false;
        }
    }

    public void Dispose() => dataSource.Dispose();

    private static readonly string SelectColumns = """
        select activity_id, installation_id, request_id, mode, project_id,
               stage_id, operation_id, base_project_revision, consent_generation,
               result_project_revision, outcome, requested_settlement_outcome, settlement_hash,
               created_at, updated_at
          from public.project_ai_activity
        """;

    private static void AddActivityParameters(NpgsqlCommand command, Guid accountId, ProjectAiActivityCreate activity)
    {
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        command.Parameters.AddWithValue("installation_id", NpgsqlDbType.Uuid, activity.InstallationId);
        command.Parameters.AddWithValue("request_id", NpgsqlDbType.Text, activity.RequestId);
        command.Parameters.AddWithValue("mode", NpgsqlDbType.Text, activity.Mode);
        command.Parameters.Add(new NpgsqlParameter("project_id", NpgsqlDbType.Uuid)
        {
            Value = activity.ProjectId is null ? DBNull.Value : activity.ProjectId.Value,
        });
        command.Parameters.Add(new NpgsqlParameter("stage_id", NpgsqlDbType.Text)
        {
            Value = activity.StageId is null ? DBNull.Value : activity.StageId,
        });
        command.Parameters.Add(new NpgsqlParameter("operation_id", NpgsqlDbType.Text)
        {
            Value = activity.OperationId is null ? DBNull.Value : activity.OperationId,
        });
        command.Parameters.Add(new NpgsqlParameter("base_project_revision", NpgsqlDbType.Integer)
        {
            Value = activity.BaseProjectRevision is null ? DBNull.Value : activity.BaseProjectRevision.Value,
        });
        command.Parameters.Add(new NpgsqlParameter("consent_generation", NpgsqlDbType.Integer)
        {
            Value = activity.ConsentGeneration is null ? DBNull.Value : activity.ConsentGeneration.Value,
        });
    }

    private static void AddScopeParameters(NpgsqlCommand command, Guid accountId, Guid installationId, string requestId)
    {
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        command.Parameters.AddWithValue("installation_id", NpgsqlDbType.Uuid, installationId);
        command.Parameters.AddWithValue("request_id", NpgsqlDbType.Text, requestId);
    }

    private static ProjectAiActivityRecord ReadActivity(NpgsqlDataReader reader) => new(
        reader.GetGuid(0),
        reader.GetGuid(1),
        reader.GetString(2),
        reader.GetString(3),
        reader.IsDBNull(4) ? null : reader.GetGuid(4),
        reader.IsDBNull(5) ? null : reader.GetString(5),
        reader.IsDBNull(6) ? null : reader.GetString(6),
        reader.IsDBNull(7) ? null : reader.GetInt32(7),
        reader.IsDBNull(8) ? null : reader.GetInt32(8),
        reader.IsDBNull(9) ? null : reader.GetInt32(9),
        reader.GetString(10),
        reader.IsDBNull(11) ? null : reader.GetString(11),
        reader.IsDBNull(12) ? null : reader.GetString(12),
        AsUtcOffset(reader.GetDateTime(13)),
        AsUtcOffset(reader.GetDateTime(14)));

    private static void ValidateActivity(ProjectAiActivityCreate activity)
    {
        if (activity.InstallationId != Guid.Empty
            && ProjectAiScaffoldValidator.IsValidRequestId(activity.RequestId)
            && activity.Mode == "PROJECT"
            && activity.ProjectId is not null
            && !string.IsNullOrWhiteSpace(activity.StageId)
            && !string.IsNullOrWhiteSpace(activity.OperationId)
            && activity.BaseProjectRevision is > 0
            && activity.ConsentGeneration is > 0)
            return;
        if (activity.Mode == "GENERAL"
            && activity.ProjectId is null
            && activity.StageId is null
            && activity.OperationId is null
            && activity.BaseProjectRevision is null
            && activity.ConsentGeneration is null)
            return;
        throw InvalidActivityScope();
    }

    private static void ValidateScope(Guid accountId, Guid installationId, string requestId)
    {
        ValidateAccount(accountId);
        if (installationId == Guid.Empty || !ProjectAiScaffoldValidator.IsValidRequestId(requestId))
            throw InvalidActivityScope();
    }

    private static void ValidateAccount(Guid accountId)
    {
        if (accountId == Guid.Empty) throw new ApiException(StatusCodes.Status401Unauthorized, "AUTH_REQUIRED", "Authentication is required.");
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
            throw new ApiException(StatusCodes.Status410Gone, "ACCOUNT_DELETED", "This account has been deleted.");
    }

    private static DateTimeOffset AsUtcOffset(DateTime timestamp) =>
        new(DateTime.SpecifyKind(timestamp, DateTimeKind.Utc));

    private static ApiException InvalidActivityScope() => new(
        StatusCodes.Status400BadRequest,
        "INVALID_PROJECT_AI_ACTIVITY",
        "The project-AI activity request is invalid.");

    private static ApiException InvalidActivityOutcome() => new(
        StatusCodes.Status400BadRequest,
        "INVALID_PROJECT_AI_ACTIVITY_OUTCOME",
        "The project-AI activity outcome is invalid.");

    private static ApiException DatabaseUnavailable(Exception exception) => new(
        StatusCodes.Status503ServiceUnavailable,
        "DATABASE_UNAVAILABLE",
        "Project AI activity is temporarily unavailable.",
        exception);
}
