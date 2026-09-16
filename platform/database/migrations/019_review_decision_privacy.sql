-- Evidrilo P4, keep reviewer decisions inside the server-owned boundary.

drop policy if exists published_case_review_select on public.case_review_decisions;
