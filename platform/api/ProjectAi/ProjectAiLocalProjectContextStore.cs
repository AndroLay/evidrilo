using System.Text.Json;
using Evidrilo.Api.Common;
using Microsoft.AspNetCore.Http;
using Npgsql;
using NpgsqlTypes;

namespace Evidrilo.Api.ProjectAi;

public sealed record ProjectAiLocalProjectContext(
    Guid ProjectId,
    int CurrentRevision,
    long BindingGeneration,
    string? TemplateId,
    int? TemplateVersion,
    IReadOnlyList<string> AvailableEvidenceIds,
    DateTimeOffset UpdatedAt);

public enum ProjectAiLocalContextWriteOutcome
{
    Updated,
    Unchanged,
    Stale,
    Conflict,
}

public interface IProjectAiLocalProjectContextStore
{
    Task<ProjectAiLocalContextWriteOutcome> UpsertOwnAsync(
        Guid accountId,
        Guid projectId,
        int revision,
        string templateId,
        int templateVersion,
        IReadOnlyList<string> availableEvidenceIds,
        CancellationToken cancellationToken);

    Task<ProjectAiLocalProjectContext?> ReadOwnAsync(
        Guid accountId,
        Guid projectId,
        CancellationToken cancellationToken);

    Task<bool> DeleteOwnAsync(
        Guid accountId,
        Guid projectId,
        CancellationToken cancellationToken);
}

public sealed class DatabaseUnavailableProjectAiLocalProjectContextStore
    : IProjectAiLocalProjectContextStore
{
    public Task<ProjectAiLocalContextWriteOutcome> UpsertOwnAsync(
        Guid accountId,
        Guid projectId,
        int revision,
        string templateId,
        int templateVersion,
        IReadOnlyList<string> availableEvidenceIds,
        CancellationToken cancellationToken) => throw NotConfigured();

    public Task<ProjectAiLocalProjectContext?> ReadOwnAsync(
        Guid accountId,
        Guid projectId,
        CancellationToken cancellationToken) => throw NotConfigured();

    public Task<bool> DeleteOwnAsync(
        Guid accountId,
        Guid projectId,
        CancellationToken cancellationToken) => throw NotConfigured();

    private static ApiException NotConfigured() => new(
        StatusCodes.Status503ServiceUnavailable,
        "DATABASE_NOT_CONFIGURED",
        "Project AI context storage is not configured.");
}

public sealed class NpgsqlProjectAiLocalProjectContextStore : IProjectAiLocalProjectContextStore, IDisposable
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);
    private readonly NpgsqlDataSource dataSource;

    public NpgsqlProjectAiLocalProjectContextStore(string connectionString) =>
        dataSource = NpgsqlDataSource.Create(connectionString);

    public async Task<ProjectAiLocalContextWriteOutcome> UpsertOwnAsync(
        Guid accountId,
        Guid projectId,
        int revision,
        string templateId,
        int templateVersion,
        IReadOnlyList<string> availableEvidenceIds,
        CancellationToken cancellationToken)
    {
        ValidateIdentity(accountId, projectId, revision);
        var normalizedEvidenceIds = NormalizeEvidenceIds(availableEvidenceIds);
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);
            var existing = await ReadOwnAsync(
                connection,
                transaction,
                accountId,
                projectId,
                forUpdate: true,
                cancellationToken);

            if (existing is not null)
            {
                if (revision < existing.CurrentRevision)
                {
                    await transaction.CommitAsync(cancellationToken);
                    return ProjectAiLocalContextWriteOutcome.Stale;
                }

                if (revision == existing.CurrentRevision)
                {
                    if ((existing.TemplateId is not null && existing.TemplateId != templateId)
                        || (existing.TemplateVersion is not null && existing.TemplateVersion != templateVersion))
                    {
                        await transaction.CommitAsync(cancellationToken);
                        return ProjectAiLocalContextWriteOutcome.Conflict;
                    }

                    var sameTemplate = existing.TemplateId == templateId
                        && existing.TemplateVersion == templateVersion;
                    var sameEvidence = existing.AvailableEvidenceIds.SequenceEqual(
                        normalizedEvidenceIds,
                        StringComparer.Ordinal);
                    if (sameTemplate && sameEvidence)
                    {
                        await transaction.CommitAsync(cancellationToken);
                        return ProjectAiLocalContextWriteOutcome.Unchanged;
                    }

                    if (existing.TemplateId is not null || existing.AvailableEvidenceIds.Count > 0)
                    {
                        await transaction.CommitAsync(cancellationToken);
                        return ProjectAiLocalContextWriteOutcome.Conflict;
                    }
                }
            }

            await using var command = connection.CreateCommand();
            command.Transaction = transaction;
            command.CommandText = """
                insert into public.project_ai_local_contexts (
                    account_id, project_id, current_revision, template_id, template_version,
                    binding_generation, available_evidence_ids, updated_at
                ) values (
                    @account_id, @project_id, @revision, @template_id, @template_version,
                    1, @available_evidence_ids, clock_timestamp()
                )
                on conflict (account_id, project_id) do update
                set current_revision = excluded.current_revision,
                    template_id = excluded.template_id,
                    template_version = excluded.template_version,
                    binding_generation = public.project_ai_local_contexts.binding_generation + 1,
                    available_evidence_ids = excluded.available_evidence_ids,
                    updated_at = clock_timestamp()
                where public.project_ai_local_contexts.current_revision < excluded.current_revision
                   or public.project_ai_local_contexts.template_id is null
                """;
            command.Parameters.AddWithValue("account_id", accountId);
            command.Parameters.AddWithValue("project_id", projectId);
            command.Parameters.AddWithValue("revision", revision);
            command.Parameters.AddWithValue("template_id", templateId);
            command.Parameters.AddWithValue("template_version", templateVersion);
            command.Parameters.Add(new NpgsqlParameter("available_evidence_ids", NpgsqlDbType.Jsonb)
            {
                Value = JsonSerializer.Serialize(normalizedEvidenceIds, JsonOptions),
            });
            var changed = await command.ExecuteNonQueryAsync(cancellationToken);
            await transaction.CommitAsync(cancellationToken);
            return changed == 1
                ? ProjectAiLocalContextWriteOutcome.Updated
                : ProjectAiLocalContextWriteOutcome.Conflict;
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

    public async Task<ProjectAiLocalProjectContext?> ReadOwnAsync(
        Guid accountId,
        Guid projectId,
        CancellationToken cancellationToken)
    {
        ValidateIdentity(accountId, projectId, revision: 1);
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);
            var context = await ReadOwnAsync(connection, transaction, accountId, projectId, false, cancellationToken);
            await transaction.CommitAsync(cancellationToken);
            return context;
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

    public async Task<bool> DeleteOwnAsync(
        Guid accountId,
        Guid projectId,
        CancellationToken cancellationToken)
    {
        ValidateIdentity(accountId, projectId, revision: 1);
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);
            await using var command = connection.CreateCommand();
            command.Transaction = transaction;
            command.CommandText = """
                delete from public.project_ai_local_contexts
                where account_id = @account_id and project_id = @project_id
                """;
            command.Parameters.AddWithValue("account_id", accountId);
            command.Parameters.AddWithValue("project_id", projectId);
            var deleted = await command.ExecuteNonQueryAsync(cancellationToken) == 1;
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

    public void Dispose() => dataSource.Dispose();

    private static async Task<ProjectAiLocalProjectContext?> ReadOwnAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        Guid projectId,
        bool forUpdate,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = $"""
            select current_revision, binding_generation, template_id, template_version,
                   available_evidence_ids::text, updated_at
            from public.project_ai_local_contexts
            where account_id = @account_id and project_id = @project_id
            {(forUpdate ? "for update" : string.Empty)}
            """;
        command.Parameters.AddWithValue("account_id", accountId);
        command.Parameters.AddWithValue("project_id", projectId);
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        if (!await reader.ReadAsync(cancellationToken)) return null;
        var evidenceIds = JsonSerializer.Deserialize<string[]>(reader.GetString(4), JsonOptions) ?? [];
        return new ProjectAiLocalProjectContext(
            projectId,
            reader.GetInt32(0),
            reader.GetInt64(1),
            reader.IsDBNull(2) ? null : reader.GetString(2),
            reader.IsDBNull(3) ? null : reader.GetInt32(3),
            evidenceIds,
            reader.GetFieldValue<DateTimeOffset>(5));
    }

    private static async Task SetRequestAccountAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            select set_config('request.jwt.claim.sub', @account_id, true),
                   set_config('request.jwt.claim.role', 'authenticated', true)
            """;
        command.Parameters.AddWithValue("account_id", accountId.ToString("D"));
        await command.ExecuteNonQueryAsync(cancellationToken);
    }

    private static string[] NormalizeEvidenceIds(IReadOnlyList<string> evidenceIds)
    {
        if (evidenceIds.Count > 2048
            || evidenceIds.Any(id => string.IsNullOrWhiteSpace(id) || id.Length > 64 || id.Any(char.IsControl))
            || evidenceIds.Distinct(StringComparer.Ordinal).Count() != evidenceIds.Count)
            throw new ArgumentException("The project AI evidence identity list is invalid.", nameof(evidenceIds));
        return evidenceIds.OrderBy(id => id, StringComparer.Ordinal).ToArray();
    }

    private static void ValidateIdentity(Guid accountId, Guid projectId, int revision)
    {
        if (accountId == Guid.Empty || projectId == Guid.Empty || revision < 1)
            throw new ArgumentException("The project AI context identity is invalid.");
    }

    private static ApiException DatabaseUnavailable(Exception exception) => new(
        StatusCodes.Status503ServiceUnavailable,
        "DATABASE_UNAVAILABLE",
        "Project AI context storage is temporarily unavailable.",
        exception);
}
