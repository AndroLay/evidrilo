package dev.nextgen.mobile.domain.conclusion

const val EVIDRILO_M0_T2_CASE_ID = "evidrilo-m0-t2-v1"
const val EVIDRILO_M0_T2_EVIDENCE_CHANGE_CASE_ID = "evidrilo-m0-t2-evidence-change-v1"
const val EVIDRILO_M0_T2_REMOTE_CASE_VERSION_ID = "M0_T2:1"
const val EVIDRILO_PREMIUM_SURFACE_CASE_ID = "evidrilo-premium-surface-v1"
const val EVIDRILO_PREMIUM_VOLUME_CASE_ID = "evidrilo-premium-volume-v1"

enum class ConclusionFactType {
    AIM,
    CONTEXT,
    OBSERVATION,
    LIMITATION,
    BOUNDARY,
}

data class ConclusionFact(
    val id: String,
    val type: ConclusionFactType,
    val text: String,
    val displayLabel: String? = null,
    val displayValue: String? = null,
)

data class ConclusionCase(
    val id: String,
    val facts: List<ConclusionFact>,
    /** Canonical server content identity; local IDs remain stable for offline storage. */
    val remoteCaseVersionId: String? = null,
    val changeNotice: String? = null,
    val title: String = id,
    val description: String = "",
    val implicationAnchors: Map<ConclusionImplication, String> = emptyMap(),
    val unsupportedClaimTerms: Set<String> = emptySet(),
) {
    fun fact(id: String): ConclusionFact? = facts.firstOrNull { it.id == id }

    fun factsOfType(type: ConclusionFactType): List<ConclusionFact> =
        facts.filter { it.type == type }

    fun aimFactId(): String = facts.first { it.type == ConclusionFactType.AIM }.id

    fun boundaryFactId(): String = facts.first { it.type == ConclusionFactType.BOUNDARY }.id

    fun requiredLimitationId(implication: ConclusionImplication): String? =
        implicationAnchors[implication]
}

object ConclusionCases {
    val M0_T2 = ConclusionCase(
        id = EVIDRILO_M0_T2_CASE_ID,
        remoteCaseVersionId = EVIDRILO_M0_T2_REMOTE_CASE_VERSION_ID,
        title = "Tablet dissolution · water temperature",
        description = "Compare supplied dissolution observations across three water temperatures.",
        implicationAnchors = mapOf(
            ConclusionImplication.REPEAT_TRIALS to "LIMIT-TRIAL-01",
            ConclusionImplication.CONTROL_STIRRING to "LIMIT-STIR-01",
        ),
        unsupportedClaimTerms = setOf("mass", "weight", "colour", "color", "pressure", "ph"),
        facts = listOf(
            ConclusionFact(
                id = "AIM-01",
                type = ConclusionFactType.AIM,
                text = "Compare dissolution time across warm, room-temperature, and cold water.",
            ),
            ConclusionFact(
                id = "HYP-01",
                type = ConclusionFactType.CONTEXT,
                text = "The case hypothesis expects faster dissolution in warmer water.",
            ),
            ConclusionFact(
                id = "OBS-WARM-01",
                type = ConclusionFactType.OBSERVATION,
                text = "Warm water: 32 seconds.",
                displayLabel = "Warm",
                displayValue = "32 s",
            ),
            ConclusionFact(
                id = "OBS-ROOM-01",
                type = ConclusionFactType.OBSERVATION,
                text = "Room temperature: 58 seconds.",
                displayLabel = "Room",
                displayValue = "58 s",
            ),
            ConclusionFact(
                id = "OBS-COLD-01",
                type = ConclusionFactType.OBSERVATION,
                text = "Cold water: 92 seconds.",
                displayLabel = "Cold",
                displayValue = "92 s",
            ),
            ConclusionFact(
                id = "LIMIT-TRIAL-01",
                type = ConclusionFactType.LIMITATION,
                text = "Each condition was measured once.",
            ),
            ConclusionFact(
                id = "LIMIT-STIR-01",
                type = ConclusionFactType.LIMITATION,
                text = "Stirring speed was not measured with an instrument.",
            ),
            ConclusionFact(
                id = "BOUND-01",
                type = ConclusionFactType.BOUNDARY,
                text = "The case does not establish a general causal effect.",
            ),
        ),
    )

    val EVIDENCE_CHANGE = ConclusionCase(
        id = EVIDRILO_M0_T2_EVIDENCE_CHANGE_CASE_ID,
        remoteCaseVersionId = EVIDRILO_M0_T2_REMOTE_CASE_VERSION_ID,
        title = "Tablet dissolution · evidence change",
        description = "Rebuild the comparison after the cold-water observation is unavailable.",
        changeNotice = "Evidence change: the cold-water observation is unavailable in this round.",
        implicationAnchors = mapOf(
            ConclusionImplication.REPEAT_TRIALS to "LIMIT-TRIAL-01",
            ConclusionImplication.CONTROL_STIRRING to "LIMIT-STIR-01",
        ),
        unsupportedClaimTerms = setOf("mass", "weight", "colour", "color", "pressure", "ph"),
        facts = listOf(
            ConclusionFact(
                id = "AIM-01",
                type = ConclusionFactType.AIM,
                text = "Compare dissolution time across warm, room-temperature, and cold water.",
            ),
            ConclusionFact(
                id = "HYP-01",
                type = ConclusionFactType.CONTEXT,
                text = "The case hypothesis expects faster dissolution in warmer water.",
            ),
            ConclusionFact(
                id = "OBS-WARM-01",
                type = ConclusionFactType.OBSERVATION,
                text = "Warm water: 32 seconds.",
                displayLabel = "Warm",
                displayValue = "32 s",
            ),
            ConclusionFact(
                id = "OBS-ROOM-01",
                type = ConclusionFactType.OBSERVATION,
                text = "Room temperature: 58 seconds.",
                displayLabel = "Room",
                displayValue = "58 s",
            ),
            ConclusionFact(
                id = "LIMIT-TRIAL-01",
                type = ConclusionFactType.LIMITATION,
                text = "Each condition was measured once.",
            ),
            ConclusionFact(
                id = "LIMIT-STIR-01",
                type = ConclusionFactType.LIMITATION,
                text = "Stirring speed was not measured with an instrument.",
            ),
            ConclusionFact(
                id = "BOUND-01",
                type = ConclusionFactType.BOUNDARY,
                text = "The case does not establish a general causal effect.",
            ),
        ),
    )

    val premium: List<ConclusionCase> = listOf(
        ConclusionCase(
            id = EVIDRILO_PREMIUM_SURFACE_CASE_ID,
            title = "Tablet form · whole versus crushed",
            description = "Compare two supplied dissolution observations while keeping the claim bounded.",
            implicationAnchors = mapOf(
                ConclusionImplication.REPEAT_TRIALS to "SURFACE-LIMIT-TRIAL-01",
                ConclusionImplication.CONTROL_STIRRING to "SURFACE-LIMIT-STIR-01",
            ),
            unsupportedClaimTerms = setOf("mass", "weight", "colour", "color", "pressure", "ph", "temperature"),
            facts = listOf(
                ConclusionFact(
                    id = "SURFACE-AIM-01",
                    type = ConclusionFactType.AIM,
                    text = "Compare dissolution time for a whole tablet and a crushed tablet in room-temperature water.",
                ),
                ConclusionFact(
                    id = "SURFACE-HYP-01",
                    type = ConclusionFactType.CONTEXT,
                    text = "The case hypothesis expects the crushed tablet to dissolve faster.",
                ),
                ConclusionFact(
                    id = "SURFACE-OBS-WHOLE-01",
                    type = ConclusionFactType.OBSERVATION,
                    text = "Whole tablet: 74 seconds.",
                    displayLabel = "Whole",
                    displayValue = "74 s",
                ),
                ConclusionFact(
                    id = "SURFACE-OBS-CRUSHED-01",
                    type = ConclusionFactType.OBSERVATION,
                    text = "Crushed tablet: 41 seconds.",
                    displayLabel = "Crushed",
                    displayValue = "41 s",
                ),
                ConclusionFact(
                    id = "SURFACE-LIMIT-TRIAL-01",
                    type = ConclusionFactType.LIMITATION,
                    text = "Each tablet form was measured once.",
                ),
                ConclusionFact(
                    id = "SURFACE-LIMIT-STIR-01",
                    type = ConclusionFactType.LIMITATION,
                    text = "Stirring speed was not measured with an instrument.",
                ),
                ConclusionFact(
                    id = "SURFACE-BOUND-01",
                    type = ConclusionFactType.BOUNDARY,
                    text = "The case does not establish a general causal effect.",
                ),
            ),
        ),
        ConclusionCase(
            id = EVIDRILO_PREMIUM_VOLUME_CASE_ID,
            title = "Water volume · 100 mL versus 200 mL",
            description = "Compare supplied dissolution observations across two water volumes.",
            implicationAnchors = mapOf(
                ConclusionImplication.REPEAT_TRIALS to "VOLUME-LIMIT-TRIAL-01",
                ConclusionImplication.CONTROL_STIRRING to "VOLUME-LIMIT-STIR-01",
            ),
            unsupportedClaimTerms = setOf("mass", "weight", "colour", "color", "pressure", "ph", "temperature"),
            facts = listOf(
                ConclusionFact(
                    id = "VOLUME-AIM-01",
                    type = ConclusionFactType.AIM,
                    text = "Compare dissolution time in 100 mL and 200 mL of room-temperature water.",
                ),
                ConclusionFact(
                    id = "VOLUME-HYP-01",
                    type = ConclusionFactType.CONTEXT,
                    text = "The case hypothesis expects the water volume to change the observed dissolution time.",
                ),
                ConclusionFact(
                    id = "VOLUME-OBS-100ML-01",
                    type = ConclusionFactType.OBSERVATION,
                    text = "100 mL: 52 seconds.",
                    displayLabel = "100 mL",
                    displayValue = "52 s",
                ),
                ConclusionFact(
                    id = "VOLUME-OBS-200ML-01",
                    type = ConclusionFactType.OBSERVATION,
                    text = "200 mL: 61 seconds.",
                    displayLabel = "200 mL",
                    displayValue = "61 s",
                ),
                ConclusionFact(
                    id = "VOLUME-LIMIT-TRIAL-01",
                    type = ConclusionFactType.LIMITATION,
                    text = "Each water volume was measured once.",
                ),
                ConclusionFact(
                    id = "VOLUME-LIMIT-STIR-01",
                    type = ConclusionFactType.LIMITATION,
                    text = "Stirring speed was not measured with an instrument.",
                ),
                ConclusionFact(
                    id = "VOLUME-BOUND-01",
                    type = ConclusionFactType.BOUNDARY,
                    text = "The case does not establish a general causal effect.",
                ),
            ),
        ),
    )
}
