-- Reserve and account for bounded AI provider spend before making a request.
-- This stores token/cost metadata only; prompts and provider responses are not persisted.

create table if not exists public.ai_provider_monthly_spend (
    provider text not null check (provider in ('openai')),
    period_start date not null check (extract(day from period_start) = 1),
    spend_limit_usd numeric(14, 6) not null check (spend_limit_usd > 0),
    reserved_usd numeric(14, 6) not null default 0 check (reserved_usd >= 0),
    spent_usd numeric(14, 6) not null default 0 check (spent_usd >= 0),
    updated_at timestamptz not null default now(),
    primary key (provider, period_start)
);

create table if not exists public.ai_provider_spend_reservations (
    provider text not null,
    period_start date not null,
    account_id uuid not null references auth.users(id) on delete cascade,
    request_id text not null check (char_length(request_id) between 8 and 128),
    model text not null check (char_length(model) between 1 and 128),
    reserved_cost_usd numeric(14, 6) not null check (reserved_cost_usd > 0),
    actual_cost_usd numeric(14, 6) null check (actual_cost_usd is null or actual_cost_usd >= 0),
    input_tokens integer null check (input_tokens is null or input_tokens >= 0),
    output_tokens integer null check (output_tokens is null or output_tokens >= 0),
    status text not null check (status in ('reserved', 'settled', 'uncertain', 'released')),
    lease_expires_at timestamptz not null,
    started_at timestamptz not null default now(),
    settled_at timestamptz null,
    primary key (provider, account_id, request_id),
    foreign key (provider, period_start)
        references public.ai_provider_monthly_spend(provider, period_start),
    constraint ai_provider_spend_token_pair_check check (
        (input_tokens is null and output_tokens is null)
        or (input_tokens is not null and output_tokens is not null)
    ),
    constraint ai_provider_spend_settlement_check check (
        (status = 'reserved' and actual_cost_usd is null and settled_at is null)
        or (status = 'settled' and actual_cost_usd is not null and settled_at is not null
            and input_tokens is not null and output_tokens is not null)
        or (status = 'uncertain' and actual_cost_usd is not null and settled_at is not null
            and input_tokens is null and output_tokens is null)
        or (status = 'released' and actual_cost_usd = 0 and settled_at is not null
            and input_tokens is null and output_tokens is null)
    )
);

create index if not exists ai_provider_spend_expired_lease_idx
    on public.ai_provider_spend_reservations (provider, period_start, lease_expires_at)
    where status = 'reserved';

create index if not exists ai_provider_spend_account_period_idx
    on public.ai_provider_spend_reservations (account_id, period_start);

alter table public.ai_provider_monthly_spend enable row level security;
alter table public.ai_provider_spend_reservations enable row level security;
revoke all on public.ai_provider_monthly_spend from public, anon, authenticated;
revoke all on public.ai_provider_spend_reservations from public, anon, authenticated;

create or replace function public.purge_ai_provider_spend_on_account_deletion()
returns trigger
language plpgsql
security definer
set search_path = public, pg_temp
as $$
declare
    budget_period record;
begin
    if new.status = 'completed'
       and old.status is distinct from new.status then
        -- Match provider settlement's budget-row-first lock order. An in-flight
        -- request is conservatively settled at its reservation before its
        -- account-linked metadata is deleted.
        for budget_period in
            select distinct period_start
              from public.ai_provider_spend_reservations
             where account_id = new.account_id
             order by period_start
        loop
            perform 1
              from public.ai_provider_monthly_spend
             where provider = 'openai'
               and period_start = budget_period.period_start
             for update;

            with expired_or_active as (
                update public.ai_provider_spend_reservations
                   set status = 'uncertain',
                       actual_cost_usd = reserved_cost_usd,
                       settled_at = now()
                 where provider = 'openai'
                   and period_start = budget_period.period_start
                   and account_id = new.account_id
                   and status = 'reserved'
                returning reserved_cost_usd
            )
            update public.ai_provider_monthly_spend
               set reserved_usd = reserved_usd - coalesce((select sum(reserved_cost_usd) from expired_or_active), 0),
                   spent_usd = spent_usd + coalesce((select sum(reserved_cost_usd) from expired_or_active), 0),
                   updated_at = now()
             where provider = 'openai'
               and period_start = budget_period.period_start;
        end loop;

        delete from public.ai_provider_spend_reservations
         where account_id = new.account_id;
    end if;
    return new;
end;
$$;

drop trigger if exists account_deletion_purge_ai_provider_spend
    on public.account_deletion_requests;
create trigger account_deletion_purge_ai_provider_spend
    after update of status on public.account_deletion_requests
    for each row execute function public.purge_ai_provider_spend_on_account_deletion();

revoke all on function public.purge_ai_provider_spend_on_account_deletion() from public;
