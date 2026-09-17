# Monitoring boundary

The repository exposes application health contracts and local logging hooks,
but no managed monitoring provider is activated. A production monitoring
implementation must define owner, retention, alert routing, privacy limits,
and recovery tests before it is enabled.
