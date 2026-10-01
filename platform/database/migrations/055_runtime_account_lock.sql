-- Preserve account-deletion fencing without granting runtime roles Auth table access.
create or replace function public.lock_runtime_account(p_account_id uuid)
returns boolean
language plpgsql
security definer
set search_path = pg_catalog
as $$
begin
    perform id from auth.users where id = p_account_id for update;
    return found;
end;
$$;
revoke all on function public.lock_runtime_account(uuid) from public, anon, authenticated, service_role;
