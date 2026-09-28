-- Keep short-lived AI conversation state account-scoped and metadata-only.
-- Raw prompts, messages, and provider responses are deliberately not stored.

create table if not exists public.ai_conversation_sessions (
    session_id uuid primary key default gen_random_uuid(),
    account_id uuid not null references auth.users(id) on delete cascade,
    creation_request_id text not null check (length(creation_request_id) between 8 and 128),
    case_version_id text not null check (length(case_version_id) between 1 and 128),
    context_fingerprint text not null check (context_fingerprint ~ '^[a-f0-9]{64}$'),
    turn_count smallint not null default 0 check (turn_count between 0 and 5),
    active_request_id text null,
    active_request_hash text null,
    active_turn_index smallint null,
    active_lease_expires_at timestamptz null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    expires_at timestamptz not null check (expires_at > created_at),
    unique (account_id, session_id),
    unique (account_id, creation_request_id),
    constraint ai_conversation_active_turn_consistency check (
        (active_request_id is null and active_request_hash is null
            and active_turn_index is null and active_lease_expires_at is null)
        or (active_request_id is not null and active_request_hash is not null
            and active_request_hash ~ '^[a-f0-9]{64}$'
            and active_turn_index between 1 and 5 and active_lease_expires_at is not null)
    )
);

create table if not exists public.ai_conversation_turn_requests (
    account_id uuid not null,
    session_id uuid not null,
    request_id text not null check (length(request_id) between 8 and 128),
    request_hash text not null check (request_hash ~ '^[a-f0-9]{64}$'),
    turn_index smallint not null check (turn_index between 1 and 5),
    status text not null check (status in ('reserved', 'completed', 'released')),
    started_at timestamptz not null default now(),
    lease_expires_at timestamptz not null,
    completed_at timestamptz null,
    primary key (account_id, request_id),
    foreign key (account_id, session_id)
        references public.ai_conversation_sessions (account_id, session_id)
        on delete cascade,
    constraint ai_conversation_turn_settlement_consistency check (
        (status = 'reserved' and completed_at is null)
        or (status in ('completed', 'released') and completed_at is not null)
    )
);

create index if not exists ai_conversation_sessions_expiry_idx
    on public.ai_conversation_sessions (account_id, expires_at);
create index if not exists ai_conversation_turn_requests_session_idx
    on public.ai_conversation_turn_requests (account_id, session_id, turn_index);
create index if not exists ai_conversation_turn_requests_lease_idx
    on public.ai_conversation_turn_requests (lease_expires_at)
    where status = 'reserved';

alter table public.ai_conversation_sessions enable row level security;
alter table public.ai_conversation_turn_requests enable row level security;

create policy ai_conversation_sessions_select_own
    on public.ai_conversation_sessions
    for select using (account_id = auth.uid());
create policy ai_conversation_turn_requests_select_own
    on public.ai_conversation_turn_requests
    for select using (account_id = auth.uid());

-- Session lifecycle writes go only through the authenticated API boundary.
revoke all on public.ai_conversation_sessions from anon, authenticated;
revoke all on public.ai_conversation_turn_requests from anon, authenticated;
grant select on public.ai_conversation_sessions, public.ai_conversation_turn_requests to authenticated;

create or replace function public.purge_ai_conversations_on_account_deletion()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
begin
    if new.status = 'completed'
       and old.status is distinct from new.status then
        delete from public.ai_conversation_sessions
         where account_id = new.account_id;
    end if;
    return new;
end;
$$;

drop trigger if exists account_deletion_purge_ai_conversations
    on public.account_deletion_requests;
create trigger account_deletion_purge_ai_conversations
    after update of status on public.account_deletion_requests
    for each row execute function public.purge_ai_conversations_on_account_deletion();

revoke all on function public.purge_ai_conversations_on_account_deletion() from public;
