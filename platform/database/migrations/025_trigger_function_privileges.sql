-- Evidrilo security hardening, keep trigger-only SECURITY DEFINER functions
-- inaccessible to public client roles.
-- The trigger executor can still invoke these functions; direct client calls
-- must not receive an execution privilege by PostgreSQL's PUBLIC default.

alter function public.append_sync_change() set search_path = public, pg_temp;
alter function public.enqueue_analytics_projection() set search_path = public, pg_temp;

revoke all on function public.append_sync_change() from public;
revoke all on function public.enqueue_analytics_projection() from public;
