using Evidrilo.Api.Common;
using Npgsql;
using NpgsqlTypes;

namespace Evidrilo.Api.Authorization;

public interface IMembershipStore
{
    Task<MembershipOperation> GrantAsync(
        Guid actorAccountId,
        Guid organizationId,
        MembershipCommand request,
        CancellationToken cancellationToken);

    Task<MembershipOperation> RevokeAsync(
        Guid actorAccountId,
        Guid organizationId,
        Guid targetAccountId,
        CancellationToken cancellationToken);

    Task<MembershipOperation> LeaveAsync(
        Guid accountId,
        Guid organizationId,
        CancellationToken cancellationToken);
}

public sealed class DatabaseUnavailableMembershipStore : IMembershipStore
{
    public Task<MembershipOperation> GrantAsync(
        Guid actorAccountId,
        Guid organizationId,
        MembershipCommand request,
        CancellationToken cancellationToken) => throw NotConfigured();

    public Task<MembershipOperation> RevokeAsync(
        Guid actorAccountId,
        Guid organizationId,
        Guid targetAccountId,
        CancellationToken cancellationToken) => throw NotConfigured();

    public Task<MembershipOperation> LeaveAsync(
        Guid accountId,
        Guid organizationId,
        CancellationToken cancellationToken) => throw NotConfigured();

    private static ApiException NotConfigured() => new(
        StatusCodes.Status503ServiceUnavailable,
        "DATABASE_NOT_CONFIGURED",
        "Membership lifecycle is not configured.");
}

public sealed class NpgsqlMembershipStore : IMembershipStore, IDisposable
{
    private readonly NpgsqlDataSource dataSource;

    public NpgsqlMembershipStore(string connectionString)
    {
        dataSource = NpgsqlDataSource.Create(connectionString);
    }

    public Task<MembershipOperation> GrantAsync(
        Guid actorAccountId,
        Guid organizationId,
        MembershipCommand request,
        CancellationToken cancellationToken) => ExecuteAsync(
        actorAccountId,
        organizationId,
        async (connection, transaction, actor, cancellation) =>
        {
            var management = AccessPolicy.CanManageMembership(actor, organizationId);
            if (!management.Allowed) throw Forbidden(management.Code);
            var assignment = AccessPolicy.CanAssignRole(actor!, request.Role);
            if (!assignment.Allowed) throw Forbidden(assignment.Code);
            if (request.TargetAccountId == actorAccountId
                && actor!.Role != request.Role)
                throw Conflict("SELF_ROLE_CHANGE_REQUIRED", "Change your own role through a separate owner transfer operation.");

            var target = await ReadMembershipAsync(
                connection,
                transaction,
                organizationId,
                request.TargetAccountId,
                cancellation);
            if (target is { Active: true, Role: PlatformRole.Owner }
                && request.Role != PlatformRole.Owner)
            {
                var ownerGuard = AccessPolicy.CanChangeMembershipRole(
                    target,
                    request.Role,
                    await ReadActiveOwnerCountAsync(connection, transaction, organizationId, cancellation));
                if (!ownerGuard.Allowed)
                    throw Conflict("LAST_OWNER_PROTECTED", "Transfer ownership before demoting the last owner.");
            }

            var outcome = target is { Active: true, Role: var existingRole }
                ? existingRole == request.Role ? "already_active" : "role_changed"
                : "granted";

            await using var command = connection.CreateCommand();
            command.Transaction = transaction;
            command.CommandText = """
                insert into public.organization_memberships (
                    organization_id, account_id, role, active
                ) values (@organization_id, @account_id, @role, true)
                on conflict (organization_id, account_id) do update
                    set role = excluded.role, active = true;
                """;
            command.Parameters.AddWithValue("organization_id", NpgsqlDbType.Uuid, organizationId);
            command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, request.TargetAccountId);
            command.Parameters.AddWithValue("role", NpgsqlDbType.Text, request.Role.ToWire());
            await command.ExecuteNonQueryAsync(cancellation);
            if (outcome != "already_active")
                await WriteAuditAsync(
                    connection,
                    transaction,
                    organizationId,
                    actorAccountId,
                    request.TargetAccountId,
                    outcome == "role_changed" ? "role_changed" : "granted",
                    request.Role,
                    request.Reason,
                    cancellation);
            return new MembershipOperation(outcome, organizationId, request.TargetAccountId, request.Role);
        },
        cancellationToken);

    public Task<MembershipOperation> RevokeAsync(
        Guid actorAccountId,
        Guid organizationId,
        Guid targetAccountId,
        CancellationToken cancellationToken) => ExecuteAsync(
        actorAccountId,
        organizationId,
        async (connection, transaction, actor, cancellation) =>
        {
            var management = AccessPolicy.CanManageMembership(actor, organizationId);
            if (!management.Allowed) throw Forbidden(management.Code);
            if (targetAccountId == actorAccountId)
                throw Conflict("USE_LEAVE_OPERATION", "Use the leave operation to remove your own membership.");

            var target = await ReadMembershipAsync(
                connection,
                transaction,
                organizationId,
                targetAccountId,
                cancellation);
            if (target is null || !target.Active)
                return new MembershipOperation("already_revoked", organizationId, targetAccountId, target?.Role);
            if (target.Role == PlatformRole.Owner
                && await ReadActiveOwnerCountAsync(connection, transaction, organizationId, cancellation) <= 1)
                throw Conflict("LAST_OWNER_PROTECTED", "Transfer ownership before revoking the last owner.");

            await SetActiveAsync(
                connection,
                transaction,
                organizationId,
                targetAccountId,
                false,
                cancellation);
            await WriteAuditAsync(
                connection,
                transaction,
                organizationId,
                actorAccountId,
                targetAccountId,
                "revoked",
                target.Role,
                null,
                cancellation);
            return new MembershipOperation("revoked", organizationId, targetAccountId, target.Role);
        },
        cancellationToken);

    public Task<MembershipOperation> LeaveAsync(
        Guid accountId,
        Guid organizationId,
        CancellationToken cancellationToken) => ExecuteAsync(
        accountId,
        organizationId,
        async (connection, transaction, actor, cancellation) =>
        {
            if (actor is null || !actor.Active)
                return new MembershipOperation("already_left", organizationId, accountId, actor?.Role);
            if (actor.Role == PlatformRole.Owner
                && await ReadActiveOwnerCountAsync(connection, transaction, organizationId, cancellation) <= 1)
                throw Conflict("OWNER_TRANSFER_REQUIRED", "Transfer ownership before leaving the organization.");

            await SetActiveAsync(connection, transaction, organizationId, accountId, false, cancellation);
            await WriteAuditAsync(
                connection,
                transaction,
                organizationId,
                accountId,
                accountId,
                "left",
                actor.Role,
                null,
                cancellation);
            return new MembershipOperation("left", organizationId, accountId, actor.Role);
        },
        cancellationToken);

    public void Dispose() => dataSource.Dispose();

    private async Task<MembershipOperation> ExecuteAsync(
        Guid actorAccountId,
        Guid organizationId,
        Func<NpgsqlConnection, NpgsqlTransaction, Membership?, CancellationToken, Task<MembershipOperation>> operation,
        CancellationToken cancellationToken)
    {
        if (actorAccountId == Guid.Empty || organizationId == Guid.Empty)
            throw new ApiException(StatusCodes.Status400BadRequest, "INVALID_MEMBERSHIP_SCOPE", "The membership scope is invalid.");
        try
        {
            await using var connection = await dataSource.OpenConnectionAsync(cancellationToken);
            await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
            await SetRequestAccountAsync(connection, transaction, actorAccountId, cancellationToken);
            await LockOrganizationAsync(connection, transaction, organizationId, cancellationToken);
            var actor = await ReadMembershipAsync(connection, transaction, organizationId, actorAccountId, cancellationToken);
            var result = await operation(connection, transaction, actor, cancellationToken);
            await transaction.CommitAsync(cancellationToken);
            return result;
        }
        catch (ApiException)
        {
            throw;
        }
        catch (PostgresException exception) when (exception.SqlState == "23503")
        {
            throw new ApiException(
                StatusCodes.Status404NotFound,
                "TARGET_ACCOUNT_NOT_FOUND",
                "The target account was not found.",
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
                "Membership lifecycle is temporarily unavailable.",
                exception);
        }
    }

    private static async Task<Membership?> ReadMembershipAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid organizationId,
        Guid accountId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            select role, active
            from public.organization_memberships
            where organization_id = @organization_id and account_id = @account_id;
            """;
        command.Parameters.AddWithValue("organization_id", NpgsqlDbType.Uuid, organizationId);
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        if (!await reader.ReadAsync(cancellationToken)) return null;
        return new Membership(
            accountId,
            organizationId,
            MembershipWireExtensions.FromWire(reader.GetString(0)),
            reader.GetBoolean(1));
    }

    private static async Task LockOrganizationAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid organizationId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            select organization_id
            from public.organizations
            where organization_id = @organization_id
            for update;
            """;
        command.Parameters.AddWithValue("organization_id", NpgsqlDbType.Uuid, organizationId);
        if (await command.ExecuteScalarAsync(cancellationToken) is null)
            throw new ApiException(
                StatusCodes.Status404NotFound,
                "ORGANIZATION_NOT_FOUND",
                "The organization was not found.");
    }

    private static async Task<int> ReadActiveOwnerCountAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid organizationId,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            select count(*)::integer
            from public.organization_memberships
            where organization_id = @organization_id and role = 'owner' and active = true;
            """;
        command.Parameters.AddWithValue("organization_id", NpgsqlDbType.Uuid, organizationId);
        return Convert.ToInt32(await command.ExecuteScalarAsync(cancellationToken));
    }

    private static async Task SetActiveAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid organizationId,
        Guid accountId,
        bool active,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            update public.organization_memberships
               set active = @active
             where organization_id = @organization_id and account_id = @account_id;
            """;
        command.Parameters.AddWithValue("organization_id", NpgsqlDbType.Uuid, organizationId);
        command.Parameters.AddWithValue("account_id", NpgsqlDbType.Uuid, accountId);
        command.Parameters.AddWithValue("active", NpgsqlDbType.Boolean, active);
        await command.ExecuteNonQueryAsync(cancellationToken);
    }

    private static async Task WriteAuditAsync(
        NpgsqlConnection connection,
        NpgsqlTransaction transaction,
        Guid organizationId,
        Guid actorAccountId,
        Guid targetAccountId,
        string eventType,
        PlatformRole role,
        string? reason,
        CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.Transaction = transaction;
        command.CommandText = """
            insert into public.membership_audit_events (
                organization_id, actor_account_id, target_account_id,
                event_type, role, reason
            ) values (
                @organization_id, @actor_account_id, @target_account_id,
                @event_type, @role, @reason
            );
            """;
        command.Parameters.AddWithValue("organization_id", NpgsqlDbType.Uuid, organizationId);
        command.Parameters.AddWithValue("actor_account_id", NpgsqlDbType.Uuid, actorAccountId);
        command.Parameters.AddWithValue("target_account_id", NpgsqlDbType.Uuid, targetAccountId);
        command.Parameters.AddWithValue("event_type", NpgsqlDbType.Text, eventType);
        command.Parameters.AddWithValue("role", NpgsqlDbType.Text, role.ToWire());
        command.Parameters.AddWithValue("reason", NpgsqlDbType.Text, (object?)reason?.Trim() ?? DBNull.Value);
        await command.ExecuteNonQueryAsync(cancellationToken);
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

    private static ApiException Forbidden(string code) => new(
        StatusCodes.Status403Forbidden,
        code,
        "You are not allowed to manage this organization membership.");

    private static ApiException Conflict(string code, string message) => new(
        StatusCodes.Status409Conflict,
        code,
        message);
}
