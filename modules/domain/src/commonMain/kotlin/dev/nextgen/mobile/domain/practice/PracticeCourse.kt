package dev.nextgen.mobile.domain.practice

/** A local, synthetic learning path. It does not represent a student Project. */
enum class PracticeLessonId { TABLET, STUDIES, SURVEY }
enum class PracticeLessonStage {
    MISSION, PREDICTION, INSPECT, ORGANIZE, CLAIM, SCOPE, LIMITS, ACTION,
    FEEDBACK, CHANGE, REVISION, FINAL_REVIEW, COMPLETE,
}
enum class PracticeEvidenceGroup { SIMILAR, DIFFERENT, CONTEXT }
enum class PracticeClaimScope { SUPPLIED_RECORDS, ALL_STUDENTS, GENERAL_CAUSE }
enum class PracticeLimitation { SMALL_SAMPLE, DIFFERENT_MEASURES, SELF_SELECTED }
enum class PracticeNextAction { BROADER_SAMPLE, SAME_MEASURE, CLAIM_PROVEN }
enum class PracticeChangeChoice { RECONSIDER, DISCARD_EARLIER, UNIVERSALIZE, REMOVE_DUPLICATE, KEEP_DUPLICATE, REMOVE_BOTH }

data class PracticeLessonDraft(
    val evidence: Set<String> = emptySet(),
    val groups: Map<String, PracticeEvidenceGroup> = emptyMap(),
    val claim: String = "",
    val scope: PracticeClaimScope? = null,
    val limitation: PracticeLimitation? = null,
    val limitationNote: String = "",
    val action: PracticeNextAction? = null,
    val actionReason: String = "",
    val evidenceVersion: Int = 1,
)

data class PracticeLessonSession(
    val lesson: PracticeLessonId,
    val stage: PracticeLessonStage = PracticeLessonStage.MISSION,
    val prediction: String? = null,
    val draft: PracticeLessonDraft = PracticeLessonDraft(),
    val initial: PracticeLessonDraft? = null,
    val revised: PracticeLessonDraft? = null,
    val changeChoice: PracticeChangeChoice? = null,
    val changeReviewed: Boolean = false,
)

data class PracticeCourseState(
    val active: PracticeLessonId? = null,
    val sessions: Map<PracticeLessonId, PracticeLessonSession> = emptyMap(),
)

sealed interface PracticeCourseEvent {
    data class Open(val lesson: PracticeLessonId) : PracticeCourseEvent
    data object Leave : PracticeCourseEvent
    data object Continue : PracticeCourseEvent
    data object Back : PracticeCourseEvent
    data class Predict(val value: String?) : PracticeCourseEvent
    data class Edit(val draft: PracticeLessonDraft) : PracticeCourseEvent
    data class ChooseChange(val choice: PracticeChangeChoice) : PracticeCourseEvent
    data object ReviewChange : PracticeCourseEvent
    data object UseChangedEvidence : PracticeCourseEvent
    data object SubmitRevision : PracticeCourseEvent
    data object Finish : PracticeCourseEvent
    data class Restart(val lesson: PracticeLessonId) : PracticeCourseEvent
    data object TabletCompleted : PracticeCourseEvent
}

object PracticeCourseReducer {
    fun reduce(state: PracticeCourseState, event: PracticeCourseEvent): PracticeCourseState {
        if (event is PracticeCourseEvent.Open) {
            return state.copy(active = event.lesson, sessions = state.sessions +
                (event.lesson to (state.sessions[event.lesson] ?: PracticeLessonSession(event.lesson))))
        }
        if (event == PracticeCourseEvent.Leave) return state.copy(active = null)
        if (event is PracticeCourseEvent.Restart) return state.copy(
            active = event.lesson, sessions = state.sessions + (event.lesson to PracticeLessonSession(event.lesson)),
        )
        if (event == PracticeCourseEvent.TabletCompleted) {
            val tablet = state.sessions[PracticeLessonId.TABLET] ?: return state
            return state.copy(sessions = state.sessions + (PracticeLessonId.TABLET to tablet.copy(stage = PracticeLessonStage.COMPLETE)))
        }
        val id = state.active ?: return state
        val session = state.sessions[id] ?: return state
        val next = when (event) {
            is PracticeCourseEvent.Predict -> if (session.stage == PracticeLessonStage.PREDICTION &&
                (event.value == null || event.value in PracticeCourseContent.predictions(id)))
                session.copy(prediction = event.value) else session
            is PracticeCourseEvent.Edit -> if (id != PracticeLessonId.TABLET && session.stage in editableStages &&
                bounded(id, event.draft) && event.draft.evidenceVersion == session.draft.evidenceVersion)
                session.copy(draft = event.draft) else session
            PracticeCourseEvent.Continue -> advance(session)
            PracticeCourseEvent.Back -> back(session)
            is PracticeCourseEvent.ChooseChange -> if (session.stage == PracticeLessonStage.CHANGE &&
                event.choice in PracticeCourseContent.changeChoices(id)) session.copy(changeChoice = event.choice, changeReviewed = false) else session
            PracticeCourseEvent.ReviewChange -> if (session.stage == PracticeLessonStage.CHANGE && session.changeChoice != null && session.initial != null) {
                if (PracticeCourseContent.isSupportedChange(id, session.changeChoice)) {
                    session.copy(stage = PracticeLessonStage.REVISION, changeReviewed = true, draft = session.initial)
                } else session.copy(changeReviewed = true)
            } else session
            PracticeCourseEvent.UseChangedEvidence -> if (session.stage == PracticeLessonStage.REVISION)
                session.copy(draft = session.draft.copy(evidenceVersion = 2)) else session
            PracticeCourseEvent.SubmitRevision -> if (session.stage == PracticeLessonStage.REVISION && session.revised == null &&
                inputProblem(session) == null) session.copy(stage = PracticeLessonStage.FINAL_REVIEW, revised = session.draft) else session
            PracticeCourseEvent.Finish -> if (session.stage == PracticeLessonStage.FINAL_REVIEW && session.revised != null)
                session.copy(stage = PracticeLessonStage.COMPLETE) else session
            else -> session
        }
        return state.copy(sessions = state.sessions + (id to next))
    }

    private val editableStages = setOf(
        PracticeLessonStage.INSPECT, PracticeLessonStage.ORGANIZE, PracticeLessonStage.CLAIM,
        PracticeLessonStage.SCOPE, PracticeLessonStage.LIMITS, PracticeLessonStage.ACTION, PracticeLessonStage.REVISION,
    )

    private fun bounded(id: PracticeLessonId, draft: PracticeLessonDraft): Boolean =
        draft.claim.length <= 320 && draft.limitationNote.length <= 240 && draft.actionReason.length <= 240 &&
            draft.evidence.size <= 4 && draft.groups.size <= 3 && draft.evidenceVersion in 1..2 &&
            draft.evidence.all { it in PracticeCourseContent.evidenceIds(id, draft.evidenceVersion == 2) } &&
            draft.groups.keys.all { id == PracticeLessonId.STUDIES && it in setOf("STUDY-A", "STUDY-B", "STUDY-C") } &&
            (draft.limitation == null || draft.limitation in PracticeCourseContent.limitations(id)) &&
            (id != PracticeLessonId.SURVEY || draft.action != PracticeNextAction.SAME_MEASURE)

    fun revisionEvidenceProblem(session: PracticeLessonSession): String? = when {
        session.draft.evidenceVersion != 2 -> "Confirm that your revision uses the changed evidence."
        session.lesson == PracticeLessonId.STUDIES &&
            !session.draft.evidence.containsAll(setOf("STUDY-A", "STUDY-B", "STUDY-D")) ->
            "Connect A, B, and the new Study D to compare the recall findings."
        session.lesson == PracticeLessonId.SURVEY &&
            !session.draft.evidence.containsAll(setOf(PracticeCourseContent.QUIET, PracticeCourseContent.GROUP)) ->
            "Connect both corrected counts to keep the comparison visible."
        session.draft.evidence.isEmpty() -> "Connect at least one active record."
        else -> null
    }

    private fun completeDraftProblem(session: PracticeLessonSession): String? {
        val draft = session.draft
        return when {
            !bounded(session.lesson, draft) -> "Use only the supplied records and choices."
            draft.evidence.isEmpty() -> "Connect at least one supplied record."
            session.lesson == PracticeLessonId.STUDIES &&
                !draft.groups.keys.containsAll(setOf("STUDY-A", "STUDY-B", "STUDY-C")) -> "Place all three cards on the comparison board."
            claimProblem(draft) != null -> claimProblem(draft)
            draft.scope == null -> "Choose the scope of your claim."
            draft.limitation == null -> "Choose a supplied limitation."
            draft.limitationNote.trim().length !in 10..240 -> "Explain the limit in 10–240 characters."
            draft.action == null -> "Choose a next action."
            draft.actionReason.trim().length !in 10..240 -> "Explain your reason in 10–240 characters."
            else -> null
        }
    }

    fun inputProblem(session: PracticeLessonSession): String? {
        val draft = session.draft
        return when (session.stage) {
            PracticeLessonStage.INSPECT -> if (draft.evidence.isEmpty()) "Connect at least one supplied record." else null
            PracticeLessonStage.ORGANIZE -> if (session.lesson == PracticeLessonId.STUDIES &&
                !draft.groups.keys.containsAll(listOf("STUDY-A", "STUDY-B", "STUDY-C")))
                "Place each of the three cards on your comparison board." else null
            PracticeLessonStage.CLAIM -> claimProblem(draft)
            PracticeLessonStage.SCOPE -> if (draft.scope == null) "Choose the scope of your claim." else null
            PracticeLessonStage.LIMITS -> if (draft.limitation == null) "Choose a supplied limitation."
                else if (draft.limitationNote.trim().length !in 10..240) "Explain the limit in 10–240 characters." else null
            PracticeLessonStage.ACTION -> completeDraftProblem(session)
            PracticeLessonStage.REVISION -> revisionEvidenceProblem(session) ?: completeDraftProblem(session)
            else -> null
        }
    }

    private fun claimProblem(draft: PracticeLessonDraft): String? =
        if (draft.claim.trim().length !in 20..320) "Write your own claim in 20–320 characters." else null

    private fun advance(session: PracticeLessonSession): PracticeLessonSession {
        if (inputProblem(session) != null) return session
        val next = when (session.stage) {
            PracticeLessonStage.MISSION -> PracticeLessonStage.PREDICTION
            PracticeLessonStage.PREDICTION -> PracticeLessonStage.INSPECT
            PracticeLessonStage.INSPECT -> if (session.lesson == PracticeLessonId.STUDIES) PracticeLessonStage.ORGANIZE else PracticeLessonStage.CLAIM
            PracticeLessonStage.ORGANIZE -> PracticeLessonStage.CLAIM
            PracticeLessonStage.CLAIM -> PracticeLessonStage.SCOPE
            PracticeLessonStage.SCOPE -> PracticeLessonStage.LIMITS
            PracticeLessonStage.LIMITS -> PracticeLessonStage.ACTION
            PracticeLessonStage.ACTION -> PracticeLessonStage.FEEDBACK
            PracticeLessonStage.FEEDBACK -> PracticeLessonStage.CHANGE
            else -> return session
        }
        return if (next == PracticeLessonStage.FEEDBACK && session.initial == null)
            session.copy(stage = next, initial = session.draft) else session.copy(stage = next)
    }

    private fun back(session: PracticeLessonSession): PracticeLessonSession {
        if (session.initial != null) return session // Frozen work is reopened only as the one revision.
        val previous = when (session.stage) {
            PracticeLessonStage.PREDICTION -> PracticeLessonStage.MISSION
            PracticeLessonStage.INSPECT -> PracticeLessonStage.PREDICTION
            PracticeLessonStage.ORGANIZE -> PracticeLessonStage.INSPECT
            PracticeLessonStage.CLAIM -> if (session.lesson == PracticeLessonId.STUDIES) PracticeLessonStage.ORGANIZE else PracticeLessonStage.INSPECT
            PracticeLessonStage.SCOPE -> PracticeLessonStage.CLAIM
            PracticeLessonStage.LIMITS -> PracticeLessonStage.SCOPE
            PracticeLessonStage.ACTION -> PracticeLessonStage.LIMITS
            else -> return session
        }
        return session.copy(stage = previous)
    }
}
