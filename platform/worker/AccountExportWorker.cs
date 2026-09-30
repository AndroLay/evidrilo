using System.Data;
using System.Text.Json;
using Evidrilo.Platform;
using Microsoft.Extensions.Hosting;
using Microsoft.Extensions.Logging;
using Npgsql;

namespace Evidrilo.Worker;

public interface IAccountExportSnapshotBuilder
{
    Task<byte[]> BuildAsync(AccountExportWorkItem item, CancellationToken cancellationToken);
}

public sealed class AccountExportArtifactTooLargeException()
    : IOException("The account export artifact exceeded its byte limit.");

public sealed class BoundedAccountExportArtifactStream(int maximumBytes) : MemoryStream
{
    public override void Write(byte[] buffer, int offset, int count)
    {
        EnsureWithinLimit(count);
        base.Write(buffer, offset, count);
    }

    public override void Write(ReadOnlySpan<byte> buffer)
    {
        EnsureWithinLimit(buffer.Length);
        base.Write(buffer);
    }

    public override void WriteByte(byte value)
    {
        EnsureWithinLimit(1);
        base.WriteByte(value);
    }

    public override Task WriteAsync(byte[] buffer, int offset, int count, CancellationToken cancellationToken)
    {
        EnsureWithinLimit(count);
        return base.WriteAsync(buffer, offset, count, cancellationToken);
    }

    public override ValueTask WriteAsync(ReadOnlyMemory<byte> buffer, CancellationToken cancellationToken = default)
    {
        EnsureWithinLimit(buffer.Length);
        return base.WriteAsync(buffer, cancellationToken);
    }

    private void EnsureWithinLimit(int count)
    {
        if (count < 0 || count > maximumBytes - Length)
            throw new AccountExportArtifactTooLargeException();
    }
}

public sealed class NpgsqlAccountExportSnapshotBuilder(
    NpgsqlDataSource dataSource,
    ILogger<NpgsqlAccountExportSnapshotBuilder> logger) : IAccountExportSnapshotBuilder
{
    private const int MaximumArtifactBytes = 64 * 1024 * 1024;

    public async Task<byte[]> BuildAsync(AccountExportWorkItem item, CancellationToken cancellationToken)
    {
        try
        {
            logger.LogInformation("Building account export snapshot for {ExportId}", item.ExportId);
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(IsolationLevel.RepeatableRead, cancellationToken);
            await EnsureSchemaReadyAsync(connection, transaction, cancellationToken);

            await using var payload = new BoundedAccountExportArtifactStream(MaximumArtifactBytes);
            await using (var writer = new Utf8JsonWriter(payload))
            {
                writer.WriteStartObject();
                writer.WriteString("schema", "evidrilo.account-export");
                writer.WriteString("version", "2");
                writer.WriteString("accountId", item.AccountId);
                writer.WriteString("generatedAt", DateTimeOffset.UtcNow);
                writer.WritePropertyName("data");
                await AccountExportDataWriter.WriteAsync(
                    writer,
                    connection,
                    transaction,
                    item.AccountId,
                    AccountExportDataVersion.V2,
                    cancellationToken);
                writer.WriteString("requestId", item.RequestId);
                writer.WriteEndObject();
                await writer.FlushAsync(cancellationToken);
            }

            logger.LogInformation(
                "Account export snapshot serialized for {ExportId} with {ArtifactBytes} bytes",
                item.ExportId,
                payload.Length);
            await transaction.CommitAsync(cancellationToken);
            logger.LogInformation("Account export snapshot committed for {ExportId}", item.ExportId);
            return payload.ToArray();
        }
        catch (AccountExportArtifactTooLargeException)
        {
            throw;
        }
        catch (AccountExportSchemaUnavailableException exception)
        {
            throw new WorkerDatabaseException("DATABASE_SCHEMA_MISSING", exception);
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (NpgsqlException exception)
        {
            var code = exception is PostgresException postgresException
                && postgresException.SqlState is PostgresErrorCodes.UndefinedTable or PostgresErrorCodes.UndefinedColumn
                ? "DATABASE_SCHEMA_MISSING"
                : "DATABASE_UNAVAILABLE";
            throw new WorkerDatabaseException(code, exception);
        }
        catch (JsonException exception)
        {
            throw new WorkerDatabaseException("DATABASE_SCHEMA_MISSING", exception);
        }
    }

    private static async Task EnsureSchemaReadyAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        CancellationToken cancellationToken)
    {
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
               and to_regclass('public.student_project_cloud_consent_events') is not null
               and to_regclass('public.project_ai_consents') is not null
               and to_regclass('public.project_ai_consent_events') is not null
               and to_regclass('public.project_ai_activity') is not null
               and to_regclass('public.ai_conversation_sessions') is not null
               and to_regclass('public.ai_conversation_turn_requests') is not null;
            """;
        if ((bool)(await command.ExecuteScalarAsync(cancellationToken) ?? false))
            return;
        throw new AccountExportSchemaUnavailableException();
    }
}

public sealed class AccountExportWorker : BackgroundService
{
    private static readonly TimeSpan LeaseRenewalInterval = TimeSpan.FromSeconds(15);
    private readonly ILogger<AccountExportWorker> logger;
    private readonly WorkerOptions options;
    private readonly IAccountExportWorkerStore store;
    private readonly IAccountExportSnapshotBuilder snapshotBuilder;

    public AccountExportWorker(
        ILogger<AccountExportWorker> logger,
        WorkerOptions options,
        IAccountExportWorkerStore store,
        IAccountExportSnapshotBuilder snapshotBuilder)
    {
        this.logger = logger;
        this.options = options;
        this.store = store;
        this.snapshotBuilder = snapshotBuilder;
    }

    protected override async Task ExecuteAsync(CancellationToken stoppingToken)
    {
        logger.LogInformation("Account export worker started with one globally fenced job slot");
        try
        {
            using var timer = new PeriodicTimer(options.PollInterval);
            while (await timer.WaitForNextTickAsync(stoppingToken))
            {
                try
                {
                    await ProcessNextAsync(stoppingToken);
                }
                catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
                {
                    throw;
                }
                catch (WorkerDatabaseException exception)
                {
                    logger.LogError("Account export worker dependency failure with code {ErrorCode}", exception.Code);
                }
            }
        }
        catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
        {
            // A claimed job is left leased for expiry so another worker can safely retry it.
        }
        finally
        {
            logger.LogInformation("Account export worker stopped");
        }
    }

    public async Task<bool> ProcessNextAsync(CancellationToken cancellationToken)
    {
        var globalLock = await store.TryAcquireGlobalLockAsync(cancellationToken);
        if (globalLock is null)
            return false;

        await using (globalLock)
        {
            if (!await globalLock.EnsureHeldAsync(cancellationToken))
                return false;
            await store.PurgeExpiredAsync(cancellationToken);
            var item = await store.ClaimNextAsync(cancellationToken);
            if (item is null)
                return true;
            await ProcessItemAsync(item, globalLock, cancellationToken);
            return true;
        }
    }

    private async Task ProcessItemAsync(
        AccountExportWorkItem item,
        IAccountExportGlobalLock globalLock,
        CancellationToken stoppingToken)
    {
        using var workCancellation = CancellationTokenSource.CreateLinkedTokenSource(stoppingToken);
        using var heartbeatCancellation = CancellationTokenSource.CreateLinkedTokenSource(stoppingToken);
        var heartbeat = RenewWhileWorkingAsync(item, globalLock, workCancellation, heartbeatCancellation.Token);
        byte[]? artifact = null;
        string? errorCode = null;
        var retryable = false;

        try
        {
            logger.LogInformation("Starting account export generation for {ExportId}", item.ExportId);
            artifact = await snapshotBuilder.BuildAsync(item, workCancellation.Token);
            logger.LogInformation(
                "Account export generation finished for {ExportId} with {ArtifactBytes} bytes",
                item.ExportId,
                artifact.LongLength);
        }
        catch (AccountExportArtifactTooLargeException)
        {
            errorCode = "ACCOUNT_EXPORT_TOO_LARGE";
        }
        catch (WorkerDatabaseException exception)
        {
            errorCode = exception.Code;
            retryable = string.Equals(errorCode, "DATABASE_UNAVAILABLE", StringComparison.Ordinal);
        }
        catch (OperationCanceledException) when (stoppingToken.IsCancellationRequested)
        {
            throw;
        }
        catch (OperationCanceledException)
        {
            // Lease or global-lock loss fences completion; generated bytes are discarded.
        }
        catch (Exception)
        {
            errorCode = "EXPORT_GENERATION_FAILED";
            retryable = true;
        }
        finally
        {
            heartbeatCancellation.Cancel();
            try
            {
                await heartbeat;
            }
            catch (OperationCanceledException) when (heartbeatCancellation.IsCancellationRequested)
            {
            }
        }

        if (stoppingToken.IsCancellationRequested || workCancellation.IsCancellationRequested)
            return;

        if (artifact is null)
        {
            if (errorCode is null)
                return;
            try
            {
                await store.FailAsync(item, errorCode, retryable, stoppingToken);
            }
            catch (WorkerDatabaseException exception)
            {
                logger.LogError(
                    "Account export failure state could not be recorded for {ExportId} with code {ErrorCode}",
                    item.ExportId,
                    exception.Code);
            }
            return;
        }

        try
        {
            var completed = await store.CompleteAsync(item, artifact, stoppingToken);
            logger.LogInformation(
                "Account export completion recorded for {ExportId} as {Completed}",
                item.ExportId,
                completed);
            if (!completed)
            {
                logger.LogInformation(
                    "Account export completion was fenced or capacity-limited for {ExportId}",
                    item.ExportId);
            }
        }
        catch (WorkerDatabaseException exception)
        {
            logger.LogError(
                "Account export completion could not be recorded for {ExportId} with code {ErrorCode}",
                item.ExportId,
                exception.Code);
        }
    }

    private async Task RenewWhileWorkingAsync(
        AccountExportWorkItem item,
        IAccountExportGlobalLock globalLock,
        CancellationTokenSource workCancellation,
        CancellationToken heartbeatToken)
    {
        try
        {
            using var timer = new PeriodicTimer(LeaseRenewalInterval);
            while (await timer.WaitForNextTickAsync(heartbeatToken))
            {
                if (!await globalLock.EnsureHeldAsync(heartbeatToken)
                    || !await store.RenewLeaseAsync(item, heartbeatToken))
                {
                    workCancellation.Cancel();
                    return;
                }
            }
        }
        catch (OperationCanceledException) when (heartbeatToken.IsCancellationRequested)
        {
        }
        catch (WorkerDatabaseException exception)
        {
            logger.LogWarning(
                "Account export lease renewal stopped for {ExportId} with code {ErrorCode}",
                item.ExportId,
                exception.Code);
            workCancellation.Cancel();
        }
        catch (NpgsqlException)
        {
            logger.LogWarning(
                "Account export global lock heartbeat failed for {ExportId} with code ACCOUNT_EXPORT_LOCK_HEARTBEAT_FAILED",
                item.ExportId);
            workCancellation.Cancel();
        }
    }
}
