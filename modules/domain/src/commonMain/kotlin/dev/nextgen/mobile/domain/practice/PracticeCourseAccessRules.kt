package dev.nextgen.mobile.domain.practice

/** Local course access. The caller supplies the current account's verified Pro entitlement. */
object PracticeCourseAccessRules {
    const val FREE_CASE_COUNT = 1
    val proCaseCount: Int get() = PracticeLessonId.entries.size

    fun canOpen(lesson: PracticeLessonId, hasVerifiedProAccess: Boolean): Boolean =
        lesson == PracticeLessonId.TABLET || hasVerifiedProAccess

    /** Gate every mutation, including resume/restart and transitions from an already saved session. */
    fun reduce(
        state: PracticeCourseState,
        event: PracticeCourseEvent,
        hasVerifiedProAccess: Boolean,
    ): PracticeCourseState {
        val allowed = when (event) {
            PracticeCourseEvent.Leave -> true
            is PracticeCourseEvent.Open -> canOpen(event.lesson, hasVerifiedProAccess)
            is PracticeCourseEvent.Restart -> canOpen(event.lesson, hasVerifiedProAccess)
            PracticeCourseEvent.TabletCompleted -> true
            else -> state.active?.let { canOpen(it, hasVerifiedProAccess) } == true
        }
        return if (allowed) PracticeCourseReducer.reduce(state, event) else state
    }
}
