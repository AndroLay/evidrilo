package dev.nextgen.mobile.domain.conclusion

internal data class ConclusionCaseValidationIssue(
    val code: String,
)

internal data class ConclusionCaseValidationResult(
    val issues: List<ConclusionCaseValidationIssue>,
) {
    val isValid: Boolean get() = issues.isEmpty()
}

/**
 * Validates the minimum shape required by the deterministic conclusion
 * evaluator. Case data may come from a future content adapter, so the
 * evaluator must not assume that a syntactically valid object is safe to use.
 */
internal object ConclusionCaseValidator {
    private val identifierPattern = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")

    fun validate(candidate: ConclusionCase): ConclusionCaseValidationResult {
        val issues = buildList {
            if (!identifierPattern.matches(candidate.id)) {
                add(ConclusionCaseValidationIssue("INVALID_CASE_ID"))
            }
            if (candidate.remoteCaseVersionId != null &&
                !identifierPattern.matches(candidate.remoteCaseVersionId)
            ) {
                add(ConclusionCaseValidationIssue("INVALID_REMOTE_CASE_VERSION_ID"))
            }

            val factIds = candidate.facts.map { it.id }
            candidate.facts.forEach { fact ->
                if (!identifierPattern.matches(fact.id)) {
                    add(ConclusionCaseValidationIssue("INVALID_FACT_ID"))
                }
                if (fact.text.isBlank() || fact.text.length > MAX_FACT_TEXT_LENGTH) {
                    add(ConclusionCaseValidationIssue("INVALID_FACT_TEXT"))
                }
            }
            if (factIds.size != factIds.toSet().size) {
                add(ConclusionCaseValidationIssue("DUPLICATE_FACT_ID"))
            }

            val aims = candidate.factsOfType(ConclusionFactType.AIM)
            if (aims.isEmpty()) add(ConclusionCaseValidationIssue("AIM_REQUIRED"))
            if (aims.size > 1) add(ConclusionCaseValidationIssue("AIM_MUST_BE_UNIQUE"))

            if (candidate.factsOfType(ConclusionFactType.OBSERVATION).map { it.id }.toSet().size < MIN_OBSERVATIONS) {
                add(ConclusionCaseValidationIssue("OBSERVATIONS_REQUIRED"))
            }

            if (candidate.factsOfType(ConclusionFactType.LIMITATION).isEmpty()) {
                add(ConclusionCaseValidationIssue("LIMITATION_REQUIRED"))
            }

            val boundaries = candidate.factsOfType(ConclusionFactType.BOUNDARY)
            if (boundaries.isEmpty()) add(ConclusionCaseValidationIssue("BOUNDARY_REQUIRED"))
            if (boundaries.size > 1) add(ConclusionCaseValidationIssue("BOUNDARY_MUST_BE_UNIQUE"))

            candidate.implicationAnchors.forEach { (implication, factId) ->
                if (implication == ConclusionImplication.UNSUPPORTED) {
                    add(ConclusionCaseValidationIssue("UNSUPPORTED_IMPLICATION_ANCHOR"))
                }
                if (candidate.fact(factId)?.type != ConclusionFactType.LIMITATION) {
                    add(ConclusionCaseValidationIssue("IMPLICATION_ANCHOR_NOT_LIMITATION"))
                }
            }

            if (candidate.unsupportedClaimTerms.any { term ->
                    term.isBlank() || term.length > MAX_TERM_LENGTH
                }) {
                add(ConclusionCaseValidationIssue("INVALID_UNSUPPORTED_TERM"))
            }
        }
        return ConclusionCaseValidationResult(issues)
    }

    private const val MIN_OBSERVATIONS = 2
    private const val MAX_FACT_TEXT_LENGTH = 2_000
    private const val MAX_TERM_LENGTH = 64
}
