using Evidrilo.Api.Common;
using Evidrilo.Api.Configuration;
using Evidrilo.Api.Sync;

namespace Evidrilo.Api.Health;

public static class HealthEndpoints
{
    public static IEndpointRouteBuilder MapHealthEndpoints(this IEndpointRouteBuilder endpoints)
    {
        endpoints.MapGet("/health/live", (HttpContext context) =>
        {
            return Results.Ok(new HealthResponse(
                "evidrilo.health",
                "1",
                "live",
                "ok",
                RequestIdMiddleware.Get(context)));
        });

        endpoints.MapGet(
            "/health/ready",
            async (HttpContext context, PlatformOptions options, ISyncStore syncStore, CancellationToken cancellationToken) =>
        {
            var readiness = options.Readiness;
            var database = await syncStore.CheckReadinessAsync(cancellationToken);
            var status = readiness.Config == "ready"
                && readiness.Auth == "ready"
                && database == "ready"
                ? "ready"
                : "degraded";
            return Results.Ok(new HealthResponse(
                "evidrilo.health",
                "1",
                "ready",
                status,
                RequestIdMiddleware.Get(context),
                new HealthDependencies(readiness.Config, readiness.Auth, database)));
        });

        return endpoints;
    }
}
