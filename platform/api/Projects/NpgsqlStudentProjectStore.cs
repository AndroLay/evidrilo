using System.Text.Json;
using System.Text.Json.Serialization;
using Evidrilo.Api.Common;
using Npgsql;
using NpgsqlTypes;

namespace Evidrilo.Api.Projects;

public sealed class NpgsqlStudentProjectStore : IStudentProjectStore, IDisposable
{
    private readonly NpgsqlDataSource dataSource;
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web)
    {
        Converters = { new JsonStringEnumConverter(JsonNamingPolicy.SnakeCaseLower) },
    };

    public NpgsqlStudentProjectStore(string connectionString)
    {
        dataSource = NpgsqlDataSource.Create(connectionString);
    }

    public async Task<StudentProjectCloudConsentState> ReadCloudConsentOwnAsync(
        Guid accountId,
        CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);

            StudentProjectCloudConsentState? state = null;
            await using (var command = connection.CreateCommand())
            {
                command.Transaction = transaction;
                command.CommandText = """
                    select granted, policy_version, granted_at, revoked_at, updated_at
                    from public.student_project_cloud_consents
                    where account_id = @account_id;
                    """;
                command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                await using var reader = await command.ExecuteReaderAsync(cancellationToken);
                if (await reader.ReadAsync(cancellationToken))
                {
                    state = new StudentProjectCloudConsentState(
                        reader.GetBoolean(0),
                        reader.GetString(1),
                        reader.IsDBNull(2) ? null : AsUtcOffset(reader.GetDateTime(2)),
                        reader.IsDBNull(3) ? null : AsUtcOffset(reader.GetDateTime(3)),
                        AsUtcOffset(reader.GetDateTime(4)));
                }
            }

            await transaction.CommitAsync(cancellationToken);
            return state ?? new StudentProjectCloudConsentState(
                false,
                StudentProjectCloudConsentPolicy.CurrentVersion,
                null,
                null,
                null);
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

    public async Task<StudentProjectCloudConsentState> UpdateCloudConsentOwnAsync(
        Guid accountId,
        string policyVersion,
        string decision,
        CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);
            await LockCloudConsentAsync(connection, transaction, accountId, cancellationToken);

            var current = await ReadCloudConsentForUpdateAsync(
                connection,
                transaction,
                accountId,
                cancellationToken);
            var grant = string.Equals(decision, "grant", StringComparison.Ordinal);
            if (grant && !StudentProjectCloudConsentPolicy.AcceptsGrant(policyVersion))
            {
                throw new ApiException(
                    StatusCodes.Status409Conflict,
                    "PROJECT_CLOUD_CONSENT_POLICY_STALE",
                    "Review and accept the current project cloud-storage policy before enabling storage.");
            }

            if (current is null && !grant)
            {
                await transaction.CommitAsync(cancellationToken);
                return new StudentProjectCloudConsentState(
                    false,
                    StudentProjectCloudConsentPolicy.CurrentVersion,
                    null,
                    null,
                    null);
            }

            if (current is not null
                && current.Granted == grant
                && (!grant || string.Equals(
                    current.PolicyVersion,
                    StudentProjectCloudConsentPolicy.CurrentVersion,
                    StringComparison.Ordinal)))
            {
                await transaction.CommitAsync(cancellationToken);
                return current;
            }

            StudentProjectCloudConsentState updated;
            await using (var command = connection.CreateCommand())
            {
                command.Transaction = transaction;
                command.CommandText = """
                    insert into public.student_project_cloud_consents (
                        account_id, policy_version, granted, granted_at, revoked_at, updated_at
                    ) values (
                        @account_id, @policy_version, @granted,
                        now(),
                        case when @granted then null else now() end,
                        now()
                    )
                    on conflict (account_id) do update
                       set policy_version = excluded.policy_version,
                           granted = excluded.granted,
                           granted_at = case when excluded.granted then now()
                                             else public.student_project_cloud_consents.granted_at end,
                           revoked_at = case when excluded.granted then null else now() end,
                           updated_at = now()
                    returning granted, policy_version, granted_at, revoked_at, updated_at;
                    """;
                command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                command.Parameters.AddWithValue("policy_version", NpgsqlDbType.Text, grant
                    ? StudentProjectCloudConsentPolicy.CurrentVersion
                    : policyVersion);
                command.Parameters.AddWithValue("granted", NpgsqlDbType.Boolean, grant);
                await using var reader = await command.ExecuteReaderAsync(cancellationToken);
                if (!await reader.ReadAsync(cancellationToken))
                    throw SchemaMissing();
                updated = new StudentProjectCloudConsentState(
                    reader.GetBoolean(0),
                    reader.GetString(1),
                    reader.IsDBNull(2) ? null : AsUtcOffset(reader.GetDateTime(2)),
                    reader.IsDBNull(3) ? null : AsUtcOffset(reader.GetDateTime(3)),
                    AsUtcOffset(reader.GetDateTime(4)));
            }

            await using (var audit = connection.CreateCommand())
            {
                audit.Transaction = transaction;
                audit.CommandText = """
                    insert into public.student_project_cloud_consent_events (
                        event_id, account_id, policy_version, decision, decided_at
                    ) values (@event_id, @account_id, @policy_version, @decision, now());
                    """;
                audit.Parameters.AddWithValue("event_id", NpgsqlDbType.Uuid, Guid.NewGuid());
                audit.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                audit.Parameters.AddWithValue("policy_version", NpgsqlDbType.Text, grant
                    ? StudentProjectCloudConsentPolicy.CurrentVersion
                    : policyVersion);
                audit.Parameters.AddWithValue("decision", NpgsqlDbType.Text, decision);
                await audit.ExecuteNonQueryAsync(cancellationToken);
            }

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

    public async Task<StudentProjectListResult> ListOwnAsync(
        Guid accountId,
        int limit,
        DateTimeOffset? beforeCreatedAt,
        Guid? beforeProjectId,
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
                select project_id, version, document ->> 'title', document ->> 'question',
                       document ->> 'method', created_at, updated_at
                from public.student_projects
                where account_id = @account_id
                  and (
                      @before_created_at is null
                      or (created_at, project_id) < (@before_created_at, @before_project_id)
                  )
                order by created_at desc, project_id desc
                limit @limit;
                """;
            command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
            command.Parameters.AddWithValue(
                "before_created_at",
                NpgsqlDbType.TimestampTz,
                beforeCreatedAt?.ToUniversalTime() is { } before ? before : DBNull.Value);
            command.Parameters.AddWithValue(
                "before_project_id",
                NpgsqlDbType.Uuid,
                beforeProjectId is { } cursorProjectId ? cursorProjectId : DBNull.Value);
            command.Parameters.AddWithValue("limit", NpgsqlDbType.Integer, limit + 1);

            var projects = new List<StudentProjectListItem>(limit);
            var hasMore = false;
            await using (var reader = await command.ExecuteReaderAsync(cancellationToken))
            {
                while (await reader.ReadAsync(cancellationToken))
                {
                    if (projects.Count == limit)
                    {
                        hasMore = true;
                        break;
                    }

                    projects.Add(new StudentProjectListItem(
                        reader.GetGuid(0),
                        reader.GetInt32(1),
                        reader.GetString(2),
                        reader.GetString(3),
                        reader.GetString(4),
                        AsUtcOffset(reader.GetDateTime(5)),
                        AsUtcOffset(reader.GetDateTime(6))));
                }
            }

            await transaction.CommitAsync(cancellationToken);
            return new StudentProjectListResult(projects, hasMore);
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

    public async Task<StudentProjectRecord?> ReadOwnAsync(
        Guid accountId,
        Guid projectId,
        CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);
            // Account-scoped lookup keeps foreign project IDs indistinguishable from missing ones.
            await EnsureProjectExistsAsync(connection, transaction, accountId, projectId, cancellationToken);

            await using var command = connection.CreateCommand();
            command.Transaction = transaction;
            command.CommandText = """
                select project_id, version, document::text, created_at, updated_at
                from public.student_projects
                where account_id = @account_id and project_id = @project_id;
                """;
            command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
            command.Parameters.AddWithValue("project_id", NpgsqlDbType.Uuid, projectId);

            StudentProjectRecord? project = null;
            await using (var reader = await command.ExecuteReaderAsync(cancellationToken))
            {
                if (await reader.ReadAsync(cancellationToken))
                {
                    project = new StudentProjectRecord(
                        reader.GetGuid(0),
                        reader.GetInt32(1),
                        DeserializeDocument(reader.GetString(2)),
                        AsUtcOffset(reader.GetDateTime(3)),
                        AsUtcOffset(reader.GetDateTime(4)));
                }
            }

            await transaction.CommitAsync(cancellationToken);
            return project;
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

    public async Task<StudentProjectRevisionPage> ReadRevisionsOwnAsync(
        Guid accountId,
        Guid projectId,
        int limit,
        int? beforeVersion,
        CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);
            await EnsureProjectExistsAsync(connection, transaction, accountId, projectId, cancellationToken);

            await using var command = connection.CreateCommand();
            command.Transaction = transaction;
            command.CommandText = """
                select revision_id, version, document::text, created_at
                from public.student_project_revisions
                where account_id = @account_id
                  and project_id = @project_id
                  and (@before_version is null or version < @before_version)
                order by version desc
                limit @limit;
                """;
            command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
            command.Parameters.AddWithValue("project_id", NpgsqlDbType.Uuid, projectId);
            command.Parameters.AddWithValue(
                "before_version",
                NpgsqlDbType.Integer,
                beforeVersion is { } version ? version : DBNull.Value);
            command.Parameters.AddWithValue("limit", NpgsqlDbType.Integer, limit + 1);

            var revisions = new List<StudentProjectRevisionRecord>(limit);
            var hasMore = false;
            await using (var reader = await command.ExecuteReaderAsync(cancellationToken))
            {
                while (await reader.ReadAsync(cancellationToken))
                {
                    if (revisions.Count == limit)
                    {
                        hasMore = true;
                        break;
                    }

                    revisions.Add(new StudentProjectRevisionRecord(
                        reader.GetGuid(0),
                        reader.GetInt32(1),
                        DeserializeDocument(reader.GetString(2)),
                        AsUtcOffset(reader.GetDateTime(3))));
                }
            }

            await transaction.CommitAsync(cancellationToken);
            return new StudentProjectRevisionPage(revisions, hasMore);
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

    public async Task<StudentProjectMutation> CreateOwnAsync(
        Guid accountId,
        string idempotencyKey,
        string requestFingerprint,
        StudentProjectDocument document,
        CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);
            await RequireCloudConsentAsync(connection, transaction, accountId, cancellationToken);

            var replay = await ReserveCommandAsync(
                connection,
                transaction,
                accountId,
                idempotencyKey,
                "create",
                requestFingerprint,
                cancellationToken);
            if (replay is not null)
            {
                await transaction.CommitAsync(cancellationToken);
                return replay;
            }

            var projectId = Guid.NewGuid();
            var json = SerializeDocument(document);
            await using (var insert = connection.CreateCommand())
            {
                insert.Transaction = transaction;
                insert.CommandText = """
                    insert into public.student_projects (
                        project_id, account_id, version, document, created_at, updated_at
                    ) values (
                        @project_id, @account_id, 1, @document, now(), now()
                    );
                    """;
                insert.Parameters.AddWithValue("project_id", NpgsqlDbType.Uuid, projectId);
                insert.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                insert.Parameters.AddWithValue("document", NpgsqlDbType.Jsonb, json);
                await insert.ExecuteNonQueryAsync(cancellationToken);
            }

            await InsertRevisionAsync(
                connection,
                transaction,
                accountId,
                projectId,
                version: 1,
                json,
                cancellationToken);
            await CompleteCommandAsync(
                connection,
                transaction,
                accountId,
                idempotencyKey,
                projectId,
                version: 1,
                cancellationToken);
            await transaction.CommitAsync(cancellationToken);
            return new StudentProjectMutation("created", projectId, 1);
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

    public async Task<StudentProjectMutation> SaveOwnAsync(
        Guid accountId,
        Guid projectId,
        string idempotencyKey,
        string requestFingerprint,
        int expectedVersion,
        StudentProjectDocument document,
        CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);

            var replay = await ReserveCommandAsync(
                connection,
                transaction,
                accountId,
                idempotencyKey,
                "save",
                requestFingerprint,
                cancellationToken);
            if (replay is not null)
            {
                await transaction.CommitAsync(cancellationToken);
                return replay;
            }

            // Replay receipts remain valid after later deletion; only new writes need an owned live project.
            await EnsureProjectExistsAsync(connection, transaction, accountId, projectId, cancellationToken);
            await RequireCloudConsentAsync(connection, transaction, accountId, cancellationToken);

            var json = SerializeDocument(document);
            int? nextVersion;
            await using (var update = connection.CreateCommand())
            {
                update.Transaction = transaction;
                update.CommandText = """
                    update public.student_projects
                       set document = @document,
                           version = version + 1,
                           updated_at = now()
                     where account_id = @account_id
                       and project_id = @project_id
                       and version = @expected_version
                    returning version;
                    """;
                update.Parameters.AddWithValue("document", NpgsqlDbType.Jsonb, json);
                update.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                update.Parameters.AddWithValue("project_id", NpgsqlDbType.Uuid, projectId);
                update.Parameters.AddWithValue("expected_version", NpgsqlDbType.Integer, expectedVersion);
                var value = await update.ExecuteScalarAsync(cancellationToken);
                nextVersion = value is int version ? version : null;
            }

            if (nextVersion is null)
            {
                await ThrowNotFoundOrVersionConflictAsync(
                    connection,
                    transaction,
                    accountId,
                    projectId,
                    cancellationToken);
            }

            await InsertRevisionAsync(
                connection,
                transaction,
                accountId,
                projectId,
                nextVersion!.Value,
                json,
                cancellationToken);
            await CompleteCommandAsync(
                connection,
                transaction,
                accountId,
                idempotencyKey,
                projectId,
                nextVersion.Value,
                cancellationToken);
            await transaction.CommitAsync(cancellationToken);
            return new StudentProjectMutation("saved", projectId, nextVersion.Value);
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

    public async Task<StudentProjectMutation> PermanentlyDeleteOwnAsync(
        Guid accountId,
        Guid projectId,
        string idempotencyKey,
        string requestFingerprint,
        int expectedVersion,
        CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);

            // Keep permanent erasure available after consent is revoked so the owner can remove retained data.
            var replay = await ReserveCommandAsync(
                connection,
                transaction,
                accountId,
                idempotencyKey,
                "delete",
                requestFingerprint,
                cancellationToken);
            if (replay is not null)
            {
                await transaction.CommitAsync(cancellationToken);
                return replay;
            }

            var currentVersion = await ReadVersionForUpdateAsync(
                connection,
                transaction,
                accountId,
                projectId,
                cancellationToken);
            if (currentVersion is null) throw ProjectNotFound();
            if (currentVersion.Value != expectedVersion) throw ProjectVersionConflict();

            await SetTransactionFlagAsync(
                connection,
                transaction,
                "evidrilo.project_deletion",
                "on",
                cancellationToken);
            await using (var delete = connection.CreateCommand())
            {
                delete.Transaction = transaction;
                delete.CommandText = """
                    delete from public.student_projects
                    where account_id = @account_id and project_id = @project_id;
                    """;
                delete.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
                delete.Parameters.AddWithValue("project_id", NpgsqlDbType.Uuid, projectId);
                if (await delete.ExecuteNonQueryAsync(cancellationToken) != 1)
                    throw ProjectVersionConflict();
            }

            await CompleteCommandAsync(
                connection,
                transaction,
                accountId,
                idempotencyKey,
                projectId,
                currentVersion.Value,
                cancellationToken);
            await transaction.CommitAsync(cancellationToken);
            return new StudentProjectMutation("deleted", projectId, currentVersion.Value);
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

    private static async Task<StudentProjectMutation?> ReserveCommandAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        string idempotencyKey,
        string operation,
        string requestFingerprint,
        CancellationToken cancellationToken)
    {
        await using (var insert = connection.CreateCommand())
        {
            insert.Transaction = transaction;
            insert.CommandText = """
                insert into public.student_project_commands (
                    account_id, idempotency_key, operation, request_fingerprint, created_at
                ) values (
                    @account_id, @idempotency_key, @operation, @request_fingerprint, now()
                )
                on conflict (account_id, idempotency_key) do nothing;
                """;
            insert.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
            insert.Parameters.AddWithValue("idempotency_key", NpgsqlDbType.Text, idempotencyKey);
            insert.Parameters.AddWithValue("operation", NpgsqlDbType.Text, operation);
            insert.Parameters.AddWithValue("request_fingerprint", NpgsqlDbType.Text, requestFingerprint);
            if (await insert.ExecuteNonQueryAsync(cancellationToken) == 1) return null;
        }

        await using var read = connection.CreateCommand();
        read.Transaction = transaction;
        read.CommandText = """
            select operation, request_fingerprint, result_project_id, result_version
            from public.student_project_commands
            where account_id = @account_id and idempotency_key = @idempotency_key
            for update;
            """;
        read.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        read.Parameters.AddWithValue("idempotency_key", NpgsqlDbType.Text, idempotencyKey);
        await using var reader = await read.ExecuteReaderAsync(cancellationToken);
        if (!await reader.ReadAsync(cancellationToken))
            throw new ApiException(
                StatusCodes.Status503ServiceUnavailable,
                "DATABASE_SCHEMA_MISSING",
                "Student project storage is not ready.");

        var storedOperation = reader.GetString(0);
        var storedFingerprint = reader.GetString(1);
        var hasResult = !reader.IsDBNull(2) && !reader.IsDBNull(3);
        var storedProjectId = hasResult ? reader.GetGuid(2) : Guid.Empty;
        var storedVersion = hasResult ? reader.GetInt32(3) : 0;
        await reader.CloseAsync();

        if (!string.Equals(storedOperation, operation, StringComparison.Ordinal)
            || !string.Equals(storedFingerprint, requestFingerprint, StringComparison.Ordinal))
        {
            throw new ApiException(
                StatusCodes.Status409Conflict,
                "IDEMPOTENCY_KEY_REUSED",
                "The Idempotency-Key was already used for a different project change.");
        }

        if (!hasResult)
        {
            throw new ApiException(
                StatusCodes.Status503ServiceUnavailable,
                "DATABASE_SCHEMA_MISSING",
                "Student project storage is not ready.");
        }

        return new StudentProjectMutation("replayed", storedProjectId, storedVersion);
    }

    private static async Task CompleteCommandAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        string idempotencyKey,
        Guid projectId,
        int version,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            update public.student_project_commands
               set result_project_id = @project_id,
                   result_version = @version
             where account_id = @account_id
               and idempotency_key = @idempotency_key
               and result_project_id is null;
            """;
        command.Parameters.AddWithValue("project_id", NpgsqlDbType.Uuid, projectId);
        command.Parameters.AddWithValue("version", NpgsqlDbType.Integer, version);
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        command.Parameters.AddWithValue("idempotency_key", NpgsqlDbType.Text, idempotencyKey);
        if (await command.ExecuteNonQueryAsync(cancellationToken) != 1)
            throw new ApiException(
                StatusCodes.Status503ServiceUnavailable,
                "DATABASE_SCHEMA_MISSING",
                "Student project storage is not ready.");
    }

    private static async Task InsertRevisionAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        Guid projectId,
        int version,
        string json,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            insert into public.student_project_revisions (
                revision_id, project_id, account_id, version, document, created_at
            ) values (
                @revision_id, @project_id, @account_id, @version, @document, now()
            );
            """;
        command.Parameters.AddWithValue("revision_id", NpgsqlDbType.Uuid, Guid.NewGuid());
        command.Parameters.AddWithValue("project_id", NpgsqlDbType.Uuid, projectId);
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        command.Parameters.AddWithValue("version", NpgsqlDbType.Integer, version);
        command.Parameters.AddWithValue("document", NpgsqlDbType.Jsonb, json);
        await command.ExecuteNonQueryAsync(cancellationToken);
    }

    private static async Task EnsureProjectExistsAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        Guid projectId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            select 1
            from public.student_projects
            where account_id = @account_id and project_id = @project_id
            for key share;
            """;
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        command.Parameters.AddWithValue("project_id", NpgsqlDbType.Uuid, projectId);
        if (await command.ExecuteScalarAsync(cancellationToken) is null)
            throw ProjectNotFound();
    }

    private static async Task<int?> ReadVersionForUpdateAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        Guid projectId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            select version
            from public.student_projects
            where account_id = @account_id and project_id = @project_id
            for update;
            """;
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        command.Parameters.AddWithValue("project_id", NpgsqlDbType.Uuid, projectId);
        var value = await command.ExecuteScalarAsync(cancellationToken);
        return value is int version ? version : null;
    }

    private static async Task ThrowNotFoundOrVersionConflictAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        Guid projectId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            select exists (
                select 1 from public.student_projects
                where account_id = @account_id and project_id = @project_id
            );
            """;
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        command.Parameters.AddWithValue("project_id", NpgsqlDbType.Uuid, projectId);
        if (await command.ExecuteScalarAsync(cancellationToken) is not true) throw ProjectNotFound();
        throw ProjectVersionConflict();
    }

    private static async Task SetRequestAccountAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        CancellationToken cancellationToken)
    {
        await SetTransactionFlagAsync(
            connection,
            transaction,
            "request.jwt.claim.sub",
            accountId.ToString(),
            cancellationToken);
    }

    private static async Task<StudentProjectCloudConsentState?> ReadCloudConsentForUpdateAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            select granted, policy_version, granted_at, revoked_at, updated_at
            from public.student_project_cloud_consents
            where account_id = @account_id
            for update;
            """;
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        if (!await reader.ReadAsync(cancellationToken)) return null;
        return new StudentProjectCloudConsentState(
            reader.GetBoolean(0),
            reader.GetString(1),
            reader.IsDBNull(2) ? null : AsUtcOffset(reader.GetDateTime(2)),
            reader.IsDBNull(3) ? null : AsUtcOffset(reader.GetDateTime(3)),
            AsUtcOffset(reader.GetDateTime(4)));
    }

    private static async Task LockCloudConsentAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = "select pg_advisory_xact_lock(hashtextextended(@account_id::text, 0));";
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        await command.ExecuteNonQueryAsync(cancellationToken);
    }

    private static async Task RequireCloudConsentAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            select granted
            from public.student_project_cloud_consents
            where account_id = @account_id
              and policy_version = @policy_version
              and granted = true
            for share;
            """;
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        command.Parameters.AddWithValue(
            "policy_version",
            NpgsqlDbType.Text,
            StudentProjectCloudConsentPolicy.CurrentVersion);
        if (await command.ExecuteScalarAsync(cancellationToken) is not true)
        {
            throw new ApiException(
                StatusCodes.Status403Forbidden,
                "PROJECT_CLOUD_CONSENT_REQUIRED",
                "Enable project cloud storage before creating or saving student projects.");
        }
    }

    private static async Task SetTransactionFlagAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        string name,
        string value,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = "select set_config(@name, @value, true);";
        command.Parameters.AddWithValue("name", NpgsqlDbType.Text, name);
        command.Parameters.AddWithValue("value", NpgsqlDbType.Text, value);
        await command.ExecuteNonQueryAsync(cancellationToken);
    }

    private static string SerializeDocument(StudentProjectDocument document) =>
        JsonSerializer.Serialize(document, JsonOptions);

    private static StudentProjectDocument DeserializeDocument(string json)
    {
        try
        {
            return JsonSerializer.Deserialize<StudentProjectDocument>(json, JsonOptions)
                ?? throw new JsonException();
        }
        catch (JsonException exception)
        {
            throw new ApiException(
                StatusCodes.Status503ServiceUnavailable,
                "DATABASE_SCHEMA_MISSING",
                "Student project storage is not ready.",
                exception);
        }
    }

    private static DateTimeOffset AsUtcOffset(DateTime value) =>
        new(DateTime.SpecifyKind(value, DateTimeKind.Utc));

    private static ApiException ProjectNotFound() => new(
        StatusCodes.Status404NotFound,
        "PROJECT_NOT_FOUND",
        "The student project was not found.");

    private static ApiException ProjectVersionConflict() => new(
        StatusCodes.Status409Conflict,
        "PROJECT_VERSION_CONFLICT",
        "The student project changed. Reload it before saving again.");

    private static ApiException SchemaMissing() => new(
        StatusCodes.Status503ServiceUnavailable,
        "DATABASE_SCHEMA_MISSING",
        "Student project storage is not ready.");

    private static ApiException DatabaseUnavailable(NpgsqlException exception) => new(
        StatusCodes.Status503ServiceUnavailable,
        "DATABASE_UNAVAILABLE",
        "Student project storage is temporarily unavailable.",
        exception);
}
