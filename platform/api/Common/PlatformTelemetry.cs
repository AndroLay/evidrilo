using System.Diagnostics;
using System.Diagnostics.Metrics;

namespace Evidrilo.Api.Common;

public static class PlatformTelemetry
{
    public static readonly Meter Meter = new("Evidrilo.Platform", "1.0");

    public static readonly Counter<long> Requests = Meter.CreateCounter<long>(
        "evidrilo.http.requests",
        unit: "{request}",
        description: "HTTP requests completed by the API.");

    public static readonly Histogram<double> RequestDuration = Meter.CreateHistogram<double>(
        "evidrilo.http.request.duration",
        unit: "ms",
        description: "HTTP request duration in milliseconds.");

    public static void RecordRequest(HttpContext context, double durationMilliseconds)
    {
        var status = context.Response.StatusCode.ToString(System.Globalization.CultureInfo.InvariantCulture);
        var tags = new TagList { { "status", status } };
        Requests.Add(1, tags);
        RequestDuration.Record(durationMilliseconds, tags);
    }
}
