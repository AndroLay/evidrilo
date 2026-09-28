package dev.nextgen.mobile.content

import dev.nextgen.mobile.domain.conclusion.ConclusionCase

/**
 * Adapts the published M0 case without allowing remote content to silently
 * change the evaluator contract. The server must publish every stable fact ID
 * and type required by the bundled evaluator; otherwise the local case stays
 * authoritative.
 */
internal fun PublishedCaseSummary.toBundledEvaluatorCase(
    fallback: ConclusionCase,
): ConclusionCase? {
    if (caseVersionId != fallback.remoteCaseVersionId) return null

    val remoteFacts = facts.associateBy { it.id }
    val adaptedFacts = fallback.facts.map { localFact ->
        val remoteFact = remoteFacts[localFact.id] ?: return null
        if (!remoteFact.type.equals(localFact.type.name, ignoreCase = true)) return null
        localFact.copy(text = remoteFact.text)
    }

    return fallback.copy(
        title = title,
        description = objective,
        remoteCaseVersionId = caseVersionId,
        facts = adaptedFacts,
    )
}
