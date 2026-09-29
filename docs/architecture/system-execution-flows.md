# System Architecture and Execution Flows

This guide shows how Evidrilo components exchange data at runtime. It
complements the student-facing journeys in
[`../product/workflows.md`](../product/workflows.md), which describe choices and
screens rather than component-to-component calls.

## Status and diagram conventions

| Label | Meaning |
| --- | --- |
| **Current** | The corresponding interaction exists in source. This does not prove a device, provider, or hosted run. |
| **Gated** | The contract or code exists, but configuration, content, UI, approval, or runtime acceptance is still missing. |
| **Target** | An accepted design direction that is not established as an available path. |

Solid arrows show an implemented boundary. Dashed arrows show an optional,
unwired, or gated boundary. A diagram is an architecture aid, not runtime
evidence.

## 1. System context

**Current architecture:** the mobile app owns the offline learning and local
project paths. The API and worker provide optional account-backed capabilities.
The mobile project editor does not automatically synchronize local projects to
the server.

```mermaid
flowchart LR
    Learner[Student] --> Host[Android or iOS host]
    Host --> Compose[Compose Multiplatform UI]

    subgraph Device[On-device application]
        Compose --> App[Shared application coordinators]
        App --> Domain[Domain reducers and deterministic M0 evaluator]
        App --> ProjectFlow[Local student-project flow]
        ProjectFlow --> ProjectStore[(Local project and attachment stores)]
        App --> CaseStore[(Local case session and history)]
        App --> Billing[BillingGateway and platform RevenueCat adapter]
    end

    Billing --> RC[RevenueCat SDK]
    RC <--> Stores[Apple App Store or Google Play]
    RC -. optional signed webhook .-> Api
    RC -->|CustomerInfo for local case access| Billing

    subgraph Platform[Optional ASP.NET Core modular monolith]
        Api[Evidrilo API]
        Api --> Pg[(PostgreSQL)]
        Worker[.NET worker] --> Pg
        Auth[Supabase JWT validation] -->|verified principal| Api
        Api --> Credits[Consent and AI-credit ledger]
        Api -. provider disabled .-> AiProvider[AI provider]
    end

    App -. optional account, catalog, progress, and AI requests .-> Api
```

The local M0 evaluator remains authoritative for case feedback. RevenueCat
`CustomerInfo` is the immediate authority for bundled premium-case access; the
optional webhook projection does not grant those local cases. The API's
student-project routes are a separate server-owned capability and are not yet
wired to the local mobile project editor.

## 2. Data ownership and trust boundaries

```mermaid
flowchart TB
    subgraph Device[Device-owned data]
        LocalProject[Project brief, sources, evidence notes, findings, claims, revisions]
        Attachments[Project attachments]
        CaseDraft[Case drafts, evaluation snapshots, and local history]
        CustomerInfo[RevenueCat CustomerInfo]
    end

    subgraph Server[Account-owned platform data]
        Account[Verified account and consent]
        Progress[Progress and other consented account records]
        ServerProject[Optional server-owned student-project records]
        AiLedger[AI consent, reservations, and credit ledger]
        EntitlementProjection[Optional server entitlement projection]
    end

    LocalProject -->|references separately stored bytes| Attachments
    LocalProject -. sign-in does not upload or merge .-> ServerProject
    CaseDraft -. only explicit bounded sync commands .-> Progress
    CustomerInfo --> LocalCaseAccess[Local premium-case gate]
    CustomerInfo -. signed provider event .-> EntitlementProjection
    Account --> AiLedger
    AiLedger --> AiRequest[Bounded AI request when provider is enabled]
```

Project source records and evidence notes are student-entered; an attachment is
not automatically parsed into evidence. The server-owned project API has
ownership checks and versioned records, but it is not cloud sync for the
device-owned editor. Sign-in alone does not move local data.

## 3. Local development topology

**Current:** Docker Compose runs a reproducible development stack. It does not
represent a hosted deployment.

```mermaid
flowchart TB
    Postgres[(PostgreSQL 16)]
    Postgres --> AuthShim[Local auth-shim initialization]
    Postgres --> Migrations[Forward-only migration job]
    AuthShim --> Migrations
    Migrations --> Api[ASP.NET Core API]
    Migrations --> Worker[.NET worker]
    Api <--> Postgres
    Worker <--> Postgres
    Mobile[Android/iOS app or local API client] -. configured local HTTP .-> Api
```

The local database's trust authentication is development-only. Hosted API,
worker, database, managed backups, and production monitoring are not implied by
this topology; see [`../../infra/deployment/README.md`](../../infra/deployment/README.md)
and [`../../infra/monitoring/README.md`](../../infra/monitoring/README.md).

## 4. Student project data model

```mermaid
flowchart LR
    Project[StudentProjectDraft]
    Project --> Sources[Source records]
    Sources --> Evidence[Evidence items with source IDs]
    Project --> Themes[Synthesis themes with source IDs]
    Project --> Findings[Student-authored findings]
    Project --> Claims[Student-authored claims]
    Evidence --> Relations[Evidence relations]
    Relations -->|targetType + targetId| Findings
    Relations -->|targetType + targetId| Claims
    Relations -->|supports / contradicts / provides context| Meaning[Student-selected relationship]
    Findings --> Limits[Limitation and action records]
    Claims --> Limits
    Project --> Revisions[Bounded revision snapshots]
    Project --> Attachments[Separate attachment references]
```

Evidence relations record a student's selected relationship; they do not
automatically judge evidence quality or prove a claim. Attachments remain
separate from evidence until a student creates a note or link explicitly.

## 5. System execution sequences

### 5.1 Local project edit and autosave — Current

```mermaid
sequenceDiagram
    autonumber
    actor Student
    participant UI as Compose project editor
    participant App as EvidriloApp coordinator
    participant Flow as StudentProjectDraftFlow
    participant Rules as StudentProjectDraftRules
    participant Store as StudentProjectDraftStore
    participant Codec as StudentProjectDraftStoreCodec
    participant Device as Platform-local text storage

    Student->>UI: Edit a project field or linked record
    UI->>UI: Mark snapshot dirty and debounce autosave
    UI->>App: Send current snapshot through onAutosave
    App->>Flow: update(projectId, snapshot, checkpoint=false)
    Flow->>Store: Load current project collection
    Store->>Device: Read encoded local record
    Device-->>Store: Stored JSON or read failure
    Store->>Codec: Decode and validate stored collection
    Codec-->>Store: Draft records or corrupt/unavailable result
    Store-->>Flow: Current project state
    Flow->>Rules: Validate proposed project and links
    alt Valid update
        Flow->>Store: Save updated draft
        Store->>Device: Encode and write replacement collection
        Device-->>Store: Write result
        Store-->>Flow: Saved
        Flow-->>App: Updated draft
        App-->>UI: Replace active project state
        UI-->>Student: Show saved-on-device status
    else Invalid data or storage failure
        Flow-->>App: Rejected or storage failure
        App-->>UI: Preserve edits and show retry message
        UI-->>Student: Show not-saved state
    end
```

Ordinary autosave updates the working draft without creating a checkpoint for
each keystroke. A meaningful checkpoint is created through the explicit
checkpoint path. A failed write leaves the edits on screen and must not be
reported as saved.

### 5.2 Guided M0 case evaluation — Current

```mermaid
sequenceDiagram
    autonumber
    actor Student
    participant UI as Case draft screen
    participant App as EvidriloApp dispatch
    participant Reducer as ConclusionReducer
    participant Evaluator as ConclusionEvaluator
    participant Store as ConclusionSessionStore

    Student->>UI: Submit claim, scope, evidence, limits, and action
    UI->>App: ConclusionEvent.Submit
    App->>Reducer: reduce(currentState, Submit)
    Reducer->>Evaluator: Evaluate against active bundled CaseVersion
    Evaluator->>Evaluator: Check required input, supported enums, facts, and rules
    Evaluator-->>Reducer: Anchored evaluation or bounded cannot-assess feedback
    Reducer-->>App: Feedback, revision summary, or incomplete state
    App->>Store: Persist resulting case session locally
    Store-->>App: Local write result
    App-->>UI: Render the returned case state
    UI-->>Student: Show feedback and the next permitted step
```

No AI provider or general-purpose model is called to decide M0 case truth. The
evaluator may abstain with `CANNOT_ASSESS` when the active case cannot support a
check. Optional account progress synchronization is a separate, consented
boundary and does not replace this local evaluation.

### 5.3 Server-owned student project API — API exists; mobile integration gated

The server-owned project API is separate from the local project editor. A
client must obtain explicit project-cloud consent before creating or saving
server records. The current mobile project workflow is not wired to these
routes, so this sequence describes the API capability rather than active app
sync.

#### Grant consent and save a project: accepted path

```mermaid
sequenceDiagram
    autonumber
    participant Student
    participant Client
    participant API as Project API
    participant Auth as Account auth
    participant Store as Project store
    participant DB as PostgreSQL

    Student->>Client: Grant project cloud consent
    Client->>API: Save consent choice
    API->>Auth: Validate signed-in account
    Auth-->>API: Verified account
    API->>Store: Save account consent
    Store->>DB: Write consent and audit event
    DB-->>Store: Write committed
    Store-->>API: Consent saved
    API-->>Client: Consent result

    Student->>Client: Create or save a project
    Client->>API: Send versioned save with idempotency key
    API->>Auth: Resolve verified account
    Auth-->>API: Verified account
    API->>Store: Create or update owned project
    Store->>DB: Start account-scoped transaction
    DB->>DB: Check consent, ownership, and expected version
    DB->>DB: Save project and append revision
    DB-->>Store: Commit and new version
    Store-->>API: Save succeeded
    API-->>Client: Versioned project result
```

#### Save rejected: consent or ownership conflict

```mermaid
sequenceDiagram
    autonumber
    participant Client
    participant API as Project API
    participant Store as Project store
    participant DB as PostgreSQL

    Client->>API: Send versioned project save
    API->>Store: Authorize and save within account scope
    Store->>DB: Check current consent, owner, and version
    DB-->>Store: Reject revoked consent or conflicting owner/version
    Store-->>API: Typed authorization or conflict error
    API-->>Client: Reject write, preserve device-local project
```

The API routes are `PUT /v1/projects/cloud-consent`, `POST /v1/projects`, and
`PUT /v1/projects/{id}`. The mobile project workflow is not yet connected to
these server-owned records.

This server path does not silently import or merge device-local drafts. A
successful account sign-in is not project consent, and the local project flow
remains usable when this API is unavailable.

### 5.4 Project AI scaffold contract — Gated

The API route exists, but the project-AI generator is disabled and the complete
student UI/provider path is not available. The **current** route verifies the
account and consent, validates the idempotency key, then reads and validates
the bounded request body before checking the generator flag. With the current
disabled generator it returns `PROJECT_AI_NOT_READY`; it does not load a
template, reserve credits, dispatch to a provider, or persist the student text.

#### Current request: provider disabled

```mermaid
sequenceDiagram
    autonumber
    participant Caller
    participant API as Project AI API
    participant Auth as Account auth
    participant Consent as Consent store
    participant Generator

    Caller->>API: Submit bounded scaffold request with idempotency key
    API->>Auth: Resolve verified account
    Auth-->>API: Verified account
    API->>Consent: Read current consent
    Consent-->>API: Current consent policy accepted
    API->>API: Validate idempotency key, parse and validate bounded request
    API->>Generator: Check whether generation is enabled
    Generator-->>API: Disabled
    API-->>Caller: PROJECT_AI_NOT_READY
```

The current disabled path has already read and validated the request body, but
does not look up the template, reserve or consume credits, call an external
provider, or store the submitted text.

#### Future request: gated proposal generation

```mermaid
sequenceDiagram
    autonumber
    participant Caller
    participant UI as Project AI preview
    participant API as Project AI API
    participant Auth as Verified account
    participant Generator as Approved generator
    participant Template as Template store
    participant Consent as Consent store
    participant Credits as Credit ledger
    participant Provider as AI provider

    Caller->>API: Submit bounded request with idempotency key
    API->>Auth: Verify account and email
    Auth-->>API: Verified account
    API->>Consent: Read and validate current Project AI consent
    Consent-->>API: Current consent policy accepted
    API->>API: Parse and validate request, confirm generator enabled
    API->>Template: Load exact published version and validate fields
    Template-->>API: Reviewed compatible template
    API->>Consent: Recheck consent before reserving credits
    Consent-->>API: Still authorized or revoked
    alt Consent still authorized before reservation
        API->>Credits: Ensure grant, reserve 3 create or 1 assist credit
        Credits-->>API: Request-bound reservation or rejection
        alt Credit reservation succeeded
            API->>Consent: Recheck consent immediately before dispatch
            alt Consent still authorizes dispatch
                Consent-->>API: Dispatch authorized
                API->>API: Redact bounded student fields
                API->>Generator: Generate a structured proposal
                Generator->>Provider: Send redacted context
                Provider-->>Generator: Candidate proposal or provider failure
                Generator-->>API: Output or typed failure
                API->>API: Validate version, field allowlist, size, and credentials
                alt Output is valid and reservation remains held
                    API->>Credits: Mark preview ready, keep reservation for student decision
                    Credits-->>API: Preview reservation confirmed
                    API-->>UI: Show preview without changing project
                else Provider fails, output is invalid, or reservation expires
                    API->>Credits: Release reservation
                    Credits-->>API: Reservation released
                    API-->>UI: Typed failure, keep project unchanged
                end
            else Consent revoked before dispatch
                Consent-->>API: Dispatch no longer authorized
                API->>Credits: Release held reservation
                Credits-->>API: Reservation released
                API-->>UI: Consent error, keep project unchanged
            end
        else Credit reservation rejected
            API-->>UI: Insufficient credit or replay error, no provider dispatch
        end
    else Consent revoked before reservation
        Consent-->>API: Consent no longer authorizes request
        API-->>UI: Consent error, no credit reservation or provider dispatch
    end
```

#### Future request: a gate rejects the proposal

```mermaid
sequenceDiagram
    autonumber
    participant Caller
    participant API as Project AI API
    participant Gates as Consent and template checks
    participant Credits as Credit ledger

    Caller->>API: Submit request with idempotency key
    API->>Gates: Check account, consent, template, and request state
    Gates-->>API: A required gate rejects the request
    API->>Credits: Release reservation if one was created
    Credits-->>API: Reservation released
    API-->>Caller: Return typed error, keep project unchanged
```

#### Future response: student review and settlement

```mermaid
sequenceDiagram
    autonumber
    participant Student
    participant UI as Project AI preview
    participant API as Project AI API
    participant Credits as Credit ledger

    Student->>UI: Review, edit, apply selected parts, or dismiss
    UI->>API: Send settlement decision with request key
    API->>Credits: Settle reservation
    alt Student applies selected parts
        Credits-->>API: Consume reserved credits
        API-->>UI: Settlement accepted
        UI->>UI: Apply only selected fields
        UI-->>Student: Show updated draft and provenance
    else Student dismisses proposal
        Credits-->>API: Release reserved credits
        API-->>UI: Proposal dismissed
        UI-->>Student: Keep project unchanged
    end
```

The future diagrams separate the disabled current path from the gated proposal
and settlement contract. Initial consent and request validation precede
template lookup; consent is rechecked before credit reservation and immediately
before dispatch. Template availability, credit reservation, idempotency,
validation, and provider approval remain required gates. Failures before a
reservation do not touch credits; failures after reservation release it unless
a validated preview is ready for student settlement.

The future branch is a contract/design path, not an available feature. Provider
privacy, retention, cost, human/domain review, selectable reviewed templates,
student-facing UI, and runtime acceptance remain gates. Generated content is a
proposal, never evidence or evaluator authority.

### 5.5 RevenueCat purchase and local access — Current code; provider acceptance gated

```mermaid
sequenceDiagram
    autonumber
    actor Student
    participant UI as Evidrilo paywall
    participant Gateway as BillingGateway
    participant Adapter as Android/iOS RevenueCat adapter
    participant RC as RevenueCat SDK
    participant Store as Apple App Store or Google Play
    participant Access as Local premium access state
    participant API as Optional entitlement projection

    Student->>UI: Open plans or a locked premium case
    UI->>Gateway: Load offerings or refresh access
    Gateway->>Adapter: Platform billing operation
    Adapter->>RC: getOfferings or getCustomerInfo
    RC-->>Adapter: Offerings or CustomerInfo
    Adapter-->>Gateway: BillingOutcome
    Gateway-->>UI: Display offers and verified local access state

    alt Student purchases a selected package
        UI->>Gateway: purchasePracticePack(productId)
        Gateway->>Adapter: Start purchase
        Adapter->>RC: purchase package
        RC->>Store: Complete store transaction
        Store-->>RC: Store outcome
        RC-->>Adapter: CustomerInfo or cancellation/error
        Adapter->>Adapter: Check evidrilo_pro and allowed product ID
        Adapter-->>Gateway: Access, Cancelled, Pending, Unknown, or Failure
        Gateway-->>UI: Update access without assuming success
        UI->>Access: Unlock only for active matching entitlement
    else Student restores purchases
        UI->>Gateway: restorePurchases()
        Gateway->>Adapter: Restore request
        Adapter->>RC: restorePurchases
        RC-->>Adapter: CustomerInfo or restore error
        Adapter->>Adapter: Recheck entitlement and product ID
        Adapter-->>Gateway: Access or truthful unavailable state
        Gateway-->>UI: Refresh paywall/access state
    end

    opt RevenueCat sends a signed server event
        RC-->>API: POST /v1/billing/webhook
        API->>API: Verify signature, event identity, ordering, and idempotency
        API->>API: Update server-owned entitlement projection
    end
```

For bundled premium cases, local RevenueCat `CustomerInfo` is the immediate
access authority; the server projection is not the unlock path. Cancelling a
purchase leaves access unchanged. Cancelling renewal keeps access through the
store-reported entitlement period; expiry or revoke removes Pro benefits while
preserving local work. Test Store purchase, restore, revoke, and expiry still
require provider/device evidence.

### 5.6 Template authoring, review, and student catalog — API workflow; dashboard/content gated

```mermaid
sequenceDiagram
    autonumber
    actor Author
    actor Reviewer
    actor Maintainer
    participant Client as Authoring client
    participant API as ASP.NET Core template endpoints
    participant Store as IProjectTemplateStore / PostgreSQL
    participant StudentApp as Student catalog reader

    Author->>Client: Prepare versioned draft with normal and edge examples
    Client->>API: POST /v1/authoring/project-templates
    API->>Store: CreateDraftAsync(accountId, request)
    Store->>Store: Check author membership and validate draft
    Store-->>API: Draft state and version
    API-->>Client: Draft created

    Reviewer->>Client: Inspect content, limitations, provenance, and examples
    Client->>API: POST version transition with reviewed example IDs
    API->>Store: TransitionAsync(accountId, decision, example IDs)
    Store->>Store: Check reviewer membership and review requirements
    Store-->>API: Review or approved state
    API-->>Client: Transition result

    Maintainer->>Client: Publish approved immutable version
    Client->>API: POST version transition to published
    API->>Store: TransitionAsync(accountId, publish decision)
    Store->>Store: Check maintainer membership and approved state
    Store->>Store: Publish version and set current published pointer
    Store-->>API: Published version
    API-->>Client: Publication result
    StudentApp->>API: GET /v1/project-templates
    API->>Store: Read current published versions only
    Store-->>API: Published catalog entries
    API-->>StudentApp: Read-only reviewed catalog
```

The API authoring endpoints exist, but a developer authoring dashboard and
human/domain review are not established here. No reviewed, student-selectable
template is currently recorded; blank/manual project creation remains the
available route.

### 5.7 `.evproj` export/import — Local device path; device acceptance gated

#### Export a report or `.evproj` archive

```mermaid
sequenceDiagram
    autonumber
    participant Student
    participant UI as Project editor
    participant Flow as Project draft flow
    participant Store as Local project store
    participant Builder as Report formatter or archive codec
    participant Picker as System file picker

    Student->>UI: Choose export and review included content
    UI->>Flow: Save changed snapshot or checkpoint
    Flow->>Store: Persist local snapshot
    Store-->>Flow: Snapshot saved
    Flow-->>UI: Saved snapshot
    UI->>Builder: Build bounded report or project archive
    Builder-->>UI: Export artifact
    UI->>Picker: Request destination
    Student->>Picker: Choose destination or cancel
    Picker-->>UI: Write result, cancel, or error
    UI-->>Student: Show truthful result, keep project local
```

#### Export stopped because the local save failed

```mermaid
sequenceDiagram
    autonumber
    participant Student
    participant UI as Project editor
    participant Flow as Project draft flow
    participant Store as Local project store

    Student->>UI: Choose export
    UI->>Flow: Save changed snapshot or checkpoint
    Flow->>Store: Persist local snapshot
    Store-->>Flow: Write failure
    Flow-->>UI: Save failed
    UI-->>Student: Do not create a file, keep project on device
```

#### Import a `.evproj` archive: preflight and staging

```mermaid
sequenceDiagram
    autonumber
    participant Student
    participant UI as Project editor
    participant Picker as System file picker
    participant Preflight as Archive preflight
    participant Archive as Archive codec

    Student->>Picker: Choose archive
    Picker-->>UI: File bytes and metadata
    UI->>Preflight: Check bounds, manifest, hashes, and records
    alt Archive passes preflight
        Preflight-->>UI: Show import preview
        Student->>UI: Confirm copy or compatible revision restore
        UI->>Archive: Stage validated attachments
        Archive-->>UI: Staged data or failure
    else Archive is invalid or exceeds bounds
        Preflight-->>UI: Reject without changing project list
        UI-->>Student: Show import error
    end
```

#### Import a `.evproj` archive: commit staged data

```mermaid
sequenceDiagram
    autonumber
    participant Student
    participant UI as Project editor
    participant Flow as Project draft flow
    participant Store as Local project and attachment stores

    Student->>UI: Confirm validated staged import
    UI->>Flow: Import under capacity and conflict rules
    Flow->>Store: Commit project and attachment references
    Store-->>Flow: Commit succeeds
    Flow-->>UI: Imported project
    UI-->>Student: Open local project
```

#### Import commit failed: rollback staged data

```mermaid
sequenceDiagram
    autonumber
    participant UI as Project editor
    participant Flow as Project draft flow
    participant Store as Local project and attachment stores

    UI->>Flow: Import validated staged project
    Flow->>Store: Commit project and attachment references
    Store-->>Flow: Commit fails
    Flow->>Store: Roll back staged bytes where possible
    Flow-->>UI: Failure, preserve existing project
    UI-->>UI: Offer retry without changing existing projects
```

Source and target-compilation checks exist for the platform adapters. Actual
device save/reopen, accessibility, and cleanup acceptance remain open. An
exported file is student-owned; exporting does not create cloud sync.

### 5.8 D-106 case-AI conversation — mobile path source-wired; provider gated

The mobile case-AI client is connected to the authenticated conversation
start/turn/clear routes. Each request requires a signed-in case session and an
explicit, request-level choice to share the displayed feedback, claim, and
selected evidence. The current provider configuration is disabled; the
successful-response branch below is a future gated path, not a live capability.
The client holds a bounded transcript for the active context; the server stores
conversation metadata rather than raw dialogue. `next_action` and unmapped
proposal fields remain preview-only. Supported claim, scope, and limitation
proposals change the draft only after explicit application, and the student must
submit again for a fresh deterministic result.

```mermaid
sequenceDiagram
    autonumber
    actor Student
    participant UI as Case AI panel
    participant App as EvidriloApp
    participant Client as KMP AiGateway
    participant API as AI conversation API
    participant Auth as Verified account
    participant Context as Published case context
    participant Store as Conversation and turn store
    participant Server as AiConversationGateway
    participant Credits as Account credit ledger
    participant Provider as Optional AI provider
    participant Audit as AI audit store
    participant Reducer as ConclusionReducer
    participant Evaluator as ConclusionEvaluator

    Student->>UI: Confirm context sharing and submit a question
    UI->>App: Send bounded prompt for the active case
    App->>App: Build case, draft, and evaluation fingerprint
    App->>Client: Start or reuse matching account-scoped session
    Client->>API: POST /v1/ai/conversations
    API->>Auth: Verify signed-in account
    Auth-->>API: Account accepted
    API->>API: Require explicit request opt-in and validate request
    API->>Context: Rehydrate case and validate anchors/fingerprint
    Context-->>API: Context valid
    API->>Store: Create or replay metadata-only session
    Store-->>API: Session result
    API-->>Client: Session result
    Client-->>App: Session result
    App->>Client: Submit bounded turn with request key and per-turn opt-in
    Client->>API: POST /v1/ai/conversations/{id}/turns
    API->>Auth: Recheck signed-in account
    Auth-->>API: Account accepted
    API->>API: Require explicit per-turn opt-in and validate request
    API->>Context: Rebuild context and reject stale identity
    Context-->>API: Valid or stale context
    API->>Store: Reserve idempotent turn slot
    Store-->>API: Turn reservation or typed conflict/limit error
    alt Turn reservation created
        API->>Server: Generate from bounded, redacted prompt
        Server->>Credits: Check ledger consent and reserve one credit
        alt Credit reservation denied or replayed
            Credits-->>Server: No new reservation
            Server-->>API: Quota or idempotency fallback, do not call provider
        else Credit reserved
            Credits-->>Server: Request-bound reservation
            Server->>Provider: Invoke configured adapter (disabled in current build)
            alt Provider disabled or unavailable
                Provider-->>Server: No generated response or provider failure
                Server->>Credits: Release reserved credit
                Credits-->>Server: Released, no credit consumed
                Server-->>API: Typed fallback, draft unchanged
            else Future approved provider returns a response
                Provider-->>Server: Candidate explanation, question, or typed proposal
                Server->>Server: Validate purpose, anchors, schema, and proposal before-value
                alt Response is valid
                    Server->>Credits: Consume reserved credit for accepted output
                    Credits-->>Server: Settlement succeeded or failed
                    Server-->>API: Validated typed response or settlement fallback
                else Response is invalid or stale
                    Server->>Credits: Release reserved credit
                    Credits-->>Server: Released, no credit consumed
                    Server-->>API: Typed fallback, draft unchanged
                end
            end
        end
        API->>Store: Complete turn as accepted only for a successful response
        Store-->>API: Turn recorded or reservation released
        API->>Audit: Record safe outcome metadata
        API-->>Client: Typed response or error
        Client->>Client: Validate response purpose, session, turn, anchors, and before-values
        Client-->>App: Valid result or bounded failure
        alt Valid typed response
            App-->>UI: Show response or before/after preview, do not auto-apply
            opt Student applies a supported proposal
                Student->>UI: Choose Apply to draft
                UI->>App: Apply proposal
                App->>App: Check field allowlist and current before-value
                App->>Reducer: ConclusionEvent.UpdateDraft
                Reducer-->>App: Updated learner draft
                App-->>UI: Draft changed, not yet re-evaluated
                Student->>UI: Submit the revised draft
                UI->>Reducer: ConclusionEvent.Submit
                Reducer->>Evaluator: Re-evaluate the active case deterministically
                Evaluator-->>Reducer: New bounded case feedback
            end
        else Provider unavailable, rejected output, quota, replay, or turn error
            App-->>UI: Keep the draft and case result unchanged
        end
    else Turn replayed, in progress, stale, or over limit
        API-->>Client: Typed API error, do not generate
        Client-->>App: Bounded failure
        App-->>UI: Keep the draft and case result unchanged
    end
    opt Student clears a completed conversation
        UI->>App: Clear completed chat (control is hidden during an active request)
        App->>UI: Clear local transcript and detach local session immediately
        App->>Client: Request server conversation clear
        Client->>API: DELETE /v1/ai/conversations/{sessionId}
        API->>Auth: Verify signed-in account
        Auth-->>API: Account accepted
        API->>Store: Clear only this account's session and turn metadata
        Store-->>API: Cleared, absent, in-progress, or error
        API-->>Client: Clear result
        Client-->>App: Clear result (current UI does not surface this result)
        App->>Client: Refresh account credit balance
        Client->>API: GET /v1/ai/credits
        API->>Credits: Read account balance
        Credits-->>API: Current balance
        API-->>Client: Balance result
        Client-->>App: Balance or refresh failure
        App-->>UI: Show ready/unavailable state, keep local transcript cleared
    end
```

The case-AI reducer path is not the student-project reducer path. Mobile source,
focused tests, and Android compilation are verified; provider operation,
device interaction, accessibility, iOS runtime, and human usefulness remain
open. A dismiss or unsupported proposal leaves the draft unchanged. The current
Clear action removes the local transcript immediately and requests server
metadata deletion separately; the UI does not currently surface a failed delete,
so the server's 30-minute session expiry remains the cleanup bound in that case.

### 5.9 Remove an attachment from the current project revision — Current source; device acceptance gated

Attachment removal is a revision change, not an instruction to erase every
stored copy. The editor saves pending changes first, asks for confirmation, and
removes the attachment reference only from the new current checkpoint. Older
revision snapshots keep their references; physical bytes are removed only when
no retained revision refers to them.

```mermaid
sequenceDiagram
    autonumber
    actor Student
    participant UI as Project editor
    participant Flow as StudentProjectDraftFlow
    participant Projects as Local project store
    participant Files as Attachment store and deletion fence

    Student->>UI: Choose Remove attachment
    UI->>Student: Confirm removal from the current project revision
    alt Student cancels
        Student-->>UI: Cancel
        UI-->>Student: Keep project metadata and file unchanged
    else Student confirms
        UI->>Flow: Save dirty project edits
        Flow->>Projects: Persist current working snapshot
        Projects-->>Flow: Saved or failure
        alt Save failed
            Flow-->>UI: Stop removal
            UI-->>Student: Keep attachment and project unchanged
        else Current snapshot saved
            UI->>Flow: removeAttachment(projectId, attachmentId)
            Flow->>Projects: Load current project and revision history
            Projects-->>Flow: Current project state
            Flow->>Files: Prepare per-attachment deletion fence
            Files-->>Flow: Fence prepared or failure
            Flow->>Projects: Save new checkpoint without current attachment reference
            Projects-->>Flow: Checkpoint saved or failure
            alt Checkpoint failed
                Flow->>Files: Cancel fence or recover using saved references
                Files-->>Flow: Attachment retained or recovery pending
                Flow-->>UI: Return save failure
                UI-->>Student: Keep current attachment and report recovery state
            else Checkpoint saved
                Flow->>Files: Reconcile file against current and historical references
                alt Earlier revision still references file
                    Files-->>Flow: Retain file bytes for revision history
                else No retained revision references file
                    Files->>Files: Remove bytes from private local storage
                    Files-->>Flow: Cleanup result
                end
                Flow-->>UI: Updated project and cleanup status
                UI-->>Student: Show removal result without rewriting older revisions
            end
        end
    end
```

Local flow tests cover checkpoint failure, recovery, and historical reference
retention. Android/iOS device cleanup, persistence, and accessibility remain
separate acceptance gates.

## 6. Related product journeys

The following diagrams stay in the product workflow guide because they describe
student choices rather than internal request/response details:

- app entry, sign-in gates, and return destinations;
- local project lifecycle, archive, Trash, and revision restore;
- M0 case practice and evidence-change challenge;
- contextual D-106 case AI versus the gated D-119 project/general AI target;
- profile, settings, account, support, and history;
- optional local reminders.

See [`../product/workflows.md`](../product/workflows.md) for those journeys and
their Current/Gated/Target labels. For API route schemas, see
[`../api/README.md`](../api/README.md); for verification boundaries, see
[`../testing.md`](../testing.md) and [`../release.md`](../release.md).
