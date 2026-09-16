using Evidrilo.Api.Common;
using Npgsql;
using NpgsqlTypes;

namespace Evidrilo.Api.Analytics;

public interface IAnalyticsStore
{
    Task<AnalyticsAppendResult> AppendAsync(
        Guid accountId,
        AnalyticsEventRequest request,
        CancellationToken cancellationToken);
}

public sealed class DatabaseUnavailableAnalyticsStore : IAnalyticsStore
{
    public Task<AnalyticsAppendResult> AppendAsync(
        Guid accountId,
        AnalyticsEventRequest request,
        CancellationToken cancellationToken) => throw new ApiException(
        StatusCodes.Status503ServiceUnavailable,
        "DATABASE_NOT_CONFIGURED",
        "Analytics is not configured.");
}

public sealed class NpgsqlAnalyticsStore : IAnalyticsStore, IDisposable
{
    private readonly NpgsqlDataSource dataSource;

    public NpgsqlAnalyticsStore(string connectionString)
    {
        dataSource = NpgsqlDataSource.Create(connectionString);
    }

    public async Task<AnalyticsAppendResult> AppendAsync(
        Guid accountId,
        AnalyticsEventRequest request,
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
                insert into public.analytics_events (
                    account_id, client_event_id, event_name, event_version,
                    occurred_at, source, consent_version, properties
                ) values (
                    @account_id, @client_event_id, @event_name, @event_version,
                    @occurred_at, @source, 'analytics.v1', jsonb_strip_nulls(jsonb_build_object(
                        'attemptId', @attempt_id,
                        'caseVersionId', @case_version_id,
                        'outcome', @outcome,
                        'skillId', @skill_id,
                        'revisionChanged', @revision_changed,
                        'surfaceId', @surface_id,
                        'action', @action,
                        'productId', @product_id,
                        'errorCode', @error_code
                    ))
                ) on conflict (account_id, client_event_id) do nothing
                returning client_event_id;
                """;
            AddEventParameters(command, accountId, request);
            var inserted = await command.ExecuteScalarAsync(cancellationToken);
            var result = inserted is null or DBNull
                ? await ResolveDuplicateAsync(connection, transaction, accountId, request, cancellationToken)
                : new AnalyticsAppendResult("accepted");
            await transaction.CommitAsync(cancellationToken);
            return result;
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
                "Analytics is temporarily unavailable.",
                exception);
        }
    }

    public void Dispose() => dataSource.Dispose();

    private static async Task<AnalyticsAppendResult> ResolveDuplicateAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        AnalyticsEventRequest request,
        CancellationToken cancellationToken)
    {
        await using var duplicate = connection.CreateCommand();
        duplicate.Transaction = transaction;
        duplicate.CommandText = """
            select 1
            from public.analytics_events
            where account_id = @account_id
              and client_event_id = @client_event_id
              and event_name = @event_name
              and event_version = @event_version
              and occurred_at = @occurred_at
              and source = @source
              and consent_version = 'analytics.v1'
              and properties = jsonb_strip_nulls(jsonb_build_object(
                  'attemptId', @attempt_id,
                  'caseVersionId', @case_version_id,
                  'outcome', @outcome,
                  'skillId', @skill_id,
                  'revisionChanged', @revision_changed,
                  'surfaceId', @surface_id,
                  'action', @action,
                  'productId', @product_id,
                  'errorCode', @error_code
              ));
            """;
        AddEventParameters(duplicate, accountId, request);
        if (await duplicate.ExecuteScalarAsync(cancellationToken) is not null)
            return new AnalyticsAppendResult("duplicate");

        throw new ApiException(
            StatusCodes.Status409Conflict,
            "ANALYTICS_IDEMPOTENCY_KEY_REUSE",
            "The analytics event identifier was reused.");
    }

    private static void AddEventParameters(
        NpgsqlCommand command,
        Guid accountId,
        AnalyticsEventRequest request)
    {
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        command.Parameters.AddWithValue("client_event_id", NpgsqlDbType.Uuid, request.ClientEventId);
        command.Parameters.AddWithValue("event_name", NpgsqlDbType.Text, request.EventName.ToWire());
        command.Parameters.AddWithValue("event_version", NpgsqlDbType.Integer, request.EventVersion);
        command.Parameters.AddWithValue("occurred_at", NpgsqlDbType.TimestampTz, request.OccurredAt);
        command.Parameters.AddWithValue("source", NpgsqlDbType.Text, request.Source.ToWire());
        command.Parameters.AddWithValue("attempt_id", NpgsqlDbType.Uuid, (object?)request.Properties.AttemptId ?? DBNull.Value);
        command.Parameters.AddWithValue("case_version_id", NpgsqlDbType.Text, (object?)request.Properties.CaseVersionId ?? DBNull.Value);
        command.Parameters.AddWithValue("outcome", NpgsqlDbType.Text, (object?)request.Properties.Outcome ?? DBNull.Value);
        command.Parameters.AddWithValue("skill_id", NpgsqlDbType.Text, (object?)request.Properties.SkillId ?? DBNull.Value);
        command.Parameters.AddWithValue("revision_changed", NpgsqlDbType.Boolean, (object?)request.Properties.RevisionChanged ?? DBNull.Value);
        command.Parameters.AddWithValue("surface_id", NpgsqlDbType.Text, (object?)request.Properties.SurfaceId ?? DBNull.Value);
        command.Parameters.AddWithValue("action", NpgsqlDbType.Text, (object?)request.Properties.Action ?? DBNull.Value);
        command.Parameters.AddWithValue("product_id", NpgsqlDbType.Text, (object?)request.Properties.ProductId ?? DBNull.Value);
        command.Parameters.AddWithValue("error_code", NpgsqlDbType.Text, (object?)request.Properties.ErrorCode ?? DBNull.Value);
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
}

public static class AnalyticsWireExtensions
{
    public static string ToWire(this AnalyticsEventName value) => value switch
    {
        AnalyticsEventName.PracticeStarted => "practice_started",
        AnalyticsEventName.AttemptCompleted => "attempt_completed",
        AnalyticsEventName.RevisionRecorded => "revision_recorded",
        AnalyticsEventName.PaywallViewed => "paywall_viewed",
        AnalyticsEventName.PremiumAction => "premium_action",
        AnalyticsEventName.ClientError => "client_error",
        AnalyticsEventName.RecommendationShown => "recommendation_shown",
        AnalyticsEventName.RecommendationAccepted => "recommendation_accepted",
        AnalyticsEventName.RecommendationDismissed => "recommendation_dismissed",
        _ => throw new ArgumentOutOfRangeException(nameof(value)),
    };

    public static string ToWire(this AnalyticsEventSource value) => value switch
    {
        AnalyticsEventSource.Mobile => "mobile",
        _ => throw new ArgumentOutOfRangeException(nameof(value)),
    };
}
