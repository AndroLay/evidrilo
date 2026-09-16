using System.Text.Json;
using System.Text.Json.Serialization;
using System.Threading.RateLimiting;
using Evidrilo.Api.Account;
using Evidrilo.Api.Ai;
using Evidrilo.Api.Analytics;
using Evidrilo.Api.Auth;
using Evidrilo.Api.Billing;
using Evidrilo.Api.Common;
using Evidrilo.Api.Content;
using Evidrilo.Api.Configuration;
using Evidrilo.Api.Health;
using Evidrilo.Api.Authorization;
using Evidrilo.Api.Recommendations;
using Evidrilo.Api.Sync;
using Microsoft.AspNetCore.HttpOverrides;

var builder = WebApplication.CreateBuilder(args);
builder.WebHost.ConfigureKestrel(options =>
{
    // Route validators bound the semantic payload; this bounds the raw JSON
    // body before JsonElement materializes it in memory.
    options.Limits.MaxRequestBodySize = 1 * 1024 * 1024;
});
var platformOptions = PlatformOptions.From(builder.Configuration, builder.Environment.EnvironmentName);

builder.Services.AddSingleton(platformOptions);
builder.Services.AddExceptionHandler<ApiExceptionHandler>();
builder.Services.AddProblemDetails();
builder.Services.ConfigureHttpJsonOptions(options =>
{
    options.SerializerOptions.PropertyNamingPolicy = JsonNamingPolicy.CamelCase;
    options.SerializerOptions.DefaultIgnoreCondition = JsonIgnoreCondition.WhenWritingNull;
    options.SerializerOptions.Converters.Add(new JsonStringEnumConverter(JsonNamingPolicy.SnakeCaseLower));
});
builder.Services.AddCors(options =>
{
    options.AddDefaultPolicy(policy => policy
        .WithOrigins(platformOptions.CorsAllowedOrigins.ToArray())
        .AllowAnyHeader()
        .AllowAnyMethod());
});
builder.Services.AddRateLimiter(options =>
{
    options.RejectionStatusCode = StatusCodes.Status429TooManyRequests;
    options.OnRejected = async (context, cancellationToken) =>
    {
        await ApiErrors.WriteAsync(
            context.HttpContext,
            StatusCodes.Status429TooManyRequests,
            "RATE_LIMITED",
            "Too many requests. Please try again later.",
            cancellationToken);
    };
    options.AddPolicy("api", context =>
    {
        var key = context.User.FindFirst("sub")?.Value
            ?? context.Connection.RemoteIpAddress?.ToString()
            ?? "anonymous";
        return RateLimitPartition.GetFixedWindowLimiter(
            key,
            _ => new FixedWindowRateLimiterOptions
            {
                PermitLimit = 60,
                Window = TimeSpan.FromMinutes(1),
                QueueLimit = 0,
                AutoReplenishment = true,
            });
    });
    options.AddPolicy("billing-webhook", context =>
    {
        // Do not use forwarded headers for this key unless the host has
        // explicitly configured trusted proxies below.
        var key = context.Connection.RemoteIpAddress?.ToString() ?? "unknown";
        return RateLimitPartition.GetFixedWindowLimiter(
            $"billing:{key}",
            _ => new FixedWindowRateLimiterOptions
            {
                PermitLimit = 120,
                Window = TimeSpan.FromMinutes(1),
                QueueLimit = 0,
                AutoReplenishment = true,
            });
    });
});
if (platformOptions.TrustedProxyAddresses.Count > 0)
{
    builder.Services.Configure<ForwardedHeadersOptions>(options =>
    {
        options.ForwardedHeaders = ForwardedHeaders.XForwardedFor | ForwardedHeaders.XForwardedProto;
        options.KnownIPNetworks.Clear();
        options.KnownProxies.Clear();
        foreach (var address in platformOptions.TrustedProxyAddresses)
        {
            options.KnownProxies.Add(address);
        }
    });
}
builder.Services.AddSupabaseAuthentication(platformOptions);
builder.Services.AddSingleton<IAiProvider, DisabledAiProvider>();
builder.Services.AddSingleton<IAiQuota, InMemoryAiQuota>();
builder.Services.AddSingleton<AiGateway>();
if (platformOptions.DatabaseConfigured)
{
    builder.Services.AddSingleton<ISyncStore>(_ =>
        new NpgsqlSyncStore(platformOptions.DatabaseConnectionString!));
    builder.Services.AddSingleton<IAnalyticsStore>(_ =>
        new NpgsqlAnalyticsStore(platformOptions.DatabaseConnectionString!));
    builder.Services.AddSingleton<IProgressStore>(_ =>
        new NpgsqlProgressStore(platformOptions.DatabaseConnectionString!));
    builder.Services.AddSingleton<ICaseStore>(_ =>
        new NpgsqlCaseStore(platformOptions.DatabaseConnectionString!));
    builder.Services.AddSingleton<ICaseAuthoringStore>(_ =>
        new NpgsqlCaseAuthoringStore(platformOptions.DatabaseConnectionString!));
    builder.Services.AddSingleton<ICaseLifecycleAuditStore>(_ =>
        new NpgsqlCaseLifecycleAuditStore(platformOptions.DatabaseConnectionString!));
    builder.Services.AddSingleton<IRecommendationStore>(_ =>
        new NpgsqlRecommendationStore(platformOptions.DatabaseConnectionString!));
    builder.Services.AddSingleton<IRecommendationInteractionStore>(_ =>
        new NpgsqlRecommendationInteractionStore(platformOptions.DatabaseConnectionString!));
    builder.Services.AddSingleton<ICohortStore>(_ =>
        new NpgsqlCohortStore(platformOptions.DatabaseConnectionString!));
    builder.Services.AddSingleton<IAiAuditStore>(_ =>
        new NpgsqlAiAuditStore(platformOptions.DatabaseConnectionString!));
    builder.Services.AddSingleton<IBillingStore>(_ =>
        new NpgsqlBillingStore(platformOptions.DatabaseConnectionString!));
    builder.Services.AddSingleton<IEntitlementStore>(_ =>
        new NpgsqlEntitlementStore(platformOptions.DatabaseConnectionString!));
    builder.Services.AddSingleton<IAccountLifecycleStore>(_ =>
        new NpgsqlAccountLifecycleStore(platformOptions.DatabaseConnectionString!));
    builder.Services.AddSingleton<IMembershipStore>(_ =>
        new NpgsqlMembershipStore(platformOptions.DatabaseConnectionString!));
}
else
{
    builder.Services.AddSingleton<ISyncStore, DatabaseUnavailableSyncStore>();
    builder.Services.AddSingleton<IAnalyticsStore, DatabaseUnavailableAnalyticsStore>();
    builder.Services.AddSingleton<IProgressStore, DatabaseUnavailableProgressStore>();
    builder.Services.AddSingleton<ICaseStore, DatabaseUnavailableCaseStore>();
    builder.Services.AddSingleton<ICaseAuthoringStore, DatabaseUnavailableCaseAuthoringStore>();
    builder.Services.AddSingleton<ICaseLifecycleAuditStore, DatabaseUnavailableCaseLifecycleAuditStore>();
    builder.Services.AddSingleton<IRecommendationStore, DatabaseUnavailableRecommendationStore>();
    builder.Services.AddSingleton<IRecommendationInteractionStore, DatabaseUnavailableRecommendationInteractionStore>();
    builder.Services.AddSingleton<ICohortStore, DatabaseUnavailableCohortStore>();
    builder.Services.AddSingleton<IAiAuditStore, DatabaseUnavailableAiAuditStore>();
    builder.Services.AddSingleton<IBillingStore, DatabaseUnavailableBillingStore>();
    builder.Services.AddSingleton<IEntitlementStore, DatabaseUnavailableEntitlementStore>();
    builder.Services.AddSingleton<IAccountLifecycleStore, DatabaseUnavailableAccountLifecycleStore>();
    builder.Services.AddSingleton<IMembershipStore, DatabaseUnavailableMembershipStore>();
}
builder.Services.AddSingleton<BillingWebhookService>();

var app = builder.Build();
app.UseExceptionHandler();
app.UseMiddleware<RequestIdMiddleware>();
if (platformOptions.TrustedProxyAddresses.Count > 0)
{
    app.UseForwardedHeaders();
}
app.UseRouting();
app.UseCors();
app.UseAuthentication();
// The API policy partitions on the verified subject, so authentication must
// populate HttpContext.User before the limiter resolves its partition.
app.UseRateLimiter();
app.UseAuthorization();
if (platformOptions.DatabaseConfigured)
{
    app.UseMiddleware<AccountLifecycleAccessMiddleware>();
}

app.MapHealthEndpoints();
app.MapAccountEndpoints();
app.MapSyncEndpoints();
app.MapAnalyticsEndpoints();
app.MapProgressEndpoints();
app.MapCaseEndpoints();
app.MapCaseAuthoringEndpoints();
app.MapRecommendationEndpoints();
app.MapRecommendationInteractionEndpoints();
app.MapCohortEndpoints();
app.MapBillingEndpoints();
app.MapEntitlementEndpoints();
app.MapAiEndpoints();
app.MapMembershipEndpoints();

app.Run();

public partial class Program
{
}
