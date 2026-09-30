# Evidrilo landing page

A dependency-free static site. From the repository root:

    python3 -m http.server 8765 --bind 127.0.0.1 --directory apps/landing

Open http://127.0.0.1:8765/. GitHub calls to action point to the source
repository, not an app-store listing. No build step or package install is needed.

## Design and page story

The approved Evidrilo identity remains the visual authority: cobalt, white,
navy, Source Sans 3, and the transparent in-app mark. Public references inform
composition and interaction, not product claims or copied assets:

- [Linear](https://linear.app/homepage): clear hierarchy and precise product presentation.
- [Craft](https://www.craft.do/): expressive document layouts and varied section rhythm.
- [Raycast](https://www.raycast.com/): product interactions that explain a feature.
- [Momental](https://momental.ai/): a prominent mobile product in the hero.
- [SleepOmo](https://sleepomo.dofolabs.space/): a narrated phone preview, contextual
  annotations, and visible stages that explain a product through interaction.

The page follows a student-owned project from its brief to source connections
and review. The hero phone enters from the right and changes between three
illustrative screens during scroll or direct selection. Segmented stage markers,
contextual annotations, and brief connection animations follow the selected screen. The desktop project
preview follows three sequential narrative steps; on smaller screens the
three compact touch controls select the same scenes and a single accompanying
paragraph. Selecting a stage keeps the scroll position. Without JavaScript,
all three descriptions remain readable.

A report illustration explains portability and project lifecycle. Project AI
has a separate, explicitly non-live preview: selected context, an editable
proposal, and the student's decision. Its controls send no requests.
The interactive synthetic case allows visitors to remove or restore three
supplied observations. The comparison, missing facts, and bounded description
update together; it does not evaluate visitor research.

Three local WebP captures show the Android guided case. They are not captures
of the manual project editor. Mobile visitors can browse the gallery using
native horizontal scrolling or the provided arrow buttons.

## Mobile composition

The compact layout uses a centered, shorter introduction, paired calls to action,
a smaller complete phone illustration, and a compact header with a smaller brand
mark and a 44px GitHub action. Mobile navigation has no hamburger menu. Project
stages stay next to their preview. Section headings and body copy use a tighter
hierarchy, and the screenshot gallery preserves native horizontal scrolling.
Landscape phones use the compact controls with a two-column hero where it fits.
Insets respect the screen safe areas. Main actions use cobalt pressed faces and
small press feedback, consistent with Evidrilo's learning-oriented button style.

## Motion and performance

- CSS and small local JavaScript; no framework, video, remote fonts, or animation library.
- Scroll events are passive and coalesced into one requested frame. No idle
  animation loop, polling, or continuous ambient animation.
- Geometry is cached and refreshed on resize, font readiness, and layout changes.
- The phone uses transforms; a source-link diagram draws on activation.
  Two illustrations settle once when they enter view.
- The reduced-motion preference removes spatial animation and automatic phone
  transitions. Direct controls still work.
- Content is visible without JavaScript. Script-only controls are disabled until
  initialized; the static evidence comparison and native FAQ remain readable.
- Screenshot images declare dimensions and load lazily. Fonts and brand assets
  are bundled locally. The three WOFF2 fonts are Latin subsets of the approved
  in-app TTFs, preserving licensing metadata and the page's used punctuation.
  Their combined payload is 40,044 bytes, down from 327,096 bytes.
- Native links, buttons, checkboxes, details, focus indicators, and a skip link
  preserve keyboard access.

## Capability boundaries

Blank local projects and the five browse-only project-family guides work
without an account. The current manual path focuses on directed literature
synthesis. Account services and current guided-case app routes have separate
sign-in gates. Signing in does not upload or merge local projects.

Project AI consent, selected context, scaffolding, stage assistance, editable
proposals, and provenance exist in development source. The provider is
disabled and no reviewed student-selectable template is available yet.
General chat also has development source; live student AI remains unavailable.
The preview on this page is explanatory.

Markdown, PDF, DOCX, CSV report generation and .evproj exchange exist in source.
Device picker, save, and reopen acceptance are still in progress. Cloud sync,
source discovery, citation verification, and research grading are unavailable.
The FAQ keeps these boundaries visible.

The screenshots were resized from local Android captures of the bundled
synthetic tablet-dissolution case in artifacts/screenshots/all-pages-20260922/.
Re-capture actual project screens before presenting them as product proof.
Font licensing is in THIRD_PARTY_NOTICES.md and
docs/licenses/SourceSans3-OFL-1.1.md.
