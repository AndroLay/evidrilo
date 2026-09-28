using System.Text.Json;
using System.Text.Json.Serialization;
using Evidrilo.Api.Authorization;
using Evidrilo.Api.Common;
using Npgsql;
using NpgsqlTypes;

namespace Evidrilo.Api.ProjectTemplates;

public sealed class NpgsqlProjectTemplateStore : IProjectTemplateStore, IDisposable
{
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web)
    {
        Converters = { new JsonStringEnumConverter(JsonNamingPolicy.SnakeCaseLower) },
        UnmappedMemberHandling = JsonUnmappedMemberHandling.Disallow,
    };
    private readonly NpgsqlDataSource dataSource;

    public NpgsqlProjectTemplateStore(string connectionString) =>
        dataSource = NpgsqlDataSource.Create(connectionString);

    public async Task<IReadOnlyList<ProjectTemplateFamilyOffering>> ListFamiliesAsync(
        CancellationToken cancellationToken)
    {
        try
        {
            var counts = new Dictionary<string, int>(StringComparer.Ordinal);
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var command = connection.CreateCommand();
            command.CommandText = """
                select family, count(*)::integer
                from public.project_template_versions
                where state = 'published' and is_current_published = true
                group by family;
                """;
            await using var reader = await command.ExecuteReaderAsync(cancellationToken);
            while (await reader.ReadAsync(cancellationToken))
                counts[reader.GetString(0)] = reader.GetInt32(1);

            return ProjectTemplateFamilies.All
                .Select(family => new ProjectTemplateFamilyOffering(
                    family,
                    counts.GetValueOrDefault(family.Id)))
                .ToArray();
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

    public async Task<ProjectTemplateCatalogPage> ListPublishedAsync(
        string? family,
        int limit,
        string? afterTemplateId,
        CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var command = connection.CreateCommand();
            command.CommandText = """
                select template.template_id, template.template_version, template.family,
                       template.content::text, template.published_at,
                       coalesce(review.reviewed_example_ids, array[]::text[])
                from public.project_template_versions template
                left join lateral (
                    select reviewed_example_ids
                    from public.project_template_review_decisions decision
                    where decision.template_id = template.template_id
                      and decision.template_version = template.template_version
                      and decision.decision = 'approved'
                    order by decision.created_at desc, decision.review_decision_id desc
                    limit 1
                ) review on true
                where template.state = 'published'
                  and template.is_current_published = true
                  and (@family::text is null or template.family = @family)
                  and (@after_template_id::text is null or template.template_id > @after_template_id)
                order by template.template_id
                limit @row_limit;
                """;
            command.Parameters.AddWithValue("family", NpgsqlDbType.Text, (object?)family ?? DBNull.Value);
            command.Parameters.AddWithValue("after_template_id", NpgsqlDbType.Text, (object?)afterTemplateId ?? DBNull.Value);
            command.Parameters.AddWithValue("row_limit", NpgsqlDbType.Integer, limit + 1);

            var rows = new List<ProjectTemplateCatalogEntry>(limit + 1);
            await using (var reader = await command.ExecuteReaderAsync(cancellationToken))
            {
                while (await reader.ReadAsync(cancellationToken))
                    rows.Add(ReadCatalogEntry(reader));
            }

            var hasMore = rows.Count > limit;
            if (hasMore) rows.RemoveAt(rows.Count - 1);
            var next = hasMore && rows.Count > 0 ? rows[^1].TemplateId : null;
            return new ProjectTemplateCatalogPage(rows, next);
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (JsonException exception)
        {
            throw InvalidStoredTemplate(exception);
        }
        catch (NpgsqlException exception)
        {
            throw DatabaseUnavailable(exception);
        }
    }

    public async Task<ProjectTemplateCatalogEntry?> GetPublishedAsync(
        string templateId,
        int templateVersion,
        CancellationToken cancellationToken)
    {
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var command = connection.CreateCommand();
            command.CommandText = """
                select template.template_id, template.template_version, template.family,
                       template.content::text, template.published_at,
                       coalesce(review.reviewed_example_ids, array[]::text[])
                from public.project_template_versions template
                left join lateral (
                    select reviewed_example_ids
                    from public.project_template_review_decisions decision
                    where decision.template_id = template.template_id
                      and decision.template_version = template.template_version
                      and decision.decision = 'approved'
                    order by decision.created_at desc, decision.review_decision_id desc
                    limit 1
                ) review on true
                where template.template_id = @template_id
                  and template.template_version = @template_version
                  and template.state = 'published'
                  and template.is_current_published = true;
                """;
            command.Parameters.AddWithValue("template_id", NpgsqlDbType.Text, templateId);
            command.Parameters.AddWithValue("template_version", NpgsqlDbType.Integer, templateVersion);
            await using var reader = await command.ExecuteReaderAsync(cancellationToken);
            return await reader.ReadAsync(cancellationToken) ? ReadCatalogEntry(reader) : null;
        }
        catch (OperationCanceledException)
        {
            throw;
        }
        catch (JsonException exception)
        {
            throw InvalidStoredTemplate(exception);
        }
        catch (NpgsqlException exception)
        {
            throw DatabaseUnavailable(exception);
        }
    }

    public async Task<ProjectTemplateOperation> CreateDraftAsync(
        Guid actorAccountId,
        ProjectTemplateDraftCreateRequest request,
        CancellationToken cancellationToken)
    {
        var validationError = ValidateDraftRequest(actorAccountId, request);
        if (validationError is not null) throw InvalidRequest(validationError);

        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetActorAsync(connection, transaction, actorAccountId, null, cancellationToken);
            await LockTemplateAsync(connection, transaction, request.TemplateId!, cancellationToken);

            var role = await ReadMembershipRoleAsync(
                connection,
                transaction,
                actorAccountId,
                request.OrganizationId,
                cancellationToken);
            var access = AccessPolicy.CanAuthorContent(
                role is null ? null : new Membership(actorAccountId, request.OrganizationId, role.Value, true),
                request.OrganizationId);
            if (!access.Allowed) throw Forbidden(access.Code);

            var existing = await ReadLatestTemplateAsync(
                connection,
                transaction,
                request.TemplateId!,
                cancellationToken);
            if (existing is not null
                && (existing.OrganizationId != request.OrganizationId || existing.Family != request.Family))
                throw Conflict("TEMPLATE_ID_SCOPE_IMMUTABLE", "The template identifier is already assigned to another catalog scope.");
            var expectedVersion = existing is null ? 1 : existing.TemplateVersion + 1;
            if (request.TemplateVersion != expectedVersion)
                throw Conflict("TEMPLATE_VERSION_NOT_NEXT", "A template draft must use the next sequential version.");

            await using var insert = connection.CreateCommand();
            insert.Transaction = transaction;
            insert.CommandText = """
                insert into public.project_template_versions (
                    template_id, template_version, family, organization_id,
                    content, state, author_id
                ) values (
                    @template_id, @template_version, @family, @organization_id,
                    @content, 'draft', @author_id
                );
                """;
            insert.Parameters.AddWithValue("template_id", NpgsqlDbType.Text, request.TemplateId!);
            insert.Parameters.AddWithValue("template_version", NpgsqlDbType.Integer, request.TemplateVersion);
            insert.Parameters.AddWithValue("family", NpgsqlDbType.Text, request.Family!);
            insert.Parameters.AddWithValue("organization_id", NpgsqlDbType.Uuid, request.OrganizationId);
            insert.Parameters.AddWithValue("content", NpgsqlDbType.Jsonb, JsonSerializer.Serialize(request.Template, JsonOptions));
            insert.Parameters.AddWithValue("author_id", NpgsqlDbType.Uuid, actorAccountId);
            await insert.ExecuteNonQueryAsync(cancellationToken);
            await transaction.CommitAsync(cancellationToken);
            return new ProjectTemplateOperation("accepted", request.TemplateId!, request.TemplateVersion, ProjectTemplateLifecycleState.Draft);
        }
        catch (ApiException)
        {
            throw;
        }
        catch (PostgresException exception) when (exception.SqlState == PostgresErrorCodes.UniqueViolation)
        {
            throw Conflict("TEMPLATE_VERSION_EXISTS", "The template version already exists.", exception);
        }
        catch (PostgresException exception) when (exception.SqlState == PostgresErrorCodes.InsufficientPrivilege)
        {
            throw Forbidden("TEMPLATE_DATABASE_POLICY_DENIED", exception);
        }
        catch (PostgresException exception) when (exception.SqlState is PostgresErrorCodes.CheckViolation or PostgresErrorCodes.ForeignKeyViolation)
        {
            throw Conflict("TEMPLATE_DRAFT_GUARD_REJECTED", "The project template draft violates a catalog constraint.", exception);
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

    public async Task<ProjectTemplateOperation> TransitionAsync(
        Guid actorAccountId,
        string templateId,
        int templateVersion,
        ProjectTemplateTransitionRequest request,
        CancellationToken cancellationToken)
    {
        ValidateTransitionRequest(actorAccountId, templateId, templateVersion, request);
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetActorAsync(connection, transaction, actorAccountId, request.Reason, cancellationToken);
            await LockTemplateAsync(connection, transaction, templateId, cancellationToken);
            var current = await ReadVersionForUpdateAsync(
                connection,
                transaction,
                templateId,
                templateVersion,
                cancellationToken);
            if (current is null)
                throw new ApiException(StatusCodes.Status404NotFound, "TEMPLATE_NOT_FOUND", "The template version was not found.");

            var role = await ReadMembershipRoleAsync(
                connection,
                transaction,
                actorAccountId,
                current.OrganizationId,
                cancellationToken);
            var actorRole = role is null ? null : AccessPolicy.ToContentActorRole(role.Value);
            if (actorRole is null)
                throw Forbidden(role is null ? "MEMBERSHIP_REQUIRED" : "ROLE_REQUIRED");

            var transition = ProjectTemplateWorkflow.ValidateTransition(
                current.State,
                request.TargetState,
                current.AuthorId,
                actorAccountId,
                role!.Value);
            if (!transition.Allowed)
                throw Conflict(transition.Code, "The project template transition is not allowed.");

            var documentError = ProjectTemplateDocumentValidator.Validate(current.Template);
            if (request.TargetState is ProjectTemplateLifecycleState.Approved or ProjectTemplateLifecycleState.Published
                && documentError is not null)
                throw InvalidStoredTemplate();

            var isReviewDecision = current.State == ProjectTemplateLifecycleState.Review
                && request.TargetState is ProjectTemplateLifecycleState.Approved or ProjectTemplateLifecycleState.Draft;
            if (isReviewDecision && string.IsNullOrWhiteSpace(request.Reason))
                throw InvalidRequest("TEMPLATE_REVIEW_REASON_REQUIRED");
            if (request.Reason is { Length: > 2000 } || request.Reason is not null && string.IsNullOrWhiteSpace(request.Reason))
                throw InvalidRequest("TEMPLATE_TRANSITION_REASON_INVALID");

            var reviewedIds = request.ReviewedExampleIds;
            if (request.TargetState == ProjectTemplateLifecycleState.Approved)
            {
                var reviewedError = ProjectTemplateDocumentValidator.ValidateReviewedExampleIds(
                    current.Template,
                    reviewedIds,
                    required: true);
                if (reviewedError is not null) throw InvalidRequest(reviewedError);
            }
            else if (reviewedIds is { Count: > 0 })
            {
                throw InvalidRequest("REVIEWED_EXAMPLES_ONLY_ALLOWED_ON_APPROVAL");
            }

            if (request.TargetState == ProjectTemplateLifecycleState.Published)
            {
                var approvedReviewedIds = await ReadLatestApprovedReviewedExampleIdsAsync(
                    connection,
                    transaction,
                    templateId,
                    templateVersion,
                    cancellationToken);
                var reviewedError = ProjectTemplateDocumentValidator.ValidateReviewedExampleIds(
                    current.Template,
                    approvedReviewedIds,
                    required: true);
                if (reviewedError is not null)
                    throw Conflict("TEMPLATE_REVIEW_GATE_NOT_SATISFIED", "The approved review must cover both scenario kinds before publication.");
            }

            if (isReviewDecision)
            {
                await InsertReviewDecisionAsync(
                    connection,
                    transaction,
                    templateId,
                    templateVersion,
                    actorAccountId,
                    request.TargetState == ProjectTemplateLifecycleState.Approved ? "approved" : "rejected",
                    request.Reason!.Trim(),
                    reviewedIds ?? Array.Empty<string>(),
                    cancellationToken);
            }

            if (request.TargetState == ProjectTemplateLifecycleState.Published)
            {
                var currentPublished = await ReadCurrentPublishedVersionAsync(
                    connection,
                    transaction,
                    templateId,
                    cancellationToken);
                if (currentPublished is { } previousVersion)
                {
                    await SetLifecycleReasonAsync(
                        connection,
                        transaction,
                        $"Superseded by version {templateVersion}",
                        cancellationToken);
                    await RetireCurrentPublishedAsync(
                        connection,
                        transaction,
                        templateId,
                        previousVersion,
                        actorAccountId,
                        cancellationToken);
                    await SetLifecycleReasonAsync(connection, transaction, request.Reason, cancellationToken);
                }
            }

            await UpdateStateAsync(
                connection,
                transaction,
                templateId,
                templateVersion,
                current.State,
                request.TargetState,
                actorAccountId,
                cancellationToken);
            await transaction.CommitAsync(cancellationToken);
            return new ProjectTemplateOperation("accepted", templateId, templateVersion, request.TargetState);
        }
        catch (ApiException)
        {
            throw;
        }
        catch (PostgresException exception) when (exception.SqlState is PostgresErrorCodes.UniqueViolation or PostgresErrorCodes.SerializationFailure)
        {
            throw Conflict("TEMPLATE_VERSION_CHANGED", "The template changed during this operation.", exception);
        }
        catch (PostgresException exception) when (exception.SqlState == PostgresErrorCodes.InsufficientPrivilege)
        {
            throw Forbidden("TEMPLATE_DATABASE_POLICY_DENIED", exception);
        }
        catch (PostgresException exception) when (exception.SqlState is PostgresErrorCodes.CheckViolation or PostgresErrorCodes.ForeignKeyViolation)
        {
            throw Conflict("TEMPLATE_TRANSITION_GUARD_REJECTED", "The project template transition violates a catalog constraint.", exception);
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

    private static ProjectTemplateCatalogEntry ReadCatalogEntry(NpgsqlDataReader reader)
    {
        var template = JsonSerializer.Deserialize<ProjectTemplateDocument>(reader.GetString(3), JsonOptions)
            ?? throw InvalidStoredTemplate();
        if (ProjectTemplateDocumentValidator.Validate(template) is not null)
            throw InvalidStoredTemplate();
        var reviewedIds = reader.GetFieldValue<string[]>(5).ToHashSet(StringComparer.Ordinal);
        template = ProjectTemplateDocumentValidator.WithReviewedExamples(template, reviewedIds);
        if (ProjectTemplateDocumentValidator.Validate(template) is not null
            || ProjectTemplateDocumentValidator.ValidateReviewedExampleIds(
                template,
                reviewedIds.ToArray(),
                required: true,
                requireBothScenarioKinds: false) is not null)
            throw InvalidStoredTemplate();
        return new ProjectTemplateCatalogEntry(
            reader.GetString(0),
            reader.GetInt32(1),
            reader.GetString(2),
            template,
            reader.GetFieldValue<DateTimeOffset>(4));
    }

    private static async Task LockTemplateAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        string templateId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = "select pg_advisory_xact_lock(hashtextextended(@template_id, 0));";
        command.Parameters.AddWithValue("template_id", NpgsqlDbType.Text, templateId);
        await command.ExecuteNonQueryAsync(cancellationToken);
    }

    private static async Task SetActorAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid actorAccountId,
        string? reason,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            select set_config('request.jwt.claim.sub', @actor_id, true),
                   set_config('evidrilo.project_template_lifecycle_reason', @reason, true);
            """;
        command.Parameters.AddWithValue("actor_id", NpgsqlDbType.Text, actorAccountId.ToString());
        command.Parameters.AddWithValue("reason", NpgsqlDbType.Text, reason ?? string.Empty);
        await command.ExecuteNonQueryAsync(cancellationToken);
    }

    private static async Task SetLifecycleReasonAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        string? reason,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = "select set_config('evidrilo.project_template_lifecycle_reason', @reason, true);";
        command.Parameters.AddWithValue("reason", NpgsqlDbType.Text, reason ?? string.Empty);
        await command.ExecuteNonQueryAsync(cancellationToken);
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
            select role from public.organization_memberships
            where organization_id = @organization_id
              and account_id = @account_id
              and active = true;
            """;
        command.Parameters.AddWithValue("organization_id", NpgsqlDbType.Uuid, organizationId);
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        var role = await command.ExecuteScalarAsync(cancellationToken);
        return role is string roleName ? ParseRole(roleName) : null;
    }

    private static async Task<LatestTemplate?> ReadLatestTemplateAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        string templateId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            select template_version, family, organization_id
            from public.project_template_versions
            where template_id = @template_id
            order by template_version desc
            limit 1;
            """;
        command.Parameters.AddWithValue("template_id", NpgsqlDbType.Text, templateId);
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        return await reader.ReadAsync(cancellationToken)
            ? new LatestTemplate(reader.GetInt32(0), reader.GetString(1), reader.GetGuid(2))
            : null;
    }

    private static async Task<StoredTemplate?> ReadVersionForUpdateAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        string templateId,
        int templateVersion,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            select family, organization_id, content::text, state, author_id
            from public.project_template_versions
            where template_id = @template_id and template_version = @template_version
            for update;
            """;
        command.Parameters.AddWithValue("template_id", NpgsqlDbType.Text, templateId);
        command.Parameters.AddWithValue("template_version", NpgsqlDbType.Integer, templateVersion);
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        if (!await reader.ReadAsync(cancellationToken)) return null;
        var template = JsonSerializer.Deserialize<ProjectTemplateDocument>(reader.GetString(2), JsonOptions)
            ?? throw InvalidStoredTemplate();
        if (ProjectTemplateDocumentValidator.Validate(template) is not null)
            throw InvalidStoredTemplate();
        return new StoredTemplate(
            reader.GetString(0),
            reader.GetGuid(1),
            template,
            ParseState(reader.GetString(3)),
            reader.IsDBNull(4) ? Guid.Empty : reader.GetGuid(4));
    }

    private static async Task<int?> ReadCurrentPublishedVersionAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        string templateId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            select template_version from public.project_template_versions
            where template_id = @template_id and is_current_published = true
            for update;
            """;
        command.Parameters.AddWithValue("template_id", NpgsqlDbType.Text, templateId);
        var value = await command.ExecuteScalarAsync(cancellationToken);
        return value is int version ? version : null;
    }

    private static async Task<IReadOnlyList<string>> ReadLatestApprovedReviewedExampleIdsAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        string templateId,
        int templateVersion,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            select reviewed_example_ids
            from public.project_template_review_decisions
            where template_id = @template_id
              and template_version = @template_version
              and decision = 'approved'
            order by created_at desc, review_decision_id desc
            limit 1;
            """;
        command.Parameters.AddWithValue("template_id", NpgsqlDbType.Text, templateId);
        command.Parameters.AddWithValue("template_version", NpgsqlDbType.Integer, templateVersion);
        var result = await command.ExecuteScalarAsync(cancellationToken);
        return result is string[] reviewedIds ? reviewedIds : Array.Empty<string>();
    }

    private static async Task InsertReviewDecisionAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        string templateId,
        int templateVersion,
        Guid reviewerId,
        string decision,
        string reason,
        IReadOnlyList<string> reviewedExampleIds,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            insert into public.project_template_review_decisions (
                template_id, template_version, reviewer_id, decision, reason, reviewed_example_ids
            ) values (
                @template_id, @template_version, @reviewer_id, @decision, @reason, @reviewed_example_ids
            );
            """;
        command.Parameters.AddWithValue("template_id", NpgsqlDbType.Text, templateId);
        command.Parameters.AddWithValue("template_version", NpgsqlDbType.Integer, templateVersion);
        command.Parameters.AddWithValue("reviewer_id", NpgsqlDbType.Uuid, reviewerId);
        command.Parameters.AddWithValue("decision", NpgsqlDbType.Text, decision);
        command.Parameters.AddWithValue("reason", NpgsqlDbType.Text, reason);
        command.Parameters.AddWithValue("reviewed_example_ids", NpgsqlDbType.Array | NpgsqlDbType.Text, reviewedExampleIds.ToArray());
        await command.ExecuteNonQueryAsync(cancellationToken);
    }

    private static async Task RetireCurrentPublishedAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        string templateId,
        int version,
        Guid actorAccountId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            update public.project_template_versions
            set state = 'retired', is_current_published = false,
                updated_at = now(), retired_by = @actor_id
            where template_id = @template_id
              and template_version = @template_version
              and state = 'published'
              and is_current_published = true;
            """;
        command.Parameters.AddWithValue("template_id", NpgsqlDbType.Text, templateId);
        command.Parameters.AddWithValue("template_version", NpgsqlDbType.Integer, version);
        command.Parameters.AddWithValue("actor_id", NpgsqlDbType.Uuid, actorAccountId);
        if (await command.ExecuteNonQueryAsync(cancellationToken) != 1)
            throw Conflict("TEMPLATE_VERSION_CHANGED", "The current published template changed during this operation.");
    }

    private static async Task UpdateStateAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        string templateId,
        int templateVersion,
        ProjectTemplateLifecycleState currentState,
        ProjectTemplateLifecycleState targetState,
        Guid actorAccountId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            update public.project_template_versions
            set state = @target_state,
                reviewer_id = case when @target_state = 'approved' then @actor_id else reviewer_id end,
                retired_by = case when @target_state = 'retired' then @actor_id else retired_by end,
                is_current_published = (@target_state = 'published'),
                published_at = case when @target_state = 'published' then coalesce(published_at, now()) else published_at end,
                updated_at = now()
            where template_id = @template_id
              and template_version = @template_version
              and state = @current_state;
            """;
        command.Parameters.AddWithValue("target_state", NpgsqlDbType.Text, ToWire(targetState));
        command.Parameters.AddWithValue("actor_id", NpgsqlDbType.Uuid, actorAccountId);
        command.Parameters.AddWithValue("template_id", NpgsqlDbType.Text, templateId);
        command.Parameters.AddWithValue("template_version", NpgsqlDbType.Integer, templateVersion);
        command.Parameters.AddWithValue("current_state", NpgsqlDbType.Text, ToWire(currentState));
        if (await command.ExecuteNonQueryAsync(cancellationToken) != 1)
            throw Conflict("TEMPLATE_VERSION_CHANGED", "The template changed during this operation.");
    }

    private static string? ValidateDraftRequest(Guid actorAccountId, ProjectTemplateDraftCreateRequest? request)
    {
        if (actorAccountId == Guid.Empty
            || request is null
            || request.Schema != "evidrilo.project-template-draft"
            || request.Version != "1"
            || request.OrganizationId == Guid.Empty
            || request.TemplateVersion < 1
            || !IsTemplateId(request.TemplateId)
            || !ProjectTemplateFamilies.TryFind(request.Family, out _))
            return "INVALID_PROJECT_TEMPLATE_DRAFT";
        return ProjectTemplateDocumentValidator.ValidateDraft(request.Template);
    }

    private static void ValidateTransitionRequest(
        Guid actorAccountId,
        string templateId,
        int templateVersion,
        ProjectTemplateTransitionRequest? request)
    {
        if (actorAccountId == Guid.Empty
            || !IsTemplateId(templateId)
            || templateVersion < 1
            || request is null
            || request.Schema != "evidrilo.project-template-transition"
            || request.Version != "1"
            || !Enum.IsDefined(request.TargetState))
            throw InvalidRequest("INVALID_PROJECT_TEMPLATE_TRANSITION");
        if (request.ReviewedExampleIds is { Count: > 24 })
            throw InvalidRequest("INVALID_REVIEWED_EXAMPLE_REFERENCE");
    }

    private static bool IsTemplateId(string? value) =>
        ProjectTemplateDocumentValidator.IsValidTemplateId(value);

    private static PlatformRole ParseRole(string role) => role switch
    {
        "learner" => PlatformRole.Learner,
        "author" => PlatformRole.Author,
        "teacher" => PlatformRole.Teacher,
        "reviewer" => PlatformRole.Reviewer,
        "maintainer" => PlatformRole.Maintainer,
        "owner" => PlatformRole.Owner,
        _ => throw new ApiException(StatusCodes.Status503ServiceUnavailable, "DATABASE_SCHEMA_MISSING", "The authorization schema is not ready."),
    };

    private static ProjectTemplateLifecycleState ParseState(string state) => state switch
    {
        "draft" => ProjectTemplateLifecycleState.Draft,
        "review" => ProjectTemplateLifecycleState.Review,
        "approved" => ProjectTemplateLifecycleState.Approved,
        "published" => ProjectTemplateLifecycleState.Published,
        "retired" => ProjectTemplateLifecycleState.Retired,
        _ => throw new ApiException(StatusCodes.Status503ServiceUnavailable, "DATABASE_SCHEMA_MISSING", "The project template schema is not ready."),
    };

    private static string ToWire(ProjectTemplateLifecycleState state) => state switch
    {
        ProjectTemplateLifecycleState.Draft => "draft",
        ProjectTemplateLifecycleState.Review => "review",
        ProjectTemplateLifecycleState.Approved => "approved",
        ProjectTemplateLifecycleState.Published => "published",
        ProjectTemplateLifecycleState.Retired => "retired",
        _ => throw new ArgumentOutOfRangeException(nameof(state)),
    };

    private static ApiException InvalidRequest(string code) => new(
        StatusCodes.Status400BadRequest,
        code,
        "The project template request is invalid.");

    private static ApiException InvalidStoredTemplate(Exception? exception = null) => exception is null
        ? new ApiException(StatusCodes.Status503ServiceUnavailable, "PUBLISHED_TEMPLATE_INVALID", "A published project template is not valid.")
        : new ApiException(StatusCodes.Status503ServiceUnavailable, "PUBLISHED_TEMPLATE_INVALID", "A published project template is not valid.", exception);

    private static ApiException Forbidden(string code, Exception? exception = null) => exception is null
        ? new ApiException(StatusCodes.Status403Forbidden, code, "You are not allowed to perform this project template operation.")
        : new ApiException(StatusCodes.Status403Forbidden, code, "You are not allowed to perform this project template operation.", exception);

    private static ApiException Conflict(string code, string message, Exception? exception = null) => exception is null
        ? new ApiException(StatusCodes.Status409Conflict, code, message)
        : new ApiException(StatusCodes.Status409Conflict, code, message, exception);

    private static ApiException DatabaseUnavailable(Exception exception) => new(
        StatusCodes.Status503ServiceUnavailable,
        "DATABASE_UNAVAILABLE",
        "The project template catalog is temporarily unavailable.",
        exception);

    private sealed record LatestTemplate(int TemplateVersion, string Family, Guid OrganizationId);

    private sealed record StoredTemplate(
        string Family,
        Guid OrganizationId,
        ProjectTemplateDocument Template,
        ProjectTemplateLifecycleState State,
        Guid AuthorId);
}
