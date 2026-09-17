package dev.nextgen.mobile.recommendation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class RecommendationParserTest {
    @Test
    fun parses_recommended_payload_with_exact_wire_values() {
        val result = parseRecommendation(recommendedJson())
        val payload = assertIs<RecommendationParseResult.Valid>(result).value

        assertEquals(RecommendationStatus.RECOMMENDED, payload.status)
        assertEquals("recommendation.v1", payload.calculationVersion)
        assertEquals("M0_T2:1", payload.caseVersionId)
        assertEquals("Practice the next evidence comparison.", payload.objective)
        assertEquals(RecommendationReason.PRACTICE_ACTION_REQUIRED, payload.reason)
        assertEquals(listOf("attempt-001", "feedback-001"), payload.evidenceReferences)
        assertEquals("req-rec-001", payload.requestId)
    }

    @Test
    fun parses_abstain_payload_without_a_launch_target() {
        val result = parseRecommendation(abstainJson())
        val payload = assertIs<RecommendationParseResult.Valid>(result).value

        assertEquals(RecommendationStatus.ABSTAIN, payload.status)
        assertNull(payload.caseVersionId)
        assertNull(payload.objective)
        assertEquals(RecommendationReason.NO_ELIGIBLE_CASE, payload.reason)
        assertEquals(emptyList(), payload.evidenceReferences)
    }

    @Test
    fun rejects_wrong_wire_status_and_never_accepts_abstained() {
        assertRejected(recommendedJson().replace("recommended", "abstained"))
        assertRejected(abstainJson().replace("abstain", "abstained"))
    }

    @Test
    fun rejects_wrong_schema_version_calculation_or_reason() {
        assertRejected(recommendedJson().replace("evidrilo.recommendation", "other.schema"))
        assertRejected(recommendedJson().replace("\"version\":\"1\"", "\"version\":\"2\""))
        assertRejected(recommendedJson().replace("recommendation.v1", "recommendation.v2"))
        assertRejected(recommendedJson().replace("PRACTICE_ACTION_REQUIRED", "UNSAFE_REASON"))
    }

    @Test
    fun rejects_missing_or_invalid_recommended_fields() {
        assertRejected(recommendedJson().replace("  \"caseVersionId\":\"M0_T2:1\",\n", ""))
        assertRejected(recommendedJson().replace("\"caseVersionId\":\"M0_T2:1\"", "\"caseVersionId\":null"))
        assertRejected(recommendedJson().replace("\"caseVersionId\":\"M0_T2:1\"", "\"caseVersionId\":\"M0 T2\""))
        assertRejected(recommendedJson().replace("\"objective\":\"Practice the next evidence comparison.\"", "\"objective\":\"\""))
        assertRejected(recommendedJson().replace("\"requestId\":\"req-rec-001\"", "\"requestId\":\"short\""))
        assertRejected(recommendedJson().replace("\"attempt-001\"", "\"raw evidence text\""))
    }

    @Test
    fun rejects_invalid_abstain_shape() {
        assertRejected(abstainJson().replace("\"caseVersionId\":null", "\"caseVersionId\":\"M0_T2:1\""))
        assertRejected(abstainJson().replace("\"objective\":null", "\"objective\":\"Try this\""))
        assertRejected(abstainJson().replace("\"evidenceReferences\":[]", "\"evidenceReferences\":[\"evidence-1\"]"))
    }

    @Test
    fun rejects_every_unknown_root_key_and_oversized_body() {
        assertRejected(recommendedJson().replaceFirst("}", ",\"rawDraftText\":\"hidden\"}"))
        assertRejected("{" + "\"schema\":\"evidrilo.recommendation\",\"version\":\"1\",\"status\":\"recommended\",\"calculationVersion\":\"recommendation.v1\",\"caseVersionId\":\"M0_T2:1\",\"objective\":\"${"x".repeat(200)}\",\"reasonCode\":\"PRACTICE_ACTION_REQUIRED\",\"evidenceReferences\":[\"evidence-1\"],\"requestId\":\"req-rec-001\"}" + "x".repeat(16 * 1024))
    }

    @Test
    fun malformed_json_is_returned_as_rejected_without_throwing() {
        assertRejected("{not-json")
        assertRejected("[]")
        assertRejected("null")
    }

    private fun assertRejected(json: String) {
        assertIs<RecommendationParseResult.Rejected>(parseRecommendation(json))
    }
}

private fun recommendedJson(): String = """
    {
      "schema":"evidrilo.recommendation",
      "version":"1",
      "status":"recommended",
      "calculationVersion":"recommendation.v1",
      "caseVersionId":"M0_T2:1",
      "objective":"Practice the next evidence comparison.",
      "reasonCode":"PRACTICE_ACTION_REQUIRED",
      "evidenceReferences":["attempt-001","feedback-001"],
      "requestId":"req-rec-001"
    }
""".trimIndent()

private fun abstainJson(): String = """
    {
      "schema":"evidrilo.recommendation",
      "version":"1",
      "status":"abstain",
      "calculationVersion":"recommendation.v1",
      "caseVersionId":null,
      "objective":null,
      "reasonCode":"NO_ELIGIBLE_CASE",
      "evidenceReferences":[],
      "requestId":"req-rec-002"
    }
""".trimIndent()
