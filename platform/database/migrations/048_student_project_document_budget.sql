-- Keep the account-owned API document budget compatible with the bounded
-- local project payload while retaining an explicit database-side ceiling.

alter table public.student_projects
    drop constraint if exists student_projects_document_check;

alter table public.student_projects
    add constraint student_projects_document_check
    check (
        jsonb_typeof(document) = 'object'
        and octet_length(document::text) <= 3145728
    );

alter table public.student_project_revisions
    drop constraint if exists student_project_revisions_document_check;

alter table public.student_project_revisions
    add constraint student_project_revisions_document_check
    check (
        jsonb_typeof(document) = 'object'
        and octet_length(document::text) <= 3145728
    );
