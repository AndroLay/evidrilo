using System.Diagnostics;

namespace Evidrilo.Api.Common;

public sealed class RequestIdMiddleware
{
    public const string HeaderName = "X-Request-Id";
    public const string ItemKey = "Evidrilo.RequestId";

    private static readonly System.Text.RegularExpressions.Regex Pattern = new(
        "\\A[A-Za-z0-9_-]{8,128}\\z",
        System.Text.RegularExpressions.RegexOptions.Compiled | System.Text.RegularExpressions.RegexOptions.CultureInvariant);

    private readonly RequestDelegate next;

    public RequestIdMiddleware(RequestDelegate next)
    {
        this.next = next;
    }

    public async Task InvokeAsync(HttpContext context)
    {
        var incoming = context.Request.Headers[HeaderName].FirstOrDefault();
        var requestId = IsValid(incoming) ? incoming! : $"req-{Guid.NewGuid():N}";
        context.Items[ItemKey] = requestId;
        context.Response.Headers[HeaderName] = requestId;
        var started = Stopwatch.GetTimestamp();
        try
        {
            await next(context);
        }
        finally
        {
            PlatformTelemetry.RecordRequest(
                context,
                Stopwatch.GetElapsedTime(started).TotalMilliseconds);
        }
    }

    public static bool IsValid(string? value)
    {
        return value is not null && Pattern.IsMatch(value);
    }

    public static string Get(HttpContext context)
    {
        return context.Items.TryGetValue(ItemKey, out var value) && value is string requestId
            ? requestId
            : "req-unassigned";
    }
}
