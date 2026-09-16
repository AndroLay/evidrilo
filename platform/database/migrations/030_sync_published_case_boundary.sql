-- Evidrilo sync defense-in-depth: attempt commands may reference only
-- published case versions. The API performs the user-facing classification;
-- this trigger protects the database if another server path is introduced.

create or replace function public.require_published_attempt_case_version()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if not exists (
        select 1
        from public.case_versions
        where case_version_id = new.case_version_id
          and status = 'published'
    ) then
        raise exception using
            errcode = '23514',
            constraint = 'attempt_commands_case_version_published',
            message = 'Attempt commands require a published case version.';
    end if;
    return new;
end;
$$;

drop trigger if exists attempt_commands_require_published_case on public.attempt_commands;
create trigger attempt_commands_require_published_case
    before insert on public.attempt_commands
    for each row execute function public.require_published_attempt_case_version();

revoke all on function public.require_published_attempt_case_version() from public;
alter function public.require_published_attempt_case_version()
    set search_path = public, pg_temp;
