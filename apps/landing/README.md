# Evidrilo landing page

This is a dependency-free static site for the Evidrilo mobile app. From the
repository root, preview it with:

```sh
python3 -m http.server 8000 --directory apps/landing
```

Open `http://localhost:8000`. The site can be deployed by serving the
`apps/landing` directory. Its GitHub calls to action point to the repository,
not to an app-store listing.

The page uses a light, cobalt-accented layout with a project-first story:
research workflow, a student-authored project structure, and separate guided
case practice. The project illustration is explicitly labeled as a structure
map, not a live app screen. The current manual path focuses on directed
literature synthesis with student-entered sources, evidence notes, linked
synthesis, claims, limitations, and next steps. Local projects can be created
without an account; sign-in alone does not enable cloud sync or upload existing
work. Account-bound M0 learning requires sign-in. Reviewed selectable templates,
cloud sync, and live Project AI are not available in the current build. File
export and `.evproj` exchange are implemented in development code, but their
device behavior still needs verification. The FAQ has five questions covering project work,
account access, local storage and export, sources and AI, and academic evaluation.

The interactive case lets visitors remove or restore three supplied
observations. Elapsed-time bars, evidence trace, and the bounded description
update together. The example does not assess visitor input, and motion respects
the browser's reduced-motion preference.

The hero pairs app screenshots with cards taken from the supplied synthetic
case. They illustrate the separate guided practice path, not the student-owned
research project workspace. The four short benefit points below the hero
describe project structure and student control without claiming source search,
cloud sync, or AI availability.
A later claim comparison shows why one trial per condition supports a bounded
description rather than an always-true claim. The progress line, hero depth, and
claim connection respond to scrolling without changing the learning content.

The case walkthrough uses a sticky screenshot stage on desktop. Scrolling
changes the active Android screen and fills the reasoning line; smaller screens
show each screen with its explanation in reading order. The project workflow is
described separately so the case screenshots are not presented as project
screens.

Below the case walkthrough, an illustrative three-card example explains the first
draft, anchored feedback, and one student-written revision. It uses facts and
the one-trial limit from the bundled case; the wording is not presented as an
actual saved app draft. Section headings, content, app imagery, and these cards
enter once as they reach the viewport. Content is visible without JavaScript,
and entrance effects are skipped when reduced motion is requested.

The app screen images were resized from local Android captures of the bundled
synthetic tablet-dissolution case in
`artifacts/screenshots/all-pages-20260922/`. They illustrate the guided case,
not the current manual project editor. Re-capture them before presenting project
screens in this story. The mark comes from
`apps/android/src/main/res/drawable-nodpi/evidrilo_logo_mark.png` and the
Source Sans 3 fonts come from `modules/design-system`. Font licensing is in
`THIRD_PARTY_NOTICES.md` and `docs/licenses/SourceSans3-OFL-1.1.md`.
