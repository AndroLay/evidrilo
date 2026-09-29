-- General chat stores metadata-only activity and needs a terminal success
-- outcome without any project settlement fields.
begin;

alter table public.project_ai_activity
    drop constraint if exists project_ai_activity_outcome_check,
    add constraint project_ai_activity_outcome_check
        check (outcome in ('PENDING', 'APPLIED', 'EDITED', 'DISMISSED', 'STALE', 'FAILED', 'COMPLETED'));

alter table public.project_ai_activity
    drop constraint if exists project_ai_activity_context_check,
    add constraint project_ai_activity_context_check check (
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
                or (outcome in ('PENDING', 'FAILED', 'COMPLETED')
                    and requested_settlement_outcome is null and settlement_hash is null)
                or (outcome = 'STALE'
                    and ((requested_settlement_outcome is null and settlement_hash is null)
                        or (requested_settlement_outcome is not null and settlement_hash is not null))
            )
        )
    ));

commit;
