-- Evidrilo security boundary: recommendation interactions also pass through
-- the validated API. Keep this as a forward-only follow-up to migration 022.

drop policy if exists recommendation_events_insert_own on public.recommendation_events;

revoke insert on table public.recommendation_events from anon, authenticated;
