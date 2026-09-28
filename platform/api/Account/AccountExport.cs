using System.Data;
using System.Text.Json;
using Evidrilo.Api.Common;
using Npgsql;
using NpgsqlTypes;

namespace Evidrilo.Api.Account;

public interface IAccountExportStore
{
    Task<IAccountExportSession> OpenOwnAsync(Guid accountId, CancellationToken cancellationToken);
}

public interface IAccountExportSession : IAsyncDisposable
{
    Task WriteDataAsync(Utf8JsonWriter writer, CancellationToken cancellationToken);
}

public sealed class DatabaseUnavailableAccountExportStore : IAccountExportStore
{
    public Task<IAccountExportSession> OpenOwnAsync(Guid accountId, CancellationToken cancellationToken) =>
        throw new ApiException(
            StatusCodes.Status503ServiceUnavailable,
            "DATABASE_NOT_CONFIGURED",
            "Account export is not configured.");
}

public sealed class NpgsqlAccountExportStore : IAccountExportStore, IDisposable
{
    private const string DatabaseUnavailableCode = "DATABASE_UNAVAILABLE";
    private readonly NpgsqlDataSource dataSource;

    public NpgsqlAccountExportStore(string connectionString)
    {
        dataSource = NpgsqlDataSource.Create(connectionString);
    }

    public async Task<IAccountExportSession> OpenOwnAsync(
        Guid accountId,
        CancellationToken cancellationToken)
    {
        NpgsqlConnection? connection = null;
        NpgsqlTransaction? transaction = null;
        try
        {
            connection = await dataSource.OpenConnectionAsync(cancellationToken);
            transaction = await connection.BeginTransactionAsync(IsolationLevel.RepeatableRead, cancellationToken);

            await using var command = connection.CreateCommand();
            command.Transaction = transaction;
            command.CommandText = """
                select to_regclass('public.account_profiles') is not null
                   and to_regclass('public.attempt_commands') is not null
                   and to_regclass('public.sync_changes') is not null
                   and to_regclass('public.analytics_events') is not null
                   and to_regclass('public.recommendation_events') is not null
                   and to_regclass('public.ai_audit_events') is not null
                   and to_regclass('public.ai_credit_consents') is not null
                   and to_regclass('public.ai_credit_grants') is not null
                   and to_regclass('public.ai_credit_reservations') is not null
                   and to_regclass('public.notification_preferences') is not null
                   and to_regclass('public.entitlements') is not null
                   and to_regclass('public.entitlement_events') is not null
                   and to_regclass('public.progress_daily_projections') is not null
                   and to_regclass('public.student_projects') is not null
                   and to_regclass('public.student_project_revisions') is not null
                   and to_regclass('public.student_project_cloud_consents') is not null
                   and to_regclass('public.student_project_cloud_consent_events') is not null;
                """;
            var schemaReady = await command.ExecuteScalarAsync(cancellationToken) as bool? ?? false;
            if (!schemaReady)
                throw new ApiException(
                    StatusCodes.Status503ServiceUnavailable,
                    "DATABASE_SCHEMA_MISSING",
                    "The account export schema is not ready.");

            var session = new NpgsqlAccountExportSession(connection, transaction, accountId);
            connection = null;
            transaction = null;
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
            throw new ApiException(
                StatusCodes.Status503ServiceUnavailable,
                DatabaseUnavailableCode,
                "Account export is temporarily unavailable.",
                exception);
        }
        finally
        {
            if (transaction is not null)
                await transaction.DisposeAsync();
            if (connection is not null)
                await connection.DisposeAsync();
        }
    }

    public void Dispose() => dataSource.Dispose();
}

internal sealed class NpgsqlAccountExportSession(
    NpgsqlConnection connection,
    NpgsqlTransaction transaction,
    Guid accountId) : IAccountExportSession
{
    private const int FlushThresholdBytes = 32 * 1024;
    private bool isDisposed;

    public async Task WriteDataAsync(Utf8JsonWriter writer, CancellationToken cancellationToken)
    {
        ObjectDisposedException.ThrowIf(isDisposed, this);

        try
        {
            writer.WriteStartObject();
            writer.WriteString("localDrafts", "not_on_server");

            writer.WritePropertyName("profile");
            await WriteJsonValueAsync(
                writer,
                """
                select coalesce((
                    select jsonb_build_object(
                        'createdAt', profile.created_at,
                        'deletedAt', profile.deleted_at
                    )
                    from public.account_profiles profile
                    where profile.account_id = @account_id
                ), '{}'::jsonb)::text;
                """,
                cancellationToken);

            await WriteJsonArrayAsync(
                writer,
                "attemptCommands",
                """
                select jsonb_build_object(
                    'commandId', command.command_id,
                    'attemptId', command.attempt_id,
                    'caseVersionId', command.case_version_id,
                    'commandType', command.command_type,
                    'revisionNumber', command.revision_number,
                    'clientOccurredAt', command.client_occurred_at,
                    'snapshotDigest', command.snapshot_digest,
                    'receivedAt', command.received_at
                )::text
                from public.attempt_commands command
                where command.account_id = @account_id
                order by command.received_at, command.command_id;
                """,
                cancellationToken);

            await WriteJsonArrayAsync(
                writer,
                "syncChanges",
                """
                select jsonb_build_object(
                    'serverSequence', change.server_sequence,
                    'commandId', change.command_id,
                    'attemptId', change.attempt_id,
                    'caseVersionId', change.case_version_id,
                    'commandType', change.command_type,
                    'revisionNumber', change.revision_number,
                    'snapshotDigest', change.snapshot_digest,
                    'createdAt', change.created_at
                )::text
                from public.sync_changes change
                where change.account_id = @account_id
                order by change.server_sequence;
                """,
                cancellationToken);

            await WriteJsonArrayAsync(
                writer,
                "analyticsEvents",
                """
                select jsonb_build_object(
                    'clientEventId', event.client_event_id,
                    'eventName', event.event_name,
                    'eventVersion', event.event_version,
                    'occurredAt', event.occurred_at,
                    'source', event.source,
                    'consentVersion', event.consent_version,
                    'properties', event.properties
                )::text
                from public.analytics_events event
                where event.account_id = @account_id
                order by event.occurred_at, event.client_event_id;
                """,
                cancellationToken);

            await WriteJsonArrayAsync(
                writer,
                "recommendationEvents",
                """
                select jsonb_build_object(
                    'clientEventId', event.client_event_id,
                    'caseVersionId', event.case_version_id,
                    'interaction', event.interaction,
                    'calculationVersion', event.calculation_version,
                    'reasonCode', event.reason_code,
                    'createdAt', event.created_at
                )::text
                from public.recommendation_events event
                where event.account_id = @account_id
                order by event.created_at, event.client_event_id;
                """,
                cancellationToken);

            await WriteJsonArrayAsync(
                writer,
                "aiAuditEvents",
                """
                select jsonb_build_object(
                    'requestId', event.request_id,
                    'promptVersion', event.prompt_version,
                    'provider', event.provider,
                    'outcome', event.outcome,
                    'reasonCode', event.reason_code,
                    'createdAt', event.created_at
                )::text
                from public.ai_audit_events event
                where event.account_id = @account_id
                order by event.created_at, event.request_id;
                """,
                cancellationToken);

            writer.WritePropertyName("aiCreditConsent");
            await WriteJsonValueAsync(
                writer,
                """
                select coalesce((
                    select jsonb_build_object(
                        'consentVersion', consent.consent_version,
                        'consentedAt', consent.consented_at,
                        'revokedAt', consent.revoked_at
                    )
                    from public.ai_credit_consents consent
                    where consent.account_id = @account_id
                ), '{}'::jsonb)::text;
                """,
                cancellationToken);

            await WriteJsonArrayAsync(
                writer,
                "aiCreditGrants",
                """
                select jsonb_build_object(
                    'grantKind', credit_grant.grant_kind,
                    'grantKey', credit_grant.grant_key,
                    'credits', credit_grant.credits,
                    'reservedCredits', credit_grant.reserved_credits,
                    'consumedCredits', credit_grant.consumed_credits,
                    'startsAt', credit_grant.starts_at,
                    'expiresAt', credit_grant.expires_at,
                    'createdAt', credit_grant.created_at
                )::text
                from public.ai_credit_grants credit_grant
                where credit_grant.account_id = @account_id
                order by credit_grant.created_at, credit_grant.grant_kind, credit_grant.grant_key;
                """,
                cancellationToken);

            await WriteJsonArrayAsync(
                writer,
                "aiCreditReservations",
                """
                select jsonb_build_object(
                    'requestId', reservation.request_id,
                    'status', reservation.status,
                    'reservedAt', reservation.reserved_at,
                    'completedAt', reservation.completed_at
                )::text
                from public.ai_credit_reservations reservation
                where reservation.account_id = @account_id
                order by reservation.reserved_at, reservation.request_id;
                """,
                cancellationToken);

            writer.WritePropertyName("notificationPreferences");
            await WriteJsonValueAsync(
                writer,
                """
                select coalesce((
                    select jsonb_build_object(
                        'enabled', preference.enabled,
                        'continueUnfinishedEnabled', preference.continue_unfinished_enabled,
                        'reviewCompletedEnabled', preference.review_completed_enabled,
                        'cadence', preference.cadence,
                        'localHour', preference.local_hour,
                        'localMinute', preference.local_minute,
                        'revision', preference.revision,
                        'updatedAt', preference.updated_at
                    )
                    from public.notification_preferences preference
                    where preference.account_id = @account_id
                ), '{}'::jsonb)::text;
                """,
                cancellationToken);

            await WriteJsonArrayAsync(
                writer,
                "entitlements",
                """
                select jsonb_build_object(
                    'entitlement', entitlement.entitlement,
                    'status', entitlement.status,
                    'updatedAt', entitlement.updated_at,
                    'sourceOccurredAt', entitlement.source_occurred_at
                )::text
                from public.entitlements entitlement
                where entitlement.account_id = @account_id
                order by entitlement.entitlement;
                """,
                cancellationToken);

            await WriteJsonArrayAsync(
                writer,
                "entitlementEvents",
                """
                select jsonb_build_object(
                    'providerEventId', event.provider_event_id,
                    'entitlement', event.entitlement,
                    'status', event.status,
                    'occurredAt', event.occurred_at,
                    'receivedAt', event.received_at
                )::text
                from public.entitlement_events event
                where event.account_id = @account_id
                order by event.occurred_at, event.provider_event_id;
                """,
                cancellationToken);

            await WriteJsonArrayAsync(
                writer,
                "progressDaily",
                """
                select jsonb_build_object(
                    'projectionDate', progress.projection_date,
                    'calculationVersion', progress.calculation_version,
                    'attemptsObserved', progress.attempts_observed,
                    'completedAttempts', progress.completed_attempts,
                    'revisionsObserved', progress.revisions_observed,
                    'passCount', progress.pass_count,
                    'actionRequiredCount', progress.action_required_count,
                    'abstentionCount', progress.abstention_count,
                    'coverage', progress.coverage,
                    'rebuiltAt', progress.rebuilt_at
                )::text
                from public.progress_daily_projections progress
                where progress.account_id = @account_id
                order by progress.projection_date;
                """,
                cancellationToken);

            await WriteJsonArrayAsync(
                writer,
                "studentProjects",
                """
                select jsonb_build_object(
                    'projectId', project.project_id,
                    'projectVersion', project.version,
                    'project', project.document,
                    'createdAt', project.created_at,
                    'updatedAt', project.updated_at
                )::text
                from public.student_projects project
                where project.account_id = @account_id
                order by project.created_at, project.project_id;
                """,
                cancellationToken);

            await WriteJsonArrayAsync(
                writer,
                "studentProjectRevisions",
                """
                select jsonb_build_object(
                    'revisionId', revision.revision_id,
                    'projectId', revision.project_id,
                    'version', revision.version,
                    'project', revision.document,
                    'createdAt', revision.created_at
                )::text
                from public.student_project_revisions revision
                where revision.account_id = @account_id
                order by revision.project_id, revision.version;
                """,
                cancellationToken);

            writer.WritePropertyName("studentProjectCloudConsent");
            await WriteJsonValueAsync(
                writer,
                """
                select coalesce((
                    select jsonb_build_object(
                        'granted', consent.granted,
                        'policyVersion', consent.policy_version,
                        'grantedAt', consent.granted_at,
                        'revokedAt', consent.revoked_at,
                        'updatedAt', consent.updated_at
                    )
                    from public.student_project_cloud_consents consent
                    where consent.account_id = @account_id
                ), jsonb_build_object(
                    'granted', false,
                    'policyVersion', 'student-project-cloud.v1',
                    'grantedAt', null,
                    'revokedAt', null,
                    'updatedAt', null
                ))::text;
                """,
                cancellationToken);

            await WriteJsonArrayAsync(
                writer,
                "studentProjectCloudConsentEvents",
                """
                select jsonb_build_object(
                    'eventId', event.event_id,
                    'policyVersion', event.policy_version,
                    'decision', event.decision,
                    'decidedAt', event.decided_at
                )::text
                from public.student_project_cloud_consent_events event
                where event.account_id = @account_id
                order by event.decided_at, event.event_id;
                """,
                cancellationToken);

            writer.WriteEndObject();
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
                "Account export is temporarily unavailable.",
                exception);
        }
        catch (JsonException exception)
        {
            throw new ApiException(
                StatusCodes.Status503ServiceUnavailable,
                "DATABASE_SCHEMA_MISSING",
                "The account export schema is not ready.",
                exception);
        }
    }

    public async ValueTask DisposeAsync()
    {
        if (isDisposed) return;
        isDisposed = true;
        await transaction.DisposeAsync();
        await connection.DisposeAsync();
    }

    private async Task WriteJsonValueAsync(
        Utf8JsonWriter writer,
        string commandText,
        CancellationToken cancellationToken)
    {
        await using var command = CreateCommand(commandText);
        var value = await command.ExecuteScalarAsync(cancellationToken) as string;
        if (string.IsNullOrWhiteSpace(value))
            throw new ApiException(
                StatusCodes.Status503ServiceUnavailable,
                "DATABASE_SCHEMA_MISSING",
                "The account export schema is not ready.");

        writer.WriteRawValue(value, skipInputValidation: false);
        await FlushWhenNeededAsync(writer, cancellationToken);
    }

    private async Task WriteJsonArrayAsync(
        Utf8JsonWriter writer,
        string propertyName,
        string commandText,
        CancellationToken cancellationToken)
    {
        writer.WritePropertyName(propertyName);
        writer.WriteStartArray();
        await using var command = CreateCommand(commandText);
        await using var reader = await command.ExecuteReaderAsync(
            CommandBehavior.SequentialAccess,
            cancellationToken);
        while (await reader.ReadAsync(cancellationToken))
        {
            writer.WriteRawValue(reader.GetString(0), skipInputValidation: false);
            await FlushWhenNeededAsync(writer, cancellationToken);
        }
        writer.WriteEndArray();
        await FlushWhenNeededAsync(writer, cancellationToken);
    }

    private NpgsqlCommand CreateCommand(string commandText)
    {
        var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = commandText;
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        return command;
    }

    private static async Task FlushWhenNeededAsync(
        Utf8JsonWriter writer,
        CancellationToken cancellationToken)
    {
        if (writer.BytesPending >= FlushThresholdBytes)
            await writer.FlushAsync(cancellationToken);
    }
}
