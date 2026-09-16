-- Evidrilo billing, prevent an older provider event from rolling back access.
-- The provider event ledger remains append-only; the entitlement projection
-- accepts only an event at least as recent as the current source event.

alter table public.entitlements
    add column if not exists source_occurred_at timestamptz;

update public.entitlements
   set source_occurred_at = updated_at
 where source_occurred_at is null;

alter table public.entitlements
    alter column source_occurred_at set not null;

