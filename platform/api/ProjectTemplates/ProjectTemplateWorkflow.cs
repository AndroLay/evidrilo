using Evidrilo.Api.Authorization;

namespace Evidrilo.Api.ProjectTemplates;

public sealed record ProjectTemplateTransitionDecision(bool Allowed, string Code)
{
    public static ProjectTemplateTransitionDecision Allow() => new(true, "ALLOWED");
    public static ProjectTemplateTransitionDecision Deny(string code) => new(false, code);
}

public static class ProjectTemplateWorkflow
{
    public static ProjectTemplateTransitionDecision ValidateTransition(
        ProjectTemplateLifecycleState current,
        ProjectTemplateLifecycleState target,
        Guid authorId,
        Guid actorId,
        PlatformRole actorRole)
    {
        if (actorId == Guid.Empty) return ProjectTemplateTransitionDecision.Deny("TEMPLATE_ACTOR_REQUIRED");
        if (current == ProjectTemplateLifecycleState.Retired)
            return ProjectTemplateTransitionDecision.Deny("RETIRED_TEMPLATE_IMMUTABLE");

        var isMaintainer = actorRole is PlatformRole.Maintainer or PlatformRole.Owner;
        var isReviewer = actorRole is PlatformRole.Reviewer or PlatformRole.Maintainer or PlatformRole.Owner;
        var allowed = (current, target) switch
        {
            (ProjectTemplateLifecycleState.Draft, ProjectTemplateLifecycleState.Review) =>
                isMaintainer || (actorRole == PlatformRole.Author && authorId == actorId),
            (ProjectTemplateLifecycleState.Review, ProjectTemplateLifecycleState.Approved) =>
                isReviewer && authorId != actorId,
            (ProjectTemplateLifecycleState.Review, ProjectTemplateLifecycleState.Draft) =>
                isReviewer && authorId != actorId,
            (ProjectTemplateLifecycleState.Approved, ProjectTemplateLifecycleState.Published) => isMaintainer,
            (_, ProjectTemplateLifecycleState.Retired) => isMaintainer,
            _ => false,
        };

        if (current == ProjectTemplateLifecycleState.Review
            && target is ProjectTemplateLifecycleState.Approved or ProjectTemplateLifecycleState.Draft
            && authorId == actorId)
            return ProjectTemplateTransitionDecision.Deny("TEMPLATE_AUTHOR_CANNOT_REVIEW_OWN");

        return allowed
            ? ProjectTemplateTransitionDecision.Allow()
            : ProjectTemplateTransitionDecision.Deny("INVALID_TEMPLATE_TRANSITION");
    }
}
