-- Evidrilo P2, provision the lifecycle profile for every new Auth account.
-- Keep the deletion-ledger fallback for legacy rows that predate this trigger.

create or replace function public.ensure_account_profile()
returns trigger
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
begin
    insert into public.account_profiles (account_id)
    values (new.id)
    on conflict (account_id) do nothing;
    return new;
end;
$$;

revoke all on function public.ensure_account_profile() from public;

drop trigger if exists auth_users_create_account_profile on auth.users;
create trigger auth_users_create_account_profile
    after insert on auth.users
    for each row execute function public.ensure_account_profile();
