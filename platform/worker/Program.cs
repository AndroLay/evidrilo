using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Evidrilo.Worker;

var builder = Host.CreateApplicationBuilder(args);
var workerOptions = WorkerOptions.From(builder.Configuration, builder.Environment.EnvironmentName);
builder.Services.AddSingleton(workerOptions);
if (workerOptions.DatabaseConfigured)
{
    builder.Services.AddSingleton<IProjectionJobStore>(_ =>
        new NpgsqlProjectionJobStore(workerOptions.DatabaseConnectionString!));
}
else
{
    builder.Services.AddSingleton<IProjectionJobStore, EmptyProjectionJobStore>();
}

if (workerOptions.AccountDeletionEnabled)
{
    builder.Services.AddSingleton(workerOptions.SupabaseAuthAdmin);
    builder.Services.AddSingleton<IAccountAuthDeletionOutboxStore>(_ =>
        new NpgsqlAccountAuthDeletionOutboxStore(workerOptions.DatabaseConnectionString!));
    builder.Services.AddSingleton<HttpClient>(_ => SupabaseAuthAdminHttpClientFactory.Create());
    builder.Services.AddSingleton<ISupabaseAuthAdminClient, SupabaseAuthAdminClient>();
    builder.Services.AddHostedService<AccountAuthDeletionWorker>();
}

builder.Services.AddHostedService<ProjectionWorker>();
var host = builder.Build();
await host.RunAsync();
