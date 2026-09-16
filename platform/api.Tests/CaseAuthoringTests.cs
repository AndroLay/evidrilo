using System.Text.Json;
using Evidrilo.Api.Content;
using Evidrilo.Api.Authorization;

namespace Evidrilo.Api.Tests;

public sealed class CaseAuthoringTests
{
    private static readonly Guid AuthorId = Guid.Parse("123e4567-e89b-42d3-a456-426614174000");
    private static readonly Guid ReviewerId = Guid.Parse("123e4567-e89b-42d3-a456-426614174001");

    [Fact]
    public void Authoring_validator_requires_fact_anchors_to_resolve()
    {
        var document = ValidDocument() with
        {
            Content = ValidDocument().Content with { FactAnchors = ["missing-fact"] },
        };

        Assert.Equal("INVALID_CASE_AUTHORING_DOCUMENT", CaseAuthoringValidator.Validate(document).Code);
    }

    [Fact]
    public void Authoring_validator_rejects_unknown_rule_anchors_and_duplicate_facts()
    {
        var unknownAnchor = ValidDocument() with
        {
            Rules = [new("rule-1", "PASS", ["missing-fact"])],
        };
        var duplicateFact = ValidDocument() with
        {
            Facts = [ValidDocument().Facts[0], ValidDocument().Facts[0]],
        };

        Assert.False(CaseAuthoringValidator.Validate(unknownAnchor).IsValid);
        Assert.False(CaseAuthoringValidator.Validate(duplicateFact).IsValid);
    }

    [Fact]
    public void Authoring_validator_rejects_a_noop_challenge_variant()
    {
        var noOpChallenge = ValidDocument() with
        {
            Variants = [new("challenge-1", [])],
        };

        Assert.Equal(
            "INVALID_CASE_AUTHORING_DOCUMENT",
            CaseAuthoringValidator.Validate(noOpChallenge).Code);
    }

    [Fact]
    public void Authoring_validator_rejects_a_document_without_a_challenge_variant()
    {
        var missingChallenge = ValidDocument() with { Variants = [] };

        Assert.Equal(
            "INVALID_CASE_AUTHORING_DOCUMENT",
            CaseAuthoringValidator.Validate(missingChallenge).Code);
    }

    [Fact]
    public void Stored_authoring_document_rejects_missing_challenge_before_transition()
    {
        var serialized = JsonSerializer.Serialize(ValidDocument() with { Variants = [] });

        Assert.Equal(
            "INVALID_CASE_AUTHORING_DOCUMENT",
            CaseAuthoringValidator.ValidateSerializedDocument(serialized).Code);
    }

    [Fact]
    public void Review_can_reject_to_draft_but_cannot_self_approve()
    {
        var review = new CaseVersionRecord(ValidDocument().Content, CaseLifecycleState.Review, AuthorId, null);
        Assert.True(CaseWorkflow.ValidateTransition(review, CaseLifecycleState.Draft, ReviewerId, ContentActorRole.Reviewer).IsAllowed);
        Assert.Equal(
            "INVALID_CASE_TRANSITION",
            CaseWorkflow.ValidateTransition(review, CaseLifecycleState.Approved, AuthorId, ContentActorRole.Reviewer).Code);
    }

    [Fact]
    public void Anonymized_published_case_can_be_retired_by_maintainer()
    {
        var published = new CaseVersionRecord(
            ValidDocument().Content,
            CaseLifecycleState.Published,
            Guid.Empty,
            ReviewerId);

        Assert.True(CaseWorkflow.ValidateTransition(
            published,
            CaseLifecycleState.Retired,
            ReviewerId,
            ContentActorRole.Maintainer).IsAllowed);
    }

    [Fact]
    public void Case_lifecycle_audit_is_limited_to_content_operators_in_scope()
    {
        var organizationId = Guid.Parse("123e4567-e89b-42d3-a456-426614174010");
        var accountId = Guid.Parse("123e4567-e89b-42d3-a456-426614174011");

        Assert.True(AccessPolicy.CanReadContentAudit(
            new Membership(accountId, organizationId, PlatformRole.Author, true),
            organizationId).Allowed);
        Assert.True(AccessPolicy.CanReadContentAudit(
            new Membership(accountId, organizationId, PlatformRole.Reviewer, true),
            organizationId).Allowed);
        Assert.Equal(
            "ROLE_REQUIRED",
            AccessPolicy.CanReadContentAudit(
                new Membership(accountId, organizationId, PlatformRole.Teacher, true),
                organizationId).Code);
        Assert.Equal(
            "ORGANIZATION_SCOPE_REQUIRED",
            AccessPolicy.CanReadContentAudit(
                new Membership(accountId, Guid.NewGuid(), PlatformRole.Owner, true),
                organizationId).Code);
    }

    private static CaseAuthoringDocument ValidDocument() => new(
        new CaseContent(
            "M0_T2",
            "M0_T2:1",
            "Synthetic tablet case",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            "evaluator.v1",
            ["fact-1", "fact-2"],
            ["evidence-linking"]),
        "Connect evidence to a bounded claim",
        2,
        ["fact-1"],
        [
            new AuthoringFact("fact-1", "observation", "The tablet was cold."),
            new AuthoringFact("fact-2", "limitation", "Temperature was not controlled."),
        ],
        [new AuthoringRule("rule-1", "PASS", ["fact-1"])],
        [new AuthoringVariant("challenge-1", ["fact-1"])]);
}
