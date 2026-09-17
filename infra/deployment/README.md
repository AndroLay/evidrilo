# Deployment boundary

Deployment artifacts are intentionally separated from local Docker preparation.
The checked-in configuration supports reproducible local validation only. Add a
provider-specific deployment implementation only with an approved target,
secret-handling design, migration/rollback plan, and observable verification.
