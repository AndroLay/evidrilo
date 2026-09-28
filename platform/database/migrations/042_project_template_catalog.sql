-- Versioned, server-authored project-method catalog. Student project content
-- remains in student_projects and is never stored in this catalog.

create table if not exists public.project_template_versions (
    template_id text not null check (template_id ~ '^[a-z0-9]+([._-][a-z0-9]+)*$' and char_length(template_id) <= 96),
    template_version integer not null check (template_version >= 1),
    family text not null check (family in (
        'experimental_laboratory',
        'observational_survey',
        'literature_review',
        'qualitative_interview_field_study',
        'design_engineering'
    )),
    organization_id uuid not null references public.organizations(organization_id),
    content jsonb not null check (
        jsonb_typeof(content) = 'object'
        and octet_length(content::text) <= 131072
        and coalesce(jsonb_typeof(content -> 'inputFields') = 'array', false)
        and coalesce(jsonb_typeof(content -> 'steps') = 'array', false)
        and coalesce(jsonb_typeof(content -> 'examples') = 'array', false)
    ),
    state text not null default 'draft' check (state in ('draft', 'review', 'approved', 'published', 'retired')),
    author_id uuid null,
    reviewer_id uuid null,
    retired_by uuid null,
    is_current_published boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    published_at timestamptz null,
    primary key (template_id, template_version),
    check (is_current_published = (state = 'published')),
    check (state <> 'published' or published_at is not null)
);

create unique index if not exists project_template_one_current_version_idx
    on public.project_template_versions (template_id)
    where is_current_published = true;
create index if not exists project_template_public_family_idx
    on public.project_template_versions (family, template_id)
    where is_current_published = true;

create table if not exists public.project_template_review_decisions (
    review_decision_id uuid primary key default gen_random_uuid(),
    template_id text not null,
    template_version integer not null,
    reviewer_id uuid null,
    decision text not null check (decision in ('approved', 'rejected')),
    reason text not null check (char_length(btrim(reason)) between 1 and 2000),
    reviewed_example_ids text[] not null default array[]::text[],
    created_at timestamptz not null default now(),
    foreign key (template_id, template_version)
        references public.project_template_versions(template_id, template_version),
    check (cardinality(reviewed_example_ids) <= 24),
    check ((decision = 'approved' and cardinality(reviewed_example_ids) > 0)
        or (decision = 'rejected' and cardinality(reviewed_example_ids) = 0))
);

create index if not exists project_template_review_latest_idx
    on public.project_template_review_decisions (
        template_id, template_version, decision, created_at desc, review_decision_id desc
    );

create table if not exists public.project_template_lifecycle_events (
    lifecycle_event_id uuid primary key default gen_random_uuid(),
    template_id text not null,
    template_version integer not null,
    organization_id uuid null,
    actor_account_id uuid null,
    event_type text not null check (event_type in ('created', 'transitioned')),
    from_state text null check (from_state is null or from_state in ('draft', 'review', 'approved', 'published', 'retired')),
    to_state text not null check (to_state in ('draft', 'review', 'approved', 'published', 'retired')),
    reason text null check (reason is null or char_length(reason) between 1 and 2000),
    created_at timestamptz not null default now(),
    foreign key (template_id, template_version)
        references public.project_template_versions(template_id, template_version),
    check ((event_type = 'created' and from_state is null)
        or (event_type = 'transitioned' and from_state is not null)),
    check (from_state is null or from_state <> to_state)
);

create index if not exists project_template_lifecycle_version_idx
    on public.project_template_lifecycle_events (template_id, template_version, created_at);

alter table public.project_template_versions enable row level security;
alter table public.project_template_review_decisions enable row level security;
alter table public.project_template_lifecycle_events enable row level security;

drop policy if exists project_template_published_read on public.project_template_versions;
create policy project_template_published_read on public.project_template_versions
    for select to anon, authenticated
    using (state = 'published' and is_current_published = true);

revoke all on table public.project_template_versions from public, anon, authenticated;
grant select (
    template_id, template_version, family, content, state,
    is_current_published, created_at, updated_at, published_at
) on public.project_template_versions to anon, authenticated;
revoke all on table public.project_template_review_decisions from public, anon, authenticated;
revoke all on table public.project_template_lifecycle_events from public, anon, authenticated;

create or replace function public.enforce_project_template_version_integrity()
returns trigger
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    actor_claim text;
    actor_id uuid;
    actor_role text;
    latest_version integer;
    existing_family text;
    existing_organization uuid;
    approved_ids text[];
begin
    if TG_OP = 'UPDATE'
       and coalesce(current_setting('evidrilo.account_deletion', true), 'off') = 'on'
       and new.template_id is not distinct from old.template_id
       and new.template_version is not distinct from old.template_version
       and new.family is not distinct from old.family
       and new.organization_id is not distinct from old.organization_id
       and new.content is not distinct from old.content
       and new.created_at is not distinct from old.created_at
       and new.updated_at is not distinct from old.updated_at
       and new.state is not distinct from old.state
       and new.is_current_published is not distinct from old.is_current_published
       and new.published_at is not distinct from old.published_at
       and (new.author_id is not distinct from old.author_id or new.author_id is null)
       and (new.reviewer_id is not distinct from old.reviewer_id or new.reviewer_id is null)
       and (new.retired_by is not distinct from old.retired_by or new.retired_by is null) then
        return new;
    end if;

    actor_claim := nullif(current_setting('request.jwt.claim.sub', true), '');
    if actor_claim is null
       or actor_claim !~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' then
        raise exception 'project_template_actor_required' using errcode = '42501';
    end if;
    actor_id := actor_claim::uuid;

    perform pg_advisory_xact_lock(hashtextextended(new.template_id, 0));

    if not exists (select 1 from auth.users where id = actor_id) then
        raise exception 'project_template_actor_unknown' using errcode = '42501';
    end if;

    select membership.role into actor_role
      from public.organization_memberships membership
     where membership.organization_id = new.organization_id
       and membership.account_id = actor_id
       and membership.active = true;
    if actor_role is null then
        raise exception 'project_template_membership_required' using errcode = '42501';
    end if;

    if TG_OP = 'INSERT' then
        if new.state <> 'draft'
           or new.is_current_published
           or new.published_at is not null
           or new.reviewer_id is not null
           or new.retired_by is not null
           or new.author_id is distinct from actor_id
           or actor_role not in ('author', 'maintainer', 'owner') then
            raise exception 'project_template_draft_insert_denied' using errcode = '42501';
        end if;

        select template_version, family, organization_id
          into latest_version, existing_family, existing_organization
          from public.project_template_versions
         where template_id = new.template_id
         order by template_version desc
         limit 1;
        if latest_version is null then
            if new.template_version <> 1 then
                raise exception 'project_template_version_not_next' using errcode = 'check_violation';
            end if;
        elsif new.template_version <> latest_version + 1
           or new.family <> existing_family
           or new.organization_id <> existing_organization then
            raise exception 'project_template_version_scope_immutable' using errcode = 'check_violation';
        end if;
        if exists (
            select 1 from jsonb_array_elements(new.content -> 'examples') as examples(value)
             where examples.value ->> 'reviewed' = 'true'
        ) then
            raise exception 'project_template_review_metadata_server_owned' using errcode = 'check_violation';
        end if;
        return new;
    end if;

    if new.template_id is distinct from old.template_id
       or new.template_version is distinct from old.template_version
       or new.family is distinct from old.family
       or new.organization_id is distinct from old.organization_id
       or new.content is distinct from old.content
       or new.author_id is distinct from old.author_id
       or new.created_at is distinct from old.created_at then
        raise exception 'project_template_content_immutable' using errcode = '42501';
    end if;

    if new.state is not distinct from old.state then
        if new.is_current_published is distinct from old.is_current_published
           or new.published_at is distinct from old.published_at
           or new.reviewer_id is distinct from old.reviewer_id
           or new.retired_by is distinct from old.retired_by then
            raise exception 'project_template_lifecycle_fields_immutable' using errcode = '42501';
        end if;
        if new.updated_at is distinct from old.updated_at then
            raise exception 'project_template_updated_at_managed' using errcode = '42501';
        end if;
        return new;
    end if;

    if old.state = 'retired' then
        raise exception 'retired_project_template_immutable' using errcode = 'check_violation';
    end if;

    if new.state = 'review' and old.state = 'draft' then
        if actor_role not in ('author', 'maintainer', 'owner')
           or actor_role = 'author' and actor_id <> old.author_id then
            raise exception 'project_template_author_role_required' using errcode = '42501';
        end if;
    elsif new.state in ('approved', 'draft') and old.state = 'review' then
        if actor_id = old.author_id or actor_role not in ('reviewer', 'maintainer', 'owner') then
            raise exception 'project_template_independent_reviewer_required' using errcode = '42501';
        end if;
        select reviewed_example_ids into approved_ids
          from public.project_template_review_decisions
         where template_id = new.template_id
           and template_version = new.template_version
           and reviewer_id = actor_id
           and decision = case when new.state = 'approved' then 'approved' else 'rejected' end
         order by created_at desc, review_decision_id desc
         limit 1;
        if not found then
            raise exception 'project_template_review_decision_required' using errcode = 'check_violation';
        end if;
        if new.state = 'approved' and (
            cardinality(approved_ids) < 1
            or exists (
                select 1 from unnest(approved_ids) selected(example_id)
                 where not exists (
                    select 1
                      from jsonb_array_elements(new.content -> 'examples') as examples(value)
                     where examples.value ->> 'id' = selected.example_id
                 )
            )
            or exists (
                select 1 from unnest(approved_ids) selected(example_id)
                 group by selected.example_id having count(*) > 1
            )
        ) then
            raise exception 'project_template_reviewed_example_invalid' using errcode = 'check_violation';
        end if;
        if new.state = 'approved' and new.reviewer_id is distinct from actor_id then
            raise exception 'project_template_reviewer_mismatch' using errcode = 'check_violation';
        end if;
    elsif new.state = 'published' and old.state = 'approved' then
        if actor_role not in ('maintainer', 'owner')
           or new.is_current_published is distinct from true
           or new.published_at is null
           or not exists (
                select 1 from public.project_template_review_decisions decision
                 where decision.template_id = new.template_id
                   and decision.template_version = new.template_version
                   and decision.decision = 'approved'
                   and cardinality(decision.reviewed_example_ids) > 0
           ) then
            raise exception 'project_template_publish_guard_denied' using errcode = '42501';
        end if;
    elsif new.state = 'retired' and old.state in ('draft', 'review', 'approved', 'published') then
        if actor_role not in ('maintainer', 'owner')
           or new.is_current_published
           or new.retired_by is distinct from actor_id then
            raise exception 'project_template_retirement_denied' using errcode = '42501';
        end if;
    else
        raise exception 'invalid_project_template_transition' using errcode = 'check_violation';
    end if;

    if new.state = 'approved'
       and new.is_current_published then
        raise exception 'approved_project_template_not_public' using errcode = 'check_violation';
    end if;
    if new.state = 'published'
       and new.reviewer_id is null then
        raise exception 'published_project_template_review_required' using errcode = 'check_violation';
    end if;

    new.updated_at := now();
    return new;
end;
$$;
revoke all on function public.enforce_project_template_version_integrity() from public;
drop trigger if exists project_template_version_integrity on public.project_template_versions;
create trigger project_template_version_integrity
    before insert or update on public.project_template_versions
    for each row execute function public.enforce_project_template_version_integrity();

create or replace function public.prevent_project_template_version_delete()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
    raise exception 'project_template_version_append_only' using errcode = '42501';
end;
$$;
revoke all on function public.prevent_project_template_version_delete() from public;
drop trigger if exists project_template_version_append_only_guard on public.project_template_versions;
create trigger project_template_version_append_only_guard
    before delete on public.project_template_versions
    for each row execute function public.prevent_project_template_version_delete();

create or replace function public.enforce_project_template_review_decision()
returns trigger
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    actor_claim text;
    actor_id uuid;
    author_id uuid;
    template_organization_id uuid;
    reviewer_role text;
    template_content jsonb;
begin
    if TG_OP <> 'INSERT' then
        raise exception 'project_template_review_decision_append_only' using errcode = '42501';
    end if;
    actor_claim := nullif(current_setting('request.jwt.claim.sub', true), '');
    if actor_claim is null
       or actor_claim !~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' then
        raise exception 'project_template_reviewer_required' using errcode = '42501';
    end if;
    actor_id := actor_claim::uuid;
    if new.reviewer_id is distinct from actor_id then
        raise exception 'project_template_reviewer_mismatch' using errcode = '42501';
    end if;

    select template.author_id, template.organization_id, template.content
      into author_id, template_organization_id, template_content
      from public.project_template_versions template
     where template.template_id = new.template_id
       and template.template_version = new.template_version
       and template.state = 'review'
     for update;
    if not found or actor_id = author_id then
        raise exception 'project_template_independent_reviewer_required' using errcode = '42501';
    end if;
    select membership.role into reviewer_role
      from public.organization_memberships membership
     where membership.organization_id = template_organization_id
       and membership.account_id = actor_id and membership.active = true;
    if reviewer_role not in ('reviewer', 'maintainer', 'owner') then
        raise exception 'project_template_reviewer_role_required' using errcode = '42501';
    end if;

    if new.decision = 'approved' then
        if cardinality(new.reviewed_example_ids) < 1
           or exists (
                select 1 from unnest(new.reviewed_example_ids) selected(example_id)
                 where not exists (
                    select 1 from jsonb_array_elements(template_content -> 'examples') as examples(value)
                     where examples.value ->> 'id' = selected.example_id
                 )
           )
           or (select count(distinct id) from unnest(new.reviewed_example_ids) selected(id))
                <> cardinality(new.reviewed_example_ids) then
            raise exception 'project_template_reviewed_example_invalid' using errcode = 'check_violation';
        end if;
    elsif cardinality(new.reviewed_example_ids) <> 0 then
        raise exception 'project_template_rejected_review_cannot_mark_examples' using errcode = 'check_violation';
    end if;
    return new;
end;
$$;
revoke all on function public.enforce_project_template_review_decision() from public;
drop trigger if exists project_template_review_decision_guard on public.project_template_review_decisions;
create trigger project_template_review_decision_guard
    before insert on public.project_template_review_decisions
    for each row execute function public.enforce_project_template_review_decision();

create or replace function public.append_project_template_lifecycle_event()
returns trigger
language plpgsql
security definer
set search_path = public, auth, pg_temp
as $$
declare
    actor_claim text;
    actor_id uuid;
begin
    if TG_OP = 'UPDATE' and old.state is not distinct from new.state then
        return new;
    end if;
    actor_claim := nullif(current_setting('request.jwt.claim.sub', true), '');
    if actor_claim is null
       or actor_claim !~ '^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$' then
        raise exception 'project_template_actor_required' using errcode = '42501';
    end if;
    actor_id := actor_claim::uuid;
    if TG_OP = 'INSERT' then
        insert into public.project_template_lifecycle_events (
            template_id, template_version, organization_id, actor_account_id,
            event_type, from_state, to_state,
            reason
        ) values (
            new.template_id, new.template_version, new.organization_id, actor_id,
            'created', null, new.state,
            nullif(btrim(coalesce(current_setting('evidrilo.project_template_lifecycle_reason', true), '')), '')
        );
    else
        insert into public.project_template_lifecycle_events (
            template_id, template_version, organization_id, actor_account_id,
            event_type, from_state, to_state, reason
        ) values (
            new.template_id, new.template_version, new.organization_id, actor_id,
            'transitioned', old.state, new.state,
            nullif(btrim(coalesce(current_setting('evidrilo.project_template_lifecycle_reason', true), '')), '')
        );
    end if;
    return new;
end;
$$;
revoke all on function public.append_project_template_lifecycle_event() from public;
drop trigger if exists project_template_lifecycle_append on public.project_template_versions;
create trigger project_template_lifecycle_append
    after insert or update on public.project_template_versions
    for each row execute function public.append_project_template_lifecycle_event();

-- The integrity trigger writes the event in the same transaction so lifecycle
-- state and history cannot diverge; prevent mutation through a dedicated guard.
create or replace function public.prevent_project_template_lifecycle_event_rewrite()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
    if TG_OP = 'UPDATE'
       and coalesce(current_setting('evidrilo.account_deletion', true), 'off') = 'on'
       and old.actor_account_id is not null
       and new.actor_account_id is null
       and new.reason = '[ACCOUNT_DELETED]'
       and new.lifecycle_event_id is not distinct from old.lifecycle_event_id
       and new.template_id is not distinct from old.template_id
       and new.template_version is not distinct from old.template_version
       and new.organization_id is not distinct from old.organization_id
       and new.event_type is not distinct from old.event_type
       and new.from_state is not distinct from old.from_state
       and new.to_state is not distinct from old.to_state
       and new.created_at is not distinct from old.created_at then
        return new;
    end if;
    raise exception 'project_template_lifecycle_append_only' using errcode = '42501';
end;
$$;
revoke all on function public.prevent_project_template_lifecycle_event_rewrite() from public;
drop trigger if exists project_template_lifecycle_append_only_guard on public.project_template_lifecycle_events;
create trigger project_template_lifecycle_append_only_guard
    before update or delete on public.project_template_lifecycle_events
    for each row execute function public.prevent_project_template_lifecycle_event_rewrite();

create or replace function public.prevent_project_template_review_decision_rewrite()
returns trigger
language plpgsql
set search_path = public, pg_temp
as $$
begin
    if TG_OP = 'UPDATE'
       and coalesce(current_setting('evidrilo.account_deletion', true), 'off') = 'on'
       and old.reviewer_id is not null
       and new.reviewer_id is null
       and new.reason = '[ACCOUNT_DELETED]'
       and new.review_decision_id is not distinct from old.review_decision_id
       and new.template_id is not distinct from old.template_id
       and new.template_version is not distinct from old.template_version
       and new.decision is not distinct from old.decision
       and new.reviewed_example_ids is not distinct from old.reviewed_example_ids
       and new.created_at is not distinct from old.created_at then
        return new;
    end if;
    raise exception 'project_template_review_decision_append_only' using errcode = '42501';
end;
$$;
revoke all on function public.prevent_project_template_review_decision_rewrite() from public;
drop trigger if exists project_template_review_decision_append_only_guard on public.project_template_review_decisions;
create trigger project_template_review_decision_append_only_guard
    before update or delete on public.project_template_review_decisions
    for each row execute function public.prevent_project_template_review_decision_rewrite();

create or replace function public.anonymize_project_template_actors_on_account_deletion()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if new.status = 'completed'
       and old.status is distinct from new.status
       and coalesce(current_setting('evidrilo.account_deletion', true), 'off') = 'on' then
        update public.project_template_versions
           set author_id = case when author_id = new.account_id then null else author_id end,
               reviewer_id = case when reviewer_id = new.account_id then null else reviewer_id end,
               retired_by = case when retired_by = new.account_id then null else retired_by end
         where author_id = new.account_id or reviewer_id = new.account_id or retired_by = new.account_id;
        update public.project_template_review_decisions
           set reviewer_id = null, reason = '[ACCOUNT_DELETED]'
         where reviewer_id = new.account_id;
        update public.project_template_lifecycle_events
           set actor_account_id = null, reason = '[ACCOUNT_DELETED]'
         where actor_account_id = new.account_id;
    end if;
    return new;
end;
$$;
revoke all on function public.anonymize_project_template_actors_on_account_deletion() from public;
drop trigger if exists account_deletion_project_template_actors on public.account_deletion_requests;
create trigger account_deletion_project_template_actors
    after update on public.account_deletion_requests
    for each row execute function public.anonymize_project_template_actors_on_account_deletion();
