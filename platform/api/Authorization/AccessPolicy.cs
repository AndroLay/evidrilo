using Evidrilo.Api.Content;

namespace Evidrilo.Api.Authorization;

public enum PlatformRole
{
    Learner,
    Author,
    Teacher,
    Reviewer,
    Maintainer,
    Owner,
}

public sealed record Membership(
    Guid AccountId,
    Guid OrganizationId,
    PlatformRole Role,
    bool Active);

public sealed record CohortScope(
    Guid OrganizationId,
    Guid CohortId,
    int ActiveLearnerCount);

public sealed record AuthorizationDecision(bool Allowed, string Code)
{
    public static AuthorizationDecision Allow() => new(true, "ALLOWED");
    public static AuthorizationDecision Deny(string code) => new(false, code);
}

public static class AccessPolicy
{
    public const int AggregateSuppressionThreshold = 5;

    public static AuthorizationDecision CanReadCohortAggregate(
        Membership? membership,
        CohortScope cohort)
    {
        if (membership is null || !membership.Active)
            return AuthorizationDecision.Deny("MEMBERSHIP_REQUIRED");
        if (membership.OrganizationId != cohort.OrganizationId)
            return AuthorizationDecision.Deny("ORGANIZATION_SCOPE_REQUIRED");
        if (membership.Role is not (PlatformRole.Owner or PlatformRole.Teacher or PlatformRole.Maintainer))
            return AuthorizationDecision.Deny("ROLE_REQUIRED");
        if (cohort.ActiveLearnerCount < AggregateSuppressionThreshold)
            return AuthorizationDecision.Deny("COHORT_SUPPRESSED");
        return AuthorizationDecision.Allow();
    }

    public static AuthorizationDecision CanReviewContent(Membership? membership, Guid organizationId)
    {
        if (membership is null || !membership.Active)
            return AuthorizationDecision.Deny("MEMBERSHIP_REQUIRED");
        if (membership.OrganizationId != organizationId)
            return AuthorizationDecision.Deny("ORGANIZATION_SCOPE_REQUIRED");
        return membership.Role is PlatformRole.Reviewer or PlatformRole.Maintainer or PlatformRole.Owner
            ? AuthorizationDecision.Allow()
            : AuthorizationDecision.Deny("REVIEWER_ROLE_REQUIRED");
    }

    public static AuthorizationDecision CanAuthorContent(Membership? membership, Guid organizationId)
    {
        if (membership is null || !membership.Active)
            return AuthorizationDecision.Deny("MEMBERSHIP_REQUIRED");
        if (membership.OrganizationId != organizationId)
            return AuthorizationDecision.Deny("ORGANIZATION_SCOPE_REQUIRED");
        return membership.Role is PlatformRole.Author or PlatformRole.Maintainer or PlatformRole.Owner
            ? AuthorizationDecision.Allow()
            : AuthorizationDecision.Deny("AUTHOR_ROLE_REQUIRED");
    }

    public static AuthorizationDecision CanReadContentAudit(Membership? membership, Guid organizationId)
    {
        if (membership is null || !membership.Active)
            return AuthorizationDecision.Deny("MEMBERSHIP_REQUIRED");
        if (membership.OrganizationId != organizationId)
            return AuthorizationDecision.Deny("ORGANIZATION_SCOPE_REQUIRED");
        return membership.Role is PlatformRole.Author
            or PlatformRole.Reviewer
            or PlatformRole.Maintainer
            or PlatformRole.Owner
            ? AuthorizationDecision.Allow()
            : AuthorizationDecision.Deny("ROLE_REQUIRED");
    }

    public static AuthorizationDecision CanManageMembership(Membership? membership, Guid organizationId)
    {
        if (membership is null || !membership.Active)
            return AuthorizationDecision.Deny("MEMBERSHIP_REQUIRED");
        if (membership.OrganizationId != organizationId)
            return AuthorizationDecision.Deny("ORGANIZATION_SCOPE_REQUIRED");
        return membership.Role is PlatformRole.Owner or PlatformRole.Maintainer
            ? AuthorizationDecision.Allow()
            : AuthorizationDecision.Deny("MEMBERSHIP_MANAGER_ROLE_REQUIRED");
    }

    public static AuthorizationDecision CanAssignRole(Membership membership, PlatformRole targetRole)
    {
        if (membership.Role == PlatformRole.Owner) return AuthorizationDecision.Allow();
        if (membership.Role == PlatformRole.Maintainer
            && targetRole is PlatformRole.Learner
                or PlatformRole.Author
                or PlatformRole.Teacher
                or PlatformRole.Reviewer)
            return AuthorizationDecision.Allow();
        return AuthorizationDecision.Deny("ROLE_ASSIGNMENT_NOT_ALLOWED");
    }

    public static AuthorizationDecision CanChangeMembershipRole(
        Membership? target,
        PlatformRole requestedRole,
        int activeOwnerCount)
    {
        if (target is not { Active: true, Role: PlatformRole.Owner }
            || requestedRole == PlatformRole.Owner)
            return AuthorizationDecision.Allow();

        return activeOwnerCount > 1
            ? AuthorizationDecision.Allow()
            : AuthorizationDecision.Deny("LAST_OWNER_PROTECTED");
    }

    public static ContentActorRole? ToContentActorRole(PlatformRole role) => role switch
    {
        PlatformRole.Author => ContentActorRole.Author,
        PlatformRole.Reviewer => ContentActorRole.Reviewer,
        PlatformRole.Maintainer or PlatformRole.Owner => ContentActorRole.Maintainer,
        _ => null,
    };

    public static AuthorizationDecision CanReadRawDraft(Membership? membership) =>
        AuthorizationDecision.Deny("RAW_DRAFT_ACCESS_DISABLED");
}
