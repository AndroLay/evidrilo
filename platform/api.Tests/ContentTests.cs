using System.Text.Json;
using Evidrilo.Api.Content;

namespace Evidrilo.Api.Tests;

public sealed class ContentTests
{
    private static readonly Guid AuthorId = Guid.Parse("123e4567-e89b-42d3-a456-426614174000");
    private static readonly Guid ReviewerId = Guid.Parse("123e4567-e89b-42d3-a456-426614174001");

    [Fact]
    public void Workflow_requires_independent_review_and_freezes_published_versions()
    {
        var draft = new CaseVersionRecord(ValidContent(), CaseLifecycleState.Draft, AuthorId, null);
        Assert.True(CaseWorkflow.ValidateTransition(draft, CaseLifecycleState.Review, AuthorId, ContentActorRole.Author).IsAllowed);

        var review = draft with { State = CaseLifecycleState.Review };
        Assert.Equal("INVALID_CASE_TRANSITION", CaseWorkflow.ValidateTransition(review, CaseLifecycleState.Approved, AuthorId, ContentActorRole.Reviewer).Code);
        Assert.True(CaseWorkflow.ValidateTransition(review, CaseLifecycleState.Approved, ReviewerId, ContentActorRole.Reviewer).IsAllowed);

        var published = review with { State = CaseLifecycleState.Published, ReviewerId = ReviewerId };
        Assert.True(CaseWorkflow.ValidateTransition(published, CaseLifecycleState.Retired, ReviewerId, ContentActorRole.Maintainer).IsAllowed);
        Assert.Equal("PUBLISHED_VERSION_IMMUTABLE", CaseWorkflow.ValidateTransition(published, CaseLifecycleState.Approved, ReviewerId, ContentActorRole.Maintainer).Code);
    }

    [Fact]
    public void Content_validator_fails_closed_for_missing_anchors_or_hash()
    {
        Assert.False(CaseContentValidator.Validate(ValidContent() with { FactAnchors = Array.Empty<string>() }).IsValid);
        Assert.False(CaseContentValidator.Validate(ValidContent() with { ContentHash = "not-a-hash" }).IsValid);
        Assert.True(CaseContentValidator.Validate(ValidContent()).IsValid);
    }

    [Fact]
    public void Content_validator_rejects_fact_anchors_outside_the_identifier_boundary()
    {
        var result = CaseContentValidator.Validate(ValidContent() with
        {
            FactAnchors = ["fact-1\n"],
        });

        Assert.False(result.IsValid);
        Assert.Equal("INVALID_CASE_CONTENT", result.Code);
    }

    [Fact]
    public void Published_content_reader_preserves_challenge_and_feedback_rules()
    {
        using var document = JsonDocument.Parse("""
            {
              "content": {
                "caseId": "case-1",
                "caseVersionId": "case-1:v1",
                "title": "A bounded case",
                "contentHash": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "evaluatorVersion": "evaluator.v1",
                "factAnchors": ["fact-observation", "fact-limitation"],
                "skillTags": ["evidence"]
              },
              "objective": "Connect evidence to a bounded claim",
              "difficulty": 2,
              "evidenceReferences": ["fact-observation"],
              "facts": [
                { "id": "fact-observation", "type": "observation", "text": "The tablet was cold." },
                { "id": "fact-limitation", "type": "limitation", "text": "Temperature was not controlled." }
              ],
              "rules": [
                { "id": "rule-1", "outcome": "PASS", "anchorIds": ["fact-observation"] }
              ],
              "variants": [
                { "id": "challenge-1", "removedFactIds": ["fact-observation"] }
              ]
            }
            """);

        var accepted = PublishedCaseContentReader.TryRead(
            document.RootElement,
            ValidContent() with
            {
                CaseId = "case-1",
                CaseVersionId = "case-1:v1",
                Title = "A bounded case",
                FactAnchors = ["fact-observation", "fact-limitation"],
                SkillTags = ["evidence"],
            },
            out var content);

        Assert.True(accepted);
        Assert.NotNull(content);
        Assert.Equal("Connect evidence to a bounded claim", content!.Objective);
        Assert.Equal("observation", content.Facts[0].Type);
        Assert.Equal("PASS", content.Rules[0].Outcome);
        Assert.Equal("challenge-1", content.Variants[0].Id);
    }

    [Fact]
    public void Published_content_reader_rejects_invalid_or_mismatched_documents()
    {
        using var document = JsonDocument.Parse("""
            {
              "content": {
                "caseId": "case-1",
                "caseVersionId": "case-1:v1",
                "title": "A bounded case",
                "contentHash": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "evaluatorVersion": "evaluator.v1",
                "factAnchors": ["missing-fact"],
                "skillTags": ["evidence"]
              },
              "objective": "Connect evidence to a bounded claim",
              "difficulty": 2,
              "evidenceReferences": ["missing-fact"],
              "facts": [],
              "rules": [],
              "variants": []
            }
            """);

        var accepted = PublishedCaseContentReader.TryRead(
            document.RootElement,
            ValidContent(),
            out var content);

        Assert.False(accepted);
        Assert.Null(content);
    }

    [Fact]
    public void Published_content_reader_fails_closed_when_document_metadata_is_incomplete()
    {
        using var document = JsonDocument.Parse("""
            {
              "content": {
                "caseId": "case-1",
                "caseVersionId": "case-1:v1",
                "title": "A bounded case",
                "contentHash": "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "evaluatorVersion": "evaluator.v1",
                "skillTags": ["evidence"]
              },
              "objective": "Connect evidence to a bounded claim",
              "difficulty": 2,
              "evidenceReferences": ["fact-1"],
              "facts": [{ "id": "fact-1", "type": "observation", "text": "An observation." }],
              "rules": [{ "id": "rule-1", "outcome": "PASS", "anchorIds": ["fact-1"] }],
              "variants": []
            }
            """);

        var accepted = PublishedCaseContentReader.TryRead(
            document.RootElement,
            ValidContent(),
            out var content);

        Assert.False(accepted);
        Assert.Null(content);
    }

    private static CaseContent ValidContent() => new(
        "M0_T2",
        "M0_T2:1",
        "Synthetic tablet case",
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "evaluator.v1",
        ["fact-1"],
        ["evidence-linking"]);
}
