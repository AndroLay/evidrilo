-- Evidrilo P4/P5, server-owned authoring and recommendation metadata.
-- Existing published rows remain readable; newly authored rows must provide
-- organization and recommendation metadata before they can be published.

alter table public.case_versions add column if not exists organization_id uuid
    references public.organizations(organization_id);
alter table public.case_versions add column if not exists objective text;
alter table public.case_versions add column if not exists difficulty integer;
alter table public.case_versions add column if not exists evidence_references text[];
alter table public.case_versions add column if not exists content jsonb;

alter table public.case_versions drop constraint if exists case_versions_difficulty_check;
alter table public.case_versions add constraint case_versions_difficulty_check
    check (difficulty is null or difficulty between 1 and 5);

alter table public.case_versions drop constraint if exists case_versions_objective_check;
alter table public.case_versions add constraint case_versions_objective_check
    check (objective is null or char_length(objective) between 1 and 200);

alter table public.case_versions drop constraint if exists case_versions_evidence_references_check;
alter table public.case_versions add constraint case_versions_evidence_references_check
    check (evidence_references is null or (
        cardinality(evidence_references) between 1 and 128
        and array_position(evidence_references, null) is null
    ));

alter table public.case_versions drop constraint if exists case_versions_content_object_check;
alter table public.case_versions add constraint case_versions_content_object_check
    check (content is null or jsonb_typeof(content) = 'object');

create index if not exists case_versions_recommendation_idx
    on public.case_versions (status, difficulty, case_version_id)
    where status = 'published';

-- Authoring writes remain server-owned. The API sets the request subject before
-- it executes a mutation and performs the role check in its transaction.
-- No client insert/update policy is granted here.
