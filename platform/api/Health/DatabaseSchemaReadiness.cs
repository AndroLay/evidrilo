namespace Evidrilo.Api.Health;

/// <summary>
/// The migration version the API was compiled against. Keep this in lockstep
/// with the highest numbered SQL migration and update it in the same change.
/// </summary>
public static class DatabaseSchemaReadiness
{
    public const string CurrentMigrationVersion = "049_project_ai_activity_history";

    public static string Status(
        bool connectionSucceeded,
        bool migrationLedgerExists,
        bool currentMigrationApplied) =>
        !connectionSucceeded
            ? "degraded"
            : !migrationLedgerExists
                ? "missing"
                : currentMigrationApplied
                    ? "ready"
                    : "degraded";
}
