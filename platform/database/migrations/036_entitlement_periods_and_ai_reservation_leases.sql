-- Preserve provider-owned entitlement periods and recover abandoned AI credit reservations.

alter table public.entitlements
    add column if not exists period_started_at timestamptz null,
    add column if not exists period_expires_at timestamptz null;

alter table public.entitlement_events
    add column if not exists period_started_at timestamptz null,
    add column if not exists period_expires_at timestamptz null;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conrelid = 'public.entitlements'::regclass
          and conname = 'entitlements_period_bounds_check'
    ) then
        alter table public.entitlements
            add constraint entitlements_period_bounds_check
            check (
                (period_started_at is null and period_expires_at is null)
                or (
                    period_started_at is not null
                    and period_expires_at is not null
                    and period_expires_at > period_started_at
                )
            );
    end if;
end;
$$;

do $$
begin
    if not exists (
        select 1
        from pg_constraint
        where conrelid = 'public.entitlement_events'::regclass
          and conname = 'entitlement_events_period_bounds_check'
    ) then
        alter table public.entitlement_events
            add constraint entitlement_events_period_bounds_check
            check (
                (period_started_at is null and period_expires_at is null)
                or (
                    period_started_at is not null
                    and period_expires_at is not null
                    and period_expires_at > period_started_at
                )
            );
    end if;
end;
$$;

alter table public.ai_credit_reservations
    add column if not exists lease_expires_at timestamptz null,
    add column if not exists release_reason text null;

update public.ai_credit_reservations
set lease_expires_at = reserved_at + interval '2 minutes'
where lease_expires_at is null;

alter table public.ai_credit_reservations
    alter column lease_expires_at set default (now() + interval '2 minutes'),
    alter column lease_expires_at set not null;

create index if not exists ai_credit_reservations_expired_lease_idx
    on public.ai_credit_reservations (account_id, lease_expires_at)
    where status = 'reserved';
