using System.Text.Json;
using Evidrilo.Api.Authorization;
using Evidrilo.Api.Common;
using Npgsql;
using NpgsqlTypes;

namespace Evidrilo.Api.Content;

public sealed record CaseAuthoringOperation(
    string Outcome,
    string CaseVersionId,
    CaseLifecycleState State);

public interface ICaseAuthoringStore
{
    Task<CaseAuthoringOperation> CreateDraftAsync(
        Guid accountId,
        CaseAuthoringRequest request,
        CancellationToken cancellationToken);

    Task<CaseAuthoringOperation> TransitionAsync(
        Guid accountId,
        string caseVersionId,
        CaseTransitionRequest request,
        CancellationToken cancellationToken);
}

public sealed class DatabaseUnavailableCaseAuthoringStore : ICaseAuthoringStore
{
    public Task<CaseAuthoringOperation> CreateDraftAsync(
        Guid accountId,
        CaseAuthoringRequest request,
        CancellationToken cancellationToken) => throw NotConfigured();

    public Task<CaseAuthoringOperation> TransitionAsync(
        Guid accountId,
        string caseVersionId,
        CaseTransitionRequest request,
        CancellationToken cancellationToken) => throw NotConfigured();

    private static ApiException NotConfigured() => new(
        StatusCodes.Status503ServiceUnavailable,
        "DATABASE_NOT_CONFIGURED",
        "Case authoring is not configured.");
}

public sealed class NpgsqlCaseAuthoringStore : ICaseAuthoringStore, IDisposable
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);
    private readonly NpgsqlDataSource dataSource;

    public NpgsqlCaseAuthoringStore(string connectionString)
    {
        dataSource = NpgsqlDataSource.Create(connectionString);
    }

    public async Task<CaseAuthoringOperation> CreateDraftAsync(
        Guid accountId,
        CaseAuthoringRequest request,
        CancellationToken cancellationToken)
    {
        if (request.OrganizationId == Guid.Empty
            || request.Document is null
            || !CaseAuthoringValidator.Validate(request.Document).IsValid)
            throw InvalidDocument();

        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);
            await SetCaseLifecycleReasonAsync(connection, transaction, "draft_created", cancellationToken);
            var role = await ReadMembershipRoleAsync(
                connection,
                transaction,
                accountId,
                request.OrganizationId,
                cancellationToken);
            var decision = AccessPolicy.CanAuthorContent(
                role is null ? null : new Membership(accountId, request.OrganizationId, role.Value, true),
                request.OrganizationId);
            if (!decision.Allowed) throw Forbidden(decision.Code);

            await using var command = connection.CreateCommand();
            command.Transaction = transaction;
            command.CommandText = """
                insert into public.case_versions (
                    case_version_id, case_id, content_hash, status, title,
                    evaluator_version, skill_tags, author_id, organization_id,
                    objective, difficulty, evidence_references, content
                ) values (
                    @case_version_id, @case_id, @content_hash, 'draft', @title,
                    @evaluator_version, @skill_tags, @author_id, @organization_id,
                    @objective, @difficulty, @evidence_references, @content
                );
                """;
            command.Parameters.AddWithValue("case_version_id", NpgsqlDbType.Text, request.Document.Content.CaseVersionId);
            command.Parameters.AddWithValue("case_id", NpgsqlDbType.Text, request.Document.Content.CaseId);
            command.Parameters.AddWithValue("content_hash", NpgsqlDbType.Text, request.Document.Content.ContentHash);
            command.Parameters.AddWithValue("title", NpgsqlDbType.Text, request.Document.Content.Title);
            command.Parameters.AddWithValue("evaluator_version", NpgsqlDbType.Text, request.Document.Content.EvaluatorVersion);
            command.Parameters.AddWithValue("skill_tags", NpgsqlDbType.Array | NpgsqlDbType.Text, request.Document.Content.SkillTags.ToArray());
            command.Parameters.AddWithValue("author_id", NpgsqlDbType.Uuid, accountId);
            command.Parameters.AddWithValue("organization_id", NpgsqlDbType.Uuid, request.OrganizationId);
            command.Parameters.AddWithValue("objective", NpgsqlDbType.Text, request.Document.Objective);
            command.Parameters.AddWithValue("difficulty", NpgsqlDbType.Integer, request.Document.Difficulty);
            command.Parameters.AddWithValue("evidence_references", NpgsqlDbType.Array | NpgsqlDbType.Text, request.Document.EvidenceReferences.ToArray());
            command.Parameters.AddWithValue(
                "content",
                NpgsqlDbType.Jsonb,
                JsonSerializer.Serialize(request.Document, JsonOptions));
            await command.ExecuteNonQueryAsync(cancellationToken);
            await transaction.CommitAsync(cancellationToken);
            return new CaseAuthoringOperation("accepted", request.Document.Content.CaseVersionId, CaseLifecycleState.Draft);
        }
        catch (ApiException)
        {
            throw;
        }
        catch (PostgresException exception) when (exception.SqlState == "23505")
        {
            throw new ApiException(
                StatusCodes.Status409Conflict,
                "CASE_VERSION_EXISTS",
                "The case version already exists.",
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
                "Case authoring is temporarily unavailable.",
                exception);
        }
    }

    public async Task<CaseAuthoringOperation> TransitionAsync(
        Guid accountId,
        string caseVersionId,
        CaseTransitionRequest request,
        CancellationToken cancellationToken)
    {
        if (request is null
            || request.Schema != "evidrilo.case-transition"
            || request.Version != "1"
            || !Enum.IsDefined(request.TargetState))
            throw new ApiException(StatusCodes.Status400BadRequest, "INVALID_CASE_TRANSITION", "The case transition is invalid.");

        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, accountId, cancellationToken);
            var current = await ReadCurrentVersionAsync(connection, transaction, caseVersionId, cancellationToken);
            if (current is null)
                throw new ApiException(StatusCodes.Status404NotFound, "CASE_NOT_FOUND", "The case version was not found.");

            var role = await ReadMembershipRoleAsync(
                connection,
                transaction,
                accountId,
                current.OrganizationId,
                cancellationToken);
            var actorRole = role is null ? null : AccessPolicy.ToContentActorRole(role.Value);
            if (actorRole is null)
                throw Forbidden(role is null ? "MEMBERSHIP_REQUIRED" : "ROLE_REQUIRED");

            if (request.TargetState is CaseLifecycleState.Approved or CaseLifecycleState.Published
                && !CaseAuthoringValidator.ValidateSerializedDocument(current.ContentJson).IsValid)
                throw InvalidStoredDocument();

            var transition = CaseWorkflow.ValidateTransition(
                new CaseVersionRecord(
                    new CaseContent(
                        caseVersionId,
                        caseVersionId,
                        "stored",
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                        "stored",
                        ["stored"],
                        ["stored"]),
                    current.State,
                    current.AuthorId,
                    current.ReviewerId),
                request.TargetState,
                accountId,
                actorRole.Value);
            if (!transition.IsAllowed)
                throw new ApiException(StatusCodes.Status409Conflict, transition.Code!, "The case transition is not allowed.");

            if (current.State == CaseLifecycleState.Review
                && request.TargetState is CaseLifecycleState.Approved or CaseLifecycleState.Draft
                && string.IsNullOrWhiteSpace(request.Reason))
                throw new ApiException(StatusCodes.Status400BadRequest, "REVIEW_REASON_REQUIRED", "A review reason is required.");
            if (request.Reason is { Length: > 2000 })
                throw new ApiException(StatusCodes.Status400BadRequest, "REVIEW_REASON_INVALID", "The review reason is invalid.");

            var lifecycleReason = string.IsNullOrWhiteSpace(request.Reason)
                ? null
                : request.Reason.Trim();
            await SetCaseLifecycleReasonAsync(connection, transaction, lifecycleReason, cancellationToken);

            await using var update = connection.CreateCommand();
            update.Transaction = transaction;
            update.CommandText = """
                update public.case_versions
                set status = @target_state,
                    reviewer_id = case when @target_state = 'approved' then @actor_id else reviewer_id end,
                    published_at = case when @target_state = 'published' then now() else published_at end
                where case_version_id = @case_version_id and status = @current_state;
                """;
            update.Parameters.AddWithValue("target_state", NpgsqlDbType.Text, request.TargetState.ToWire());
            update.Parameters.AddWithValue("actor_id", NpgsqlDbType.Uuid, accountId);
            update.Parameters.AddWithValue("case_version_id", NpgsqlDbType.Text, caseVersionId);
            update.Parameters.AddWithValue("current_state", NpgsqlDbType.Text, current.State.ToWire());
            if (await update.ExecuteNonQueryAsync(cancellationToken) != 1)
                throw new ApiException(StatusCodes.Status409Conflict, "CASE_VERSION_CHANGED", "The case version changed during this operation.");

            if (current.State == CaseLifecycleState.Review
                && request.TargetState is CaseLifecycleState.Approved or CaseLifecycleState.Draft)
            {
                await using var decisionCommand = connection.CreateCommand();
                decisionCommand.Transaction = transaction;
                decisionCommand.CommandText = """
                    insert into public.case_review_decisions (
                        case_version_id, reviewer_id, decision, reason
                    ) values (@case_version_id, @reviewer_id, @decision, @reason);
                    """;
                decisionCommand.Parameters.AddWithValue("case_version_id", NpgsqlDbType.Text, caseVersionId);
                decisionCommand.Parameters.AddWithValue("reviewer_id", NpgsqlDbType.Uuid, accountId);
                decisionCommand.Parameters.AddWithValue(
                    "decision",
                    NpgsqlDbType.Text,
                    request.TargetState == CaseLifecycleState.Approved ? "approved" : "rejected");
                decisionCommand.Parameters.AddWithValue("reason", NpgsqlDbType.Text, lifecycleReason!);
                await decisionCommand.ExecuteNonQueryAsync(cancellationToken);
            }

            await transaction.CommitAsync(cancellationToken);
            return new CaseAuthoringOperation("accepted", caseVersionId, request.TargetState);
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
                "Case authoring is temporarily unavailable.",
                exception);
        }
    }

    public void Dispose() => dataSource.Dispose();

    private static async Task<StoredVersion?> ReadCurrentVersionAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        string caseVersionId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            select status, author_id, reviewer_id, organization_id, content::text
            from public.case_versions
            where case_version_id = @case_version_id
            for update;
            """;
        command.Parameters.AddWithValue("case_version_id", NpgsqlDbType.Text, caseVersionId);
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        if (!await reader.ReadAsync(cancellationToken)) return null;
        if (reader.IsDBNull(3))
            throw new ApiException(StatusCodes.Status409Conflict, "CASE_VERSION_NOT_AUTHORABLE", "The case version is not authorable.");
        return new StoredVersion(
            CaseLifecycleStateExtensions.FromWire(reader.GetString(0)),
            reader.IsDBNull(1) ? Guid.Empty : reader.GetGuid(1),
            reader.IsDBNull(2) ? null : reader.GetGuid(2),
            reader.GetGuid(3),
            reader.IsDBNull(4) ? null : reader.GetString(4));
    }

    private static async Task<PlatformRole?> ReadMembershipRoleAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid accountId,
        Guid organizationId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            select role
            from public.organization_memberships
            where organization_id = @organization_id
              and account_id = @account_id
              and active = true;
            """;
        command.Parameters.AddWithValue("organization_id", NpgsqlDbType.Uuid, organizationId);
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        var value = await command.ExecuteScalarAsync(cancellationToken);
        return value switch
        {
            null or DBNull => null,
            string role => role switch
            {
                "learner" => PlatformRole.Learner,
                "author" => PlatformRole.Author,
                "teacher" => PlatformRole.Teacher,
                "reviewer" => PlatformRole.Reviewer,
                "maintainer" => PlatformRole.Maintainer,
                "owner" => PlatformRole.Owner,
                _ => throw new ApiException(StatusCodes.Status503ServiceUnavailable, "DATABASE_SCHEMA_MISSING", "The authorization schema is not ready."),
            },
            _ => throw new ApiException(StatusCodes.Status503ServiceUnavailable, "DATABASE_SCHEMA_MISSING", "The authorization schema is not ready."),
        };
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

    private static async Task SetCaseLifecycleReasonAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        string? reason,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = "select set_config('evidrilo.case_lifecycle_reason', @reason, true);";
        command.Parameters.AddWithValue("reason", NpgsqlDbType.Text, reason ?? string.Empty);
        await command.ExecuteNonQueryAsync(cancellationToken);
    }

    private static ApiException InvalidDocument() => new(
        StatusCodes.Status400BadRequest,
        "INVALID_CASE_AUTHORING_DOCUMENT",
        "The case authoring document is invalid.");

    private static ApiException InvalidStoredDocument() => new(
        StatusCodes.Status409Conflict,
        "INVALID_CASE_AUTHORING_DOCUMENT",
        "The stored case content cannot enter the requested lifecycle state.");

    private static ApiException Forbidden(string code) => new(
        StatusCodes.Status403Forbidden,
        code,
        "You are not allowed to perform this case operation.");

    private sealed record StoredVersion(
        CaseLifecycleState State,
        Guid AuthorId,
        Guid? ReviewerId,
        Guid OrganizationId,
        string? ContentJson);
}

public static class CaseLifecycleStateExtensions
{
    public static string ToWire(this CaseLifecycleState value) => value switch
    {
        CaseLifecycleState.Draft => "draft",
        CaseLifecycleState.Review => "review",
        CaseLifecycleState.Approved => "approved",
        CaseLifecycleState.Published => "published",
        CaseLifecycleState.Retired => "retired",
        _ => throw new ArgumentOutOfRangeException(nameof(value)),
    };

    public static CaseLifecycleState FromWire(string value) => value switch
    {
        "draft" => CaseLifecycleState.Draft,
        "review" => CaseLifecycleState.Review,
        "approved" => CaseLifecycleState.Approved,
        "published" => CaseLifecycleState.Published,
        "retired" => CaseLifecycleState.Retired,
        _ => throw new ApiException(StatusCodes.Status503ServiceUnavailable, "DATABASE_SCHEMA_MISSING", "The content schema is not ready."),
    };
}
