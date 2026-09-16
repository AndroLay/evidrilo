using Evidrilo.Api.Authorization;

namespace Evidrilo.Api.Tests;

public sealed class AccessPolicyTests
{
    private static readonly Guid AccountId = Guid.Parse("123e4567-e89b-42d3-a456-426614174000");
    private static readonly Guid OrganizationId = Guid.Parse("123e4567-e89b-42d3-a456-426614174001");
    private static readonly Guid OtherOrganizationId = Guid.Parse("123e4567-e89b-42d3-a456-426614174002");

    [Fact]
    public void Aggregate_access_requires_active_scoped_teacher_and_suppression_threshold()
    {
        var teacher = new Membership(AccountId, OrganizationId, PlatformRole.Teacher, true);
        Assert.True(AccessPolicy.CanReadCohortAggregate(teacher, new(OrganizationId, Guid.NewGuid(), 5)).Allowed);
        Assert.Equal("COHORT_SUPPRESSED", AccessPolicy.CanReadCohortAggregate(teacher, new(OrganizationId, Guid.NewGuid(), 4)).Code);
        Assert.Equal("ORGANIZATION_SCOPE_REQUIRED", AccessPolicy.CanReadCohortAggregate(teacher, new(OtherOrganizationId, Guid.NewGuid(), 20)).Code);
    }

    [Fact]
    public void Learners_cannot_escalate_to_teacher_or_raw_draft_access()
    {
        var learner = new Membership(AccountId, OrganizationId, PlatformRole.Learner, true);
        Assert.Equal("ROLE_REQUIRED", AccessPolicy.CanReadCohortAggregate(learner, new(OrganizationId, Guid.NewGuid(), 20)).Code);
        Assert.Equal("RAW_DRAFT_ACCESS_DISABLED", AccessPolicy.CanReadRawDraft(learner).Code);
    }

    [Fact]
    public void Reviewer_scope_is_explicit()
    {
        var reviewer = new Membership(AccountId, OrganizationId, PlatformRole.Reviewer, true);
        Assert.True(AccessPolicy.CanReviewContent(reviewer, OrganizationId).Allowed);
        Assert.Equal("REVIEWER_ROLE_REQUIRED", AccessPolicy.CanReviewContent(
            new Membership(AccountId, OrganizationId, PlatformRole.Teacher, true), OrganizationId).Code);
    }

    [Fact]
    public void Membership_management_cannot_let_maintainers_grant_owner_access()
    {
        var maintainer = new Membership(AccountId, OrganizationId, PlatformRole.Maintainer, true);
        Assert.True(AccessPolicy.CanManageMembership(maintainer, OrganizationId).Allowed);
        Assert.Equal(
            "ROLE_ASSIGNMENT_NOT_ALLOWED",
            AccessPolicy.CanAssignRole(maintainer, PlatformRole.Owner).Code);
        Assert.True(AccessPolicy.CanAssignRole(maintainer, PlatformRole.Author).Allowed);
    }

    [Theory]
    [InlineData(PlatformRole.Learner, PlatformRole.Author)]
    [InlineData(PlatformRole.Teacher, PlatformRole.Reviewer)]
    [InlineData(PlatformRole.Reviewer, PlatformRole.Learner)]
    public void Non_owner_non_maintainer_roles_cannot_assign_membership_roles(
        PlatformRole actorRole,
        PlatformRole targetRole)
    {
        var actor = new Membership(AccountId, OrganizationId, actorRole, true);

        Assert.Equal(
            "ROLE_ASSIGNMENT_NOT_ALLOWED",
            AccessPolicy.CanAssignRole(actor, targetRole).Code);
    }

    [Fact]
    public void Membership_role_change_cannot_demote_the_last_active_owner()
    {
        var owner = new Membership(AccountId, OrganizationId, PlatformRole.Owner, true);

        Assert.Equal(
            "LAST_OWNER_PROTECTED",
            AccessPolicy.CanChangeMembershipRole(owner, PlatformRole.Teacher, 1).Code);
        Assert.True(AccessPolicy.CanChangeMembershipRole(owner, PlatformRole.Teacher, 2).Allowed);
        Assert.True(AccessPolicy.CanChangeMembershipRole(owner, PlatformRole.Owner, 1).Allowed);
    }

    [Fact]
    public void Membership_command_rejects_empty_or_unknown_reason_shape()
    {
        var valid = new MembershipCommand(
            "evidrilo.membership-command",
            "1",
            Guid.NewGuid(),
            PlatformRole.Teacher,
            "setup");

        Assert.Null(MembershipCommandValidator.Validate(valid));
        Assert.Equal(
            "INVALID_MEMBERSHIP_COMMAND",
            MembershipCommandValidator.Validate(valid with { Reason = "   " }));
        Assert.Equal(
            "INVALID_MEMBERSHIP_COMMAND",
            MembershipCommandValidator.Validate(valid with { Role = (PlatformRole)999 }));
    }
}
