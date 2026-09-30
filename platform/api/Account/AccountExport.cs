using System.Data;
using System.Text.Json;
using Evidrilo.Api.Common;
using Npgsql;

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
    private bool isDisposed;

    public async Task WriteDataAsync(Utf8JsonWriter writer, CancellationToken cancellationToken)
    {
        ObjectDisposedException.ThrowIf(isDisposed, this);

        try
        {
            await Evidrilo.Platform.AccountExportDataWriter.WriteAsync(
                writer,
                connection,
                transaction,
                accountId,
                Evidrilo.Platform.AccountExportDataVersion.V1,
                cancellationToken);
            await transaction.CommitAsync(cancellationToken);
        }
        catch (ApiException)
        {
            throw;
        }
        catch (Evidrilo.Platform.AccountExportSchemaUnavailableException exception)
        {
            throw new ApiException(
                StatusCodes.Status503ServiceUnavailable,
                "DATABASE_SCHEMA_MISSING",
                "The account export schema is not ready.",
                exception);
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
}
