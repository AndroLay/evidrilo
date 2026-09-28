using Evidrilo.Api.Authorization;
using Evidrilo.Api.ProjectTemplates;

namespace Evidrilo.Api.Tests;

public sealed class ProjectTemplateWorkflowTests
{
    private static readonly Guid AuthorId = Guid.Parse("123e4567-e89b-42d3-a456-426614174000");
    private static readonly Guid ReviewerId = Guid.Parse("123e4567-e89b-42d3-a456-426614174001");

    [Fact]
    public void Author_can_submit_own_draft_for_review()
    {
        var decision = ProjectTemplateWorkflow.ValidateTransition(
            ProjectTemplateLifecycleState.Draft,
            ProjectTemplateLifecycleState.Review,
            AuthorId,
            AuthorId,
            PlatformRole.Author);

        Assert.True(decision.Allowed);
    }

    [Fact]
    public void Author_cannot_approve_their_own_template()
    {
        var decision = ProjectTemplateWorkflow.ValidateTransition(
            ProjectTemplateLifecycleState.Review,
            ProjectTemplateLifecycleState.Approved,
            AuthorId,
            AuthorId,
            PlatformRole.Reviewer);

        Assert.Equal("TEMPLATE_AUTHOR_CANNOT_REVIEW_OWN", decision.Code);
    }

    [Fact]
    public void Reviewer_can_approve_another_authors_template_but_cannot_publish_it()
    {
        var approve = ProjectTemplateWorkflow.ValidateTransition(
            ProjectTemplateLifecycleState.Review,
            ProjectTemplateLifecycleState.Approved,
            AuthorId,
            ReviewerId,
            PlatformRole.Reviewer);
        var publish = ProjectTemplateWorkflow.ValidateTransition(
            ProjectTemplateLifecycleState.Approved,
            ProjectTemplateLifecycleState.Published,
            AuthorId,
            ReviewerId,
            PlatformRole.Reviewer);

        Assert.True(approve.Allowed);
        Assert.Equal("INVALID_TEMPLATE_TRANSITION", publish.Code);
    }

    [Fact]
    public void Maintainer_can_publish_approved_template_and_retire_published_template()
    {
        var maintainer = ProjectTemplateWorkflow.ValidateTransition(
            ProjectTemplateLifecycleState.Approved,
            ProjectTemplateLifecycleState.Published,
            AuthorId,
            ReviewerId,
            PlatformRole.Maintainer);
        var retire = ProjectTemplateWorkflow.ValidateTransition(
            ProjectTemplateLifecycleState.Published,
            ProjectTemplateLifecycleState.Retired,
            AuthorId,
            ReviewerId,
            PlatformRole.Maintainer);

        Assert.True(maintainer.Allowed);
        Assert.True(retire.Allowed);
    }

    [Fact]
    public void Retired_template_version_cannot_be_reopened()
    {
        var decision = ProjectTemplateWorkflow.ValidateTransition(
            ProjectTemplateLifecycleState.Retired,
            ProjectTemplateLifecycleState.Published,
            AuthorId,
            ReviewerId,
            PlatformRole.Owner);

        Assert.Equal("RETIRED_TEMPLATE_IMMUTABLE", decision.Code);
    }
}
