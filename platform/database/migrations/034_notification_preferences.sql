-- Evidrilo: account-scoped local notification preferences.
-- The mobile scheduler remains local and permission-aware; this table is the
-- optional authenticated-account mirror used for recovery across devices.

create table if not exists public.notification_preferences (
    account_id uuid primary key references auth.users(id) on delete cascade,
    enabled boolean not null default false,
    continue_unfinished_enabled boolean not null default false,
    review_completed_enabled boolean not null default false,
    cadence text not null default 'daily' check (cadence in ('daily', 'weekly')),
    local_hour smallint not null default 9 check (local_hour between 0 and 23),
    local_minute smallint not null default 0 check (local_minute between 0 and 59),
    revision bigint not null default 1 check (revision > 0),
    updated_at timestamptz not null default now()
);

alter table public.notification_preferences enable row level security;

create policy notification_preferences_select_own
    on public.notification_preferences
    for select using (account_id = auth.uid());

-- Preference writes go through the validated API so revision checks and audit
-- boundaries cannot be bypassed by an untrusted client role.
revoke all on public.notification_preferences from anon, authenticated;
grant select on public.notification_preferences to authenticated;

create or replace function public.purge_notification_preferences_on_account_deletion()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if new.status = 'completed'
       and old.status is distinct from new.status then
        delete from public.notification_preferences
         where account_id = new.account_id;
    end if;
    return new;
end;
$$;

drop trigger if exists account_deletion_purge_notification_preferences
    on public.account_deletion_requests;
create trigger account_deletion_purge_notification_preferences
    after update of status on public.account_deletion_requests
    for each row execute function public.purge_notification_preferences_on_account_deletion();

revoke all on function public.purge_notification_preferences_on_account_deletion() from public;
