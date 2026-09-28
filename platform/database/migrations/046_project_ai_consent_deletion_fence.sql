-- Serialize Project AI consent grants with account deletion completion.
-- GrantOwnAsync takes the same account-scoped advisory lock before checking
-- deletion tombstones, so a grant cannot recreate consent after the purge.

create or replace function public.lock_project_ai_consent_on_account_deletion()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if new.status = 'completed'
       and old.status is distinct from new.status then
        perform pg_advisory_xact_lock(hashtextextended(new.account_id::text, 0));
    end if;
    return new;
end;
$$;

revoke all on function public.lock_project_ai_consent_on_account_deletion() from public;
drop trigger if exists account_deletion_lock_project_ai_consent
    on public.account_deletion_requests;
create trigger account_deletion_lock_project_ai_consent
    before update of status on public.account_deletion_requests
    for each row execute function public.lock_project_ai_consent_on_account_deletion();
