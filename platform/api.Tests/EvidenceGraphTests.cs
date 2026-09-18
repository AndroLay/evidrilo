using System.Text.Json;
using Evidrilo.Api.Content;

namespace Evidrilo.Api.Tests;

public sealed class EvidenceGraphTests
{
    [Fact]
    public void Published_case_projection_emits_requirement_evidence_claim_and_verification_edges()
    {
        var graph = EvidenceGraphProjection.Build(ValidCase());

        Assert.Equal("evidrilo.evidence-graph", graph.Schema);
        Assert.Equal("1", graph.Version);
        Assert.Equal("case-1:v1", graph.CaseVersionId);
        Assert.Equal(5, graph.Nodes.Count);
        Assert.Equal(5, graph.Edges.Count);
        Assert.Contains(
            new EvidenceGraphNode(
                "requirement:case-1:v1",
                "requirement",
                "objective",
                "Connect evidence to a bounded claim",
                "required"),
            graph.Nodes);
        Assert.Contains(
            new EvidenceGraphNode(
                "evidence:fact-observation",
                "evidence",
                "observation",
                "The tablet was cold.",
                "observed"),
            graph.Nodes);
        Assert.Contains(
            new EvidenceGraphNode(
                "claim:rule-1",
                "claim",
                "rule",
                "rule-1",
                "asserted"),
            graph.Nodes);
        Assert.Contains(
            new EvidenceGraphNode(
                "verification:rule-1",
                "verification",
                "outcome",
                "PASS",
                "verified"),
            graph.Nodes);
        Assert.Contains(
            new EvidenceGraphEdge(
                "evidence:fact-observation",
                "claim:rule-1",
                "supports"),
            graph.Edges);
        Assert.Contains(
            new EvidenceGraphEdge(
                "claim:rule-1",
                "verification:rule-1",
                "verified_by"),
            graph.Edges);
    }

    [Fact]
    public void Published_case_projection_preserves_non_pass_verification_state()
    {
        var graph = EvidenceGraphProjection.Build(ValidCase() with
        {
            Content = ValidCase().Content with
            {
                Rules = [new AuthoringRule("rule-1", "ACTION_REQUIRED", ["fact-observation"])],
            },
        });

        var verification = Assert.Single(graph.Nodes, node => node.Id == "verification:rule-1");

        Assert.Equal("ACTION_REQUIRED", verification.Text);
        Assert.Equal("action_required", verification.State);
    }

    [Fact]
    public void Limitation_anchors_are_not_emitted_as_supporting_evidence()
    {
        var graph = EvidenceGraphProjection.Build(ValidCase() with
        {
            Content = ValidCase().Content with
            {
                Rules = [new AuthoringRule("rule-1", "PASS", ["fact-limitation"])],
            },
        });

        Assert.Contains(
            new EvidenceGraphEdge(
                "evidence:fact-limitation",
                "claim:rule-1",
                "limits"),
            graph.Edges);
        Assert.DoesNotContain(
            new EvidenceGraphEdge(
                "evidence:fact-limitation",
                "claim:rule-1",
                "supports"),
            graph.Edges);
    }

    [Fact]
    public void Unresolved_rule_anchors_have_opaque_nodes_for_every_edge_endpoint()
    {
        const string unresolvedAnchor = "private-anchor";
        var publishedCase = ValidCase() with
        {
            Content = ValidCase().Content with
            {
                Rules = [new AuthoringRule("rule-1", "PASS", [unresolvedAnchor])],
            },
        };

        var graph = EvidenceGraphProjection.Build(publishedCase);
        var nodeIds = graph.Nodes.Select(node => node.Id).ToHashSet(StringComparer.Ordinal);

        Assert.All(
            graph.Edges,
            edge =>
            {
                Assert.Contains(edge.From, nodeIds);
                Assert.Contains(edge.To, nodeIds);
            });

        var unresolved = Assert.Single(graph.Nodes, node => node.State == "unresolved");
        Assert.Equal("evidence:unresolved:18329716184aec4b24a450458dcd32eba3c53be0f69d5789c014991388702797", unresolved.Id);
        Assert.Equal("evidence", unresolved.Kind);
        Assert.Equal("anchor", unresolved.Label);
        Assert.Equal("Unresolved evidence anchor", unresolved.Text);
        Assert.DoesNotContain(unresolvedAnchor, JsonSerializer.Serialize(graph), StringComparison.Ordinal);
    }

    private static PublishedCaseSummary ValidCase() => new(
        "case-1",
        "case-1:v1",
        "A bounded case",
        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
        "evaluator.v1",
        ["evidence"],
        new PublishedCaseContent(
            "Connect evidence to a bounded claim",
            2,
            ["fact-observation"],
            [
                new AuthoringFact("fact-observation", "observation", "The tablet was cold."),
                new AuthoringFact("fact-limitation", "limitation", "Temperature was not controlled."),
            ],
            [new AuthoringRule("rule-1", "PASS", ["fact-observation"])],
            [new AuthoringVariant("challenge-1", ["fact-observation"])])
    );
}
