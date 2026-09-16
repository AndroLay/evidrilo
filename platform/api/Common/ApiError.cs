using System.Net;
using System.Text.Json.Serialization;
using Microsoft.AspNetCore.Diagnostics;

namespace Evidrilo.Api.Common;

public sealed record ApiErrorResponse(
    [property: JsonPropertyName("schema")] string Schema,
    [property: JsonPropertyName("version")] string Version,
    [property: JsonPropertyName("code")] string Code,
    [property: JsonPropertyName("message")] string Message,
    [property: JsonPropertyName("requestId")] string RequestId);

public sealed class ApiException : Exception
{
    public ApiException(int statusCode, string code, string message)
        : base(message)
    {
        StatusCode = statusCode;
        Code = code;
        SafeMessage = message;
    }

    public ApiException(int statusCode, string code, string message, Exception innerException)
        : base(message, innerException)
    {
        StatusCode = statusCode;
        Code = code;
        SafeMessage = message;
    }

    public int StatusCode { get; }

    public string Code { get; }

    public string SafeMessage { get; }
}

public static class ApiErrors
{
    public static ApiErrorResponse Create(HttpContext context, string code, string message)
    {
        return new ApiErrorResponse(
            "evidrilo.http-error",
            "1",
            code,
            message,
            RequestIdMiddleware.Get(context));
    }

    public static Task WriteAsync(
        HttpContext context,
        int statusCode,
        string code,
        string message,
        CancellationToken cancellationToken = default)
    {
        context.Response.StatusCode = statusCode;
        context.Response.ContentType = "application/json";
        return context.Response.WriteAsJsonAsync(Create(context, code, message), cancellationToken);
    }
}

public sealed class ApiExceptionHandler : IExceptionHandler
{
    private readonly ILogger<ApiExceptionHandler> logger;

    public ApiExceptionHandler(ILogger<ApiExceptionHandler> logger)
    {
        this.logger = logger;
    }

    public async ValueTask<bool> TryHandleAsync(
        HttpContext httpContext,
        Exception exception,
        CancellationToken cancellationToken)
    {
        if (httpContext.Response.HasStarted) return false;

        var (statusCode, code, message) = exception switch
        {
            ApiException apiException => (apiException.StatusCode, apiException.Code, apiException.SafeMessage),
            _ => ((int)HttpStatusCode.InternalServerError, "INTERNAL_ERROR", "An unexpected error occurred."),
        };

        if (statusCode >= 500)
        {
            logger.LogError(
                "Unhandled API exception type {ExceptionType} with request ID {RequestId}",
                exception.GetType().Name,
                RequestIdMiddleware.Get(httpContext));
        }

        await ApiErrors.WriteAsync(httpContext, statusCode, code, message, cancellationToken);
        return true;
    }
}
