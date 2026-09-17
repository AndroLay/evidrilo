# Production environment boundary

Production deployment is owner-managed and intentionally unconfigured here.
Do not add certificates, tokens, database passwords, provider payloads, or
machine-specific files. The release gate requires HTTPS, explicit origins,
managed secrets, backups, restore evidence, observability, rollback rehearsal,
and load validation.
