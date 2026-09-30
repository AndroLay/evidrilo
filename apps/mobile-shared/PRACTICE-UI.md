# Native Practice ownership and integration

The owner authorized completing all three contexts on 2026-09-30. This scope covers the Practice destination, its dedicated components, and independent local lesson progress. Projects, provider contracts, accounts, and premium content remain under their existing hosts.

## Files and ownership

- `EvidriloPracticeCourseScreen.kt`: case chooser, resume/save/error handling, optional Project navigation, and tablet delegation.
- `EvidriloPracticeCourseLesson.kt`: missions, optional predictions, authored claims, feedback, one revision, retained comparison, and help.
- `EvidriloPracticeCourseEvidence.kt`: source cards, tap-operated comparison board, linked records, and survey charts.
- `EvidriloPracticeCourseStorage.kt` and platform actuals: Android SharedPreferences, iOS NSUserDefaults, and an explicitly unavailable JVM store.
- `modules/domain/.../practice/`: typed lesson events, synthetic facts, and deterministic checks of selected links/decisions.
- `modules/data/.../storage/PracticeCourseStore.kt`: separate bounded/versioned local record and corrupt/failed-save states.
- `EvidriloPracticeScreens.kt`, `EvidriloPracticePresentation.kt`, and `EvidriloPracticeScreen.kt`: existing tablet tasks, feedback, one revision, and the missing-cold-observation challenge.
- `EvidriloApp.kt`: Practice destination branch, tablet context flag, and a private extraction of the existing legacy Practice renderer to avoid the JVM method-size bound. The four existing draft/feedback/challenge adapters stay compatible with premium callers.

Other agents may edit the surrounding application. Keep Practice changes in these dedicated files and preserve unrelated work.

## Three complete contexts

| Case | Evidence interaction | Evidence change | Retained comparison |
| --- | --- | --- | --- |
| Tablet dissolution | Three measured-time observations, source links, bounded claim | Cold-water observation becomes unavailable | Original reducer retains the first draft, one revision, and fresh challenge draft |
| Synthetic study comparison | A/B recall results, C preference result, three-part comparison board | New D records no observed recall difference at 14 days | Initial and revised claims, links, scope, board decisions, limitation, and next action |
| Synthetic study-space survey | Anonymous rows, counts and denominators | Remove duplicate R07 while retaining respondent C's R03; 5/8 becomes 4/7 | Both evidence versions, claims, scope, limitation, and next action |

The chooser recommends tablet → studies → survey but permits direct opening, pausing, resuming, and revisiting. There are no progression locks, XP, or mastery claims. New attempts replace only the selected course case after explicit confirmation. These authored fixtures are synthetic; this implementation does not establish reviewed source quality or student learning effectiveness.

## State, feedback, and recovery

Tablet updates, submission, the single revision, challenge, account/access policy, history, and storage stay on the existing host. The two new cases freeze the initial draft before feedback, reveal one evidence event, and accept one revision. Study revisions require links to A/B/D; survey revisions require both corrected count anchors. Incomplete entries cannot submit. Structurally complete attempts can retain unresolved reasoning checks; completion is an attempt, not certification of correctness.

Checks assess supplied references, declared scope, grouping, and limit/action consistency. Free prose is learner-authored and is not automatically assessed as scientific truth. The final review exposes both versions and expandable retained decisions. No generated answer or synthetic content is copied into Projects.

Course progress uses a separate record (`evidrilo_practice_course_v1` on Android). It does not read/write tablet history, Projects, or chat. Saving, failure, retry, unavailable storage, and corrupt data are explicit. Corrupt records are retained until the learner explicitly chooses replacement. Exit saves are serialized and learner mutations pause during the exit write. The JVM build labels course progress temporary because it has no persistent platform store.

The existing case-linked assistant is available only in the delegated tablet context. Study/survey help is optional authored local guidance, not a response from an AI provider. This avoids presenting tablet context as assistance for a different case. Existing audio callbacks and selection effects remain delegated to the old presentation policy.

## Native interaction and motion

Use the shared Evidrilo theme, Source Sans 3, cobalt/navy colors, icons, and tactile buttons. Fixed navigation/actions respect drawing and IME insets; the body scrolls independently. Details, hints, and retained decisions expand on demand. Study board controls have a tap alternative and 48 dp minimum height. Progress, selected connections, disclosures, and corrected survey proportions use brief Compose motion; charts retain labels and stable scales. No artwork, provider, or dependency was added.

Tablet field bounds remain claim 20–320 characters, notes 10–240, up to three observation links, and two limitation links. New cases use the same text bounds. Before and after values stay available as readable text. Overlong tablet recovery text is not silently truncated.

## Android debug preview

`apps/android/src/debug/kotlin/dev/nextgen/mobile/android/PracticePreviewActivity.kt` hosts the same native course UI with a fresh original tablet reducer and an in-memory course store. It does not initialize account/project/history storage or call providers. The activity is only in the debug manifest and is absent from Release.

```sh
adb -s emulator-5554 shell am start -n dev.nextgen.mobile/.android.PracticePreviewActivity
```

Closing/recreating this activity starts a temporary fixture. It cannot establish real application persistence or access behavior.

## Evidence boundary

No automated test suite was added or run for this task. Build and native evidence must identify the concurrent working-tree snapshot. A compile or walkthrough does not establish iOS runtime behavior, provider availability, TalkBack compliance, fully docked IME behavior, physical-device performance, or student learning effectiveness.

The earlier observation below belongs to the previous tablet-only APK; it is historical evidence and does not validate the new course host.

## Earlier tablet-only observation on 2026-09-30

The final working-tree build completed with exit code 0 for `:composeApp:compileKotlinJvm` and `:androidApp:assembleDebug`, using offline Gradle, one worker, and the in-process Kotlin compiler. No test task was requested or executed. `git diff --check` also completed without findings.

This is an uncommitted, concurrent working-tree result over HEAD `85582b8a920106870ef10d7a59669f5f8b9a5238`, not verification of that commit alone. The final APK was generated at 17:25:41 +0800. Its SHA-256 is `1009b959793e645a320b2ca448d7f66931c00517f7e3435ac58812091081a806`; the final `EvidriloPracticeScreens.kt` SHA-256 is `45241bff653adc7c7934449a2a9960f655581e56f78b143de43f22203ac25e53`.

The Android API 35 preview was inspected at 1080 × 1920. The initial inspection covered all six tasks, supplied-evidence selection, authored claim input, existing evaluator feedback, retained initial claim beside one revision, a fresh evidence-change draft, active observations after withdrawal, challenge feedback, and the final comparison. Help-sheet dismissal and cancellation of reset retained the in-memory draft. The first evidence screen was also viewed with 130% system text and the dark theme; settings were restored afterwards.

One final confirmation used the final APK above. Selection showed two connected observations, task navigation reached claim and scope, and returning retained the authored claim. Advancing cleared input focus. Native screenshots are saved locally under the ignored `artifacts/practice-ui/` directory: `evidence.png`, `selected-evidence.png`, and `claim.png`.

The real application entry requested sign-in and its gate was preserved. The authenticated journey, persistence/recovery, premium-case runtime, floating-assistant overlap, iOS, TalkBack, reduced-motion behavior, and physical-device performance were not verified. The emulator exposed a compact Gboard toolbar during input; a fully docked software keyboard still needs device inspection. The debug preview cannot establish these broader boundaries.

## Final three-case observation on 2026-09-30

The final working-tree build completed with exit code 0 for `:composeApp:compileKotlinJvm` and `:androidApp:assembleDebug`, using the plan's offline Gradle command. No test tasks were run. Scoped `git diff --check` had no findings. This is a concurrent, uncommitted overlay over HEAD `85582b8a920106870ef10d7a59669f5f8b9a5238`, not proof of that commit alone.

The final APK was generated at 2026-09-30T20:40:13.483882+08:00. SHA-256: `dcb1bd2f41b62093fbdb4d954e392fac42d5081724f4b1928b50eaee7452f4a6`. The ignored `artifacts/practice-course/build-record.json` records APK identity and 17 current Practice integration/source hashes; their latest modification preceded this APK.

The final APK was installed on Android API 35 at 1080 × 1920. In one final native preview confirmation, all three complete journeys were walked: tablet initial feedback, one retained revision, a fresh challenge with only warm/room observations, and completion; studies A/B/C grouping, initial response, new D, explicit evidence-version confirmation, A/B/C/D links, one revision, and completion; survey counts, original authored response, removal of only R07, explicit corrected evidence, one revised 4/7 claim, and completion. The chooser displayed three completed attempts. The preview store now exercises the same bounded production codec, while remaining temporary and isolated from application data. No save error was shown in this confirmation.

Native visual inspection covered the final tablet and study comparisons and the three-case chooser. Final images: `tablet-revision-final.png`, `tablet-complete-final.png`, `study-complete-final.png`, `survey-complete-final.png`, and `all-three-completed-final.png` under ignored `artifacts/practice-course/`. Before/after claims are adjacent; linked evidence and retained scope/limits/actions expand on request. The duplicated survey correction chart was removed. Finishing the tablet returns to Practice with its result retained; old premium callers keep their existing default finish behavior.

The earlier main-app entry required sign-in for the protected workflow; its gate was retained. Authenticated production navigation and real platform persistence/recovery were not walked. The preview cannot establish iOS runtime, physical-device performance, fully docked IME behavior, TalkBack compliance, provider availability, or student learning effectiveness. No automated tests were added or run.
