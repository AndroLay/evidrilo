using System.Security.Cryptography;
using System.Text;
using System.Text.Json.Serialization;

namespace Evidrilo.Api.Content;

public sealed record EvidenceGraph(
    string Schema,
    string Version,
    string CaseVersionId,
    string ContentHash,
    IReadOnlyList<EvidenceGraphNode> Nodes,
    IReadOnlyList<EvidenceGraphEdge> Edges);

public sealed record EvidenceGraphNode(
    [property: JsonPropertyName("id")] string Id,
    [property: JsonPropertyName("kind")] string Kind,
    [property: JsonPropertyName("label")] string Label,
    [property: JsonPropertyName("text")] string Text,
    [property: JsonPropertyName("state")] string State);

public sealed record EvidenceGraphEdge(
    [property: JsonPropertyName("from")] string From,
    [property: JsonPropertyName("to")] string To,
    [property: JsonPropertyName("relation")] string Relation);

public sealed record EvidenceGraphResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("caseVersionId")] string CaseVersionId,
    [property: JsonPropertyName("contentHash")] string ContentHash,
    [property: JsonPropertyName("nodes")] IReadOnlyList<EvidenceGraphNode> Nodes,
    [property: JsonPropertyName("edges")] IReadOnlyList<EvidenceGraphEdge> Edges,
    [property: JsonPropertyName("requestId")] string RequestId);

public static class EvidenceGraphProjection
{
    public static EvidenceGraph Build(PublishedCaseSummary publishedCase)
    {
        ArgumentNullException.ThrowIfNull(publishedCase);

        var nodes = new List<EvidenceGraphNode>
        {
            new(
                $"requirement:{publishedCase.CaseVersionId}",
                "requirement",
                "objective",
                publishedCase.Content.Objective,
                "required"),
        };
        var edges = new List<EvidenceGraphEdge>();
        var evidenceIdSet = new HashSet<string>(StringComparer.Ordinal);
        var evidenceIds = new List<string>();
        var evidenceNodeIds = new Dictionary<string, string>(StringComparer.Ordinal);
        var factsById = new Dictionary<string, AuthoringFact>(StringComparer.Ordinal);

        foreach (var fact in publishedCase.Content.Facts)
        {
            if (!evidenceIdSet.Add(fact.Id)) continue;
            factsById.Add(fact.Id, fact);
            evidenceIds.Add(fact.Id);
            evidenceNodeIds.Add(fact.Id, $"evidence:{fact.Id}");
            nodes.Add(new EvidenceGraphNode(
                $"evidence:{fact.Id}",
                "evidence",
                fact.Type,
                fact.Text,
                EvidenceState(fact.Type)));
        }

        foreach (var reference in publishedCase.Content.EvidenceReferences)
        {
            if (evidenceIdSet.Add(reference))
            {
                evidenceIds.Add(reference);
                evidenceNodeIds.Add(reference, $"evidence:{reference}");
                nodes.Add(new EvidenceGraphNode(
                    $"evidence:{reference}",
                    "evidence",
                    "reference",
                    reference,
                    "unresolved"));
            }
        }

        foreach (var evidenceId in evidenceIds)
        {
            edges.Add(new EvidenceGraphEdge(
                $"requirement:{publishedCase.CaseVersionId}",
                $"evidence:{evidenceId}",
                "requires"));
        }

        foreach (var rule in publishedCase.Content.Rules)
        {
            var claimId = $"claim:{rule.Id}";
            var verificationId = $"verification:{rule.Id}";
            nodes.Add(new EvidenceGraphNode(claimId, "claim", "rule", rule.Id, "asserted"));
            nodes.Add(new EvidenceGraphNode(
                verificationId,
                "verification",
                "outcome",
                rule.Outcome,
                VerificationState(rule.Outcome)));
            edges.Add(new EvidenceGraphEdge(
                $"requirement:{publishedCase.CaseVersionId}",
                claimId,
                "tests"));

            foreach (var anchorId in rule.AnchorIds)
            {
                if (!evidenceNodeIds.TryGetValue(anchorId, out var evidenceNodeId))
                {
                    evidenceNodeId = UnresolvedEvidenceNodeId(anchorId);
                    evidenceNodeIds.Add(anchorId, evidenceNodeId);
                    nodes.Add(new EvidenceGraphNode(
                        evidenceNodeId,
                        "evidence",
                        "anchor",
                        "Unresolved evidence anchor",
                        "unresolved"));
                }

                var relation = factsById.TryGetValue(anchorId, out var fact)
                    && fact.Type is "limitation" or "boundary"
                    ? "limits"
                    : "supports";
                edges.Add(new EvidenceGraphEdge(
                    evidenceNodeId,
                    claimId,
                    relation));
            }

            edges.Add(new EvidenceGraphEdge(claimId, verificationId, "verified_by"));
        }

        return new EvidenceGraph(
            "evidrilo.evidence-graph",
            "1",
            publishedCase.CaseVersionId,
            publishedCase.ContentHash,
            nodes,
            edges);
    }

    private static string EvidenceState(string type) => type switch
    {
        "observation" => "observed",
        "limitation" => "limiting",
        "boundary" => "boundary",
        "aim" => "aim",
        "context" => "context",
        _ => "unresolved",
    };

    private static string VerificationState(string outcome) => outcome switch
    {
        "PASS" => "verified",
        "ACTION_REQUIRED" => "action_required",
        "CANNOT_ASSESS" => "cannot_assess",
        "INCOMPLETE" => "incomplete",
        _ => "unverified",
    };

    private static string UnresolvedEvidenceNodeId(string anchorId) =>
        $"evidence:unresolved:{Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(anchorId))).ToLowerInvariant()}";
}
