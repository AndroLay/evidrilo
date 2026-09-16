using System.Text.Json;

namespace Evidrilo.Api.Common;

/// <summary>
/// Reads JSON inside the endpoint so malformed bodies use the same public error
/// contract as syntactically valid but semantically invalid payloads.
/// </summary>
public static class RequestJsonReader
{
    public static async Task<JsonElement> ReadAsync(
        HttpRequest request,
        string errorCode,
        string errorMessage,
        CancellationToken cancellationToken)
    {
        try
        {
            using var document = await JsonDocument.ParseAsync(
                request.Body,
                new JsonDocumentOptions
                {
                    AllowTrailingCommas = false,
                    CommentHandling = JsonCommentHandling.Disallow,
                    MaxDepth = 64,
                },
                cancellationToken);
            return document.RootElement.Clone();
        }
        catch (JsonException exception)
        {
            throw new ApiException(
                StatusCodes.Status400BadRequest,
                errorCode,
                errorMessage,
                exception);
        }
    }
}
