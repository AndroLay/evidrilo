package dev.nextgen.mobile.storage

import dev.nextgen.mobile.domain.practice.PracticeCourseState
import dev.nextgen.mobile.domain.practice.PracticeClaimScope
import dev.nextgen.mobile.domain.practice.PracticeEvidenceGroup
import dev.nextgen.mobile.domain.practice.PracticeLessonDraft
import dev.nextgen.mobile.domain.practice.PracticeLessonId
import dev.nextgen.mobile.domain.practice.PracticeLessonSession
import dev.nextgen.mobile.domain.practice.PracticeLessonStage
import dev.nextgen.mobile.domain.practice.PracticeLimitation
import dev.nextgen.mobile.domain.practice.PracticeNextAction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PracticeCourseStoreTest {
    @Test
    fun encodingSortsEvidenceGroupKeysAcrossMapInsertionOrders() {
        val fromReverseOrder = PracticeCourseCodec.encode(state(
            linkedMapOf(
                "STUDY-B" to PracticeEvidenceGroup.DIFFERENT,
                "STUDY-A" to PracticeEvidenceGroup.SIMILAR,
            ),
        ))
        val fromSortedOrder = PracticeCourseCodec.encode(state(
            linkedMapOf(
                "STUDY-A" to PracticeEvidenceGroup.SIMILAR,
                "STUDY-B" to PracticeEvidenceGroup.DIFFERENT,
            ),
        ))

        assertEquals(fromSortedOrder, fromReverseOrder)
        assertTrue(fromReverseOrder.contains("\"groups\":{\"STUDY-A\":\"SIMILAR\",\"STUDY-B\":\"DIFFERENT\"}"))
        assertEquals(state(linkedMapOf(
            "STUDY-A" to PracticeEvidenceGroup.SIMILAR,
            "STUDY-B" to PracticeEvidenceGroup.DIFFERENT,
        )), PracticeCourseCodec.decode(fromReverseOrder))
    }

    private fun state(groups: Map<String, PracticeEvidenceGroup>) = PracticeCourseState(
        active = PracticeLessonId.STUDIES,
        sessions = mapOf(
            PracticeLessonId.STUDIES to PracticeLessonSession(
                lesson = PracticeLessonId.STUDIES,
                stage = PracticeLessonStage.ORGANIZE,
                draft = PracticeLessonDraft(
                    evidence = setOf("STUDY-A", "STUDY-B"),
                    groups = groups,
                    claim = "The studies support a bounded comparison.",
                    scope = PracticeClaimScope.SUPPLIED_RECORDS,
                    limitation = PracticeLimitation.DIFFERENT_MEASURES,
                    limitationNote = "Measures and time points differ.",
                    action = PracticeNextAction.BROADER_SAMPLE,
                    actionReason = "Check more learners.",
                ),
            ),
        ),
    )
}
