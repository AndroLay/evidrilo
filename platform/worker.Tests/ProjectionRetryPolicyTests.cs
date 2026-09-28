using Microsoft.Extensions.Configuration;
using Npgsql;
using Evidrilo.Worker;
using Xunit;

namespace Evidrilo.Worker.Tests;

public sealed class ProjectionRetryPolicyTests
{
    [Fact]
    public void Retry_delay_is_bounded_and_monotonic()
    {
        Assert.Equal(TimeSpan.FromSeconds(5), ProjectionRetryPolicy.DelayForAttempt(0));
        Assert.Equal(TimeSpan.FromSeconds(5), ProjectionRetryPolicy.DelayForAttempt(1));
        Assert.Equal(TimeSpan.FromSeconds(10), ProjectionRetryPolicy.DelayForAttempt(2));
        Assert.Equal(TimeSpan.FromMinutes(5), ProjectionRetryPolicy.DelayForAttempt(100));
    }

    [Fact]
    public void Worker_options_default_without_database_and_reject_invalid_bounds()
    {
        var configuration = new ConfigurationBuilder().Build();
        var options = WorkerOptions.From(configuration);

        Assert.False(options.DatabaseConfigured);
        Assert.Equal(5, options.PollInterval.TotalSeconds);
        Assert.Equal(10, options.BatchSize);

        var invalid = new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["WORKER_BATCH_SIZE"] = "101",
            })
            .Build();
        Assert.Throws<WorkerConfigurationException>(() => WorkerOptions.From(invalid));
    }

    [Fact]
    public void Worker_normalizes_a_standard_postgresql_uri()
    {
        var configuration = new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["DATABASE_URL"] = "postgresql://student:pass%40word@db.example.invalid:5432/evidrilo?sslmode=require",
            })
            .Build();

        var options = WorkerOptions.From(configuration, "Development");
        var parsed = new NpgsqlConnectionStringBuilder(options.DatabaseConnectionString);

        Assert.Equal("db.example.invalid", parsed.Host);
        Assert.Equal("pass@word", parsed.Password);
        Assert.Equal(SslMode.Require, parsed.SslMode);
    }

    [Fact]
    public void Deployed_worker_defaults_postgresql_uri_to_encrypted_transport()
    {
        var configuration = new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["DATABASE_URL"] = "postgresql://student:synthetic-password@db.example.invalid:5432/evidrilo",
                ["SUPABASE_AUTH_ADMIN_ENABLED"] = "true",
                ["SUPABASE_URL"] = "https://project.supabase.co",
                ["SUPABASE_SERVICE_ROLE_KEY"] = "synthetic-server-only-key",
            })
            .Build();

        var options = WorkerOptions.From(configuration, "Production");
        var parsed = new NpgsqlConnectionStringBuilder(options.DatabaseConnectionString);

        Assert.Equal(SslMode.Require, parsed.SslMode);
    }

    [Fact]
    public void Deployed_worker_rejects_database_transport_that_can_be_plaintext()
    {
        var configuration = new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["DATABASE_URL"] = "Host=db.example.invalid;Database=evidrilo;Username=student;SSL Mode=Prefer",
                ["SUPABASE_AUTH_ADMIN_ENABLED"] = "true",
                ["SUPABASE_URL"] = "https://project.supabase.co",
                ["SUPABASE_SERVICE_ROLE_KEY"] = "synthetic-server-only-key",
            })
            .Build();

        var exception = Assert.Throws<WorkerConfigurationException>(
            () => WorkerOptions.From(configuration, "Production"));

        Assert.DoesNotContain("student", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void Deployed_worker_rejects_connection_string_without_explicit_username()
    {
        var configuration = new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["DATABASE_URL"] = "Host=db.example.invalid;Database=evidrilo;SSL Mode=Require",
                ["SUPABASE_AUTH_ADMIN_ENABLED"] = "true",
                ["SUPABASE_URL"] = "https://project.supabase.co",
                ["SUPABASE_SERVICE_ROLE_KEY"] = "synthetic-server-only-key",
            })
            .Build();

        var exception = Assert.Throws<WorkerConfigurationException>(
            () => WorkerOptions.From(configuration, "Production"));

        Assert.DoesNotContain("db.example.invalid", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void Production_worker_requires_database_and_enabled_auth_deletion_provider()
    {
        var empty = new ConfigurationBuilder().Build();

        Assert.Throws<WorkerConfigurationException>(() => WorkerOptions.From(empty, "Production"));
        Assert.False(WorkerOptions.From(empty, "Development").AccountDeletionEnabled);

        var missingCredential = new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["DATABASE_URL"] = "Host=database.invalid;Database=evidrilo;Username=student;SSL Mode=Require",
                ["SUPABASE_AUTH_ADMIN_ENABLED"] = "true",
                ["SUPABASE_URL"] = "https://project.supabase.co",
            })
            .Build();
        Assert.Throws<WorkerConfigurationException>(() => WorkerOptions.From(missingCredential, "Production"));

        var complete = new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["DATABASE_URL"] = "Host=database.invalid;Database=evidrilo;Username=student;SSL Mode=Require",
                ["SUPABASE_AUTH_ADMIN_ENABLED"] = "true",
                ["SUPABASE_URL"] = "https://project.supabase.co",
                ["SUPABASE_SERVICE_ROLE_KEY"] = "synthetic-server-only-key",
            })
            .Build();
        Assert.True(WorkerOptions.From(complete, "Production").AccountDeletionEnabled);
    }

    [Fact]
    public void Account_auth_deletion_retry_delay_is_bounded()
    {
        Assert.Equal(TimeSpan.FromSeconds(5), AccountAuthDeletionRetryPolicy.DelayForAttempt(1));
        Assert.Equal(TimeSpan.FromSeconds(10), AccountAuthDeletionRetryPolicy.DelayForAttempt(2));
        Assert.Equal(TimeSpan.FromMinutes(15), AccountAuthDeletionRetryPolicy.DelayForAttempt(100));
    }

    [Theory]
    [InlineData("http://project.supabase.co")]
    [InlineData("https://project.supabase.co/auth")]
    [InlineData("https://user:password@project.supabase.co")]
    [InlineData("https://project.supabase.co/?tenant=other")]
    public void Enabled_auth_admin_rejects_non_origin_or_non_https_urls(string url)
    {
        var configuration = new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["SUPABASE_AUTH_ADMIN_ENABLED"] = "true",
                ["SUPABASE_URL"] = url,
                ["SUPABASE_SERVICE_ROLE_KEY"] = "synthetic-server-only-key",
            })
            .Build();

        var exception = Assert.Throws<WorkerConfigurationException>(
            () => SupabaseAuthAdminOptions.From(configuration, "Production"));

        Assert.DoesNotContain("password", exception.Message, StringComparison.OrdinalIgnoreCase);
        Assert.DoesNotContain("synthetic-server-only-key", exception.Message, StringComparison.Ordinal);
    }

    [Fact]
    public void Enabled_auth_admin_rejects_whitespace_in_server_key()
    {
        var configuration = new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["SUPABASE_AUTH_ADMIN_ENABLED"] = "true",
                ["SUPABASE_URL"] = "https://project.supabase.co",
                ["SUPABASE_SERVICE_ROLE_KEY"] = "synthetic key",
            })
            .Build();

        Assert.Throws<WorkerConfigurationException>(
            () => SupabaseAuthAdminOptions.From(configuration, "Production"));
    }
}
