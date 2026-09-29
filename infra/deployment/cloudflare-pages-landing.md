# Cloudflare Pages landing deployment

Status: initial deployment was recorded as successful at
[https://evidrilo.pages.dev](https://evidrilo.pages.dev); current reachability
has not been reverified from this workspace.

The Evidrilo landing page is a dependency-free static site in `apps/landing`,
deployed from the existing GitHub repository. No API, Supabase, RevenueCat,
environment variables, or build command are required.

## Pages project settings

| Setting | Value |
| --- | --- |
| Project name | `evidrilo` |
| Git provider / repository | GitHub / `AndroLay/evidrilo` |
| Production branch | `main` |
| Framework preset | None |
| Root directory | Repository root (leave unset) |
| Build command | Blank |
| Build output directory | `apps/landing` |
| Environment variables | None |
| Build watch include paths | `apps/landing/*` |
| Build watch exclude paths | None |

The include path keeps unrelated app/API commits in this monorepo from
triggering landing builds. Git integration is configured to deploy changes on
`main` to production; preview deployments are disabled. Automatic deployment
from a later Git push has not yet been exercised. The production URL is
`https://evidrilo.pages.dev`; a custom domain can be connected later.

This project uses **Git integration**. Cloudflare Pages does not allow
converting a Direct Upload project to Git integration later; a new project
would be needed to change that deployment model.

## Initial deployment record

The Git-integrated Pages project is named `evidrilo` and points at
`AndroLay/evidrilo` on `main`. Project creation did not start a build, so the
initial production version was manually uploaded with Wrangler to the same
Git-integrated project.

- Commit: `2511b3fc87d368cfbb99426d6dfd9477b19cefee`
- Cloudflare deployment status: `success`, environment `production`
- Production URL: [https://evidrilo.pages.dev](https://evidrilo.pages.dev)
- Reachability: HTTP 200; the Evidrilo page title was returned.
- Static assets: all 7 linked CSS, JavaScript, font, and image assets returned 200.

Automatic deployment from a later push to `main` is configured but not yet
verified. Cloudflare project settings report the Git source, production branch,
and `apps/landing/*` build include path.

## Future deployments

Changes to `apps/landing/*` on `main` should trigger a production build. For a
manual deployment, use Wrangler against the existing project; do not create a
second Pages project.

Cloudflare static asset requests are free. The Free plan currently allows 500
builds per month and one concurrent build; the watch path prevents unrelated
monorepo changes from triggering builds. The landing page points visitors to
the repository and does not depend on the API or a custom domain.

## Official references

- [Cloudflare Pages Git integration](https://developers.cloudflare.com/pages/get-started/git-integration/)
- [Cloudflare Pages build configuration](https://developers.cloudflare.com/pages/configuration/build-configuration/)
- [Cloudflare Pages build watch paths](https://developers.cloudflare.com/pages/configuration/build-watch-paths/)
- [Cloudflare Pages limits](https://developers.cloudflare.com/pages/platform/limits/)
