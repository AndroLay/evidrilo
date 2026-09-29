using System.Text.Json.Serialization;
using System.Text.RegularExpressions;
using Evidrilo.Api.Ai;

namespace Evidrilo.Api.ProjectAi;

public sealed record ProjectAiGeneralChatRequest(
    [property: JsonRequired, JsonPropertyName("schema")] string? Schema,
    [property: JsonRequired, JsonPropertyName("version")] string? Version,
    [property: JsonRequired, JsonPropertyName("installationId")] string? InstallationId,
    [property: JsonRequired, JsonPropertyName("locale")] string? Locale,
    [property: JsonRequired, JsonPropertyName("message")] string? Message);

public sealed record ProjectAiGeneralChatResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("mode")] string Mode,
    [property: JsonPropertyName("status")] string Status,
    [property: JsonPropertyName("answer")] string Answer,
    [property: JsonPropertyName("requestId")] string RequestId,
    [property: JsonPropertyName("creditCost")] int CreditCost);

public sealed record ProjectAiGeneralChatProviderRequest(
    Guid AccountId,
    string RequestId,
    string Message,
    string Locale);

public sealed record ProjectAiGeneralChatOutput(string Answer)
{
    [JsonIgnore]
    public AiProviderTokenUsage? Usage { get; init; }
}

public static partial class ProjectAiGeneralChatValidator
{
    public const string Schema = "evidrilo.project-ai-general-chat";
    public const string Version = "2";
    public const string Mode = "GENERAL";
    public const string PromptVersion = "project-ai-general-chat-v2.1";
    public const int MaximumMessageLength = 4_000;
    public const int MaximumAnswerLength = 8_000;

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
            || string.IsNullOrWhiteSpace(request.Message))
        {
            return "INVALID_PROJECT_AI_GENERAL_CHAT";
        }

        return null;
    }
}
