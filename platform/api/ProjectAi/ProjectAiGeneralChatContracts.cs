using System.Text.Json.Serialization;
using System.Text.RegularExpressions;
using Evidrilo.Api.Ai;

namespace Evidrilo.Api.ProjectAi;

public sealed record ProjectAiGeneralChatRequest(
    [property: JsonRequired, JsonPropertyName("schema")] string? Schema,
    [property: JsonRequired, JsonPropertyName("version")] string? Version,
    [property: JsonRequired, JsonPropertyName("installationId")] string? InstallationId,
    [property: JsonRequired, JsonPropertyName("locale")] string? Locale,
    [property: JsonRequired, JsonPropertyName("message")] string? Message)
{
    public IReadOnlyList<ProjectAiChatTurn>? History { get; init; }
    public ProjectAiChatContext? ProjectContext { get; init; }
}

public sealed record ProjectAiChatTurn(string Role, string Text);
public sealed record ProjectAiChatField(string Id, string Label, string Value);
// Explicit client-selected local material; never proof of server project ownership.
public sealed record ProjectAiChatContext(string ProjectId, int Revision, string Title,
    string Notes, IReadOnlyList<ProjectAiChatField> Fields);
public sealed record ProjectAiChatEdit(string FieldId, string Value, string Reason);

public sealed record ProjectAiGeneralChatResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("mode")] string Mode,
    [property: JsonPropertyName("status")] string Status,
    [property: JsonPropertyName("answer")] string Answer,
    [property: JsonPropertyName("recommendedNextPrompts")] IReadOnlyList<string> RecommendedNextPrompts,
    [property: JsonPropertyName("requestId")] string RequestId,
    [property: JsonPropertyName("creditCost")] int CreditCost)
{
    [JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    public IReadOnlyList<ProjectAiChatEdit>? ProposedEdits { get; init; }
}

public sealed record ProjectAiGeneralChatProviderRequest(
    Guid AccountId,
    string RequestId,
    string Message,
    string Locale)
{
    public IReadOnlyList<ProjectAiChatTurn>? History { get; init; }
    public ProjectAiChatContext? ProjectContext { get; init; }
}

public sealed record ProjectAiGeneralChatOutput(string Answer, IReadOnlyList<string> RecommendedNextPrompts)
{
    [JsonIgnore]
    public AiProviderTokenUsage? Usage { get; init; }
    public IReadOnlyList<ProjectAiChatEdit> ProposedEdits { get; init; } = [];
}

public static partial class ProjectAiGeneralChatValidator
{
    public const string Schema = "evidrilo.project-ai-general-chat";
    public const string Version = "2";
    public const string Mode = "GENERAL";
    public const string PromptVersion = "project-ai-general-chat-v2.3";
    public const int MaximumMessageLength = 4_000;
    public const int MaximumAnswerLength = 8_000;
    public const int MinimumRecommendedNextPrompts = 1;
    public const int MaximumRecommendedNextPrompts = 3;
    public const int MaximumRecommendedNextPromptLength = 240;

    [GeneratedRegex("\\A[A-Za-z]{2,8}(?:-[A-Za-z0-9]{1,8})*\\z", RegexOptions.CultureInvariant)]
    private static partial Regex LocalePattern();

    public static string? ValidateRequest(ProjectAiGeneralChatRequest? request)
    {
        if (request is null
            || !string.Equals(request.Schema, Schema, StringComparison.Ordinal)
            || !string.Equals(request.Version, Version, StringComparison.Ordinal)
            || !Guid.TryParseExact(request.InstallationId, "D", out var installationId)
            || installationId == Guid.Empty
            || request.Locale is null
            || request.Locale.Length > 32
            || !LocalePattern().IsMatch(request.Locale)
            || request.Message is null
            || request.Message.Length is < 1 or > MaximumMessageLength
            || string.IsNullOrWhiteSpace(request.Message)
            || request.Message.Contains('\0')
            || request.History is { } history && (history.Count > 8 || history.Any(turn =>
                turn is null || turn.Role is not ("user" or "assistant") ||
                string.IsNullOrWhiteSpace(turn.Text) || turn.Text.Length > 8000 || turn.Text.Contains('\0'))
                || history.Sum(turn => turn.Text.Length) > 16000)
            || request.ProjectContext is { } project && !ValidContext(project))
        {
            return "INVALID_PROJECT_AI_GENERAL_CHAT";
        }

        return null;
    }

    private static bool ValidContext(ProjectAiChatContext project) =>
        Guid.TryParseExact(project.ProjectId, "D", out var id) && id != Guid.Empty && project.Revision >= 1 &&
        !string.IsNullOrWhiteSpace(project.Title) && project.Title.Length <= 160 &&
        project.Notes is not null && project.Notes.Length <= 6000 && !project.Notes.Contains('\0') &&
        project.Fields is { Count: <= 16 } && project.Fields.All(field => field is not null &&
            !string.IsNullOrWhiteSpace(field.Id) && field.Id.Length <= 96 &&
            !string.IsNullOrWhiteSpace(field.Label) && field.Label.Length <= 160 &&
            field.Value is not null && field.Value.Length <= 8000 && !field.Value.Contains('\0')) &&
        project.Fields.Select(field => field.Id).Distinct().Count() == project.Fields.Count &&
        project.Fields.Sum(field => field.Value.Length) <= 16000;

    public static bool ValidEdits(IReadOnlyList<ProjectAiChatEdit>? edits, ProjectAiChatContext? project) =>
        edits is { Count: <= 6 } && (edits.Count == 0 || project is not null) &&
        edits.All(edit => edit is not null && project!.Fields.Any(field => field.Id == edit.FieldId) &&
            !string.IsNullOrWhiteSpace(edit.Value) && edit.Value.Length <= 8000 && !edit.Value.Contains('\0') &&
            !string.IsNullOrWhiteSpace(edit.Reason) && edit.Reason.Length <= 400) &&
        edits.Select(edit => edit.FieldId).Distinct().Count() == edits.Count;
}
