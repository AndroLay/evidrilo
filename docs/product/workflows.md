# Product Workflows

This guide collects Evidrilo's student, account, project, and purchase
workflows. It separates behavior present in the current source from product
targets that remain gated. A Mermaid diagram describes a path; it does not by
itself prove device, provider, hosted-service, or human acceptance.
For component-to-component request sequences and runtime architecture, see
[`../architecture/system-execution-flows.md`](../architecture/system-execution-flows.md).

## Diagram index

1. [D-123 interaction principles](#d-123-interaction-principles)
2. [App entry and access](#1-app-entry-and-access)
3. [Local project lifecycle](#2-local-project-lifecycle)
4. [Project import, export, and attachments](#3-project-import-export-and-attachments)
5. [Guided M0 case](#4-guided-m0-case)
6. [Template review and catalog](#5-template-review-and-catalog)
7. [Profile, Settings, account, and history](#6-profile-settings-account-and-history)
8. [Local reminders](#7-local-reminders)
9. [AI assistance](#8-ai-assistance)
10. [Premium access and RevenueCat](#9-premium-access-and-revenuecat)
11. [Data and service boundaries](#10-data-and-service-boundaries)

## Status language

- **Current path** means the source contains the described path. It does not
  imply a device or provider run unless that is stated explicitly.
- **Gated** means a policy, provider, reviewed content, or external acceptance
  is still required before the path is available or can be claimed as verified.
- **Target** means an adopted product direction whose full implementation is
  not established.

The current project path and the guided M0 case are separate. The case does not
create or populate a student project. Local projects remain on the device unless
the student explicitly exports them; signing in does not upload or merge them.

## D-123 interaction principles

These are the adopted student-facing UX targets. They refine D-120/D-121's
local-first access and Home-first entry without changing account, consent, data,
or evaluator boundaries.

- Keep routine screens concise: one clear purpose, one short explanation when
  needed, and one primary action. Put optional detail behind a disclosure. Keep
  privacy, AI context/cost, and data-transfer consent understandable at the
  point where they matter; brevity must not hide a consequential choice.
- When a student chooses an account-gated action, open the main sign-in surface
  directly and preserve that requested destination. Show configured email or
  **Continue with Google** options. Apple sign-in is deferred by D-132: keep it
  disabled and hidden until the owner revisits the decision and provider setup
  is available. After successful authentication, resume the requested action.
  If no provider is available, show one short recovery message and a route back
  to local work. Do not expose long configuration diagnostics or a disabled
  provider button as if it were usable.

~~~mermaid
flowchart TD
    Action[Choose an account-gated action] --> Ready{A sign-in provider is configured?}
    Ready -->|Yes| SignIn[Open the main sign-in screen and keep the destination]
    SignIn --> Provider[Continue with a configured provider]
    Provider --> Result{Sign-in succeeds?}
    Result -->|Yes| Resume[Resume the requested action]
    Result -->|Cancelled or failed| Return[Return to the prior surface]
    Ready -->|No| Unavailable[Show a short unavailable message and local-work exit]
~~~
- Keep Get Started optional, skippable, replayable, and targeted at about three
  minutes; that duration is a design target, not validated usability evidence.
  Use a visual progress bar and a plain-language current-section title instead
  of visible “Step N of M” copy. Back, Next, and Skip remain available, and
  assistive technology receives a meaningful progress description.
- Start the guide nearly blank. Let a student use their own assignment or topic,
  or continue without one. Explain the general path from a task, to material,
  to a student-written note or finding, to a bounded claim, to what remains
  unknown and a next action. Do not present a fabricated record such as
  “synthetic source S-01” as something the student selected or cited. The guide
  must not create a project or history entry without an explicit create action.
- Use ordinary, field-appropriate words. For example, explain a finding as
  “what you learned from the material,” describe a claim as “what you can
  currently say,” and describe a limitation as “what this information cannot
  tell you.” Use “source” for a real student-entered publication in the current
  literature-synthesis path; use “observation” for a supplied case fact. Avoid
  exposing record IDs or internal link terminology as the main instruction.
- Every selection must show its effect immediately: selected state, what was
  added or changed, and the next useful action. A recorded link means that the
  student connected two records; it must not look like an Evidrilo verdict that
  the evidence proves a claim.
- In project work, show one work section at a time with a visible progress bar,
  current section title, and free movement backward or forward. Progress reports
  structural completion only, never research quality. Keep the one-page full
  review available as a separate final inspection before confirmation/export.
- Each of the five family pages has a bundled offline **Use this guide in a
  project** action. It creates a local, editable, versioned structure-only
  starter and leaves the student's answers, sources, data, findings, and claims
  empty. Show the starter's purpose, sections, limits, provenance, and
  accessibility notes; identify it as unreviewed guidance, never an evaluator.
  **Start a blank project** remains available at all times.
- The remote catalog is a separate source of genuinely reviewed, published
  method templates. Before **Use reviewed template**, show its exact method,
  output, sections, and version. Only that reviewed/published path may be used
  for method-specific AI. A family starter is never silently upgraded into a
  remote template or treated as human approval.
- Keep case practice separate from the student's project. A synthetic case is
  clearly labeled as practice, its facts never become project sources, and its
  account gate does not block local project work. Remove visible narration
  **Listen** controls from the current product surfaces; this does not remove
  operating-system screen-reader support or decide separate selection feedback
  and device-local sound preferences. D-098's optional-audio capability remains
  a separately accepted future choice.
- Keep the D-119 project-AI bubble persistent across supported project sections.
  Show the project and section it is using; suggestions remain editable
  proposals that the student may review, apply selectively, or dismiss. This
  remains a target while the project provider and full integration are gated.

The current workspace pilot remains manual directed literature synthesis.
Field-neutral wording helps students understand the reasoning path; it does not
claim that every research method or template family is implemented or reviewed.

## 1. App entry and access

**Current path:** Home is the first-launch destination. The optional Get Started
tour can be opened from the app and skipped; it is an orientation, not a gate to
local project work. Browsing the read-only catalog, using local projects, and
practicing the bundled M0 case with local history do not require an account.
Sign-in is optional for local Free work. Pro requires sign-in and a confirmed
active RevenueCat entitlement. D-129 pauses server AI, cloud sync, and analytics
transmission only when `TEMPORARY_GUEST_MODE_ENABLED` is true; the current
source constant is false. AI remains separately gated by verified account,
consent, provider, and reviewed-template requirements.

~~~mermaid
flowchart TD
    Launch[Open Evidrilo] --> Home[Home]
    Home --> Projects[Create or resume a local project]
    Home --> Catalog[Browse the read-only template catalog]
    Home --> Guide[Open optional Get Started tour]
    Guide --> GuideChoice{Finish or skip?}
    GuideChoice -->|Finish| Home
    GuideChoice -->|Skip| Home
    Guide --> Demo[Inspect clearly labeled synthetic demo]
    Demo --> Home
    Home --> Practice[Open guided M0 case]
    Practice --> Case[Open local case workspace]
    Home --> Profile[Open Profile or Account]
    Profile --> Identity[Choose sign-in, session, or provider-link action]
    Identity --> Auth
    Home --> Protected[Choose an account-backed service]
    Protected --> ServiceReady{Service enabled in this build?}
    ServiceReady -->|No: guest guard enabled| Paused[No account-backed operation; keep local work]
    Paused --> LocalSurface
    ServiceReady -->|Yes| AccountCheck{Verified session available?}
    AccountCheck -->|No| Auth[Run configured identity flow and keep destination]
    AccountCheck -->|Yes| Resume[Resume the requested service]
    Auth --> AuthResult{Authentication succeeds?}
    AuthResult -->|No or cancelled| Prior[Return to the prior surface]
    AuthResult -->|Yes| GuideStatus{Get Started unfinished?}
    GuideStatus -->|No| Resume
    GuideStatus -->|Yes| GuideOffer[Offer optional Resume or Skip without blocking the action]
    GuideOffer -->|Resume| PostAuthGuide[Continue the optional guide]
    PostAuthGuide --> Resume
    GuideOffer -->|Skip or later| Resume
    Home --> Settings[Open Settings]
    Settings --> LocalSurface[Keep local preferences, projects, and history available]
~~~

The Get Started tour does not create a project, add project history, or turn its
synthetic example into a reviewed template. Successful sign-in does not imply
project sync or backup. Identity-provider behavior and the full sign-in journey
still require provider and device acceptance. If a returning user has not
finished Get Started, offer Resume or Skip while preserving the requested
destination.
The current source uses an eight-step fictional tablet-dissolution walkthrough
and synthetic record IDs. D-123 records a different presentation target; this
description is not an endorsement of the current example as cross-discipline
onboarding.

## 2. Local project lifecycle

**Current path:** A student may start blank/manual or choose one of five
bundled offline family starters. A starter saves an immutable, versioned
structure snapshot to a local editable project; all student content starts
empty. It provides prompts and boundaries, not reviewed methodological
approval, an evaluator, or a grade. The initial manual workflow focuses on
directed literature synthesis. Any project can hold the brief and question,
source records, evidence notes, findings, themes, claims, limitations, next
actions, and revision checkpoints. Structural checks surface missing fields or
links; they do not grade academic quality or decide whether a claim is true.

~~~mermaid
flowchart TD
    Home[Home] --> List[My Projects]
    List --> CreateChoice{Choose how to start}
    CreateChoice -->|Blank/manual| Blank[Create an empty local project]
    CreateChoice -->|Choose a project type| Families[Browse five family guides]
    Families --> Starter[Use an offline structure-only starter]
    Starter --> Snapshot[Create project from its exact local version]
    Families -->|Optional| Catalog[Browse remote published templates]
    Catalog --> Available{Compatible reviewed template available?}
    Available -->|No| Explain[Keep starter and blank routes available]
    Explain --> Families
    Available -->|Yes| ReviewedSnapshot[Create from published reviewed version]
    Blank --> Editor[Open project editor]
    Snapshot --> Editor
    ReviewedSnapshot --> Editor
    Editor --> Brief[Record assignment brief, question, purpose, and scope]
    Brief --> Sources[Add and select sources]
    Sources --> Evidence[Record evidence notes and findings]
    Evidence --> Synthesis[Link evidence to themes and student-authored claims]
    Synthesis --> Limits[Record limitations and next actions]
    Limits --> Check[Review structural gaps and links]
    Check --> Save[Autosave the working draft locally]
    Save --> Checkpoint{Explicit or meaningful checkpoint?}
    Checkpoint -->|Yes| Revision[Create a revision checkpoint]
    Checkpoint -->|No| Continue{Continue working?}
    Revision --> Continue
    Continue -->|Yes| Editor
    Continue -->|Complete| Complete[Mark project completed]
    Continue -->|Archive| Archive[Archive project]
    Continue -->|Move to Trash| Trash[Move project to local Trash]
    Complete --> LocalHistory[Keep project and its revision history locally]
    Archive --> LocalHistory
    Trash --> Retention{Within 30-day retention?}
    Retention -->|No| Expired[Eligible for expiry purge]
    Retention -->|Yes| Capacity{Active-project slot available?}
    Capacity -->|Yes| RestoreDraft[Restore as a draft]
    Capacity -->|No| ArchiveChoice{Restore as archived?}
    ArchiveChoice -->|Yes| RestoreArchived[Restore archived without an active slot]
    ArchiveChoice -->|No| KeepTrash[Keep in Trash; free a slot or retry later]
    RestoreDraft --> Editor
    RestoreArchived --> List
    KeepTrash --> Trash
~~~

**Current editor and next hub target:** The source has a multi-section project
editor with a progress bar, Back/Continue navigation, autosave state, and a
full-project review. Device acceptance remains open. A dedicated project hub
that summarizes the question, progress, and one next action is still a target;
the diagram below describes that proposed hub around the existing editor.

~~~mermaid
flowchart TD
    Hub[Project hub: question, progress, next action] --> Workspace[Open project workspace]
    Workspace --> Brief[Brief and research question]
    Workspace --> Sources[Sources]
    Workspace --> Evidence[Evidence notes]
    Workspace --> Findings[Findings and synthesis]
    Workspace --> Claims[Claims, limitations, and next action]
    Brief -. progress bar, back, or next .-> Workspace
    Sources -. progress bar, back, or next .-> Workspace
    Evidence -. progress bar, back, or next .-> Workspace
    Findings -. progress bar, back, or next .-> Workspace
    Claims -. progress bar, back, or next .-> Workspace
    Workspace -->|Open at any time| Review[Full project review]
    Review -->|Edit a section| Workspace
    Review -->|Confirm you reviewed the draft| Reviewed[Return to project hub]
    Reviewed -->|Continue or export| Hub
~~~

The progress indicator reports structural completion, missing inputs, and links
that need attention; it is not a score for research quality or claim truth.
Capture evidence notes from the source they belong to, then connect those notes
to student-authored findings and claims. If an edit changes or removes linked
material, show the affected relationships and let the student revisit them; do
not silently rewrite findings or claims. The full review presents the project
on one page with section-level edit links. Confirming review records that the
student inspected the draft, does not certify academic validity, and does not
lock the project. Export remains an explicit action from the reviewed project.
Each stage choice also names its immediate effect in plain language—for
example, selecting a source visibly adds it to the project and offers the next
action of recording a note from it.

The guided M0 case remains a separate learning path. Opening or finishing a
synthetic case does not populate the project with its facts or conclusions.

Local active-project capacity is five on the Free path and up to fifty only
when a signed-in account has a verified active `evidrilo_pro` entitlement. If
entitlement state is unknown, the app uses the Free limit; it must not delete,
hide, or rewrite existing projects. These are per-installation limits, not
account-wide quota or cloud sync. The temporary guest-mode switch controls
whether RevenueCat is initialized for a guest installation; it is currently
disabled in the client. Trash retention is thirty days in the local project
rules; a full active quota does not expire or purge an item early.

The five bundled offline family starters are selectable and create local
projects from their exact structure snapshots. The last recorded remote
catalog observation (E225) had no reviewed, student-selectable template; it
was not refreshed in E286, and its data is not substituted with local starters.
A project remains local unless the student explicitly
exports it; cloud continuity is a later, separately consented capability.
Both starter and reviewed-template actions show what sections will be created,
then create and open the local project. Neither path supplies the student's
research material, observations, findings, or conclusions.

## 3. Project import, export, and attachments

**Current path:** Export prepares a snapshot and presents a review before the
system file picker. Import validates and previews a versioned `.evproj` archive
before saving it locally. Import-as-copy remaps record IDs; conflicts require a
clear choice. Attachments remain files, not evidence, unless the student
records evidence separately.

~~~mermaid
flowchart LR
    subgraph Export
        Project[Local project] --> Prepare[Prepare report or .evproj snapshot]
        Prepare --> Review[Review included text or archive contents]
        Review --> Confirm{Student confirms?}
        Confirm -->|No, cancel, or dismiss| Kept[Project remains saved locally]
        Confirm -->|Yes| Picker[Open system file destination]
        Picker --> Result{Write result}
        Result -->|Saved| File[Student-owned exported file]
        Result -->|Cancelled or failed| Report[Show truthful cancel or error state]
    end
    subgraph Import
        Select[Select .evproj file] --> Preflight[Check archive bounds and manifest]
        Preflight --> Valid{Archive valid?}
        Valid -->|No| Reject[Reject without changing project list]
        Valid -->|Yes| Preview[Preview project and attachment metadata]
        Preview --> Choice{Choose import action}
        Choice -->|Import as copy| Remap[Remap project and record IDs]
        Choice -->|Restore newer revision| Conflict[Check matching project ID and revision]
        Conflict --> RevisionValid{Same project and newer compatible revision?}
        RevisionValid -->|No| Reject
        RevisionValid -->|Yes| ConfirmRevision{Student confirms restore?}
        ConfirmRevision -->|No| CancelImport
        ConfirmRevision -->|Yes| RestoreRevision[Restore as a new local revision]
        RestoreRevision -->|Saved| Imported
        RestoreRevision -->|Failed| Preserve[Keep the existing project revision]
        Remap --> Capacity{Within local capacity?}
        Capacity -->|No| CapacityChoice{Archive the imported project?}
        Capacity -->|Yes| Stage[Stage attachment bytes privately]
        CapacityChoice -->|Cancel| CancelImport[Leave the existing project list unchanged]
        CapacityChoice -->|Yes| ArchiveImport[Keep imported project archived]
        ArchiveImport --> Stage
        Stage --> StageResult{Attachment staging succeeded?}
        StageResult -->|No| StageFailed[Stop import; preserve project list and offer retry]
        StageResult -->|Yes| Commit[Write project and attachment references]
        Commit -->|Failure| Rollback[Rollback staged files and preserve existing work]
        Commit -->|Success| Imported[Open imported local project]
    end
~~~

Reports can be generated as Markdown, PDF, and DOCX; structured records can be
exported as CSV. Before sharing, review the exact export: Evidrilo does not
automatically detect or redact personal information. Attachment preview is
bounded and read-only (TXT, Markdown, and CSV; DOCX text preview on Android).
Preview does not create evidence, verify a source, or send content to AI.

The project-file adapters and bounded handling are covered by local source,
test, and target-compilation evidence on Android/iOS. Save/reopen behavior,
accessibility, and cleanup still need device acceptance. PDF/image text
extraction and automatic redaction are not available.

## 4. Guided M0 case

**Current path:** The bundled tablet-dissolution case is a synthetic, bounded
learning experience. It uses supplied facts and deterministic rules. It is
separate from student projects. The case and its local history are available
without sign-in while the D-125/D-129 guest workflow is enabled.

~~~mermaid
flowchart TD
    Entry[Choose guided case practice] --> Case[Open supplied case and requirement locally]
    Case --> Facts[Inspect supplied observations and limitations]
    Facts --> Select[Select relevant observation anchors in Evidence Lens]
    Select --> Draft[Write claim, scope, limits, and next action]
    Draft --> Evaluate[Run deterministic case rules]
    Evaluate --> Assess{Can the supplied facts assess this input?}
    Assess -->|No| Abstain[Return CANNOT_ASSESS with a bounded explanation]
    Assess -->|Yes| Feedback[Show prioritized feedback and fact anchors]
    Abstain --> Revise[Make the one guided revision]
    Feedback --> Revise
    Revise --> Compare[Compare initial and revised drafts]
    Compare --> Challenge{Try the evidence-change challenge?}
    Challenge -->|No| History[Save case history locally]
    Challenge -->|Yes| Change[Apply the supplied evidence change]
    Change --> Delta[Review evidence, claim, gap, and action changes]
    Delta --> History
~~~

The evaluator only checks the active case and its versioned rules. It is not a
scientific-truth oracle, general grader, or universal assessment. `CANNOT_ASSESS`
is an intentional abstention when the configured case cannot support a check.
Case history is distinct from a project's revision history. Premium cases, if
locked, use RevenueCat only for a signed-in account after provider identity is
confirmed; an active matching entitlement is still required to unlock Pro.
Provider configuration and Test Store runtime acceptance remain open. Guest
mode continues to pause server AI, cloud sync, and analytics transmission.

## 5. Template review and catalog

The catalog is read-only for students. Template authoring and publication are
content-governance workflows; merely having API contracts or a review screen
does not make a template ready for students.

~~~mermaid
flowchart LR
    Author[Create template draft] --> Define[Define method, fields, steps, and constraints]
    Define --> Examples[Add normal and edge-case examples]
    Examples --> Review[Human and domain review]
    Review --> Decision{Approved for this method?}
    Decision -->|No| Revise[Return draft with review notes]
    Revise --> Define
    Decision -->|Yes| Publish[Publish immutable version]
    Publish --> Catalog[Student read-only catalog]
    Catalog --> Compatible{Published and compatible?}
    Compatible -->|Yes| Snapshot[Start project with version snapshot]
    Compatible -->|No| Manual[Offer blank/manual project path]
    Publish --> NewVersion[Change content by reviewing and publishing a new version]
    NewVersion --> Catalog
~~~

The last recorded remote published-catalog observation (E225) had no reviewed,
student-selectable template; no refresh was made in E286. The app must state
that accurately while keeping blank/manual and
offline structure-only starters available. A local starter is useful for
organizing work but must not be described as reviewed method guidance or
presented as an evaluator. Any remote method template or method-specific
evaluation still requires human/domain review before publication. Existing
projects retain the exact template or starter version they started from.

## 6. Profile, Settings, account, and history

**Current path:** Profile and Settings surfaces are reachable from the app
while signed out. Local projects and Support remain accessible. An action that
needs an account must gate that action, preserve the requested destination, and
return there after successful authentication. D-129 permits optional
sign-in/linking for configured providers; D-132 defers Apple sign-in. D-131
enables RevenueCat Pro separately for signed-in accounts with a
provider-confirmed entitlement; server AI, cloud sync, and analytics
transmission remain paused in guest mode. Signing in alone never uploads local
projects or enables those services.

~~~mermaid
flowchart TD
    Home[Home] --> Profile[Profile]
    Home --> Settings[Settings]
    Profile --> LocalProjects[Open local projects without sign-in]
    Profile --> Support[Open Support]
    Profile --> AccountAction[Open sign-in or identity settings]
    Settings --> Preferences[Device-local preferences, privacy, and audio controls]
    Settings --> Reminders[Reminder controls]
    Settings --> Help[Help, Support, and About]
    Settings --> Protected[Choose account-backed service]
    Protected --> ServiceReady{Service enabled in this build?}
    ServiceReady -->|No: guest guard enabled| Paused[Account-backed action paused; preserve local work]
    Paused --> LocalProjects
    ServiceReady -->|Yes| Gate{Verified session available?}
    AccountAction --> Auth
    Gate -->|No| Auth[Sign in, keep requested destination]
    Auth --> AuthResult{Provider succeeds?}
    AuthResult -->|No, cancelled, or expired| Return[Keep local data and show recovery path]
    AuthResult -->|Yes| Destination[Return to requested account action]
    Gate -->|Yes| Destination
    Destination --> AccountData[Show only authorized account data]
    AccountData --> ConnectedMethods[Review connected sign-in methods]
    ConnectedMethods --> LinkProvider[Choose another configured provider]
    LinkProvider --> Reauthenticate[Authenticate provider for this account]
    Reauthenticate --> LinkCheck{Verified identity belongs to this account?}
    LinkCheck -->|Yes| Linked[Link provider without changing account ID]
    LinkCheck -->|No, conflict, or cancel| LinkRecovery[Keep current session and show generic recovery]
    Profile --> HistoryChoice{Choose history}
    HistoryChoice --> ProjectHistory[Local project revision history]
    HistoryChoice --> CaseHistory[Local M0 case history; available in guest mode]
    AccountData --> Delete[Choose account export or deletion]
    Delete --> AccountServiceReady{Account service enabled in this build?}
    AccountServiceReady -->|No: guest guard enabled| Paused
    AccountServiceReady -->|Yes| Confirm[Review scope and confirm explicitly]
    Confirm -->|Account export| Export[Request account data export]
    Confirm -->|Delete account| DeleteAccount[Request server-account deletion]
    Confirm -->|Clear local work| ClearLocal[Separate confirmation for local projects/history]
~~~

Project revision history and M0 case history are separate local records and
remain available without sign-in. Account history and server projections
require a restored signed-in session and an enabled service; D-129 pauses them
only when the temporary guest guard is enabled. Account export and deletion
follow the same guard. Account deletion is distinct from clearing local
projects; neither action should silently delete the other store. Support is intended to be reachable from
Profile, Settings, and account gates. The selected contact is
`andrlay30@gmail.com`, but the current app build reports that a production
support address is not configured; owner configuration and runtime verification
are still required.

The account screen exposes configured sign-in methods and an explicit action
to link another configured provider to the account already in use. The internal
Evidrilo account ID stays canonical; provider identities use their verified
stable subject IDs, not email addresses. The client verifies the same Supabase
user ID after linking. Apple-specific sign-in, linking, and private-relay
handling remain deferred by D-132; keep Apple configuration and app flags off
and do not present an Apple action. Revisit that flow only after a new owner
decision and Apple provider setup.

Supabase Auth automatically links OAuth identities with the same verified
email. This managed behavior cannot be disabled by this client, so same-email
provider sign-in may attach an identity without the in-app link confirmation;
the app does not merge separate Evidrilo records. Email is not an identity key:
on a provider collision, preserve the active session and direct the student to
authenticate with the existing account before attempting an explicit link.
Sign-in also does not imply cloud project transfer.

Configured email/Google redirects, session recovery, and account linking still
require provider and device acceptance. Apple provider setup and runtime remain
out of current scope under D-132. Account export/deletion also require their
own API and device acceptance.

## 7. Local reminders

**Target:** Reminders are optional and local. They must not block Home or
project work and must not require a network or account. Remote push and
promotional campaigns are outside this local reminder flow.

~~~mermaid
flowchart TD
    Settings[Open reminder settings] --> Enable[Explicitly enable reminders]
    Enable --> Permission{OS notification permission granted?}
    Permission -->|No| Recovery[Explain denial and offer OS settings path]
    Permission -->|Yes| Categories[Choose reminder categories]
    Categories --> Schedule[Choose daily or weekly cadence and local time]
    Schedule --> Preview[Show next scheduled reminder]
    Preview --> Device[Schedule on this device]
    Device --> Change{Preference or project state changes?}
    Change -->|Reminder disabled| Cancel[Cancel future Evidrilo schedules]
    Change -->|Category/time changed| Reschedule[Replace local schedules]
    Change -->|No| Device
~~~

The initial categories are **Continue an unfinished case** and **Review a
completed case**; both are off by default. Permission is requested only after an
explicit action or from Settings. Turning reminders off cancels future
Evidrilo schedules without deleting learning data. Scheduling across reboot,
Doze, timezone changes, archive/completion, and device acceptance remains a
runtime verification gate; this diagram is the intended local behavior, not a
delivery-time guarantee.

## 8. AI assistance

**Current-source case path (D-106):** The mobile case-AI conversation and typed
proposal bridge are connected in source. Each request requires a verified
signed-in session and a fresh, explicit context-sharing choice. D-129's guest
guard applies only when `TEMPORARY_GUEST_MODE_ENABLED` is true; the current
source sets it false. The provider remains disabled by default, so this does
not establish a live AI capability. Provider, device, accessibility, privacy,
and human-usefulness acceptance remain open. A failed or unavailable request
leaves the case draft and deterministic feedback unchanged.

~~~mermaid
flowchart TD
    Case[Open AI from the local M0 case] --> GuestMode{Temporary guest mode enabled?}
    GuestMode -->|Yes: if the guest guard is enabled| Paused[Show AI paused; send no context]
    Paused --> Manual[Continue the local case workflow]
    GuestMode -->|No, account services enabled| Auth[Require a verified account session]
    Auth --> Consent[Review shown context and consent for this request]
    Consent --> Send[Submit bounded case question]
    Send --> API[Rebuild active case context, check account, session, and anchors]
    API --> Provider{Provider enabled?}
    Provider -->|No: current configuration| Unavailable[Show unavailable, keep draft and deterministic feedback]
    Provider -->|Only after approval| Response[Return validated explanation, question, or proposal]
    Response --> Kind{Typed proposal?}
    Kind -->|No| Explain[Show explanation or reflection question, draft unchanged]
    Kind -->|Yes| Preview[Show before/after proposal, do not auto-apply]
    Preview --> Choice{Student action}
    Choice -->|Dismiss| Unchanged[Keep draft unchanged]
    Choice -->|Apply supported claim, scope, or limitation| Reducer[Apply through Conclusion reducer]
    Choice -->|next_action or unmapped field| ReadOnly[Keep proposal preview-only]
    Reducer --> Submit[Student submits the revised draft]
    Submit --> Evaluate[Run deterministic case evaluator]
    Evaluate --> Result[Show normal case feedback and comparison when applicable]
    Unavailable --> Manual
~~~

The server reserves a bounded estimate using the configured model rates, then
settles verified provider token usage for a valid response. Disabled,
unavailable, rejected, or invalid responses release the reservation. The Clear
control is hidden while a turn is running. Clearing a completed chat removes
the local transcript immediately, requests deletion of server session metadata,
and refreshes the balance. If the server does not confirm deletion, the UI says
so and offers a same-session retry for retryable failures; metadata may
otherwise remain until the 30-minute session expires.
Current mobile requests the negotiated v2 turn response to show the server's
actual settled cost and refresh the shared account balance. A normal fallback
reports zero credits charged; if usage was already settled before the
conversation session expired, show that actual nonzero cost instead of implying
the turn was free. Older v1 response shapes remain unchanged.

**D-115/D-116/D-119 source implementation; provider and reviewed-content gates
remain:** AI project scaffolding is exposed only for a reviewed/published
template. Stage assistance is exposed only for a project step whose template
declares supported AI operations; the five bundled structure-only starters do
not enable method-specific AI. The project panel identifies the active project
and stage and lets the student select fields/items before sending. The API
binds the local project ID to the authenticated account, checks consent and the
latest saved revision, and rejects stale proposals. Home's AI entry first asks
the student to choose General Chat or one project; its project list includes
only Draft/Active projects whose saved template is Published and declares an AI
operation. Choosing a project opens it through the normal resume flow; other
projects remain available through My Projects for manual work. The project list
is not sent to the provider. General Chat has no project ID or case context and
sends only the current message. Before sending, the app refreshes the signed-in
account's revocable AI data-use consent from the platform; granting it sends no
message by itself, and each message still requires separate `general-chat.v1`
confirmation. The server stores metadata rather than the transcript. No
project history is sent to its provider.

These mobile surfaces and API contracts are implemented in source, but they
are not yet provider-backed end-to-end acceptance. The committed/default local
provider and General Chat policy flags remain off. A real method-specific flow
also depends on an actually reviewed and published selectable template; an
offline structure-only starter is not a substitute.

Project AI can help at each supported stage, but generated content is always a
proposal. Before sending, show the project, step, selected context, purpose,
cost, and retention for review and consent. Return editable field-level or
multi-field proposals with their context and provenance. The student may edit
the proposal, apply selected parts to the saved project draft, or dismiss it;
AI must never overwrite project fields silently. Applied AI content remains a
draft and is visibly identified in the full project review, where the student
can edit it again and confirm that they reviewed the overall draft. This
confirmation is not a claim that the research is correct. If project content
changes while a request is running, reject a proposal based on the stale
revision and ask the student to review or regenerate it.

Stage help may clarify a brief from instructions the student supplied, organize
student-entered source details, draft evidence notes from explicitly selected
material, suggest candidate themes from linked notes, or help phrase a claim,
limitation, or next action using the project's own records. It must identify the
records it used and mark unsupported gaps for the student. It must not invent or
verify sources, DOI data, observations, or findings.

Project AI history records its project and stage; unlinked General chat stays
separate. Stage-assist and metadata-history API routes now exist, and the
stage-assist, scaffold, General Chat, and project activity surfaces exist in
mobile source. Project history displays a bounded page and can request older
pages by cursor. General Chat can open the account's metadata-only AI activity
view; entries label General Chat as unlinked and distinguish project-bound
records. Neither view reads or stores chat transcripts. Assistance remains
limited to operations declared by the selected template; no all-method
capability is implied. General Chat's API policy and all Project AI provider
activation flags remain off by default. Every applied proposal passes through
the project reducer and preserves AI provenance. This is separate from the
current D-106 case-AI path and conclusion reducer. Source tests and builds do
not substitute for provider-backed Android acceptance.

~~~mermaid
flowchart TD
    Entry{Open AI} -->|Persistent project bubble| BoundContext[Bind active project, step, and revision]
    Entry -->|Global AI entry| Mode{Select an isolated mode}
    Mode -->|Project| Select[Choose one project and supported step]
    Mode -->|General| General[Start unlinked chat with no project context]
    Select --> BoundContext
    BoundContext --> Context[Preview selected context, purpose, cost, and retention]
    General --> Policy[Review separate General-chat policy and cost]
    Context --> Consent{Consent, account, template, and credits valid?}
    Policy --> Consent
    Consent -->|No or current provider disabled| Manual[Keep AI unavailable, continue manual work]
    Consent -->|Approved future configuration| Generate[Request bounded typed response]
    Generate --> Validate[Validate schema, anchors, scope, and base revision]
    Validate --> Proposal[Show editable proposal and AI provenance]
    Proposal --> Choice{Student choice}
    Choice -->|Edit and apply selected parts| ProjectReducer[Apply to draft through project reducer]
    Choice -->|Dismiss| Unchanged[Leave project unchanged]
    ProjectReducer --> Recheck[Run supported structural checks]
    Recheck --> FullReview[Show AI-assisted fields in the full project review]
    FullReview -->|Edit a section| BoundContext
    FullReview -->|Confirm student review| Reviewed[Keep project editable and reviewed]
~~~

## 9. Premium access and RevenueCat

### Free and Pro boundary — D-124

The catalog and project workflow should make Free useful for real academic
work. Subscription access controls optional capacity and depth, not whether a
student can understand a method, keep their own work, or retrieve their data.

| Capability | Free | Pro |
| --- | --- | --- |
| Catalog | Browse and use all five offline structure-only starters; preview every remote published version; start a reviewed baseline template whenever that family has one | May start optional reviewed specialist templates introduced later; no family is Pro-only |
| Projects | Blank/manual projects, full core workflow, five active projects per installation | Fifty active projects per installation while entitlement is verified |
| Case practice | Complete Free learning loop | Two additional approved premium cases |
| AI allowance | 20 one-time credits for an eligible verified account after consent | Adds 200 credits for each active entitlement month; monthly and yearly plans accumulate unused credits, including after Pro ends |
| Reports and data | Essential report export and complete project-data portability | No paid-only lock on existing project data or recovery/export |

Template publication and Pro access are separate decisions. Bundled
structure-only starters are startable on Free; remote templates used as reviewed
method guidance require a reviewed, published, versioned snapshot. Every family
stays browseable on Free, with at least one reviewed baseline template Free in
each family once such a template exists. A Pro specialist template is optional
future depth, not a method-family paywall or a claim that its results are more
correct.

These are product rules, not proof of availability or enforcement. The current
catalog may still have no selectable template. AI remains finite and metered:
explicit consent and a verified account are required for the Free grant.
Every AI mode uses D-127 token-based pricing from one account balance. A valid
Project AI preview is charged when generated, whether applied or dismissed;
failure or invalid output releases the reservation. General chat uses the same
account balance and does not have a separate grant. Cloud sync/backup is not included in the
current Pro promise and needs the separate R2 privacy, consent, retention,
quota, cost, operations, and acceptance gates. Do not add paid top-ups,
lifetime, one-time packs, institutional sales, or ads in v1.

**Gated:** Premium access must come from verified RevenueCat entitlement, not a
local UI flag. The Test Store transaction matrix has not been completed.

Free means no subscription is required. The bundled M0 case, local histories,
local projects, and read-only catalog browsing work without sign-in. Pro
requires a signed-in account and a confirmed active RevenueCat entitlement;
signing in alone does not activate Pro. When the temporary guest guard is
enabled, D-129 also pauses server AI, cloud sync, and analytics transmission;
regardless of that flag, AI still requires its separate consent, provider, and
content gates.

~~~mermaid
flowchart TD
    Free[Use the local Free workflow] --> PremiumValue[Open a premium case, future specialist template, or plans]
    PremiumValue --> SignIn{Signed in?}
    SignIn -->|No| Auth[Sign in and return to the requested destination]
    SignIn -->|Yes| Compare[Review Free and Pro value]
    Auth --> Compare
    Compare --> Action{Purchase, restore, or close?}
    Action -->|Purchase| Offer[Load localized monthly and yearly offerings]
    Action -->|Restore| Restore[Start a user-initiated restore]
    Action -->|Close| FreeFallback
    Offer --> Offers{Offerings available?}
    Offers -->|No or error| FreeFallback[Keep Free work available, show retry or unavailable]
    Offers -->|Yes| Choice[Select a package and review renewal terms]
    Choice --> Purchase[Start RevenueCat purchase]
    Purchase --> Outcome{Provider outcome}
    Outcome -->|User cancels purchase| Cancelled[Return to paywall, entitlement unchanged]
    Outcome -->|Pending| Pending[Show pending, wait for verified provider state]
    Outcome -->|Failure| Failed[Show failure and retry option]
    Outcome -->|Verified active entitlement| Active[Unlock Pro benefits]
    Pending --> Reconcile[Refresh or await verified CustomerInfo]
    Reconcile -->|Active| Active
    Reconcile -->|Unknown, still pending, or unavailable| FreeFallback
    Active --> Manage[Manage renewal through the store or Customer Center]
    Manage --> Renewal{Renewal state}
    Renewal -->|Renewal cancelled, period still active| Retain[Keep Pro until entitlement expires]
    Renewal -->|Expiry or revoke confirmed| FreeAgain[Remove Pro benefits only]
    FreeAgain --> Preserve[Preserve local projects and Free functionality]
    Cancelled --> Action
    Failed --> Action
    Restore --> Refresh[Ask store to restore and refresh CustomerInfo]
    Refresh --> Verified{Active entitlement verified?}
    Verified -->|Yes| Active
    Verified -->|No, offline, or unknown| FreeFallback
~~~

Cancelling a purchase is different from cancelling subscription renewal. A
renewal cancellation keeps access until the store-reported entitlement expires;
expiry or revocation removes Pro benefits but preserves local work and Free
access. Restore is initiated by the student and resolved from provider state.
Offer prices and trial eligibility must come from the current store offering.
Next Gen evidence may use the RevenueCat Test Store, but it must be labeled as
sandbox evidence and does not prove production revenue.

## 10. Data and service boundaries

Local projects stay in local storage and the local file system. Account-bound
API operations require a verified session, server-side ownership checks, and
the relevant separate consent. Signing in does not sync project drafts. The
current API/worker/PostgreSQL lane has local integration evidence; hosted
deployment and managed-provider behavior are not established by that evidence.

For module ownership, dependency direction, API/database boundaries, and the
technical architecture diagram, see
[`../architecture/repository-structure.md`](../architecture/repository-structure.md).
For the billing adapter and server projection boundary, see
[`../architecture/revenuecat.md`](../architecture/revenuecat.md). For release
proof requirements, see [`../release.md`](../release.md).
