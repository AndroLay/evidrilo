-- Evidrilo P4, explicit author membership role.

alter table public.organization_memberships
    drop constraint if exists organization_memberships_role_check;
alter table public.organization_memberships
    add constraint organization_memberships_role_check
    check (role in ('learner', 'author', 'teacher', 'reviewer', 'maintainer', 'owner'));

-- Draft/review/publish mutations remain server-owned. No client write policy is
-- granted by this migration.
