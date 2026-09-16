-- Local-only Supabase Auth compatibility shim.
-- Never apply this file to a managed or production database.

create extension if not exists pgcrypto;
create schema if not exists auth;

create table if not exists auth.users (
    id uuid primary key,
    email_confirmed_at timestamptz null
);

create or replace function auth.uid()
returns uuid
language sql
stable
as $$
    select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid
$$;

do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'authenticated') then
        create role authenticated nologin;
    end if;
    if not exists (select 1 from pg_roles where rolname = 'anon') then
        create role anon nologin;
    end if;
    if not exists (select 1 from pg_roles where rolname = 'service_role') then
        create role service_role nologin;
    end if;
    if not exists (select 1 from pg_roles where rolname = 'supabase_auth_admin') then
        create role supabase_auth_admin nologin;
    end if;
end
$$;

grant usage on schema auth to authenticated;
grant select on auth.users to authenticated;
grant execute on function auth.uid() to authenticated;
