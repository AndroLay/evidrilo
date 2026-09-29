-- D-119 metadata-only Project AI activity. Prompt and response bodies are not
-- stored. Activity is per installation; cross-device sync is not enabled.

create table if not exists public.project_ai_activity (
    activity_id uuid primary key default gen_random_uuid(),
    account_id uuid not null references auth.users(id) on delete cascade,
    installation_id uuid not null,
    request_id text not null check (char_length(request_id) between 8 and 128),
    mode text not null check (mode in ('PROJECT', 'GENERAL')),
    project_id uuid null,
    stage_id text null check (
        stage_id is null or (
            char_length(stage_id) between 1 and 80
            and stage_id ~ '^[a-z0-9]+([._-][a-z0-9]+)*$'
        )
    ),
    operation_id text null check (
        operation_id is null or (
            char_length(operation_id) between 1 and 80
            and operation_id ~ '^[a-z0-9]+([._-][a-z0-9]+)*$'
        )
    ),
    base_project_revision integer null check (base_project_revision is null or base_project_revision >= 1),
    consent_generation integer null check (consent_generation is null or consent_generation >= 1),
    result_project_revision integer null check (result_project_revision is null or result_project_revision >= 1),
    outcome text not null check (outcome in ('PENDING', 'APPLIED', 'EDITED', 'DISMISSED', 'STALE', 'FAILED')),
    requested_settlement_outcome text null check (
        requested_settlement_outcome is null
        or requested_settlement_outcome in ('APPLIED', 'EDITED', 'DISMISSED', 'STALE')
    ),
    settlement_hash text null check (settlement_hash is null or settlement_hash ~ '^[a-f0-9]{64}$'),
    created_at timestamptz not null default clock_timestamp(),
    updated_at timestamptz not null default clock_timestamp(),
    unique (account_id, request_id),
    foreign key (account_id, project_id)
        references public.student_projects (account_id, project_id)
        on delete cascade,
    constraint project_ai_activity_context_check check (
        (
            mode = 'PROJECT'
            and project_id is not null
            and stage_id is not null
            and operation_id is not null
            and base_project_revision is not null
            and consent_generation is not null
            and (
                (outcome in ('APPLIED', 'EDITED')
                    and result_project_revision is not null
                    and result_project_revision > base_project_revision)
                or (outcome not in ('APPLIED', 'EDITED') and result_project_revision is null)
            )
            and (
                (outcome in ('APPLIED', 'EDITED', 'DISMISSED')
                    and requested_settlement_outcome = outcome and settlement_hash is not null)
                or (outcome in ('PENDING', 'FAILED')
                    and requested_settlement_outcome is null and settlement_hash is null)
                or (outcome = 'STALE'
                    and ((requested_settlement_outcome is null and settlement_hash is null)
                        or (requested_settlement_outcome is not null and settlement_hash is not null))
            )
        )
        )
        or (
            mode = 'GENERAL'
            and project_id is null
            and stage_id is null
            and operation_id is null
            and base_project_revision is null
            and consent_generation is null
            and result_project_revision is null
            and (
                (outcome = 'DISMISSED'
                    and requested_settlement_outcome = outcome and settlement_hash is not null)
                or (outcome in ('PENDING', 'FAILED')
                    and requested_settlement_outcome is null and settlement_hash is null)
                or (outcome = 'STALE'
                    and ((requested_settlement_outcome is null and settlement_hash is null)
                        or (requested_settlement_outcome is not null and settlement_hash is not null))
            )
        )
    )
));

create index if not exists project_ai_activity_installation_history_idx
    on public.project_ai_activity (account_id, installation_id, created_at desc, activity_id desc);
create index if not exists project_ai_activity_project_history_idx
    on public.project_ai_activity (account_id, project_id, created_at desc, activity_id desc)
    where project_id is not null;

alter table public.project_ai_activity enable row level security;
drop policy if exists project_ai_activity_owner_read on public.project_ai_activity;
create policy project_ai_activity_owner_read on public.project_ai_activity
    for select to authenticated using (account_id = auth.uid());
revoke all on public.project_ai_activity from public, anon, authenticated;
grant select on public.project_ai_activity to authenticated;

-- The 039 project deletion path cascades project-linked activity. General-mode
-- metadata has no project FK, so account completion clears every mode here.
create or replace function public.purge_project_ai_activity_on_account_deletion()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if new.status = 'completed'
       and old.status is distinct from new.status then
        perform set_config('evidrilo.account_deletion', 'on', true);
        delete from public.project_ai_activity where account_id = new.account_id;
    end if;
    return new;
end;
$$;

revoke all on function public.purge_project_ai_activity_on_account_deletion() from public;
drop trigger if exists account_deletion_purge_project_ai_activity
    on public.account_deletion_requests;
create trigger account_deletion_purge_project_ai_activity
    after update of status on public.account_deletion_requests
    for each row execute function public.purge_project_ai_activity_on_account_deletion();
