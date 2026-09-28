using Evidrilo.Api.Projects;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace Evidrilo.Api.Tests;

public sealed class StudentProjectContractTests
{
    [Fact]
    public void Project_hypothesis_is_optional_and_method_is_student_supplied()
    {
        var project = MinimalProject();

        Assert.Null(project.Hypothesis);
        Assert.Equal("Student-described method", project.Method);
        Assert.True(StudentProjectDocumentValidator.IsValid(project));
    }

    [Fact]
    public void Project_rejects_a_trace_link_to_an_unknown_criterion_or_evidence_item()
    {
        var project = MinimalProject() with
        {
            Criteria = [new StudentProjectCriterion("criterion-1", "Compare the observations.")],
            EvidenceItems =
            [
                new StudentProjectEvidenceItem(
                    "observation-1",
                    StudentProjectEvidenceKind.Observation,
                    "First observation",
                    "A manually entered result.",
                    "Student notes"),
            ],
            CriterionEvidenceLinks =
            [
                new StudentProjectCriterionEvidenceLink("criterion-unknown", "observation-1"),
            ],
        };

        Assert.False(StudentProjectDocumentValidator.IsValid(project));
    }

    [Fact]
    public void Project_supports_multiple_stable_claims_with_explicit_evidence_relationships()
    {
        var project = MinimalProject() with
        {
            EvidenceItems =
            [
                new StudentProjectEvidenceItem("observation-1", StudentProjectEvidenceKind.Observation, "First", "Result one", "Student notes"),
                new StudentProjectEvidenceItem("observation-2", StudentProjectEvidenceKind.Observation, "Second", "Result two", "Student notes"),
            ],
            Claims =
            [
                new StudentProjectClaim("claim-one", "A bounded statement.", "In this sample.", null, StudentProjectClaimReviewStatus.Draft),
                new StudentProjectClaim("claim-two", "A second statement.", "Under the recorded conditions.", "One observation is missing.", StudentProjectClaimReviewStatus.ReadyForReview),
            ],
            ClaimEvidenceLinks =
            [
                new StudentProjectClaimEvidenceLink("claim-one", "observation-1", StudentProjectClaimRelation.Supports, "The observation is relevant."),
                new StudentProjectClaimEvidenceLink("claim-two", "observation-2", StudentProjectClaimRelation.ProvidesContext, "Different condition."),
            ],
        };

        Assert.True(StudentProjectDocumentValidator.IsValid(project));
    }

    [Fact]
    public void Project_rejects_claim_evidence_links_to_unknown_claims()
    {
        var project = MinimalProject() with
        {
            EvidenceItems =
            [new StudentProjectEvidenceItem("observation-1", StudentProjectEvidenceKind.Observation, "First", "Result", "Student notes")],
            Claims = [new StudentProjectClaim("claim-one", "A bounded statement.", "In this sample.", null, StudentProjectClaimReviewStatus.Draft)],
            ClaimEvidenceLinks =
            [new StudentProjectClaimEvidenceLink("missing-claim", "observation-1", StudentProjectClaimRelation.Supports, null)],
        };

        Assert.False(StudentProjectDocumentValidator.IsValid(project));
    }

    [Fact]
    public void Legacy_claim_evidence_links_require_a_claim_statement()
    {
        var project = MinimalProject() with
        {
            EvidenceItems =
            [new StudentProjectEvidenceItem("observation-1", StudentProjectEvidenceKind.Observation, "First", "Result", "Student notes")],
            ClaimEvidenceIds = ["observation-1"],
        };

        Assert.False(StudentProjectDocumentValidator.IsValid(project));
    }

    [Fact]
    public void Project_document_budget_allows_the_declared_bounded_record_counts()
    {
        var evidence = Enumerable.Range(1, 100)
            .Select(index => new StudentProjectEvidenceItem(
                $"observation-{index}",
                StudentProjectEvidenceKind.Observation,
                $"Observation {index}",
                new string('界', 2_000),
                "Student notes"))
            .ToArray();
        var claims = Enumerable.Range(1, 5)
            .Select(index => new StudentProjectClaim(
                $"claim-{index}",
                new string('界', 600),
                "A bounded scope.",
                null,
                StudentProjectClaimReviewStatus.Draft))
            .ToArray();
        var relations = claims.SelectMany(claim => evidence.Select(item => new StudentProjectClaimEvidenceLink(
            claim.Id,
            item.Id,
            StudentProjectClaimRelation.Supports,
            new string('界', 200)))).ToArray();
        var project = MinimalProject() with
        {
            EvidenceItems = evidence,
            Claims = claims,
            ClaimEvidenceLinks = relations,
        };

        Assert.True(StudentProjectDocumentValidator.IsValid(project));
    }

    [Fact]
    public void Project_document_rejects_a_bounded_record_set_that_exceeds_the_storage_budget()
    {
        var evidence = Enumerable.Range(1, 100)
            .Select(index => new StudentProjectEvidenceItem(
                $"observation-{index}",
                StudentProjectEvidenceKind.Observation,
                $"Observation {index}",
                "A manually entered result.",
                "Student notes"))
            .ToArray();
        var claims = Enumerable.Range(1, 5)
            .Select(index => new StudentProjectClaim(
                $"claim-{index}",
                "A bounded statement.",
                "A bounded scope.",
                null,
                StudentProjectClaimReviewStatus.Draft))
            .ToArray();
        var relations = claims.SelectMany(claim => evidence.Select(item => new StudentProjectClaimEvidenceLink(
            claim.Id,
            item.Id,
            StudentProjectClaimRelation.Supports,
            new string('界', 2_000)))).ToArray();
        var project = MinimalProject() with
        {
            EvidenceItems = evidence,
            Claims = claims,
            ClaimEvidenceLinks = relations,
        };

        Assert.False(StudentProjectDocumentValidator.IsValid(project));
    }

    [Fact]
    public void Structure_report_counts_student_entered_sections_without_assessing_merit()
    {
        var project = MinimalProject() with
        {
            Criteria = [new StudentProjectCriterion("criterion-1", "Compare the observations.")],
            EvidenceItems =
            [
                new StudentProjectEvidenceItem(
                    "observation-1",
                    StudentProjectEvidenceKind.Observation,
                    "First observation",
                    "A manually entered result.",
                    "Student notes"),
            ],
            CriterionEvidenceLinks =
            [
                new StudentProjectCriterionEvidenceLink("criterion-1", "observation-1"),
            ],
            Claim = "A student-entered claim.",
            ClaimEvidenceIds = ["observation-1"],
        };

        var report = StudentProjectStructureReporter.Create(
            Guid.Parse("2d86853d-6163-4f8f-8097-37c2a115ec3b"),
            3,
            project,
            "req-project-report-001");

        Assert.Equal("not_assessed", report.AssessmentStatus);
        Assert.False(report.AcademicMeritAssessed);
        Assert.Equal(1, report.CriterionEvidenceLinkCount);
        Assert.Equal(1, report.ClaimEvidenceLinkCount);
        Assert.Contains("does not assess academic merit", report.Notice, StringComparison.Ordinal);
        Assert.DoesNotContain("grade", report.Notice, StringComparison.OrdinalIgnoreCase);
    }

    [Fact]
    public void Project_pagination_responses_emit_explicit_null_cursors()
    {
        var options = new JsonSerializerOptions(JsonSerializerDefaults.Web)
        {
            DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
        };
        var revisions = JsonSerializer.SerializeToElement(
            new StudentProjectRevisionPageResponse(
                "evidrilo.student-project-revisions",
                "1",
                Guid.NewGuid(),
                [],
                null,
                "req-project-cursor-001"),
            options);
        var projects = JsonSerializer.SerializeToElement(
            new StudentProjectListResponse(
                "evidrilo.student-project-list",
                "1",
                [],
                null,
                "req-project-cursor-002"),
            options);

        Assert.Equal(JsonValueKind.Null, revisions.GetProperty("nextBeforeVersion").ValueKind);
        Assert.Equal(JsonValueKind.Null, projects.GetProperty("nextCursor").ValueKind);
    }

    [Fact]
    public void Student_project_responses_emit_schema_required_nullable_fields_as_null()
    {
        var options = new JsonSerializerOptions(JsonSerializerDefaults.Web)
        {
            DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull,
            Converters = { new JsonStringEnumConverter(JsonNamingPolicy.SnakeCaseLower) },
        };
        var project = MinimalProject() with
        {
            EvidenceItems =
            [
                new StudentProjectEvidenceItem(
                    "observation-1",
                    StudentProjectEvidenceKind.Observation,
                    "First observation",
                    null,
                    null),
            ],
        };
        var detail = JsonSerializer.SerializeToElement(
            new StudentProjectDetailResponse(
                "evidrilo.student-project",
                "1",
                Guid.Parse("31c0d18c-943a-49d0-8e45-d6d693f8f6c8"),
                1,
                project,
                DateTimeOffset.Parse("2026-09-27T10:00:00Z"),
                DateTimeOffset.Parse("2026-09-27T10:00:00Z"),
                "req-project-detail-001"),
            options);
        var projectJson = detail.GetProperty("project");
        var evidenceJson = projectJson.GetProperty("evidenceItems")[0];
        var consent = JsonSerializer.SerializeToElement(
            new StudentProjectCloudConsentResponse(
                "evidrilo.project-cloud-consent",
                "1",
                false,
                "student-project-cloud.v1",
                null,
                null,
                null,
                "req-project-consent-001"),
            options);

        Assert.Equal(JsonValueKind.Null, projectJson.GetProperty("hypothesis").ValueKind);
        Assert.Equal(JsonValueKind.Null, projectJson.GetProperty("analysis").ValueKind);
        Assert.Equal(JsonValueKind.Null, projectJson.GetProperty("claim").ValueKind);
        Assert.Equal(JsonValueKind.Null, projectJson.GetProperty("claims").ValueKind);
        Assert.Equal(JsonValueKind.Null, projectJson.GetProperty("claimEvidenceLinks").ValueKind);
        Assert.Equal(JsonValueKind.Null, projectJson.GetProperty("nextAction").ValueKind);
        Assert.Equal(JsonValueKind.Null, evidenceJson.GetProperty("summary").ValueKind);
        Assert.Equal(JsonValueKind.Null, evidenceJson.GetProperty("origin").ValueKind);
        Assert.Equal(JsonValueKind.Null, consent.GetProperty("grantedAt").ValueKind);
        Assert.Equal(JsonValueKind.Null, consent.GetProperty("revokedAt").ValueKind);
        Assert.Equal(JsonValueKind.Null, consent.GetProperty("updatedAt").ValueKind);
    }

    private static StudentProjectDocument MinimalProject() => new(
        "A local inquiry project",
        "Compare one measured condition with another.",
        "How does the selected condition relate to the observed outcome?",
        "Student-described method",
        null,
        [],
        [],
        [],
        null,
        null,
        [],
        [],
        null);
}
