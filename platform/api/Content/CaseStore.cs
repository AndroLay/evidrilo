using System.Text.Json;
using Evidrilo.Api.Common;
using Npgsql;
using NpgsqlTypes;

namespace Evidrilo.Api.Content;

public sealed record PublishedCaseSummary(
    string CaseId,
    string CaseVersionId,
    string Title,
    string ContentHash,
    string EvaluatorVersion,
    IReadOnlyList<string> SkillTags,
    PublishedCaseContent Content);

public sealed record PublishedCaseListItem(
    string CaseId,
    string CaseVersionId,
    string Title,
    string ContentHash,
    string EvaluatorVersion,
    IReadOnlyList<string> SkillTags,
    string Objective,
    int Difficulty);

public interface ICaseStore
{
    Task<PublishedCaseSummary?> GetPublishedAsync(
        Guid accountId,
        string caseVersionId,
        CancellationToken cancellationToken);

    Task<IReadOnlyList<PublishedCaseListItem>> ListPublishedAsync(
        Guid accountId,
        CancellationToken cancellationToken);
}

public sealed class DatabaseUnavailableCaseStore : ICaseStore
{
    public Task<PublishedCaseSummary?> GetPublishedAsync(
        Guid accountId,
        string caseVersionId,
        CancellationToken cancellationToken) => throw new ApiException(
        StatusCodes.Status503ServiceUnavailable,
        "DATABASE_NOT_CONFIGURED",
        "Published cases are not configured.");

    public Task<IReadOnlyList<PublishedCaseListItem>> ListPublishedAsync(
        Guid accountId,
        CancellationToken cancellationToken) => throw new ApiException(
        StatusCodes.Status503ServiceUnavailable,
        "DATABASE_NOT_CONFIGURED",
        "Published cases are not configured.");
}

public sealed class NpgsqlCaseStore : ICaseStore, IDisposable
{
    private readonly NpgsqlDataSource dataSource;

    public NpgsqlCaseStore(string connectionString)
    {
        dataSource = NpgsqlDataSource.Create(connectionString);
    }

    public async Task<PublishedCaseSummary?> GetPublishedAsync(
        Guid accountId,
        string caseVersionId,
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
                select case_id, case_version_id, title, content_hash,
                       evaluator_version, skill_tags, objective, difficulty,
                       evidence_references, content::text,
                       (content #> '{content,factAnchors}')::text
                from public.case_versions
                where case_version_id = @case_version_id and status = 'published'
                  and case_id is not null and title is not null
                  and evaluator_version is not null and skill_tags is not null;
                """;
            command.Parameters.AddWithValue("case_version_id", NpgsqlDbType.Text, caseVersionId);
            PublishedCaseSummary? result = null;
            await using (var reader = await command.ExecuteReaderAsync(cancellationToken))
            {
                if (await reader.ReadAsync(cancellationToken))
                {
                    var tags = reader.IsDBNull(5)
                        ? Array.Empty<string>()
                        : reader.GetFieldValue<string[]>(5);
                    if (reader.IsDBNull(6)
                        || reader.IsDBNull(7)
                        || reader.IsDBNull(8)
                        || reader.IsDBNull(9)
                        || reader.IsDBNull(10))
                        throw InvalidPublishedContent();

                    var factAnchors = ReadStringArray(reader, 10);
                    var expectedContent = new CaseContent(
                        reader.GetString(0),
                        reader.GetString(1),
                        reader.GetString(2),
                        reader.GetString(3),
                        reader.GetString(4),
                        factAnchors,
                        tags);
                    using var storedDocument = JsonDocument.Parse(reader.GetString(9));
                    if (!PublishedCaseContentReader.TryRead(
                            storedDocument.RootElement,
                            expectedContent,
                            out var publishedContent))
                        throw InvalidPublishedContent();

                    var evidenceReferences = reader.GetFieldValue<string[]>(8);
                    if (publishedContent!.Objective != reader.GetString(6)
                        || publishedContent.Difficulty != reader.GetInt32(7)
                        || !publishedContent.EvidenceReferences.SequenceEqual(evidenceReferences, StringComparer.Ordinal))
                        throw InvalidPublishedContent();

                    result = new PublishedCaseSummary(
                        reader.GetString(0),
                        reader.GetString(1),
                        reader.GetString(2),
                        reader.GetString(3),
                        reader.GetString(4),
                        tags,
                        publishedContent);
                }
            }

            await transaction.CommitAsync(cancellationToken);
            return result;
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (JsonException)
        {
            throw InvalidPublishedContent();
        }
        catch (NpgsqlException exception)
        {
            throw new ApiException(
                StatusCodes.Status503ServiceUnavailable,
                "DATABASE_UNAVAILABLE",
                "Published cases are temporarily unavailable.",
                exception);
        }
    }

    public async Task<IReadOnlyList<PublishedCaseListItem>> ListPublishedAsync(
        Guid accountId,
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
                select case_id, case_version_id, title, content_hash,
                       evaluator_version, skill_tags, objective, difficulty
                from public.case_versions
                where status = 'published'
                  and case_id is not null
                  and case_version_id is not null
                  and title is not null
                  and content_hash is not null
                  and evaluator_version is not null
                  and skill_tags is not null
                  and objective is not null
                  and difficulty between 1 and 5
                order by case_id, case_version_id
                limit 128;
                """;

            var results = new List<PublishedCaseListItem>();
            await using (var reader = await command.ExecuteReaderAsync(cancellationToken))
            {
                while (await reader.ReadAsync(cancellationToken))
                {
                    results.Add(new PublishedCaseListItem(
                        reader.GetString(0),
                        reader.GetString(1),
                        reader.GetString(2),
                        reader.GetString(3),
                        reader.GetString(4),
                        reader.GetFieldValue<string[]>(5),
                        reader.GetString(6),
                        reader.GetInt32(7)));
                }
            }

            await transaction.CommitAsync(cancellationToken);
            return results;
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
                "Published cases are temporarily unavailable.",
                exception);
        }
    }

    public void Dispose() => dataSource.Dispose();

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

    private static ApiException InvalidPublishedContent() => new(
        StatusCodes.Status503ServiceUnavailable,
        "INVALID_PUBLISHED_CONTENT",
        "Published content is temporarily unavailable.");

    private static string[] ReadStringArray(NpgsqlDataReader reader, int ordinal)
    {
        try
        {
            return JsonSerializer.Deserialize<string[]>(reader.GetString(ordinal))
                ?? throw InvalidPublishedContent();
        }
        catch (JsonException)
        {
            throw InvalidPublishedContent();
        }
    }
}
