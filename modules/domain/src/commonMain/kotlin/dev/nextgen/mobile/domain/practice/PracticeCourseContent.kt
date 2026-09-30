package dev.nextgen.mobile.domain.practice

data class PracticeStudyCard(
    val id: String, val title: String, val context: String, val method: String,
    val result: String, val limitation: String,
)

data class PracticeSurveyResponse(val id: String, val respondent: String, val quiet: Boolean)

object PracticeCourseContent {
    const val VERSION = "synthetic-practice-course-1"
    val studies = listOf(
        PracticeStudyCard("STUDY-A", "A · Recall after a week",
            "24 first-year volunteers at one fictional campus.",
            "12 practiced a quiz and 12 reread the same material for 30 minutes; groups were randomly assigned.",
            "At seven days, mean recall was 7/10 after quiz practice and 5/10 after rereading.",
            "Small volunteer sample; one material and one delayed recall measure."),
        PracticeStudyCard("STUDY-B", "B · Recall on the same day",
            "18 volunteers from a different fictional course.",
            "The same learners used both methods in a fixed order, then took an immediate quiz.",
            "Mean immediate recall was 8/10 after quiz practice and 6/10 after rereading.",
            "Fixed order can influence results; the follow-up differs from Study A."),
        PracticeStudyCard("STUDY-C", "C · A preference survey",
            "46 self-selected students at a third fictional campus.",
            "An anonymous survey asked which study method they preferred; recall was not measured.",
            "31/46 respondents said they preferred rereading.",
            "Preference is a different outcome from recall; self-selection limits reach."),
    )
    val fourthStudy = PracticeStudyCard("STUDY-D", "D · A new delayed comparison",
        "12 volunteers in another fictional course.",
        "Six used quiz practice and six reread; recall was checked after 14 days.",
        "Both groups averaged 5/10 at 14 days: no observed difference on this measure.",
        "Another small sample, material, and follow-up. It does not erase A or B.")

    val responses = listOf(
        PracticeSurveyResponse("R01", "A", true), PracticeSurveyResponse("R02", "B", false),
        PracticeSurveyResponse("R03", "C", true), PracticeSurveyResponse("R04", "D", true),
        PracticeSurveyResponse("R05", "E", false), PracticeSurveyResponse("R06", "F", true),
        PracticeSurveyResponse("R07", "C", true), PracticeSurveyResponse("R08", "G", false),
    )
    val correctedResponses get() = responses.filterNot { it.id == "R07" }
    const val QUIET = "COUNT-QUIET"
    const val GROUP = "COUNT-GROUP"

    fun title(id: PracticeLessonId): String = when (id) {
        PracticeLessonId.TABLET -> "The tablet investigation"
        PracticeLessonId.STUDIES -> "When studies tell different stories"
        PracticeLessonId.SURVEY -> "One response changes the picture"
    }
    fun skill(id: PracticeLessonId): String = when (id) {
        PracticeLessonId.TABLET -> "Trace a claim back to observations."
        PracticeLessonId.STUDIES -> "Synthesize findings without flattening their differences."
        PracticeLessonId.SURVEY -> "Reconsider a claim when its data changes."
    }
    fun mission(id: PracticeLessonId): String = when (id) {
        PracticeLessonId.TABLET -> "What can the supplied dissolution times tell you about these three water conditions?"
        PracticeLessonId.STUDIES -> "What do three supplied cards support about quiz practice and rereading?"
        PracticeLessonId.SURVEY -> "What can a small, self-selected survey tell you about study-space preferences?"
    }
    fun predictions(id: PracticeLessonId): List<String> = when (id) {
        PracticeLessonId.TABLET -> listOf("The times may differ.", "I need to see the observations.", "The times may be similar.")
        PracticeLessonId.STUDIES -> listOf("The findings may agree.", "Different methods may matter.", "I want to inspect the cards first.")
        PracticeLessonId.SURVEY -> listOf("The counts may suggest a preference.", "The sample may limit the claim.", "I need to inspect the records first.")
    }
    fun evidenceIds(id: PracticeLessonId, changed: Boolean): Set<String> = when (id) {
        PracticeLessonId.STUDIES -> (studies.map { it.id } + if (changed) listOf(fourthStudy.id) else emptyList()).toSet()
        PracticeLessonId.SURVEY -> setOf(QUIET, GROUP)
        PracticeLessonId.TABLET -> emptySet()
    }
    fun limitations(id: PracticeLessonId): List<PracticeLimitation> = when (id) {
        PracticeLessonId.STUDIES -> listOf(PracticeLimitation.DIFFERENT_MEASURES, PracticeLimitation.SMALL_SAMPLE)
        PracticeLessonId.SURVEY -> listOf(PracticeLimitation.SELF_SELECTED, PracticeLimitation.SMALL_SAMPLE)
        PracticeLessonId.TABLET -> emptyList()
    }
    fun changeChoices(id: PracticeLessonId): Set<PracticeChangeChoice> = when (id) {
        PracticeLessonId.STUDIES -> setOf(PracticeChangeChoice.RECONSIDER, PracticeChangeChoice.DISCARD_EARLIER, PracticeChangeChoice.UNIVERSALIZE)
        PracticeLessonId.SURVEY -> setOf(PracticeChangeChoice.REMOVE_DUPLICATE, PracticeChangeChoice.KEEP_DUPLICATE, PracticeChangeChoice.REMOVE_BOTH)
        PracticeLessonId.TABLET -> emptySet()
    }
    fun isSupportedChange(id: PracticeLessonId, choice: PracticeChangeChoice?): Boolean =
        (id == PracticeLessonId.STUDIES && choice == PracticeChangeChoice.RECONSIDER) ||
            (id == PracticeLessonId.SURVEY && choice == PracticeChangeChoice.REMOVE_DUPLICATE)

    fun limitationText(limit: PracticeLimitation): String = when (limit) {
        PracticeLimitation.SMALL_SAMPLE -> "A small sample does not represent every student."
        PracticeLimitation.DIFFERENT_MEASURES -> "Recall time, method, population, and preference are different."
        PracticeLimitation.SELF_SELECTED -> "Respondents chose to participate at one fictional campus."
    }
    fun actionText(action: PracticeNextAction): String = when (action) {
        PracticeNextAction.BROADER_SAMPLE -> "Include a broader, planned sample"
        PracticeNextAction.SAME_MEASURE -> "Compare the same outcome and follow-up"
        PracticeNextAction.CLAIM_PROVEN -> "Treat this as conclusive proof"
    }
    fun changeText(choice: PracticeChangeChoice): String = when (choice) {
        PracticeChangeChoice.RECONSIDER -> "Keep all findings and reconsider the pattern"
        PracticeChangeChoice.DISCARD_EARLIER -> "Discard A and B because D differs"
        PracticeChangeChoice.UNIVERSALIZE -> "Say quiz practice always works better"
        PracticeChangeChoice.REMOVE_DUPLICATE -> "Keep R03 and remove only duplicate R07"
        PracticeChangeChoice.KEEP_DUPLICATE -> "Keep both submissions as two respondents"
        PracticeChangeChoice.REMOVE_BOTH -> "Remove both of C's submissions"
    }
}
