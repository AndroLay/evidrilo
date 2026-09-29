-- Keep unused Pro monthly grants in the account balance and allow a request
-- reservation to draw from more than one grant row.

update public.ai_credit_grants
set credits = 200,
    expires_at = null
where grant_kind = 'subscription_month';

create table if not exists public.ai_credit_reservation_allocations (
    account_id uuid not null,
    request_id text not null,
    allocation_index integer not null check (allocation_index >= 0),
    grant_id uuid not null,
    reserved_credits integer not null check (reserved_credits between 1 and 200),
    settled_credits integer null check (
        settled_credits is null or settled_credits between 0 and reserved_credits
    ),
    created_at timestamptz not null default now(),
    primary key (account_id, request_id, allocation_index),
    unique (account_id, request_id, grant_id),
    foreign key (account_id, request_id)
        references public.ai_credit_reservations (account_id, request_id)
        on delete cascade,
    foreign key (account_id, grant_id)
        references public.ai_credit_grants (account_id, grant_id)
        on delete cascade
);

insert into public.ai_credit_reservation_allocations (
    account_id, request_id, allocation_index, grant_id,
    reserved_credits, settled_credits
)
select
    reservation.account_id,
    reservation.request_id,
    0,
    reservation.grant_id,
    reservation.credit_cost,
    case reservation.status
        when 'reserved' then null
        when 'consumed' then coalesce(reservation.settled_credit_cost, reservation.credit_cost)
        else coalesce(reservation.settled_credit_cost, 0)
    end
from public.ai_credit_reservations reservation
on conflict (account_id, request_id, allocation_index) do nothing;

create index if not exists ai_credit_reservation_allocations_grant_idx
    on public.ai_credit_reservation_allocations (account_id, grant_id, settled_credits);

create index if not exists entitlement_events_account_period_idx
    on public.entitlement_events (account_id, entitlement, occurred_at);

alter table public.ai_credit_reservation_allocations enable row level security;

create policy ai_credit_reservation_allocations_select_own
    on public.ai_credit_reservation_allocations
    for select using (account_id = auth.uid());

revoke all on public.ai_credit_reservation_allocations from anon, authenticated;
grant select on public.ai_credit_reservation_allocations to authenticated;
