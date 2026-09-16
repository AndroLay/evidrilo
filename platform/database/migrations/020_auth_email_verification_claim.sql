-- Evidrilo auth boundary, derive email verification from Auth-owned state.
-- Enable this function as Supabase Auth's Custom Access Token Hook.

create or replace function public.custom_access_token_hook(event jsonb)
returns jsonb
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    claims jsonb := coalesce(event -> 'claims', '{}'::jsonb);
    user_id uuid;
    email_verified boolean := false;
begin
    user_id := nullif(event ->> 'user_id', '')::uuid;

    select email_confirmed_at is not null
      into email_verified
      from auth.users
     where id = user_id;

    claims := jsonb_set(
        claims,
        '{email_verified}',
        to_jsonb(coalesce(email_verified, false)),
        true
    );
    return jsonb_build_object('claims', claims);
end;
$$;

revoke execute on function public.custom_access_token_hook(jsonb) from public;
revoke execute on function public.custom_access_token_hook(jsonb) from anon;
revoke execute on function public.custom_access_token_hook(jsonb) from authenticated;
grant usage on schema public to supabase_auth_admin;
grant execute on function public.custom_access_token_hook(jsonb) to supabase_auth_admin;
