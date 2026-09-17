# Public operations boundary

This directory contains only public-safe operational contracts. It must not
contain credentials, private provider payloads, participant data, reviewer
records, internal audit evidence, or environment-specific secrets.

The repository currently provides local Docker Compose preparation, migration
ordering, health checks, and release handoff documentation. Staging and
production environments remain owner-managed gates: they require approved
secrets, HTTPS/proxy configuration, backups, restore evidence, monitoring,
rollback rehearsal, and load validation.

Keep private execution packets and provider observations outside the public
repository. The public package exporter and GitHub safety checker enforce this
boundary.
