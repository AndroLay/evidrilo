-- Evidrilo: bind an AI operation key to its normalized request fingerprint.
-- A released reservation is terminal for that key; callers must create a new
-- key for a new attempt rather than accidentally repeating a provider call.

alter table public.ai_credit_reservations
    add column if not exists request_hash text;

update public.ai_credit_reservations
set request_hash = repeat('0', 64)
where request_hash is null;

alter table public.ai_credit_reservations
    alter column request_hash set not null;

alter table public.ai_credit_reservations
    drop constraint if exists ai_credit_reservations_request_hash_check;

alter table public.ai_credit_reservations
    add constraint ai_credit_reservations_request_hash_check
    check (request_hash ~ '^[a-f0-9]{64}$');
