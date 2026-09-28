-- Evidrilo database integrity hardening.
-- Keep the AI ledger account-scoped at the relational boundary and prevent a
-- newly created or newly published case from bypassing the minimum metadata
-- required by the published-case API. Existing legacy published rows remain
-- readable and can still be anonymized during account deletion.

alter table public.ai_credit_grants
    add constraint ai_credit_grants_account_grant_unique
    unique (account_id, grant_id);

alter table public.ai_credit_reservations
    add constraint ai_credit_reservations_account_grant_fk
    foreign key (account_id, grant_id)
    references public.ai_credit_grants (account_id, grant_id)
    on delete cascade;

create or replace function public.enforce_published_case_metadata()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
    -- Do not rewrite legacy published rows that predate the authoring
    -- metadata columns. Normal publication of a new row or a transition into
    -- published must satisfy the complete server-side content envelope.
    if new.status = 'published'
       and (TG_OP = 'INSERT' or old.status is distinct from new.status)
       and coalesce(current_setting('evidrilo.account_deletion', true), 'off') <> 'on'
       and (
           new.published_at is null
           or new.organization_id is null
           or new.objective is null
           or new.difficulty is null
           or new.evidence_references is null
           or new.content is null
           or jsonb_typeof(new.content) <> 'object'
       ) then
        raise exception 'published_case_metadata_required'
            using errcode = 'check_violation';
    end if;

    return new;
end;
$$;

revoke all on function public.enforce_published_case_metadata() from public;

drop trigger if exists case_versions_published_metadata_guard on public.case_versions;
create trigger case_versions_published_metadata_guard
    before insert or update on public.case_versions
    for each row execute function public.enforce_published_case_metadata();
