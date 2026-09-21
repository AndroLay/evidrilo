using System.Text.Json;
using Evidrilo.Api.Common;
using Npgsql;
using NpgsqlTypes;

namespace Evidrilo.Api.Account;

public interface IAccountExportStore
{
    Task<JsonElement> GetOwnAsync(Guid accountId, CancellationToken cancellationToken);
}

public sealed class DatabaseUnavailableAccountExportStore : IAccountExportStore
{
    public Task<JsonElement> GetOwnAsync(Guid accountId, CancellationToken cancellationToken) =>
        throw new ApiException(
            StatusCodes.Status503ServiceUnavailable,
            "DATABASE_NOT_CONFIGURED",
            "Account export is not configured.");
}

public sealed class NpgsqlAccountExportStore : IAccountExportStore, IDisposable
{
    private readonly NpgsqlDataSource dataSource;

    public NpgsqlAccountExportStore(string connectionString)
    {
        dataSource = NpgsqlDataSource.Create(connectionString);
    }

    public async Task<JsonElement> GetOwnAsync(
        Guid accountId,
        CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var command = connection.CreateCommand();
            command.CommandText = """
                select jsonb_build_object(
                    'localDrafts', 'not_on_server',
                    'profile', coalesce((
                        select jsonb_build_object(
                            'createdAt', profile.created_at,
                            'deletedAt', profile.deleted_at
                        )
                        from public.account_profiles profile
                        where profile.account_id = @account_id
                    ), '{}'::jsonb),
                    'attemptCommands', coalesce((
                        select jsonb_agg(jsonb_build_object(
                            'commandId', command.command_id,
                            'attemptId', command.attempt_id,
                            'caseVersionId', command.case_version_id,
                            'commandType', command.command_type,
                            'revisionNumber', command.revision_number,
                            'clientOccurredAt', command.client_occurred_at,
                            'snapshotDigest', command.snapshot_digest,
                            'receivedAt', command.received_at
                        ) order by command.received_at, command.command_id)
                        from public.attempt_commands command
                        where command.account_id = @account_id
                    ), '[]'::jsonb),
                    'syncChanges', coalesce((
                        select jsonb_agg(jsonb_build_object(
                            'serverSequence', change.server_sequence,
                            'commandId', change.command_id,
                            'attemptId', change.attempt_id,
                            'caseVersionId', change.case_version_id,
                            'commandType', change.command_type,
                            'revisionNumber', change.revision_number,
                            'snapshotDigest', change.snapshot_digest,
                            'createdAt', change.created_at
                        ) order by change.server_sequence)
                        from public.sync_changes change
                        where change.account_id = @account_id
                    ), '[]'::jsonb),
                    'analyticsEvents', coalesce((
                        select jsonb_agg(jsonb_build_object(
                            'clientEventId', event.client_event_id,
                            'eventName', event.event_name,
                            'eventVersion', event.event_version,
                            'occurredAt', event.occurred_at,
                            'source', event.source,
                            'consentVersion', event.consent_version,
                            'properties', event.properties
                        ) order by event.occurred_at, event.client_event_id)
                        from public.analytics_events event
                        where event.account_id = @account_id
                    ), '[]'::jsonb),
                    'recommendationEvents', coalesce((
                        select jsonb_agg(jsonb_build_object(
                            'clientEventId', event.client_event_id,
                            'caseVersionId', event.case_version_id,
                            'interaction', event.interaction,
                            'calculationVersion', event.calculation_version,
                            'reasonCode', event.reason_code,
                            'createdAt', event.created_at
                        ) order by event.created_at, event.client_event_id)
                        from public.recommendation_events event
                        where event.account_id = @account_id
                    ), '[]'::jsonb),
                    'aiAuditEvents', coalesce((
                        select jsonb_agg(jsonb_build_object(
                            'requestId', event.request_id,
                            'promptVersion', event.prompt_version,
                            'provider', event.provider,
                            'outcome', event.outcome,
                            'reasonCode', event.reason_code,
                            'createdAt', event.created_at
                        ) order by event.created_at, event.request_id)
                        from public.ai_audit_events event
                        where event.account_id = @account_id
                    ), '[]'::jsonb),
                    'entitlements', coalesce((
                        select jsonb_agg(jsonb_build_object(
                            'entitlement', entitlement.entitlement,
                            'status', entitlement.status,
                            'updatedAt', entitlement.updated_at,
                            'sourceOccurredAt', entitlement.source_occurred_at
                        ) order by entitlement.entitlement)
                        from public.entitlements entitlement
                        where entitlement.account_id = @account_id
                    ), '[]'::jsonb),
                    'entitlementEvents', coalesce((
                        select jsonb_agg(jsonb_build_object(
                            'providerEventId', event.provider_event_id,
                            'entitlement', event.entitlement,
                            'status', event.status,
                            'occurredAt', event.occurred_at,
                            'receivedAt', event.received_at
                        ) order by event.occurred_at, event.provider_event_id)
                        from public.entitlement_events event
                        where event.account_id = @account_id
                    ), '[]'::jsonb),
                    'progressDaily', coalesce((
                        select jsonb_agg(jsonb_build_object(
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
                        ) order by progress.projection_date)
                        from public.progress_daily_projections progress
                        where progress.account_id = @account_id
                    ), '[]'::jsonb)
                );
                """;
            command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
            var raw = await command.ExecuteScalarAsync(cancellationToken) as string;
            if (string.IsNullOrWhiteSpace(raw))
            {
                throw new ApiException(
                    StatusCodes.Status503ServiceUnavailable,
                    "DATABASE_SCHEMA_MISSING",
                    "The account export schema is not ready.");
            }

            using var document = JsonDocument.Parse(raw);
            return document.RootElement.Clone();
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

    public void Dispose() => dataSource.Dispose();
}
