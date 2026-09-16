using Evidrilo.Api.Health;

namespace Evidrilo.Api.Tests;

public sealed class DatabaseReadinessTests
{
    [Fact]
    public void Unreachable_database_is_degraded()
    {
        Assert.Equal(
            "degraded",
            DatabaseSchemaReadiness.Status(
                connectionSucceeded: false,
                migrationLedgerExists: false,
                currentMigrationApplied: false));
    }

    [Fact]
    public void Connected_database_without_migration_ledger_is_missing()
    {
        Assert.Equal(
            "missing",
            DatabaseSchemaReadiness.Status(
                connectionSucceeded: true,
                migrationLedgerExists: false,
                currentMigrationApplied: false));
    }

    [Fact]
    public void Connected_database_with_an_older_schema_is_degraded()
    {
        Assert.Equal(
            "degraded",
            DatabaseSchemaReadiness.Status(
                connectionSucceeded: true,
                migrationLedgerExists: true,
                currentMigrationApplied: false));
    }

    [Fact]
    public void Current_migration_is_required_for_ready_status()
    {
        Assert.Equal(
            "ready",
            DatabaseSchemaReadiness.Status(
                connectionSucceeded: true,
                migrationLedgerExists: true,
                currentMigrationApplied: true));
        Assert.Equal("030_sync_published_case_boundary", DatabaseSchemaReadiness.CurrentMigrationVersion);
    }
}
