using System.Text.Json.Serialization;

namespace Evidrilo.Api.Health;

public sealed record HealthResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("check")] string Check,
    [property: JsonPropertyName("status")] string Status,
    [property: JsonPropertyName("requestId")] string RequestId,
    [property: JsonPropertyName("dependencies")] HealthDependencies? Dependencies = null);

public sealed record HealthDependencies(
    [property: JsonPropertyName("config")] string Config,
    [property: JsonPropertyName("auth")] string Auth,
    [property: JsonPropertyName("database")] string Database);
