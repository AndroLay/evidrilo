package dev.nextgen.mobile.domain.practice

import dev.nextgen.mobile.domain.conclusion.ConclusionStatus

data class PracticeCourseCheck(
    val title: String, val status: ConclusionStatus, val explanation: String,
    val nextMove: String, val anchors: List<String>,
)

/** Checks declared links/decisions only. It never grades the meaning of arbitrary prose. */
object PracticeCourseEvaluator {
    fun evaluate(lesson: PracticeLessonId, draft: PracticeLessonDraft, changed: Boolean): List<PracticeCourseCheck> {
        if (lesson == PracticeLessonId.TABLET) return listOf(PracticeCourseCheck(
            "Use the tablet evaluator", ConclusionStatus.CANNOT_ASSESS,
            "The tablet case is assessed by the existing conclusion evaluator.", "Return to the tablet workflow.", emptyList()))
        val active = PracticeCourseContent.evidenceIds(lesson, changed)
        val checks = mutableListOf<PracticeCourseCheck>()
        checks += PracticeCourseCheck("Active evidence",
            if (draft.evidence.isEmpty()) ConclusionStatus.INCOMPLETE else if (draft.evidence.any { it !in active }) ConclusionStatus.CANNOT_ASSESS else ConclusionStatus.PASS,
            if (draft.evidence.all { it in active } && draft.evidence.isNotEmpty()) "Every selected link belongs to this synthetic evidence set."
            else "An absent or unknown link cannot support this case.",
            "Inspect and select available records.", draft.evidence.toList())
        if (lesson == PracticeLessonId.STUDIES) {
            val comparisonFits = draft.groups["STUDY-A"] == PracticeEvidenceGroup.SIMILAR &&
                draft.groups["STUDY-B"] == PracticeEvidenceGroup.SIMILAR && draft.groups["STUDY-C"] == PracticeEvidenceGroup.CONTEXT
            checks += PracticeCourseCheck("Compare like outcomes",
                if (comparisonFits) ConclusionStatus.PASS else ConclusionStatus.ACTION_REQUIRED,
                "A and B both record higher recall after quiz practice on their own measures. C measures preference, so its result is context, not a recall contradiction.",
                "Keep A/B's observed pattern distinct from C's preference result; do not pool unlike measures.", listOf("STUDY-A", "STUDY-B", "STUDY-C"))
            val linksFit = setOf("STUDY-A", "STUDY-B").all { it in draft.evidence } &&
                (!changed || "STUDY-D" in draft.evidence)
            checks += PracticeCourseCheck("Support the comparison", if (linksFit) ConclusionStatus.PASS else ConclusionStatus.ACTION_REQUIRED,
                if (changed) "D's 5/10 versus 5/10 adds a no-difference finding. A and B are retained; the combined pattern is mixed across these contexts."
                else "A and B are the two supplied recall comparisons. A synthesis that compares them needs links to both.",
                if (changed) "Connect D as well as A/B and reconsider the wording." else "Link both recall comparisons.", if (changed) listOf("STUDY-A", "STUDY-B", "STUDY-D") else listOf("STUDY-A", "STUDY-B"))
        } else {
            checks += PracticeCourseCheck("Counts and denominator",
                if (draft.evidence.contains(PracticeCourseContent.QUIET)) ConclusionStatus.PASS else ConclusionStatus.ACTION_REQUIRED,
                if (changed) "After removing R07 only, four of seven unique respondents prefer quiet space. Three prefer group space."
                else "Five of eight submitted rows prefer quiet space; three prefer group space. These are submission counts before duplicate review.",
                "Keep the count, denominator, and sampling boundary in view.", listOf(PracticeCourseContent.QUIET, PracticeCourseContent.GROUP, if (changed) "R03/R07" else "SURVEY-SAMPLE"))
        }
        checks += PracticeCourseCheck("Reach of the claim",
            if (draft.scope == PracticeClaimScope.SUPPLIED_RECORDS) ConclusionStatus.PASS else ConclusionStatus.ACTION_REQUIRED,
            if (lesson == PracticeLessonId.STUDIES) "Small, different source contexts do not establish one causal rule for all students."
            else "A small, self-selected survey at one campus does not establish every student's preference or a cause.",
            "Limit the declared scope to the supplied records.", listOf(if (lesson == PracticeLessonId.STUDIES) "STUDY-A" else "SURVEY-SAMPLE"))
        val limitationFits = draft.limitation in PracticeCourseContent.limitations(lesson)
        checks += PracticeCourseCheck("Limit and next action",
            if (limitationFits && when (draft.action) {
                    PracticeNextAction.BROADER_SAMPLE -> draft.limitation == PracticeLimitation.SMALL_SAMPLE || draft.limitation == PracticeLimitation.SELF_SELECTED
                    PracticeNextAction.SAME_MEASURE -> draft.limitation == PracticeLimitation.DIFFERENT_MEASURES
                    else -> false
                }) ConclusionStatus.PASS else ConclusionStatus.ACTION_REQUIRED,
            "A wider sampling limitation calls for planned broader sampling; unlike outcomes call for a comparable measure. Neither turns the present claim into proof.",
            "Choose a next action that responds to your selected limitation.", listOf(draft.limitation?.name ?: "LIMITATION"))
        return checks
    }

    fun changeExplanation(lesson: PracticeLessonId, choice: PracticeChangeChoice?): String = when {
        choice == null -> "Choose a decision before reviewing its consequence."
        lesson == PracticeLessonId.STUDIES && choice == PracticeChangeChoice.RECONSIDER ->
            "D adds a no-difference observation. Keep A and B, include D, and reconsider how far the pattern reaches. C still measures preference."
        lesson == PracticeLessonId.STUDIES ->
            "D differs on another sample and follow-up. It neither erases A/B nor supports an always-better claim. Reconsider with all active cards."
        choice == PracticeChangeChoice.REMOVE_DUPLICATE ->
            "R07 repeats respondent C's R03 submission. Retain R03, remove only R07: quiet 5/8 becomes 4/7; group 3/8 becomes 3/7."
        choice == PracticeChangeChoice.REMOVE_BOTH ->
            "R03 is C's retained response. Removing both would exclude that respondent entirely. Remove only the duplicated submission R07."
        else -> "Counting R03 and R07 as two people double-counts C. Keep the retained R03 and remove only R07."
    }
}
