using System.Net;
using System.Text;
using System.Text.Json;
using Evidrilo.Api.Ai;
using Evidrilo.Api.Common;
using Evidrilo.Api.ProjectAi;
using Evidrilo.Api.ProjectTemplates;
using Evidrilo.Api.Projects;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.DependencyInjection.Extensions;

namespace Evidrilo.Api.Tests;

public sealed class ProjectAiScaffoldFlowTests
{
    private static readonly Guid AccountA = Guid.Parse("7dd783d9-811a-4e96-96ac-21c5cfa72a60");
    private static readonly Guid AccountB = Guid.Parse("5f609407-371a-4bb4-9a77-4e9f4358ae6e");
    private static readonly Guid ExistingProjectId = Guid.Parse("11111111-1111-4111-8111-111111111111");

    [Fact]
    public async Task New_project_scaffold_reserves_a_maximum_and_settles_actual_token_cost_on_preview()
    {
        using var host = new TestHost();

        using var response = await host.PostScaffoldAsync(AccountA, "project-ai-flow-0001", ValidRequest());
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal("preview", body.RootElement.GetProperty("status").GetString());
        Assert.Equal(ProjectAiScaffoldValidator.CreateOperation, body.RootElement.GetProperty("operation").GetString());
        Assert.Equal(JsonValueKind.Null, body.RootElement.GetProperty("projectId").ValueKind);
        Assert.Equal(JsonValueKind.Null, body.RootElement.GetProperty("baseProjectRevision").ValueKind);
        Assert.Equal(3, body.RootElement.GetProperty("creditCost").GetInt32());
        Assert.Equal(0, host.Credits.ReservedFor(AccountA, "project-ai-flow-0001"));
        Assert.Equal(3, host.Credits.ConsumedFor(AccountA, "project-ai-flow-0001"));
        Assert.Equal(1, host.Generator.Calls);
    }

    [Fact]
    public async Task In_project_assist_settles_actual_token_cost_on_preview()
    {
        using var host = new TestHost();

        using var response = await host.PostScaffoldAsync(
            AccountA,
            "project-ai-flow-0002",
            ValidRequest(
                baseProjectRevision: 7,
                operation: ProjectAiScaffoldValidator.AssistOperation,
                projectId: "11111111-1111-4111-8111-111111111111"));
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal(ProjectAiScaffoldValidator.AssistOperation, body.RootElement.GetProperty("operation").GetString());
        Assert.Equal("11111111-1111-4111-8111-111111111111", body.RootElement.GetProperty("projectId").GetString());
        Assert.Equal(7, body.RootElement.GetProperty("baseProjectRevision").GetInt32());
        Assert.Equal(1, body.RootElement.GetProperty("creditCost").GetInt32());
        Assert.Equal(0, host.Credits.ReservedFor(AccountA, "project-ai-flow-0002"));
        Assert.Equal(1, host.Credits.ConsumedFor(AccountA, "project-ai-flow-0002"));
    }

    [Fact]
    public async Task Stage_assist_binds_project_stage_and_template_operation()
    {
        using var host = new TestHost();
        const string requestId = "project-ai-stage-0001";

        using var response = await host.PostStageAssistAsync(AccountA, requestId, ValidStageAssistRequest());
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal("PROJECT", body.RootElement.GetProperty("mode").GetString());
        Assert.Equal(ExistingProjectId.ToString(), body.RootElement.GetProperty("projectId").GetString());
        Assert.Equal("frame", body.RootElement.GetProperty("stageId").GetString());
        Assert.Equal("explain_template_step", body.RootElement.GetProperty("operationId").GetString());
        Assert.Equal(0, host.Credits.ReservedFor(AccountA, requestId));
        Assert.Equal(1, host.Credits.ConsumedFor(AccountA, requestId));
        Assert.Equal(3, host.StudentProjects.ReadCount);
        Assert.Equal(1, host.Generator.Calls);
        Assert.Equal("frame", host.Generator.LastStageRequest?.StageId);
        Assert.Equal("explain_template_step", host.Generator.LastStageRequest?.OperationId);
        Assert.Equal(new[] { "question" }, host.Generator.LastStageRequest?.AllowedOutputFieldIds);
        Assert.Equal(new[] { "question" }, host.Generator.LastStageRequest?.SelectedFields.Select(field => field.Id));
        Assert.Empty(host.Generator.LastStageRequest?.SelectedEvidence ?? []);
    }

    [Fact]
    public async Task Stage_assist_returns_typed_content_and_a_server_built_evaluation_preview()
    {
        using var host = new TestHost();

        using var response = await host.PostStageAssistAsync(
            AccountA,
            "project-ai-stage-typed-01",
            ValidStageAssistRequest());
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.True(body.RootElement.TryGetProperty("assist", out var assist), "The stage response must use the typed assist contract.");
        var items = assist.GetProperty("items").EnumerateArray().ToArray();
        Assert.Contains(items, item => item.GetProperty("kind").GetString() == "EXPLANATION");
        var proposal = Assert.Single(items, item => item.GetProperty("kind").GetString() == "PROPOSAL");
        Assert.Equal("question", proposal.GetProperty("targetFieldId").GetString());
        Assert.Equal("How does the reported outcome vary?", proposal.GetProperty("beforeValue").GetString());
        Assert.NotEqual(proposal.GetProperty("beforeValue").GetString(), proposal.GetProperty("afterValue").GetString());

        var evaluation = body.RootElement.GetProperty("evaluationPreview");
        Assert.Equal("NOT_ASSESSED", evaluation.GetProperty("assessmentStatus").GetString());
        Assert.Contains("proposal-question", evaluation.GetProperty("proposalsWithoutReferences").EnumerateArray().Select(item => item.GetString()));
        Assert.Contains("A narrower scope may make comparison easier.", evaluation.GetProperty("reportedAssumptions").EnumerateArray().Select(item => item.GetString()));
        Assert.Contains("A manual source review is still required.", evaluation.GetProperty("reportedKnownLimits").EnumerateArray().Select(item => item.GetString()));
        Assert.Contains("Do not claim causation from association alone.", evaluation.GetProperty("templateLimits").EnumerateArray().Select(item => item.GetString()));
        Assert.Contains("ACADEMIC_TRUTH", evaluation.GetProperty("checksUnavailable").EnumerateArray().Select(item => item.GetString()));
    }

    [Fact]
    public async Task Stage_assist_sends_redacted_context_and_binds_the_original_before_value_in_the_preview()
    {
        const string selectedText = "Contact learner@example.org before comparing sources.";
        using var host = new TestHost(projects: new RecordingStudentProjectStore(projectQuestion: selectedText));

        using var response = await host.PostStageAssistAsync(
            AccountA,
            "project-ai-stage-redaction-01",
            ValidStageAssistRequest());
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var providerField = Assert.Single(host.Generator.LastStageRequest!.SelectedFields);
        Assert.Equal("Contact [REDACTED_EMAIL] before comparing sources.", providerField.Value);
        var proposal = Assert.Single(body.RootElement.GetProperty("assist").GetProperty("items").EnumerateArray(),
            item => item.GetProperty("kind").GetString() == "PROPOSAL");
        Assert.Equal(selectedText, proposal.GetProperty("beforeValue").GetString());

        using var history = await host.GetProjectAiActivityAsync(AccountA, "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        Assert.DoesNotContain("learner@example.org", await history.Content.ReadAsStringAsync());
    }

    [Fact]
    public async Task Stage_assist_accepts_an_existing_evidence_item_explicitly_selected_for_the_operation()
    {
        using var host = new TestHost();
        host.Generator.StageOutput = host.Generator.StageOutput with
        {
            Items = host.Generator.StageOutput.Items.Select(item => item.Kind == "PROPOSAL"
                ? item with { ReferenceIds = ["source-1"] }
                : item).ToArray(),
        };

        using var response = await host.PostStageAssistAsync(
            AccountA,
            "project-ai-stage-selected-evidence",
            ValidStageAssistRequest(selectedEvidenceIds: ["source-1"]));
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal("source-1", Assert.Single(host.Generator.LastStageRequest!.SelectedEvidence).Id);
        var supportingItem = Assert.Single(body.RootElement.GetProperty("evaluationPreview").GetProperty("supportingItems").EnumerateArray());
        Assert.Equal("source-1", supportingItem.GetProperty("id").GetString());
        Assert.Equal("Selected source", supportingItem.GetProperty("label").GetString());
    }

    [Fact]
    public async Task Stage_assist_rejects_a_reference_to_evidence_the_student_did_not_select()
    {
        using var host = new TestHost();
        host.Generator.StageOutput = host.Generator.StageOutput with
        {
            Items = host.Generator.StageOutput.Items.Select(item => item.Kind == "PROPOSAL"
                ? item with { ReferenceIds = ["source-1"] }
                : item).ToArray(),
        };
        const string requestId = "project-ai-stage-unselected-source";

        using var response = await host.PostStageAssistAsync(AccountA, requestId, ValidStageAssistRequest());

        Assert.Equal(HttpStatusCode.BadGateway, response.StatusCode);
        Assert.Equal(0, host.Credits.ConsumedFor(AccountA, requestId));
        Assert.Equal(1, host.Credits.ReleasedFor(AccountA, requestId));
    }

    [Fact]
    public async Task Stage_assist_rejects_an_evidence_id_missing_from_the_owned_project()
    {
        using var host = new TestHost();
        const string requestId = "project-ai-stage-missing-source";

        using var response = await host.PostStageAssistAsync(
            AccountA,
            requestId,
            ValidStageAssistRequest(selectedEvidenceIds: ["not-in-project"]));

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal(0, host.Credits.ReservationCount);
        Assert.Equal(0, host.Generator.Calls);
    }

    [Fact]
    public async Task Stage_assist_rejects_a_proposal_whose_before_value_is_not_the_selected_value()
    {
        using var host = new TestHost();
        host.Generator.StageOutput = host.Generator.StageOutput with
        {
            Items = host.Generator.StageOutput.Items.Select(item => item.Kind == "PROPOSAL"
                ? item with { BeforeValue = "A different saved question" }
                : item).ToArray(),
        };
        const string requestId = "project-ai-stage-stale-before";

        using var response = await host.PostStageAssistAsync(AccountA, requestId, ValidStageAssistRequest());

        Assert.Equal(HttpStatusCode.BadGateway, response.StatusCode);
        Assert.Equal(1, host.Credits.ReleasedFor(AccountA, requestId));
    }

    [Fact]
    public async Task Stage_assist_rejects_a_proposal_for_a_field_outside_the_operation_output_allowlist()
    {
        using var host = new TestHost();
        host.Generator.StageOutput = host.Generator.StageOutput with
        {
            Items = host.Generator.StageOutput.Items.Select(item => item.Kind == "PROPOSAL"
                ? item with { TargetFieldId = "source" }
                : item).ToArray(),
        };
        const string requestId = "project-ai-stage-output-field-denied";

        using var response = await host.PostStageAssistAsync(AccountA, requestId, ValidStageAssistRequest());

        Assert.Equal(HttpStatusCode.BadGateway, response.StatusCode);
        Assert.Equal(1, host.Credits.ReleasedFor(AccountA, requestId));
    }

    [Fact]
    public async Task Stage_assist_rejects_an_operation_not_declared_for_the_stage()
    {
        using var host = new TestHost();
        const string requestId = "project-ai-stage-0002";

        using var response = await host.PostStageAssistAsync(
            AccountA,
            requestId,
            ValidStageAssistRequest(operationId: "prepare_output_section"));

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal(0, host.Credits.ReservedFor(AccountA, requestId));
        Assert.Equal(0, host.Generator.Calls);
    }

    [Fact]
    public async Task Stage_assist_rejects_fields_outside_the_selected_operation()
    {
        using var host = new TestHost();
        const string requestId = "project-ai-stage-0003";

        using var response = await host.PostStageAssistAsync(
            AccountA,
            requestId,
            ValidStageAssistRequest(selectedFieldIds: ["unrelated_field"]));
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal("PROJECT_AI_FIELD_NOT_ALLOWED_FOR_OPERATION", body.RootElement.GetProperty("code").GetString());
        Assert.Equal(0, host.Credits.ReservedFor(AccountA, requestId));
        Assert.Equal(0, host.Generator.Calls);
    }

    [Fact]
    public async Task Stage_assist_rejects_client_supplied_field_values_instead_of_reading_the_saved_project()
    {
        using var host = new TestHost();
        const string requestId = "project-ai-stage-client-text-denied";
        var request = JsonSerializer.Serialize(new
        {
            schema = "evidrilo.project-ai-stage-assist",
            version = "1",
            mode = "PROJECT",
            installationId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
            projectId = ExistingProjectId,
            templateId = "reviewed-template",
            templateVersion = 1,
            baseProjectRevision = 7,
            stageId = "frame",
            operationId = "explain_template_step",
            selectedFieldIds = new[] { "question" },
            selectedFields = new Dictionary<string, string> { ["question"] = "Text from another project." },
            selectedEvidenceIds = Array.Empty<string>(),
            locale = "en",
        });

        using var response = await host.PostStageAssistAsync(AccountA, requestId, request);
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal("INVALID_PROJECT_AI_STAGE_ASSIST", body.RootElement.GetProperty("code").GetString());
        Assert.Equal(0, host.Credits.ReservationCount);
        Assert.Equal(0, host.Generator.Calls);
    }

    [Fact]
    public async Task Stage_assist_requires_evidence_items_to_be_selected_by_item_id()
    {
        using var host = new TestHost();
        const string requestId = "project-ai-stage-source-as-field";

        using var response = await host.PostStageAssistAsync(
            AccountA,
            requestId,
            ValidStageAssistRequest(selectedFieldIds: ["source"]));
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal("PROJECT_AI_EVIDENCE_SELECTION_REQUIRES_IDS", body.RootElement.GetProperty("code").GetString());
        Assert.Equal(0, host.Credits.ReservationCount);
        Assert.Equal(0, host.Generator.Calls);
    }

    [Fact]
    public async Task General_chat_is_not_ready_and_never_reads_a_project_or_reserves_credits()
    {
        using var host = new TestHost();
        const string requestId = "project-ai-general-0001";

        using var response = await host.PostStageAssistAsync(
            AccountA,
            requestId,
            ValidStageAssistRequest(mode: "GENERAL"));
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal("PROJECT_AI_GENERAL_NOT_READY", body.RootElement.GetProperty("code").GetString());
        Assert.Equal(0, host.StudentProjects.ReadCount);
        Assert.Equal(0, host.Credits.ReservationCount);
        Assert.Equal(0, host.Generator.Calls);
    }

    [Fact]
    public async Task Stage_assist_history_is_metadata_only_and_scoped_to_the_installation()
    {
        using var host = new TestHost();
        const string requestId = "project-ai-stage-history-01";

        using var preview = await host.PostStageAssistAsync(AccountA, requestId, ValidStageAssistRequest());
        Assert.Equal(HttpStatusCode.OK, preview.StatusCode);

        using var response = await host.GetProjectAiActivityAsync(
            AccountA,
            "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        var responseText = await response.Content.ReadAsStringAsync();
        using var body = JsonDocument.Parse(responseText);
        var activities = body.RootElement.GetProperty("activities");

        var activity = Assert.Single(activities.EnumerateArray());
        Assert.Equal("PROJECT", activity.GetProperty("mode").GetString());
        Assert.Equal(ExistingProjectId.ToString(), activity.GetProperty("projectId").GetString());
        Assert.Equal("frame", activity.GetProperty("stageId").GetString());
        Assert.Equal("explain_template_step", activity.GetProperty("operationId").GetString());
        Assert.Equal(7, activity.GetProperty("baseProjectRevision").GetInt32());
        Assert.Equal("PENDING", activity.GetProperty("outcome").GetString());
        Assert.DoesNotContain("How does the reported outcome vary?", responseText);

        using var anotherInstallation = await host.GetProjectAiActivityAsync(
            AccountA,
            "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
        Assert.Equal(HttpStatusCode.OK, anotherInstallation.StatusCode);
        using var otherBody = JsonDocument.Parse(await anotherInstallation.Content.ReadAsStringAsync());
        Assert.Empty(otherBody.RootElement.GetProperty("activities").EnumerateArray());
        using var otherAccount = await host.GetProjectAiActivityAsync(
            AccountB,
            "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        using var otherAccountBody = JsonDocument.Parse(await otherAccount.Content.ReadAsStringAsync());
        Assert.Empty(otherAccountBody.RootElement.GetProperty("activities").EnumerateArray());
    }

    [Fact]
    public async Task Activity_history_paginates_without_repeating_entries()
    {
        using var host = new TestHost();
        using var firstPreview = await host.PostStageAssistAsync(AccountA, "project-ai-history-page-01", ValidStageAssistRequest());
        using var secondPreview = await host.PostStageAssistAsync(AccountA, "project-ai-history-page-02", ValidStageAssistRequest());
        Assert.Equal(HttpStatusCode.OK, firstPreview.StatusCode);
        Assert.Equal(HttpStatusCode.OK, secondPreview.StatusCode);

        using var firstPage = await host.GetProjectAiActivityAsync(
            AccountA,
            "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
            limit: "1");
        using var firstBody = JsonDocument.Parse(await firstPage.Content.ReadAsStringAsync());
        var firstEntry = Assert.Single(firstBody.RootElement.GetProperty("activities").EnumerateArray());
        var firstId = firstEntry.GetProperty("activityId").GetString();
        var cursor = firstBody.RootElement.GetProperty("nextCursor").GetString();
        Assert.False(string.IsNullOrWhiteSpace(cursor));

        using var secondPage = await host.GetProjectAiActivityAsync(
            AccountA,
            "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
            limit: "1",
            cursor: cursor);
        using var secondBody = JsonDocument.Parse(await secondPage.Content.ReadAsStringAsync());
        var secondEntry = Assert.Single(secondBody.RootElement.GetProperty("activities").EnumerateArray());
        Assert.NotEqual(firstId, secondEntry.GetProperty("activityId").GetString());
    }

    [Fact]
    public async Task Applied_stage_assist_settlement_records_revision_and_is_idempotent()
    {
        using var host = new TestHost();
        const string requestId = "project-ai-stage-settle-01";

        using var preview = await host.PostStageAssistAsync(AccountA, requestId, ValidStageAssistRequest());
        Assert.Equal(HttpStatusCode.OK, preview.StatusCode);
        host.StudentProjects.SetVersion(8);

        using var first = await host.PostStageSettlementAsync(
            AccountA,
            requestId,
            "APPLIED",
            resultProjectRevision: 8);
        using var replay = await host.PostStageSettlementAsync(
            AccountA,
            requestId,
            "APPLIED",
            resultProjectRevision: 8);

        Assert.Equal(HttpStatusCode.OK, first.StatusCode);
        Assert.Equal(HttpStatusCode.OK, replay.StatusCode);
        Assert.Equal(1, host.Credits.ConsumedFor(AccountA, requestId));
        using var history = await host.GetProjectAiActivityAsync(
            AccountA,
            "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        using var body = JsonDocument.Parse(await history.Content.ReadAsStringAsync());
        var activity = Assert.Single(body.RootElement.GetProperty("activities").EnumerateArray());
        Assert.Equal("APPLIED", activity.GetProperty("outcome").GetString());
        Assert.Equal(8, activity.GetProperty("resultProjectRevision").GetInt32());
    }

    [Fact]
    public async Task Stage_assist_settlement_marks_an_unsaved_result_stale_after_charging_generation()
    {
        using var host = new TestHost();
        const string requestId = "project-ai-stage-settle-02";

        using var preview = await host.PostStageAssistAsync(AccountA, requestId, ValidStageAssistRequest());
        Assert.Equal(HttpStatusCode.OK, preview.StatusCode);

        using var response = await host.PostStageSettlementAsync(
            AccountA,
            requestId,
            "APPLIED",
            resultProjectRevision: 8);

        Assert.Equal(HttpStatusCode.Conflict, response.StatusCode);
        Assert.Equal(1, host.Credits.ConsumedFor(AccountA, requestId));
        Assert.Equal(0, host.Credits.ReleasedFor(AccountA, requestId));
        using var history = await host.GetProjectAiActivityAsync(
            AccountA,
            "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        using var body = JsonDocument.Parse(await history.Content.ReadAsStringAsync());
        var activity = Assert.Single(body.RootElement.GetProperty("activities").EnumerateArray());
        Assert.Equal("STALE", activity.GetProperty("outcome").GetString());
    }

    [Fact]
    public async Task Stale_stage_assist_settlement_records_stale_without_refunding_generation()
    {
        using var host = new TestHost();
        const string requestId = "project-ai-stage-settle-03";

        using var preview = await host.PostStageAssistAsync(AccountA, requestId, ValidStageAssistRequest());
        Assert.Equal(HttpStatusCode.OK, preview.StatusCode);

        using var response = await host.PostStageSettlementAsync(AccountA, requestId, "STALE");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal(1, host.Credits.ConsumedFor(AccountA, requestId));
        Assert.Equal(0, host.Credits.ReleasedFor(AccountA, requestId));
        using var history = await host.GetProjectAiActivityAsync(
            AccountA,
            "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        using var body = JsonDocument.Parse(await history.Content.ReadAsStringAsync());
        var activity = Assert.Single(body.RootElement.GetProperty("activities").EnumerateArray());
        Assert.Equal("STALE", activity.GetProperty("outcome").GetString());
    }

    [Fact]
    public async Task Dismissed_stage_assist_settlement_keeps_generation_charge_and_history()
    {
        using var host = new TestHost();
        const string requestId = "project-ai-stage-settle-dismiss";

        using var preview = await host.PostStageAssistAsync(AccountA, requestId, ValidStageAssistRequest());
        Assert.Equal(HttpStatusCode.OK, preview.StatusCode);
        using var response = await host.PostStageSettlementAsync(AccountA, requestId, "DISMISSED");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        Assert.Equal(1, host.Credits.ConsumedFor(AccountA, requestId));
        Assert.Equal(0, host.Credits.ReleasedFor(AccountA, requestId));
        using var history = await host.GetProjectAiActivityAsync(AccountA, "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        using var body = JsonDocument.Parse(await history.Content.ReadAsStringAsync());
        var activity = Assert.Single(body.RootElement.GetProperty("activities").EnumerateArray());
        Assert.Equal("DISMISSED", activity.GetProperty("outcome").GetString());
    }

    [Fact]
    public async Task General_activity_clear_only_removes_unlinked_history_for_the_selected_installation()
    {
        using var host = new TestHost();
        const string projectRequestId = "project-ai-general-clear-project";
        using var preview = await host.PostStageAssistAsync(AccountA, projectRequestId, ValidStageAssistRequest());
        Assert.Equal(HttpStatusCode.OK, preview.StatusCode);
        host.Activities.SeedGeneralActivity(AccountA, Guid.Parse("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"), "project-ai-general-clear-001");
        host.Activities.SeedGeneralActivity(AccountA, Guid.Parse("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"), "project-ai-general-clear-002");

        using var response = await host.ClearGeneralProjectAiActivityAsync(
            AccountA,
            "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");

        Assert.Equal(HttpStatusCode.OK, response.StatusCode);
        using var clearBody = JsonDocument.Parse(await response.Content.ReadAsStringAsync());
        Assert.Equal(1, clearBody.RootElement.GetProperty("clearedCount").GetInt32());
        Assert.Equal(3, host.StudentProjects.ReadCount);
        Assert.Equal(0, host.Credits.ReservedFor(AccountA, projectRequestId));
        Assert.Equal(1, host.Credits.ConsumedFor(AccountA, projectRequestId));
        Assert.Equal(1, host.Generator.Calls);
        using var sameInstallation = await host.GetProjectAiActivityAsync(
            AccountA,
            "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        using var sameBody = JsonDocument.Parse(await sameInstallation.Content.ReadAsStringAsync());
        var projectActivity = Assert.Single(sameBody.RootElement.GetProperty("activities").EnumerateArray());
        Assert.Equal("PROJECT", projectActivity.GetProperty("mode").GetString());
        using var anotherInstallation = await host.GetProjectAiActivityAsync(
            AccountA,
            "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
        using var anotherBody = JsonDocument.Parse(await anotherInstallation.Content.ReadAsStringAsync());
        var generalActivity = Assert.Single(anotherBody.RootElement.GetProperty("activities").EnumerateArray());
        Assert.Equal("GENERAL", generalActivity.GetProperty("mode").GetString());
        Assert.Equal(JsonValueKind.Null, generalActivity.GetProperty("projectId").ValueKind);
    }

    [Fact]
    public async Task Project_change_while_provider_runs_marks_preview_stale_without_consuming_credit()
    {
        using var host = new TestHost();
        const string requestId = "project-ai-stage-stale-after-dispatch";
        host.Generator.OnGenerate = () => host.StudentProjects.SetVersion(8);

        using var response = await host.PostStageAssistAsync(AccountA, requestId, ValidStageAssistRequest());

        Assert.Equal(HttpStatusCode.Conflict, response.StatusCode);
        Assert.Equal(1, host.Credits.ReleasedFor(AccountA, requestId));
        using var history = await host.GetProjectAiActivityAsync(AccountA, "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        using var body = JsonDocument.Parse(await history.Content.ReadAsStringAsync());
        var activity = Assert.Single(body.RootElement.GetProperty("activities").EnumerateArray());
        Assert.Equal("STALE", activity.GetProperty("outcome").GetString());
    }

    [Fact]
    public async Task Consent_revoked_while_provider_runs_marks_preview_stale_and_releases_credit()
    {
        var consent = new RecordingConsentStore { RevokeOnRead = 4 };
        using var host = new TestHost(consent: consent);
        const string requestId = "project-ai-stage-consent-stale";

        using var response = await host.PostStageAssistAsync(AccountA, requestId, ValidStageAssistRequest());

        Assert.Equal(HttpStatusCode.Forbidden, response.StatusCode);
        Assert.Equal(1, host.Generator.Calls);
        Assert.Equal(1, host.Credits.ReleasedFor(AccountA, requestId));
        using var history = await host.GetProjectAiActivityAsync(AccountA, "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        using var body = JsonDocument.Parse(await history.Content.ReadAsStringAsync());
        var activity = Assert.Single(body.RootElement.GetProperty("activities").EnumerateArray());
        Assert.Equal("STALE", activity.GetProperty("outcome").GetString());
    }

    [Fact]
    public async Task Provider_failure_is_recorded_as_failed_and_releases_credit()
    {
        using var host = new TestHost();
        const string requestId = "project-ai-stage-provider-failure";
        host.Generator.Failure = new InvalidOperationException("synthetic provider failure");

        using var response = await host.PostStageAssistAsync(AccountA, requestId, ValidStageAssistRequest());

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal(1, host.Credits.ReleasedFor(AccountA, requestId));
        using var history = await host.GetProjectAiActivityAsync(AccountA, "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
        using var body = JsonDocument.Parse(await history.Content.ReadAsStringAsync());
        var activity = Assert.Single(body.RootElement.GetProperty("activities").EnumerateArray());
        Assert.Equal("FAILED", activity.GetProperty("outcome").GetString());
    }

    [Fact]
    public async Task In_project_assist_hides_a_project_owned_by_another_account()
    {
        using var host = new TestHost();
        const string requestId = "project-ai-flow-0021";

        using var response = await host.PostScaffoldAsync(
            AccountB,
            requestId,
            ValidRequest(
                baseProjectRevision: 7,
                operation: ProjectAiScaffoldValidator.AssistOperation,
                projectId: ExistingProjectId.ToString()));

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
        Assert.Equal(0, host.Credits.ReservedFor(AccountB, requestId));
        Assert.Equal(0, host.Generator.Calls);
    }

    [Fact]
    public async Task In_project_assist_rejects_a_missing_project_before_reserving_credit()
    {
        var projects = new RecordingStudentProjectStore((_, _, _) => null);
        using var host = new TestHost(projects: projects);
        const string requestId = "project-ai-flow-0022";

        using var response = await host.PostScaffoldAsync(
            AccountA,
            requestId,
            ValidRequest(
                baseProjectRevision: 7,
                operation: ProjectAiScaffoldValidator.AssistOperation,
                projectId: ExistingProjectId.ToString()));

        Assert.Equal(HttpStatusCode.NotFound, response.StatusCode);
        Assert.Equal(0, host.Credits.ReservedFor(AccountA, requestId));
        Assert.Equal(0, host.Generator.Calls);
    }

    [Fact]
    public async Task In_project_assist_rejects_a_stale_project_revision_before_reserving_credit()
    {
        var projects = new RecordingStudentProjectStore((accountId, projectId, _) =>
            accountId == AccountA && projectId == ExistingProjectId
                ? RecordingStudentProjectStore.Record(8)
                : null);
        using var host = new TestHost(projects: projects);
        const string requestId = "project-ai-flow-0023";

        using var response = await host.PostScaffoldAsync(
            AccountA,
            requestId,
            ValidRequest(
                baseProjectRevision: 7,
                operation: ProjectAiScaffoldValidator.AssistOperation,
                projectId: ExistingProjectId.ToString()));

        Assert.Equal(HttpStatusCode.Conflict, response.StatusCode);
        Assert.Equal(0, host.Credits.ReservedFor(AccountA, requestId));
        Assert.Equal(0, host.Generator.Calls);
    }

    [Fact]
    public async Task In_project_assist_releases_credit_if_the_project_changes_before_dispatch()
    {
        var projects = new RecordingStudentProjectStore((accountId, projectId, read) =>
            accountId == AccountA && projectId == ExistingProjectId
                ? RecordingStudentProjectStore.Record(read == 1 ? 7 : 8)
                : null);
        using var host = new TestHost(projects: projects);
        const string requestId = "project-ai-flow-0024";

        using var response = await host.PostScaffoldAsync(
            AccountA,
            requestId,
            ValidRequest(
                baseProjectRevision: 7,
                operation: ProjectAiScaffoldValidator.AssistOperation,
                projectId: ExistingProjectId.ToString()));

        Assert.Equal(HttpStatusCode.Conflict, response.StatusCode);
        Assert.Equal(0, host.Credits.ReservedFor(AccountA, requestId));
        Assert.Equal(1, host.Credits.ReleasedFor(AccountA, requestId));
        Assert.Equal(0, host.Generator.Calls);
    }

    [Fact]
    public async Task A_revision_number_alone_cannot_select_the_one_credit_assist_price()
    {
        using var host = new TestHost();
        using var response = await host.PostScaffoldAsync(
            AccountA,
            "project-ai-flow-0017",
            ValidRequest(baseProjectRevision: 7));

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal(0, host.Credits.ReservationCount);
        Assert.Equal(0, host.Generator.Calls);
    }

    [Fact]
    public async Task Missing_explicit_nullable_revision_key_is_rejected_before_reservation()
    {
        using var host = new TestHost();
        var requestWithoutRevisionKey = ValidRequest().Replace(
            "\"baseProjectRevision\":null,",
            string.Empty,
            StringComparison.Ordinal);

        using var response = await host.PostScaffoldAsync(
            AccountA,
            "project-ai-flow-0018",
            requestWithoutRevisionKey);

        Assert.Equal(HttpStatusCode.BadRequest, response.StatusCode);
        Assert.Equal(0, host.Credits.ReservationCount);
        Assert.Equal(0, host.Generator.Calls);
    }

    [Fact]
    public async Task Scaffold_request_replay_or_changed_payload_does_not_dispatch_again()
    {
        using var host = new TestHost();
        const string requestId = "project-ai-flow-0014";
        var request = ValidRequest();
        using var preview = await host.PostScaffoldAsync(AccountA, requestId, request);
        using var replay = await host.PostScaffoldAsync(AccountA, requestId, request);
        var changedRequest = request.Replace(
            "Help me define a manageable first step.",
            "Help me define a different first step.",
            StringComparison.Ordinal);
        using var changedPayload = await host.PostScaffoldAsync(AccountA, requestId, changedRequest);

        Assert.Equal(HttpStatusCode.OK, preview.StatusCode);
        Assert.Equal(HttpStatusCode.Conflict, replay.StatusCode);
        Assert.Equal(HttpStatusCode.Conflict, changedPayload.StatusCode);
        Assert.Equal(1, host.Generator.Calls);
        Assert.Equal(0, host.Credits.ReservedFor(AccountA, requestId));
        Assert.Equal(3, host.Credits.ConsumedFor(AccountA, requestId));
    }

    [Fact]
    public async Task Apply_consumes_once_and_an_identical_retry_returns_the_same_settlement()
    {
        using var host = new TestHost();
        const string requestId = "project-ai-flow-0003";
        using var preview = await host.PostScaffoldAsync(AccountA, requestId, ValidRequest());
        Assert.Equal(HttpStatusCode.OK, preview.StatusCode);

        using var first = await host.PostSettlementAsync(AccountA, requestId, "apply", requestId);
        var firstText = await first.Content.ReadAsStringAsync();
        using var firstBody = JsonDocument.Parse(firstText);
        using var retry = await host.PostSettlementAsync(AccountA, requestId, "apply", requestId);
        var retryText = await retry.Content.ReadAsStringAsync();

        Assert.Equal(HttpStatusCode.OK, first.StatusCode);
        Assert.Equal(HttpStatusCode.OK, retry.StatusCode);
        Assert.Equal(firstText, retryText);
        Assert.Equal(
            new[] { "creditCost", "requestId", "schema", "status", "version" },
            firstBody.RootElement.EnumerateObject()
                .Select(property => property.Name)
                .OrderBy(name => name, StringComparer.Ordinal)
                .ToArray());
        Assert.Equal("applied", firstBody.RootElement.GetProperty("status").GetString());
        Assert.Equal(3, firstBody.RootElement.GetProperty("creditCost").GetInt32());
        Assert.Equal(3, host.Credits.ConsumedFor(AccountA, requestId));
        Assert.Equal(0, host.Credits.ReservedFor(AccountA, requestId));
    }

    [Fact]
    public async Task Dismiss_keeps_the_generation_charge_and_retry_is_idempotent()
    {
        using var host = new TestHost();
        const string requestId = "project-ai-flow-0004";
        using var preview = await host.PostScaffoldAsync(AccountA, requestId, ValidRequest());
        Assert.Equal(HttpStatusCode.OK, preview.StatusCode);

        using var first = await host.PostSettlementAsync(AccountA, requestId, "dismiss", requestId);
        using var retry = await host.PostSettlementAsync(AccountA, requestId, "dismiss", requestId);

        Assert.Equal(HttpStatusCode.OK, first.StatusCode);
        Assert.Equal(HttpStatusCode.OK, retry.StatusCode);
        Assert.Equal(3, host.Credits.ConsumedFor(AccountA, requestId));
        Assert.Equal(0, host.Credits.ReservedFor(AccountA, requestId));
        Assert.Equal(0, host.Credits.ReleasedFor(AccountA, requestId));
    }

    [Fact]
    public async Task Settlement_rejects_a_changed_key_or_changed_decision()
    {
        using var host = new TestHost();
        const string requestId = "project-ai-flow-0005";
        using var preview = await host.PostScaffoldAsync(AccountA, requestId, ValidRequest());
        Assert.Equal(HttpStatusCode.OK, preview.StatusCode);

        using var changedKey = await host.PostSettlementAsync(AccountA, requestId, "dismiss", "different-key-0001");
        using var firstDecision = await host.PostSettlementAsync(AccountA, requestId, "dismiss", requestId);
        using var changedDecision = await host.PostSettlementAsync(AccountA, requestId, "apply", requestId);

        Assert.Equal(HttpStatusCode.Conflict, changedKey.StatusCode);
        Assert.Equal(HttpStatusCode.OK, firstDecision.StatusCode);
        Assert.Equal(HttpStatusCode.Conflict, changedDecision.StatusCode);
        Assert.Equal(3, host.Credits.ConsumedFor(AccountA, requestId));
        Assert.Equal(0, host.Credits.ReleasedFor(AccountA, requestId));
    }

    [Fact]
    public async Task Settlement_is_account_scoped()
    {
        using var host = new TestHost();
        const string requestId = "project-ai-flow-0006";
        using var preview = await host.PostScaffoldAsync(AccountA, requestId, ValidRequest());
        Assert.Equal(HttpStatusCode.OK, preview.StatusCode);

        using var otherAccount = await host.PostSettlementAsync(AccountB, requestId, "apply", requestId);

        Assert.Equal(HttpStatusCode.NotFound, otherAccount.StatusCode);
        Assert.Equal(3, host.Credits.ConsumedFor(AccountA, requestId));
        Assert.Equal(0, host.Credits.ReservedFor(AccountA, requestId));
    }

    [Fact]
    public async Task Settlement_rejects_apply_until_a_valid_preview_is_ready()
    {
        using var host = new TestHost();
        host.Generator.WaitUntilReleased = true;
        const string requestId = "project-ai-flow-0013";

        var pendingPreview = host.PostScaffoldAsync(AccountA, requestId, ValidRequest());
        await host.Generator.Started.Task.WaitAsync(TimeSpan.FromSeconds(10));
        using var earlyApply = await host.PostSettlementAsync(AccountA, requestId, "apply", requestId);
        using var earlyDismiss = await host.PostSettlementAsync(AccountA, requestId, "dismiss", requestId);
        host.Generator.Release.TrySetResult();
        using var preview = await pendingPreview;

        Assert.Equal(HttpStatusCode.Conflict, earlyApply.StatusCode);
        Assert.Equal(HttpStatusCode.Conflict, earlyDismiss.StatusCode);
        Assert.Equal(HttpStatusCode.OK, preview.StatusCode);
        Assert.Equal(3, host.Credits.ConsumedFor(AccountA, requestId));
        Assert.Equal(0, host.Credits.ReservedFor(AccountA, requestId));
    }

    [Fact]
    public async Task Provider_failure_releases_reserved_cost()
    {
        using var host = new TestHost();
        host.Generator.Failure = new InvalidOperationException("synthetic provider failure");
        const string requestId = "project-ai-flow-0007";

        using var response = await host.PostScaffoldAsync(AccountA, requestId, ValidRequest());

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal(0, host.Credits.ConsumedFor(AccountA, requestId));
        Assert.Equal(0, host.Credits.ReservedFor(AccountA, requestId));
        Assert.Equal(3, host.Credits.ReleasedFor(AccountA, requestId));
    }

    [Fact]
    public async Task Provider_refusal_releases_reserved_cost()
    {
        using var host = new TestHost();
        host.Generator.Output = null;
        const string requestId = "project-ai-flow-0008";

        using var response = await host.PostScaffoldAsync(AccountA, requestId, ValidRequest());

        Assert.Equal(HttpStatusCode.UnprocessableEntity, response.StatusCode);
        Assert.Equal(0, host.Credits.ConsumedFor(AccountA, requestId));
        Assert.Equal(0, host.Credits.ReservedFor(AccountA, requestId));
        Assert.Equal(3, host.Credits.ReleasedFor(AccountA, requestId));
    }

    [Fact]
    public async Task Malformed_provider_output_releases_reserved_cost()
    {
        using var host = new TestHost();
        host.Generator.Output = host.Generator.Output! with
        {
            FieldSuggestions = [new ProjectAiFieldSuggestion("unpublished-field", "Unverified suggestion")],
        };
        const string requestId = "project-ai-flow-0010";

        using var response = await host.PostScaffoldAsync(AccountA, requestId, ValidRequest());

        Assert.Equal(HttpStatusCode.BadGateway, response.StatusCode);
        Assert.Equal(0, host.Credits.ConsumedFor(AccountA, requestId));
        Assert.Equal(0, host.Credits.ReservedFor(AccountA, requestId));
        Assert.Equal(3, host.Credits.ReleasedFor(AccountA, requestId));
    }

    [Fact]
    public async Task Provider_timeout_releases_reserved_cost()
    {
        using var host = new TestHost();
        host.Generator.Failure = new OperationCanceledException("synthetic provider timeout");
        const string requestId = "project-ai-flow-0011";

        using var response = await host.PostScaffoldAsync(AccountA, requestId, ValidRequest());

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal(0, host.Credits.ConsumedFor(AccountA, requestId));
        Assert.Equal(0, host.Credits.ReservedFor(AccountA, requestId));
        Assert.Equal(3, host.Credits.ReleasedFor(AccountA, requestId));
    }

    [Fact]
    public async Task Client_cancellation_releases_reserved_cost_before_propagating_cancellation()
    {
        using var host = new TestHost();
        host.Generator.WaitUntilCancelled = true;
        using var cancellation = new CancellationTokenSource();
        const string requestId = "project-ai-flow-0012";
        var pending = host.PostScaffoldAsync(AccountA, requestId, ValidRequest(), cancellation.Token);
        await host.Generator.Started.Task.WaitAsync(TimeSpan.FromSeconds(10));
        cancellation.Cancel();

        await Assert.ThrowsAnyAsync<OperationCanceledException>(() => pending);
        Assert.Equal(0, host.Credits.ConsumedFor(AccountA, requestId));
        Assert.Equal(0, host.Credits.ReservedFor(AccountA, requestId));
        Assert.Equal(3, host.Credits.ReleasedFor(AccountA, requestId));
    }

    [Fact]
    public async Task Disabled_provider_fails_closed_before_reserving_or_dispatching()
    {
        using var host = new TestHost(providerEnabled: false);
        const string requestId = "project-ai-flow-0009";

        using var response = await host.PostScaffoldAsync(AccountA, requestId, ValidRequest());
        using var body = JsonDocument.Parse(await response.Content.ReadAsStringAsync());

        Assert.Equal(HttpStatusCode.ServiceUnavailable, response.StatusCode);
        Assert.Equal("PROJECT_AI_NOT_READY", body.RootElement.GetProperty("code").GetString());
        Assert.False(body.RootElement.TryGetProperty("creditCost", out _));
        Assert.Equal(0, host.Credits.ReservationCount);
        Assert.Equal(0, host.Generator.Calls);
    }

    private static string ValidRequest(
        int? baseProjectRevision = null,
        string operation = ProjectAiScaffoldValidator.CreateOperation,
        string? projectId = null) => JsonSerializer.Serialize(new
    {
        schema = "evidrilo.project-ai-scaffold",
        version = "1",
        operation,
        projectId,
        templateId = "reviewed-template",
        templateVersion = 1,
        baseProjectRevision,
        assignmentBrief = "Compare two measurements and explain the reasoning.",
        researchQuestion = "How does the measured outcome vary?",
        studentQuestion = "Help me define a manageable first step.",
        currentFields = new Dictionary<string, string>(),
        constraints = new[] { "Limited time" },
        locale = "en",
        optedIn = true,
        projectDataConsent = true,
        projectDataConsentVersion = "project-ai-data.v1",
    });

    private static string ValidStageAssistRequest(
        string mode = "PROJECT",
        string? projectId = null,
        int baseProjectRevision = 7,
        string? stageId = "frame",
        string? operationId = "explain_template_step",
        IReadOnlyList<string>? selectedFieldIds = null,
        IReadOnlyList<string>? selectedEvidenceIds = null)
    {
        if (mode == "GENERAL")
        {
            return JsonSerializer.Serialize(new
            {
                schema = "evidrilo.project-ai-stage-assist",
                version = "1",
                mode,
                installationId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                locale = "en",
            });
        }

        return JsonSerializer.Serialize(new
        {
            schema = "evidrilo.project-ai-stage-assist",
            version = "1",
            mode,
            installationId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
            projectId = projectId ?? ExistingProjectId.ToString(),
            templateId = "reviewed-template",
            templateVersion = 1,
            baseProjectRevision,
            stageId,
            operationId,
            selectedFieldIds = selectedFieldIds ?? ["question"],
            selectedEvidenceIds = selectedEvidenceIds ?? Array.Empty<string>(),
            locale = "en",
        });
    }

    private sealed class TestHost : IDisposable
    {
        private readonly ApiFactory factory;

        public TestHost(
            bool providerEnabled = true,
            RecordingStudentProjectStore? projects = null,
            RecordingConsentStore? consent = null)
        {
            Credits = new RecordingCreditLedger();
            Generator = new RecordingScaffoldGenerator(providerEnabled);
            StudentProjects = projects ?? new RecordingStudentProjectStore();
            Activities = new RecordingProjectAiActivityStore();
            ConsentStore = consent ?? new RecordingConsentStore();
            factory = ApiFactory.WithAdditionalServices(services =>
            {
                services.RemoveAll<IProjectAiConsentStore>();
                services.AddSingleton<IProjectAiConsentStore>(ConsentStore);
                services.RemoveAll<IProjectTemplateStore>();
                services.AddSingleton<IProjectTemplateStore>(new PublishedTemplateStore());
                services.RemoveAll<IProjectAiScaffoldGenerator>();
                services.AddSingleton<IProjectAiScaffoldGenerator>(Generator);
                services.RemoveAll<IProjectAiStageAssistGenerator>();
                services.AddSingleton<IProjectAiStageAssistGenerator>(Generator);
                services.RemoveAll<IAiCreditLedger>();
                services.AddSingleton<IAiCreditLedger>(Credits);
                services.RemoveAll<IStudentProjectStore>();
                services.AddSingleton<IStudentProjectStore>(StudentProjects);
                services.RemoveAll<IProjectAiActivityStore>();
                services.AddSingleton<IProjectAiActivityStore>(Activities);
            });
            Client = factory.CreateClient();
        }

        public HttpClient Client { get; }
        public RecordingCreditLedger Credits { get; }
        public RecordingScaffoldGenerator Generator { get; }
        public RecordingStudentProjectStore StudentProjects { get; }
        public RecordingProjectAiActivityStore Activities { get; }
        public RecordingConsentStore ConsentStore { get; }

        public Task<HttpResponseMessage> PostScaffoldAsync(
            Guid accountId,
            string requestId,
            string content,
            CancellationToken cancellationToken = default) =>
            SendAsync(HttpMethod.Post, "/v1/project-ai/scaffold", accountId, requestId, content, cancellationToken);

        public Task<HttpResponseMessage> PostStageAssistAsync(
            Guid accountId,
            string requestId,
            string content,
            CancellationToken cancellationToken = default) =>
            SendAsync(HttpMethod.Post, "/v1/project-ai/stage-assist", accountId, requestId, content, cancellationToken);

        public Task<HttpResponseMessage> GetProjectAiActivityAsync(
            Guid accountId,
            string installationId,
            string? limit = null,
            string? cursor = null) => SendAsync(
                HttpMethod.Get,
                $"/v1/project-ai/activity?installationId={installationId}"
                    + (limit is null ? string.Empty : $"&limit={limit}")
                    + (cursor is null ? string.Empty : $"&cursor={Uri.EscapeDataString(cursor)}"),
                accountId,
                "project-ai-activity-list-0001",
                "{}");

        public Task<HttpResponseMessage> ClearGeneralProjectAiActivityAsync(Guid accountId, string installationId) =>
            SendAsync(
                HttpMethod.Delete,
                $"/v1/project-ai/activity/general?installationId={installationId}",
                accountId,
                "project-ai-activity-clear-0001",
                "{}");

        public Task<HttpResponseMessage> PostStageSettlementAsync(
            Guid accountId,
            string requestId,
            string outcome,
            int? resultProjectRevision = null) => SendAsync(
                HttpMethod.Post,
                "/v1/project-ai/stage-assist/settlement",
                accountId,
                requestId,
                JsonSerializer.Serialize(new
                {
                    schema = "evidrilo.project-ai-stage-assist-settlement",
                    version = "1",
                    installationId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                    requestId,
                    outcome,
                    resultProjectRevision,
                }));

        public Task<HttpResponseMessage> PostSettlementAsync(
            Guid accountId,
            string requestId,
            string decision,
            string idempotencyKey) => SendAsync(
                HttpMethod.Post,
                "/v1/project-ai/scaffold/settlement",
                accountId,
                idempotencyKey,
                JsonSerializer.Serialize(new
                {
                    schema = "evidrilo.project-ai-scaffold-settlement",
                    version = "1",
                    requestId,
                    decision,
                }));

        private async Task<HttpResponseMessage> SendAsync(
            HttpMethod method,
            string path,
            Guid accountId,
            string idempotencyKey,
            string content,
            CancellationToken cancellationToken = default)
        {
            using var request = new HttpRequestMessage(method, path)
            {
                Content = new StringContent(content, Encoding.UTF8, "application/json"),
            };
            request.Headers.Add("X-Test-User", $"{accountId}|true");
            request.Headers.Add("Idempotency-Key", idempotencyKey);
            return await Client.SendAsync(request, cancellationToken);
        }

        public void Dispose()
        {
            Client.Dispose();
            factory.Dispose();
        }
    }

    private sealed class RecordingStudentProjectStore(
        Func<Guid, Guid, int, StudentProjectRecord?>? readOwn = null,
        string projectQuestion = "How does the reported outcome vary?") : IStudentProjectStore
    {
        private int currentVersion = 7;
        private int readCount;
        public int ReadCount => Volatile.Read(ref readCount);
        public void SetVersion(int version) => Volatile.Write(ref currentVersion, version);

        public static StudentProjectRecord Record(int version, string projectQuestion = "How does the reported outcome vary?")
        {
            var now = DateTimeOffset.Parse("2026-09-29T00:00:00Z");
            return new StudentProjectRecord(
                ExistingProjectId,
                version,
                new StudentProjectDocument(
                    "Saved project",
                    null,
                    projectQuestion,
                    null,
                    null,
                    [],
                    [new StudentProjectEvidenceItem("source-1", StudentProjectEvidenceKind.Source, "Selected source", "A reviewed abstract.", "Synthetic fixture")],
                    [],
                    null,
                    null,
                    [],
                    [],
                    null,
                    [],
                    []),
                now,
                now);
        }

        public Task<StudentProjectCloudConsentState> ReadCloudConsentOwnAsync(Guid accountId, CancellationToken cancellationToken) =>
            throw new NotSupportedException();

        public Task<StudentProjectCloudConsentState> UpdateCloudConsentOwnAsync(Guid accountId, string policyVersion, string decision, CancellationToken cancellationToken) =>
            throw new NotSupportedException();

        public Task<StudentProjectListResult> ListOwnAsync(Guid accountId, int limit, DateTimeOffset? beforeCreatedAt, Guid? beforeProjectId, CancellationToken cancellationToken) =>
            throw new NotSupportedException();

        public Task<StudentProjectRecord?> ReadOwnAsync(Guid accountId, Guid projectId, CancellationToken cancellationToken)
        {
            var count = Interlocked.Increment(ref readCount);
            return Task.FromResult(readOwn is null
                ? accountId == AccountA && projectId == ExistingProjectId ? Record(Volatile.Read(ref currentVersion), projectQuestion) : null
                : readOwn(accountId, projectId, count));
        }

        public Task<StudentProjectRevisionPage> ReadRevisionsOwnAsync(Guid accountId, Guid projectId, int limit, int? beforeVersion, CancellationToken cancellationToken) =>
            throw new NotSupportedException();

        public Task<StudentProjectMutation> CreateOwnAsync(Guid accountId, string idempotencyKey, string requestFingerprint, StudentProjectDocument document, CancellationToken cancellationToken) =>
            throw new NotSupportedException();

        public Task<StudentProjectMutation> SaveOwnAsync(Guid accountId, Guid projectId, string idempotencyKey, string requestFingerprint, int expectedVersion, StudentProjectDocument document, CancellationToken cancellationToken) =>
            throw new NotSupportedException();

        public Task<StudentProjectMutation> PermanentlyDeleteOwnAsync(Guid accountId, Guid projectId, string idempotencyKey, string requestFingerprint, int expectedVersion, CancellationToken cancellationToken) =>
            throw new NotSupportedException();
    }

    private sealed class RecordingProjectAiActivityStore : IProjectAiActivityStore
    {
        private readonly object sync = new();
        private readonly Dictionary<(Guid AccountId, string RequestId), ProjectAiActivityRecord> records = [];

        public void SeedGeneralActivity(Guid accountId, Guid installationId, string requestId)
        {
            lock (sync)
            {
                var now = DateTimeOffset.UtcNow;
                records.Add((accountId, requestId), new ProjectAiActivityRecord(
                    Guid.NewGuid(),
                    installationId,
                    requestId,
                    ProjectAiStageAssistValidator.GeneralMode,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    ProjectAiActivityOutcomes.Pending,
                    null,
                    null,
                    now,
                    now));
            }
        }

        public Task<bool> CreatePendingOwnAsync(
            Guid accountId,
            ProjectAiActivityCreate activity,
            CancellationToken cancellationToken)
        {
            lock (sync)
            {
                var key = (accountId, activity.RequestId);
                if (records.ContainsKey(key)) return Task.FromResult(false);
                var now = DateTimeOffset.UtcNow;
                records.Add(key, new ProjectAiActivityRecord(
                    Guid.NewGuid(),
                    activity.InstallationId,
                    activity.RequestId,
                    activity.Mode,
                    activity.ProjectId,
                    activity.StageId,
                    activity.OperationId,
                    activity.BaseProjectRevision,
                    activity.ConsentGeneration,
                    null,
                    ProjectAiActivityOutcomes.Pending,
                    null,
                    null,
                    now,
                    now));
                return Task.FromResult(true);
            }
        }

        public Task<ProjectAiActivityRecord?> ReadOwnAsync(
            Guid accountId,
            Guid installationId,
            string requestId,
            CancellationToken cancellationToken)
        {
            lock (sync)
            {
                var record = records.GetValueOrDefault((accountId, requestId));
                return Task.FromResult<ProjectAiActivityRecord?>(record?.InstallationId == installationId ? record : null);
            }
        }

        public Task<ProjectAiActivityPage> ListOwnAsync(
            Guid accountId,
            Guid installationId,
            Guid? projectId,
            int limit,
            ProjectAiActivityCursor? before,
            CancellationToken cancellationToken)
        {
            lock (sync)
            {
                var entries = records
                    .Where(pair => pair.Key.AccountId == accountId
                        && pair.Value.InstallationId == installationId
                        && (projectId is null || pair.Value.ProjectId == projectId)
                        && (before is null || pair.Value.CreatedAt < before.CreatedAt
                            || (pair.Value.CreatedAt == before.CreatedAt && pair.Value.ActivityId.CompareTo(before.ActivityId) < 0)))
                    .Select(pair => pair.Value)
                    .OrderByDescending(item => item.CreatedAt)
                    .ThenByDescending(item => item.ActivityId)
                    .Take(limit + 1)
                    .ToList();
                var hasMore = entries.Count > limit;
                if (hasMore) entries.RemoveAt(entries.Count - 1);
                var cursor = hasMore && entries.Count > 0 ? NpgsqlProjectAiActivityStore.EncodeCursor(entries[^1]) : null;
                return Task.FromResult(new ProjectAiActivityPage(entries, cursor));
            }
        }

        public Task<ProjectAiActivityCompletionStatus> CompleteOwnAsync(
            Guid accountId,
            Guid installationId,
            string requestId,
            string outcome,
            int? resultProjectRevision,
            string? requestedSettlementOutcome,
            string? settlementHash,
            CancellationToken cancellationToken)
        {
            lock (sync)
            {
                var key = (accountId, requestId);
                if (!records.TryGetValue(key, out var existing) || existing.InstallationId != installationId)
                    return Task.FromResult(ProjectAiActivityCompletionStatus.NotFound);
                if (existing.Outcome != ProjectAiActivityOutcomes.Pending)
                    return Task.FromResult(existing.Outcome == outcome
                        && existing.ResultProjectRevision == resultProjectRevision
                        && existing.RequestedSettlementOutcome == requestedSettlementOutcome
                        && existing.SettlementHash == settlementHash
                        ? ProjectAiActivityCompletionStatus.AlreadyCompleted
                        : ProjectAiActivityCompletionStatus.Conflict);
                records[key] = existing with
                {
                    Outcome = outcome,
                    ResultProjectRevision = resultProjectRevision,
                    RequestedSettlementOutcome = requestedSettlementOutcome,
                    SettlementHash = settlementHash,
                    UpdatedAt = DateTimeOffset.UtcNow,
                };
                return Task.FromResult(ProjectAiActivityCompletionStatus.Updated);
            }
        }

        public Task<int> ClearGeneralOwnAsync(Guid accountId, Guid installationId, CancellationToken cancellationToken)
        {
            lock (sync)
            {
                var keys = records
                    .Where(pair => pair.Key.AccountId == accountId
                        && pair.Value.InstallationId == installationId
                        && pair.Value.Mode == ProjectAiStageAssistValidator.GeneralMode)
                    .Select(pair => pair.Key)
                    .ToArray();
                foreach (var key in keys) records.Remove(key);
                return Task.FromResult(keys.Length);
            }
        }
    }

    private sealed class RecordingConsentStore : IProjectAiConsentStore
    {
        private int readCount;
        public int RevokeOnRead { get; set; } = int.MaxValue;

        public Task<ProjectAiConsentState> ReadOwnAsync(Guid accountId, CancellationToken cancellationToken)
        {
            var count = Interlocked.Increment(ref readCount);
            var now = DateTimeOffset.UtcNow;
            return Task.FromResult(count >= RevokeOnRead
                ? new ProjectAiConsentState(
                    ProjectAiConsentPolicy.Schema,
                    ProjectAiConsentPolicy.Version,
                    false,
                    ProjectAiConsentPolicy.CurrentPolicyVersion,
                    now.AddMinutes(-1),
                    now,
                    2)
                : new ProjectAiConsentState(
                    ProjectAiConsentPolicy.Schema,
                    ProjectAiConsentPolicy.Version,
                    true,
                    ProjectAiConsentPolicy.CurrentPolicyVersion,
                    now,
                    null,
                    1));
        }

        public Task<ProjectAiConsentState> GrantOwnAsync(Guid accountId, string policyVersion, CancellationToken cancellationToken) =>
            ReadOwnAsync(accountId, cancellationToken);

        public Task<ProjectAiConsentState> RevokeOwnAsync(Guid accountId, CancellationToken cancellationToken) =>
            Task.FromResult(ProjectAiConsentPolicy.NotGranted);
    }

    private sealed class PublishedTemplateStore : IProjectTemplateStore
    {
        private static readonly ProjectTemplateCatalogEntry Template = new(
            "reviewed-template",
            1,
            "literature_review",
            new ProjectTemplateDocument(
                "Reviewed literature synthesis",
                "A bounded structure for student synthesis.",
                "A student-authored synthesis plan.",
                [
                    new ProjectTemplateInputField("question", ProjectTemplateInputKind.ResearchQuestion, "Question", true),
                    new ProjectTemplateInputField("source", ProjectTemplateInputKind.Source, "Source", false),
                ],
                [new ProjectTemplateStep(
                    "frame",
                    "Frame the question",
                    ["question", "source"],
                    [new ProjectTemplateAiOperationCapability("explain_template_step", ["question", "source"], ["question"])])],
                ["Do not claim causation from association alone."],
                ["Record source provenance."],
                ["Use text labels in addition to color."],
                [
                    new ProjectTemplateExample("normal", "Normal example.", true, ProjectTemplateExampleKind.Normal),
                    new ProjectTemplateExample("edge", "Boundary example.", true, ProjectTemplateExampleKind.EdgeOrConflicting),
                ]),
            DateTimeOffset.Parse("2026-09-27T00:00:00Z"));

        public Task<IReadOnlyList<ProjectTemplateFamilyOffering>> ListFamiliesAsync(CancellationToken cancellationToken) =>
            Task.FromResult<IReadOnlyList<ProjectTemplateFamilyOffering>>([]);

        public Task<ProjectTemplateCatalogPage> ListPublishedAsync(string? family, int limit, string? afterTemplateId, CancellationToken cancellationToken) =>
            Task.FromResult(new ProjectTemplateCatalogPage([], null));

        public Task<ProjectTemplateCatalogEntry?> GetPublishedAsync(string templateId, int templateVersion, CancellationToken cancellationToken) =>
            Task.FromResult<ProjectTemplateCatalogEntry?>(templateId == Template.TemplateId && templateVersion == Template.TemplateVersion ? Template : null);

        public Task<ProjectTemplateOperation> CreateDraftAsync(Guid actorAccountId, ProjectTemplateDraftCreateRequest request, CancellationToken cancellationToken) =>
            throw new NotSupportedException();

        public Task<ProjectTemplateOperation> TransitionAsync(Guid actorAccountId, string templateId, int templateVersion, ProjectTemplateTransitionRequest request, CancellationToken cancellationToken) =>
            throw new NotSupportedException();
    }

    private sealed class RecordingScaffoldGenerator(bool enabled) : IProjectAiScaffoldGenerator, IProjectAiStageAssistGenerator
    {
        public TaskCompletionSource Started { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);
        public TaskCompletionSource Release { get; } = new(TaskCreationOptions.RunContinuationsAsynchronously);
        public bool IsEnabled { get; } = enabled;
        public int Calls { get; private set; }
        public ProjectAiScaffoldProviderRequest? LastRequest { get; private set; }
        public ProjectAiStageAssistProviderRequest? LastStageRequest { get; private set; }
        public bool WaitUntilCancelled { get; set; }
        public bool WaitUntilReleased { get; set; }
        public Action? OnGenerate { get; set; }
        public Exception? Failure { get; set; }
        public ProjectAiScaffoldOutput? Output { get; set; } = new(
            "reviewed-template",
            1,
            ProjectAiScaffoldValidator.PromptVersion,
            "Check how each source relates to the research question.",
            [new ProjectAiFieldSuggestion("question", "How does the reported outcome vary?")],
            ["Which inclusion rule will you use?"],
            ["What result would change your current interpretation?"]);
        public ProjectAiStageAssistOutput StageOutput { get; set; } = new(
            "reviewed-template",
            1,
            ProjectAiStageAssistValidator.PromptVersion,
            [
                new ProjectAiStageAssistItem("explanation-1", "EXPLANATION", "Check how each source relates to the research question.", null, null, null, [], [], [], []),
                new ProjectAiStageAssistItem("proposal-question", "PROPOSAL", null, "question", null, "How does the reported outcome vary across the selected sources?", [], ["A narrower scope may make comparison easier."], ["The selected sources may not represent all available evidence."], ["A manual source review is still required."]),
            ],
            [],
            []);

        public int EstimateMaximumCreditCost(ProjectAiScaffoldProviderRequest request) =>
            request.Operation == ProjectAiScaffoldValidator.CreateOperation ? 3 : 1;

        public int EstimateMaximumCreditCost(ProjectAiStageAssistProviderRequest request) => 1;

        public Task<ProjectAiScaffoldOutput?> GenerateAsync(ProjectAiScaffoldProviderRequest request, CancellationToken cancellationToken)
        {
            Calls++;
            LastRequest = request;
            PrepareCall();
            var output = Output is null ? null : Output with
            {
                Usage = request.Operation == ProjectAiScaffoldValidator.CreateOperation
                    ? new AiProviderTokenUsage(27_000, 0, 0, 512, 0)
                    : new AiProviderTokenUsage(5_000, 0, 0, 512, 0),
            };
            if (WaitUntilCancelled) return WaitForCancellationAsync(output, cancellationToken);
            if (WaitUntilReleased) return WaitForReleaseAsync(output, cancellationToken);
            return Task.FromResult(output);
        }

        public Task<ProjectAiStageAssistOutput?> GenerateAsync(ProjectAiStageAssistProviderRequest request, CancellationToken cancellationToken)
        {
            Calls++;
            LastStageRequest = request;
            PrepareCall();
            var output = StageOutput with { Usage = new AiProviderTokenUsage(5_000, 0, 0, 512, 0) };
            if (WaitUntilCancelled) return WaitForCancellationAsync(output, cancellationToken);
            if (WaitUntilReleased) return WaitForReleaseAsync(output, cancellationToken);
            return Task.FromResult<ProjectAiStageAssistOutput?>(output);
        }

        private void PrepareCall()
        {
            OnGenerate?.Invoke();
            var failure = Failure;
            if (failure is not null) throw failure;
            if (WaitUntilCancelled)
            {
                Started.TrySetResult();
            }
            if (WaitUntilReleased)
            {
                Started.TrySetResult();
            }
        }

        private async Task<T?> WaitForCancellationAsync<T>(T? output, CancellationToken cancellationToken)
        {
            await Task.Delay(Timeout.InfiniteTimeSpan, cancellationToken);
            return output;
        }

        private async Task<T?> WaitForReleaseAsync<T>(T? output, CancellationToken cancellationToken)
        {
            await Release.Task.WaitAsync(cancellationToken);
            return output;
        }
    }

    private sealed class RecordingCreditLedger : IAiCreditLedger, IProjectAiCreditSettlementLedger
    {
        private readonly object sync = new();
        private readonly Dictionary<(Guid AccountId, string RequestId), ReservationState> reservations = [];

        public int ReservationCount
        {
            get { lock (sync) return reservations.Count; }
        }

        public Task EnsureConsentAsync(Guid accountId, string consentVersion, CancellationToken cancellationToken) => Task.CompletedTask;

        public Task<AiCreditReservation?> TryReserveAsync(Guid accountId, string requestId, string requestHash, CancellationToken cancellationToken) =>
            TryReserveAsync(accountId, requestId, requestHash, 1, cancellationToken);

        public Task<AiCreditReservation?> TryReserveAsync(Guid accountId, string requestId, string requestHash, int creditCost, CancellationToken cancellationToken)
        {
            lock (sync)
            {
                var key = (accountId, requestId);
                if (reservations.TryGetValue(key, out var existing))
                {
                    if (existing.RequestHash != requestHash || existing.CreditCost != creditCost)
                        throw new ApiException(StatusCodes.Status409Conflict, "AI_IDEMPOTENCY_KEY_REUSE", "The request key was reused.");
                    return Task.FromResult<AiCreditReservation?>(new AiCreditReservation(requestId, true, existing.Status));
                }
                reservations.Add(key, new ReservationState(requestHash, creditCost));
                return Task.FromResult<AiCreditReservation?>(new AiCreditReservation(requestId));
            }
        }

        public Task<bool> CompleteAsync(
            Guid accountId,
            AiCreditReservation reservation,
            bool accepted,
            int settledCreditCost,
            CancellationToken cancellationToken)
        {
            lock (sync)
            {
                if (!reservations.TryGetValue((accountId, reservation.RequestId), out var state) || state.Status != "reserved")
                    return Task.FromResult(false);
                state.Status = accepted ? "consumed" : "released";
                state.SettledCreditCost = accepted ? settledCreditCost : 0;
                return Task.FromResult(true);
            }
        }

        public Task<bool> BindProjectAiReservationAsync(
            Guid accountId,
            AiCreditReservation reservation,
            string requestHash,
            int creditCost,
            CancellationToken cancellationToken)
        {
            lock (sync)
            {
                if (!reservations.TryGetValue((accountId, reservation.RequestId), out var state)
                    || state.Status != "reserved"
                    || state.RequestHash != requestHash
                    || state.CreditCost != creditCost)
                    return Task.FromResult(false);
                state.BoundForDispatch = true;
                return Task.FromResult(true);
            }
        }

        public Task<bool> MarkProjectAiPreviewReadyAsync(
            Guid accountId,
            AiCreditReservation reservation,
            string requestHash,
            int creditCost,
            int settledCreditCost,
            CancellationToken cancellationToken)
        {
            lock (sync)
            {
                if (!reservations.TryGetValue((accountId, reservation.RequestId), out var state)
                    || (state.Status != "reserved" && !(state.Status == "consumed" && state.ProjectAiPreview))
                    || state.RequestHash != requestHash
                    || state.CreditCost != creditCost
                    || state.SettledCreditCost is not null
                    || !state.BoundForDispatch)
                    return Task.FromResult(false);
                state.Status = "consumed";
                state.SettledCreditCost = settledCreditCost;
                state.ProjectAiPreview = true;
                return Task.FromResult(true);
            }
        }

        public Task<ProjectAiCreditSettlementResult> SettleProjectAiAsync(
            Guid accountId,
            string requestId,
            string settlementHash,
            bool apply,
            CancellationToken cancellationToken)
        {
            lock (sync)
            {
                if (!reservations.TryGetValue((accountId, requestId), out var state))
                    return Task.FromResult(new ProjectAiCreditSettlementResult(ProjectAiCreditSettlementStatus.NotFound));

                if (state.SettlementHash is not null)
                {
                    if (state.Status == "consumed"
                        && state.SettlementHash == settlementHash
                        && state.SettlementApplied == apply)
                        return Task.FromResult(new ProjectAiCreditSettlementResult(
                            apply ? ProjectAiCreditSettlementStatus.Applied : ProjectAiCreditSettlementStatus.Dismissed,
                            state.SettledCreditCost ?? 0));
                    return Task.FromResult(new ProjectAiCreditSettlementResult(ProjectAiCreditSettlementStatus.Conflict));
                }

                if (state.Status != "consumed" || !state.ProjectAiPreview)
                    return Task.FromResult(new ProjectAiCreditSettlementResult(ProjectAiCreditSettlementStatus.Conflict));

                state.SettlementHash = settlementHash;
                state.SettlementApplied = apply;
                return Task.FromResult(new ProjectAiCreditSettlementResult(
                    apply ? ProjectAiCreditSettlementStatus.Applied : ProjectAiCreditSettlementStatus.Dismissed,
                    state.SettledCreditCost ?? 0));
            }
        }

        public Task<AiCreditBalance> GetBalanceAsync(Guid accountId, CancellationToken cancellationToken) =>
            Task.FromResult(AiCreditBalance.Empty);

        public int ReservedFor(Guid accountId, string requestId)
        {
            lock (sync) return reservations.TryGetValue((accountId, requestId), out var value) && value.Status == "reserved" ? value.CreditCost : 0;
        }

        public int ConsumedFor(Guid accountId, string requestId)
        {
            lock (sync) return reservations.TryGetValue((accountId, requestId), out var value) && value.Status == "consumed" ? value.SettledCreditCost ?? 0 : 0;
        }

        public int ReleasedFor(Guid accountId, string requestId)
        {
            lock (sync) return reservations.TryGetValue((accountId, requestId), out var value) && value.Status == "released" ? value.CreditCost : 0;
        }

        private sealed class ReservationState(string requestHash, int creditCost)
        {
            public string RequestHash { get; } = requestHash;
            public int CreditCost { get; } = creditCost;
            public string Status { get; set; } = "reserved";
            public bool BoundForDispatch { get; set; }
            public bool ProjectAiPreview { get; set; }
            public string? SettlementHash { get; set; }
            public bool? SettlementApplied { get; set; }
            public int? SettledCreditCost { get; set; }
        }
    }
}
