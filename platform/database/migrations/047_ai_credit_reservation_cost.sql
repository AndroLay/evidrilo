-- Persist the exact allowance reserved by each AI operation.

alter table public.ai_credit_reservations
    add column if not exists credit_cost integer not null default 1;

alter table public.ai_credit_reservations
    add constraint ai_credit_reservations_credit_cost_check
    check (credit_cost between 1 and 100);
