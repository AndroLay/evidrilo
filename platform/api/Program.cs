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
using Evidrilo.Api.Notifications;
using Evidrilo.Api.ProjectAi;
using Evidrilo.Api.ProjectTemplates;
using Evidrilo.Api.Projects;
using Evidrilo.Api.Recommendations;
using Evidrilo.Api.Sync;
using Microsoft.AspNetCore.HttpOverrides;

var builder = WebApplication.CreateBuilder(args);
builder.WebHost.ConfigureKestrel(options =>
{
    // Route validators bound the semantic payload; this bounds the raw JSON
    // body before JsonElement materializes it in memory.
    options.Limits.MaxRequestBodySize = 4 * 1024 * 1024;
});
var platformOptions = PlatformOptions.From(builder.Configuration, builder.Environment.EnvironmentName);
if (builder.Configuration.GetValue<bool>("REVENUECAT_TEST_STORE_RECONCILIATION_ENABLED"))
{
    var sdkKey = builder.Configuration["REVENUECAT_TEST_STORE_SDK_KEY"];
    if (!builder.Environment.IsStaging() || !platformOptions.DatabaseConfigured || platformOptions.BillingConfigured ||
        sdkKey is null || !sdkKey.StartsWith("test_", StringComparison.Ordinal))
        throw new InvalidOperationException("Test Store reconciliation requires Staging, a database, a Test Store SDK key, and no simultaneous webhook ingestion.");
    builder.Services.AddSingleton<IRevenueCatSandboxReconciler>(services =>
        new RevenueCatSandboxReconciler(new HttpClient(new HttpClientHandler { AllowAutoRedirect = false })
            { Timeout = TimeSpan.FromSeconds(10) }, sdkKey, services.GetRequiredService<IBillingStore>(),
            services.GetRequiredService<ILogger<RevenueCatSandboxReconciler>>()));
}
else builder.Services.AddSingleton<IRevenueCatSandboxReconciler, DisabledRevenueCatSandboxReconciler>();
var aiProviderOptions = AiProviderOptions.From(builder.Configuration);
var projectAiProviderOptions = ProjectAiProviderOptions.From(builder.Configuration, aiProviderOptions);
if (aiProviderOptions.Enabled && !platformOptions.DatabaseConfigured)
{
    throw new PlatformConfigurationException("An enabled AI provider requires a configured provider-spend database.");
}
if (projectAiProviderOptions.Enabled && !platformOptions.DatabaseConfigured)
{
    throw new PlatformConfigurationException("An enabled project-AI provider requires a configured provider-spend database.");
}

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
// These fixed-window policies use process-local state. Add a shared edge or
// distributed limiter before running multiple API instances.
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
    options.AddPolicy("ai", context =>
    {
        var key = context.User.FindFirst("sub")?.Value
            ?? context.Connection.RemoteIpAddress?.ToString()
            ?? "anonymous";
        return RateLimitPartition.GetFixedWindowLimiter(
            $"ai:{key}",
            _ => new FixedWindowRateLimiterOptions
            {
                PermitLimit = 20,
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
    options.AddPolicy("account-export-create", context =>
    {
        var key = context.User.FindFirst("sub")?.Value
            ?? context.Connection.RemoteIpAddress?.ToString()
            ?? "anonymous";
        return RateLimitPartition.GetFixedWindowLimiter(
            $"account-export-create:{key}",
            _ => new FixedWindowRateLimiterOptions
            {
                PermitLimit = 2,
                Window = TimeSpan.FromHours(1),
                QueueLimit = 0,
                AutoReplenishment = true,
            });
    });
    options.AddPolicy("account-export-status", context =>
    {
        var key = context.User.FindFirst("sub")?.Value
            ?? context.Connection.RemoteIpAddress?.ToString()
            ?? "anonymous";
        return RateLimitPartition.GetFixedWindowLimiter(
            $"account-export-status:{key}",
            _ => new FixedWindowRateLimiterOptions
            {
                PermitLimit = 30,
                Window = TimeSpan.FromMinutes(1),
                QueueLimit = 0,
                AutoReplenishment = true,
            });
    });
    options.AddPolicy("account-export-download", context =>
    {
        var key = context.User.FindFirst("sub")?.Value
            ?? context.Connection.RemoteIpAddress?.ToString()
            ?? "anonymous";
        return RateLimitPartition.GetFixedWindowLimiter(
            $"account-export-download:{key}",
            _ => new FixedWindowRateLimiterOptions
            {
                PermitLimit = 5,
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
builder.Services.AddSingleton(aiProviderOptions);
builder.Services.AddSingleton(projectAiProviderOptions);
if (aiProviderOptions.Enabled)
{
    builder.Services.AddSingleton<HttpClient>(_ => new HttpClient(new SocketsHttpHandler
    {
        AllowAutoRedirect = false,
        ConnectTimeout = TimeSpan.FromSeconds(3),
        PooledConnectionLifetime = TimeSpan.FromMinutes(5),
    })
    {
        Timeout = Timeout.InfiniteTimeSpan,
    });
    builder.Services.AddSingleton<IAiProviderSpendBudgetStore>(_ =>
        new NpgsqlAiProviderSpendBudgetStore(platformOptions.DatabaseConnectionString!));
    builder.Services.AddSingleton<IAiProvider, OpenAiResponsesProvider>();
}
else
{
    builder.Services.AddSingleton<IAiProvider, DisabledAiProvider>();
}
if (platformOptions.DatabaseConfigured)
{
    builder.Services.AddSingleton<IAiCreditLedger>(_ =>
        new NpgsqlAiCreditLedger(platformOptions.DatabaseConnectionString!));
    builder.Services.AddSingleton<IAiConversationStore>(_ =>
        new NpgsqlAiConversationStore(platformOptions.DatabaseConnectionString!));
}
else
{
    builder.Services.AddSingleton<IAiCreditLedger, DatabaseUnavailableAiCreditLedger>();
    builder.Services.AddSingleton<IAiConversationStore, DatabaseUnavailableAiConversationStore>();
}
builder.Services.AddSingleton<AiGateway>();
builder.Services.AddSingleton<AiConversationGateway>();
// Project AI has separate default-off activation, privacy, and per-user consent gates.
if (projectAiProviderOptions.Enabled)
{
    builder.Services.AddSingleton<OpenAiProjectAiGenerator>();
    builder.Services.AddSingleton<IProjectAiScaffoldGenerator>(services =>
        services.GetRequiredService<OpenAiProjectAiGenerator>());
    builder.Services.AddSingleton<IProjectAiStageAssistGenerator>(services =>
        services.GetRequiredService<OpenAiProjectAiGenerator>());
    builder.Services.AddSingleton<IProjectAiGeneralChatGenerator>(services =>
        services.GetRequiredService<OpenAiProjectAiGenerator>());
}
else
{
    builder.Services.AddSingleton<IProjectAiScaffoldGenerator, DisabledProjectAiScaffoldGenerator>();
    builder.Services.AddSingleton<IProjectAiStageAssistGenerator, DisabledProjectAiStageAssistGenerator>();
    builder.Services.AddSingleton<IProjectAiGeneralChatGenerator, DisabledProjectAiGeneralChatGenerator>();
}
if (platformOptions.DatabaseConfigured)
{
    builder.Services.AddSingleton<IProjectAiConsentStore>(_ =>
        new NpgsqlProjectAiConsentStore(platformOptions.DatabaseConnectionString!));
    builder.Services.AddSingleton<IProjectAiActivityStore>(_ =>
        new NpgsqlProjectAiActivityStore(platformOptions.DatabaseConnectionString!));
    builder.Services.AddSingleton<IProjectAiLocalProjectContextStore>(_ =>
        new NpgsqlProjectAiLocalProjectContextStore(platformOptions.DatabaseConnectionString!));
    builder.Services.AddSingleton<IStudentProjectStore>(_ =>
        new NpgsqlStudentProjectStore(platformOptions.DatabaseConnectionString!));
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
    builder.Services.AddSingleton<IAccountExportStore>(_ =>
        new NpgsqlAccountExportStore(platformOptions.DatabaseConnectionString!));
    builder.Services.AddSingleton<IAccountExportJobStore>(_ =>
        new NpgsqlAccountExportJobStore(platformOptions.DatabaseConnectionString!));
    builder.Services.AddSingleton<IMembershipStore>(_ =>
        new NpgsqlMembershipStore(platformOptions.DatabaseConnectionString!));
    builder.Services.AddSingleton<INotificationPreferencesStore>(_ =>
        new NpgsqlNotificationPreferencesStore(platformOptions.DatabaseConnectionString!));
    builder.Services.AddSingleton<IProjectTemplateStore>(_ =>
        new NpgsqlProjectTemplateStore(platformOptions.DatabaseConnectionString!));
}
else
{
    builder.Services.AddSingleton<IProjectAiConsentStore, DatabaseUnavailableProjectAiConsentStore>();
    builder.Services.AddSingleton<IProjectAiActivityStore, DatabaseUnavailableProjectAiActivityStore>();
    builder.Services.AddSingleton<IProjectAiLocalProjectContextStore, DatabaseUnavailableProjectAiLocalProjectContextStore>();
    builder.Services.AddSingleton<IStudentProjectStore, DatabaseUnavailableStudentProjectStore>();
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
    builder.Services.AddSingleton<IAccountExportStore, DatabaseUnavailableAccountExportStore>();
    builder.Services.AddSingleton<IAccountExportJobStore, DatabaseUnavailableAccountExportJobStore>();
    builder.Services.AddSingleton<IMembershipStore, DatabaseUnavailableMembershipStore>();
    builder.Services.AddSingleton<INotificationPreferencesStore, DatabaseUnavailableNotificationPreferencesStore>();
    builder.Services.AddSingleton<IProjectTemplateStore, DatabaseUnavailableProjectTemplateStore>();
}
builder.Services.AddSingleton<BillingWebhookService>();

var app = builder.Build();
app.UseExceptionHandler();
app.UseMiddleware<RequestIdMiddleware>();
app.Use(async (context, next) =>
{
    if (context.Request.Path.StartsWithSegments("/v2/account/exports"))
    {
        context.Response.Headers.CacheControl = "no-store";
        context.Response.Headers.Pragma = "no-cache";
        context.Response.Headers.Expires = "0";
    }
    await next();
});
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
app.MapAccountExportJobEndpoints();
app.MapSyncEndpoints();
app.MapAnalyticsEndpoints();
app.MapProgressEndpoints();
app.MapCaseEndpoints();
app.MapEvidenceGraphEndpoints();
app.MapCaseAuthoringEndpoints();
app.MapRecommendationEndpoints();
app.MapRecommendationInteractionEndpoints();
app.MapCohortEndpoints();
app.MapBillingEndpoints();
app.MapEntitlementEndpoints();
app.MapAiEndpoints();
app.MapMembershipEndpoints();
app.MapNotificationEndpoints();
app.MapStudentProjectEndpoints();
app.MapProjectTemplateEndpoints();
app.MapProjectAiConsentEndpoints();
app.MapProjectAiLocalProjectContextEndpoints();
app.MapProjectAiScaffoldEndpoints();
app.MapProjectAiStageAssistEndpoints();
app.MapProjectAiGeneralChatEndpoints();
app.MapProjectAiActivityEndpoints();
app.MapProjectAiStageAssistSettlementEndpoints();

app.Run();

public partial class Program
{
}
