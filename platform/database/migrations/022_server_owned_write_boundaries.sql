-- Evidrilo security boundary: client roles must use the validated API for
-- analytics and sync mutations. Keep this forward-only so an already applied
-- migration is never rewritten.

drop policy if exists analytics_events_insert_own on public.analytics_events;
drop policy if exists attempt_commands_insert_own on public.attempt_commands;

revoke insert on table public.analytics_events from anon, authenticated;
revoke insert on table public.attempt_commands from anon, authenticated;
