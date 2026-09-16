using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Hosting;
using Evidrilo.Worker;

var builder = Host.CreateApplicationBuilder(args);
var workerOptions = WorkerOptions.From(builder.Configuration);
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
builder.Services.AddHostedService<ProjectionWorker>();
var host = builder.Build();
await host.RunAsync();
