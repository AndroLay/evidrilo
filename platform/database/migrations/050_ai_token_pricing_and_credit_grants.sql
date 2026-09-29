-- Meter user credits from provider token usage and align current grants to the
-- approved 20 Free / 200 Pro allowance.

alter table public.ai_credit_grants
    drop constraint if exists ai_credit_grants_credits_check;

alter table public.ai_credit_grants
    add constraint ai_credit_grants_credits_check
    check (credits between 1 and 200);

update public.ai_credit_grants
set credits = greatest(
    credits,
    case grant_kind
        when 'free_once' then 20
        when 'subscription_month' then 200
    end)
where grant_kind = 'free_once'
   or (grant_kind = 'subscription_month' and (expires_at is null or expires_at > now()));

alter table public.ai_credit_reservations
    add column if not exists settled_credit_cost integer null;

update public.ai_credit_reservations
set settled_credit_cost = case when status = 'consumed' then credit_cost else 0 end
where status in ('consumed', 'released') and settled_credit_cost is null;

alter table public.ai_credit_reservations
    drop constraint if exists ai_credit_reservations_credit_cost_check;

alter table public.ai_credit_reservations
    add constraint ai_credit_reservations_credit_cost_check
    check (credit_cost between 1 and 200);

alter table public.ai_credit_reservations
    drop constraint if exists ai_credit_reservations_settled_credit_cost_check;

alter table public.ai_credit_reservations
    add constraint ai_credit_reservations_settled_credit_cost_check
    check (
        (status = 'reserved' and settled_credit_cost is null)
        or (status = 'consumed' and settled_credit_cost is not null
            and settled_credit_cost between 0 and credit_cost)
        or (status = 'released' and settled_credit_cost is not null
            and settled_credit_cost = 0)
    );

alter table public.ai_provider_spend_reservations
    add column if not exists cached_input_tokens integer null check (cached_input_tokens is null or cached_input_tokens >= 0),
    add column if not exists cache_write_input_tokens integer null check (cache_write_input_tokens is null or cache_write_input_tokens >= 0),
    add column if not exists reasoning_tokens integer null check (reasoning_tokens is null or reasoning_tokens >= 0);

update public.ai_provider_spend_reservations
set cached_input_tokens = coalesce(cached_input_tokens, 0),
    cache_write_input_tokens = coalesce(cache_write_input_tokens, 0),
    reasoning_tokens = coalesce(reasoning_tokens, 0)
where input_tokens is not null and output_tokens is not null;

alter table public.ai_provider_spend_reservations
    drop constraint if exists ai_provider_spend_token_pair_check,
    drop constraint if exists ai_provider_spend_settlement_check;

alter table public.ai_provider_spend_reservations
    add constraint ai_provider_spend_token_categories_check
    check (
        (input_tokens is null and output_tokens is null
            and cached_input_tokens is null and cache_write_input_tokens is null and reasoning_tokens is null)
        or (input_tokens is not null and output_tokens is not null
            and cached_input_tokens is not null and cache_write_input_tokens is not null and reasoning_tokens is not null
            and cached_input_tokens + cache_write_input_tokens <= input_tokens
            and reasoning_tokens <= output_tokens)
    ),
    add constraint ai_provider_spend_settlement_check
    check (
        (status = 'reserved' and actual_cost_usd is null and settled_at is null
            and input_tokens is null and output_tokens is null
            and cached_input_tokens is null and cache_write_input_tokens is null and reasoning_tokens is null)
        or (status = 'settled' and actual_cost_usd is not null and settled_at is not null
            and input_tokens is not null and output_tokens is not null
            and cached_input_tokens is not null and cache_write_input_tokens is not null and reasoning_tokens is not null)
        or (status = 'uncertain' and actual_cost_usd is not null and settled_at is not null
            and input_tokens is null and output_tokens is null
            and cached_input_tokens is null and cache_write_input_tokens is null and reasoning_tokens is null)
        or (status = 'released' and actual_cost_usd = 0 and settled_at is not null
            and input_tokens is null and output_tokens is null
            and cached_input_tokens is null and cache_write_input_tokens is null and reasoning_tokens is null)
    );
