package dev.nextgen.mobile.domain.conclusion

private data class EvaluationIssue(
    val check: ConclusionCheck,
    val code: String,
    val status: ConclusionStatus,
    val priority: ConclusionPriority,
    val field: ConclusionField,
    val anchorIds: List<String>,
    val message: String,
    val why: String,
    val nextAction: String,
)

class ConclusionEvaluator(
    private val case: ConclusionCase = ConclusionCases.M0_T2,
) {
    private val caseValidation = ConclusionCaseValidator.validate(case)
    private val unsupportedClaimPatterns = if (caseValidation.isValid) {
        case.unsupportedClaimTerms
        .map { term ->
            val normalizedTerm = term.lowercase()
            normalizedTerm to Regex("\\b${Regex.escape(normalizedTerm)}\\b")
        }
        .distinctBy { it.first }
    } else {
        emptyList()
    }

    fun evaluate(draft: ConclusionDraft): ConclusionEvaluation {
        if (!caseValidation.isValid) return invalidCaseEvaluation()

        unsupportedEnumIssue(draft)?.let { issue ->
            return ConclusionEvaluation(
                checks = emptyList(),
                primaryFeedback = issue.toFeedback(),
            )
        }

        requiredIssue(draft)?.let { issue ->
            return ConclusionEvaluation(
                checks = emptyList(),
                primaryFeedback = issue.toFeedback(),
            )
        }

        unsupportedInputIssue(draft)?.let { issue ->
            return ConclusionEvaluation(
                checks = emptyList(),
                primaryFeedback = issue.toFeedback(),
            )
        }

        val issues = buildList {
            unsupportedClaimIssue(draft)?.let(::add)
            scopeIssue(draft)?.let(::add)
            evidenceIssue(draft)?.let(::add)
            implicationIssue(draft)?.let(::add)
        }

        val results = listOf(
            resultFor(ConclusionCheck.GOAL_CONNECTEDNESS, issues, draft),
            resultFor(ConclusionCheck.EVIDENCE_ANCHORING, issues, draft),
            resultFor(ConclusionCheck.SCOPE_UNCERTAINTY, issues, draft),
            resultFor(ConclusionCheck.ACTIONABLE_IMPLICATION, issues, draft),
        )

        return ConclusionEvaluation(
            checks = results,
            primaryFeedback = issues
                .sortedWith(compareBy<EvaluationIssue> { it.priority.ordinal }
                    .thenBy { fieldOrder(it.field) }
                    .thenBy { it.code })
                .firstOrNull()
                ?.toFeedback(),
        )
    }

    private fun invalidCaseEvaluation(): ConclusionEvaluation = ConclusionEvaluation(
        checks = emptyList(),
        primaryFeedback = ConclusionFeedbackItem(
            code = "INVALID_CASE",
            status = ConclusionStatus.CANNOT_ASSESS,
            priority = ConclusionPriority.P1,
            field = ConclusionField.CASE_ID,
            anchorIds = emptyList(),
            message = "Cannot assess this case safely from the supplied information.",
            why = "The case configuration does not satisfy the evaluator contract.",
            nextAction = "Return to a supported case and try again.",
        ),
    )

    private fun unsupportedEnumIssue(draft: ConclusionDraft): EvaluationIssue? {
        val unsupportedEnum = when {
            draft.relation == ConclusionRelation.UNSUPPORTED ->
                ConclusionField.RELATION to "relation"

            draft.scope == ConclusionScope.UNSUPPORTED ->
                ConclusionField.SCOPE to "scope"

            draft.implication == ConclusionImplication.UNSUPPORTED ->
                ConclusionField.IMPLICATION to "implication"

            else -> null
        } ?: return null

        return EvaluationIssue(
            check = when (unsupportedEnum.first) {
                ConclusionField.RELATION -> ConclusionCheck.GOAL_CONNECTEDNESS
                ConclusionField.SCOPE -> ConclusionCheck.SCOPE_UNCERTAINTY
                ConclusionField.IMPLICATION -> ConclusionCheck.ACTIONABLE_IMPLICATION
                else -> ConclusionCheck.GOAL_CONNECTEDNESS
            },
            code = "UNSUPPORTED_INPUT",
            status = ConclusionStatus.CANNOT_ASSESS,
            priority = ConclusionPriority.P1,
            field = unsupportedEnum.first,
            anchorIds = listOf(case.aimFactId()),
            message = "Cannot assess this unsupported ${unsupportedEnum.second} value.",
            why = "The value is outside the supported input contract.",
            nextAction = "Choose one of the supported options shown by the case.",
        )
    }

    private fun requiredIssue(draft: ConclusionDraft): EvaluationIssue? {
        if (draft.caseId != case.id) {
            return EvaluationIssue(
                check = ConclusionCheck.GOAL_CONNECTEDNESS,
                code = "UNSUPPORTED_CASE",
                status = ConclusionStatus.CANNOT_ASSESS,
                priority = ConclusionPriority.P1,
                field = ConclusionField.CASE_ID,
                anchorIds = listOf(case.aimFactId()),
                message = "Cannot assess a case outside the supported case.",
                why = "The selected case ID is not supported by this evaluator.",
                nextAction = "Return to the supplied case and use its supported facts.",
            )
        }

        return when {
            draft.relation == null -> incomplete(
                field = ConclusionField.RELATION,
                message = "Choose a relation to ${case.aimFactId()} before submitting.",
            )

            draft.evidenceRefs.isEmpty() -> incomplete(
                field = ConclusionField.EVIDENCE_REFS,
                message = "Choose at least one observation fact ID before submitting.",
            )

            draft.evidenceRefs.size > MAX_EVIDENCE_REFS -> unsupportedShape(
                field = ConclusionField.EVIDENCE_REFS,
                why = "The supported input contract allows at most three evidence fact IDs.",
                nextAction = "Choose no more than three OBS-* fact IDs.",
            )

            draft.claimText.trim().length !in CLAIM_LENGTH_RANGE -> incomplete(
                field = ConclusionField.CLAIM_TEXT,
                message = "Write a claim between 20 and 320 characters.",
            )

            draft.scope == null -> incomplete(
                field = ConclusionField.SCOPE,
                message = "Choose the scope of this conclusion.",
            )

            draft.limitationRefs.isEmpty() -> incomplete(
                field = ConclusionField.LIMITATION_REFS,
                message = "Choose at least one supplied limitation fact ID.",
            )

            draft.limitationRefs.size > MAX_LIMITATION_REFS -> unsupportedShape(
                field = ConclusionField.LIMITATION_REFS,
                why = "The supported input contract allows at most two limitation fact IDs.",
                nextAction = "Choose no more than two LIMIT-* fact IDs.",
            )

            draft.limitationNote.trim().length !in NOTE_LENGTH_RANGE -> incomplete(
                field = ConclusionField.LIMITATION_NOTE,
                message = "Explain the limitation in 10 to 240 characters.",
            )

            draft.implication == null -> incomplete(
                field = ConclusionField.IMPLICATION,
                message = "Choose a supported next action.",
            )

            draft.implicationReason.trim().length !in NOTE_LENGTH_RANGE -> incomplete(
                field = ConclusionField.IMPLICATION_REASON,
                message = "Explain the next action in 10 to 240 characters.",
            )

            else -> null
        }
    }

    private fun incomplete(
        field: ConclusionField,
        message: String,
    ): EvaluationIssue = EvaluationIssue(
        check = ConclusionCheck.GOAL_CONNECTEDNESS,
        code = "MISSING_REQUIRED_FIELD",
        status = ConclusionStatus.INCOMPLETE,
        priority = ConclusionPriority.P0,
        field = field,
        anchorIds = listOf(case.aimFactId()),
        message = message,
        why = "The evaluator cannot make a quality judgement until this field is complete.",
        nextAction = "Complete the highlighted field, then submit again.",
    )

    private fun unsupportedShape(
        field: ConclusionField,
        why: String,
        nextAction: String,
    ): EvaluationIssue = EvaluationIssue(
        check = when (field) {
            ConclusionField.EVIDENCE_REFS -> ConclusionCheck.EVIDENCE_ANCHORING
            else -> ConclusionCheck.SCOPE_UNCERTAINTY
        },
        code = "UNSUPPORTED_INPUT",
        status = ConclusionStatus.CANNOT_ASSESS,
        priority = ConclusionPriority.P1,
        field = field,
        anchorIds = listOf(case.aimFactId()),
        message = "Cannot assess this input shape safely.",
        why = why,
        nextAction = nextAction,
    )

    private fun unsupportedInputIssue(draft: ConclusionDraft): EvaluationIssue? {
        val unsupportedObservation = draft.evidenceRefs.firstOrNull { id ->
            case.fact(id)?.type != ConclusionFactType.OBSERVATION
        }
        if (unsupportedObservation != null) {
            return EvaluationIssue(
                check = ConclusionCheck.EVIDENCE_ANCHORING,
                code = "UNSUPPORTED_INPUT",
                status = ConclusionStatus.CANNOT_ASSESS,
                priority = ConclusionPriority.P1,
                field = ConclusionField.EVIDENCE_REFS,
                anchorIds = listOf(unsupportedObservation),
                message = "Cannot assess this evidence reference from the supplied case.",
                why = "$unsupportedObservation is not a supplied observation fact ID.",
                nextAction = "Choose only OBS-* fact IDs shown on the case card.",
            )
        }

        val unsupportedLimitation = draft.limitationRefs.firstOrNull { id ->
            case.fact(id)?.type != ConclusionFactType.LIMITATION
        }
        if (unsupportedLimitation != null) {
            return EvaluationIssue(
                check = ConclusionCheck.SCOPE_UNCERTAINTY,
                code = "UNSUPPORTED_INPUT",
                status = ConclusionStatus.CANNOT_ASSESS,
                priority = ConclusionPriority.P1,
                field = ConclusionField.LIMITATION_REFS,
                anchorIds = listOf(unsupportedLimitation),
                message = "Cannot assess this limitation reference from the supplied case.",
                why = "$unsupportedLimitation is not a supplied limitation fact ID.",
                nextAction = "Choose only LIMIT-* fact IDs shown on the case card.",
            )
        }

        return null
    }

    private fun unsupportedClaimIssue(draft: ConclusionDraft): EvaluationIssue? {
        val normalized = draft.claimText.lowercase()

        if (draft.relation == ConclusionRelation.CANNOT_CONCLUDE_FROM_CASE &&
            !CANNOT_CONCLUDE_MARKER_PATTERN.containsMatchIn(normalized)
        ) {
            return unsupportedClaim(
                code = "RELATION_CONFLICT",
                why = "The free-text claim does not state what this case cannot establish.",
                nextAction = "Explain the limitation of the case instead of asserting the observed difference.",
            )
        }

        val unsupportedNumber = NUMBER_PATTERN.findAll(normalized)
            .map { it.value }
            .firstOrNull { it !in supportedNumbers() }
        if (unsupportedNumber != null) {
            return unsupportedClaim(
                code = "UNSUPPORTED_LITERAL",
                why = "The literal $unsupportedNumber is not supplied by this case.",
                nextAction = "Remove the unsupported literal or select a supported fact ID.",
            )
        }

        if (draft.relation != ConclusionRelation.CANNOT_CONCLUDE_FROM_CASE &&
            NEGATION_PATTERN.containsMatchIn(normalized)
        ) {
            return unsupportedClaim(
                code = "UNSAFE_NEGATION",
                why = "The bounded evaluator cannot safely resolve a negation in learner-authored text.",
                nextAction = "Use the structured relation and a non-contradictory bounded claim.",
            )
        }

        val unsupportedGoal = unsupportedClaimPatterns
            .firstOrNull { (_, pattern) -> pattern.containsMatchIn(normalized) }
            ?.first
        if (unsupportedGoal != null) {
            return unsupportedClaim(
                code = "UNSUPPORTED_GOAL",
                why = "The term '$unsupportedGoal' is outside ${case.aimFactId()}.",
                nextAction = "Keep the claim focused on the supplied aim: ${case.fact(case.aimFactId())?.text.orEmpty()}",
            )
        }

        if (CAUSAL_MARKER_PATTERN.containsMatchIn(normalized) &&
            draft.relation != ConclusionRelation.CANNOT_CONCLUDE_FROM_CASE
        ) {
            return unsupportedClaim(
                code = "UNSUPPORTED_CAUSE",
                why = "The supplied facts do not provide a safe causal explanation for this claim.",
                nextAction = "Limit the claim to the observed comparison and supplied limitations.",
            )
        }

        return null
    }

    private fun unsupportedClaim(
        code: String,
        why: String,
        nextAction: String,
    ): EvaluationIssue = EvaluationIssue(
        check = ConclusionCheck.GOAL_CONNECTEDNESS,
        code = code,
        status = ConclusionStatus.CANNOT_ASSESS,
        priority = ConclusionPriority.P1,
        field = ConclusionField.CLAIM_TEXT,
        anchorIds = listOf(case.aimFactId()),
        message = "Cannot assess this claim safely from the supplied information.",
        why = why,
        nextAction = nextAction,
    )

    private fun scopeIssue(draft: ConclusionDraft): EvaluationIssue? {
        if (draft.scope == ConclusionScope.GENERAL_CAUSAL_CLAIM) {
            val boundaryId = case.boundaryFactId()
            return EvaluationIssue(
                check = ConclusionCheck.SCOPE_UNCERTAINTY,
                code = "OVERCLAIM_SCOPE",
                status = ConclusionStatus.ACTION_REQUIRED,
                priority = ConclusionPriority.P1,
                field = ConclusionField.SCOPE,
                anchorIds = draft.limitationRefs + boundaryId,
                message = "Limit the scope; the supplied case does not establish a general causal effect.",
                why = "$boundaryId and the supplied limitations restrict the conclusion.",
                nextAction = "Choose this observation or limited comparison and explain its limitation.",
            )
        }

        return null
    }

    private fun evidenceIssue(draft: ConclusionDraft): EvaluationIssue? {
        if (draft.relation == ConclusionRelation.CANNOT_CONCLUDE_FROM_CASE) return null

        val distinctObservationCount = draft.evidenceRefs.distinct().count { id ->
            case.fact(id)?.type == ConclusionFactType.OBSERVATION
        }
        if (distinctObservationCount < 2) {
            return EvaluationIssue(
                check = ConclusionCheck.EVIDENCE_ANCHORING,
                code = "MISSING_COMPARATOR",
                status = ConclusionStatus.ACTION_REQUIRED,
                priority = ConclusionPriority.P2,
                field = ConclusionField.EVIDENCE_REFS,
                anchorIds = draft.evidenceRefs,
                message = "Select at least two observation fact IDs for this comparison.",
                why = "A difference claim needs a supplied comparator, not one observation alone.",
                nextAction = "Add another OBS-* fact ID from the case card.",
            )
        }

        return null
    }

    private fun implicationIssue(draft: ConclusionDraft): EvaluationIssue? {
        val limitationIds = draft.limitationRefs.toSet()
        val requiredLimitation = draft.implication?.let(case::requiredLimitationId)

        if (requiredLimitation != null && requiredLimitation !in limitationIds) {
            return EvaluationIssue(
                check = ConclusionCheck.ACTIONABLE_IMPLICATION,
                code = "IMPLICATION_NOT_ANCHORED",
                status = ConclusionStatus.ACTION_REQUIRED,
                priority = ConclusionPriority.P3,
                field = ConclusionField.IMPLICATION,
                anchorIds = draft.limitationRefs,
                message = "Connect this next action to the limitation it addresses.",
                why = "${draft.implication.name} must reference $requiredLimitation.",
                nextAction = "Select the matching limitation fact ID and explain the connection.",
            )
        }

        return null
    }

    private fun resultFor(
        check: ConclusionCheck,
        issues: List<EvaluationIssue>,
        draft: ConclusionDraft,
    ): ConclusionCheckResult {
        val issue = issues
            .filter { it.check == check }
            .sortedWith(compareBy<EvaluationIssue> { it.priority.ordinal }
                .thenBy { fieldOrder(it.field) }
                .thenBy { it.code })
            .firstOrNull()

        return if (issue == null) {
            val field = when (check) {
                ConclusionCheck.GOAL_CONNECTEDNESS -> ConclusionField.RELATION
                ConclusionCheck.EVIDENCE_ANCHORING -> ConclusionField.EVIDENCE_REFS
                ConclusionCheck.SCOPE_UNCERTAINTY -> ConclusionField.SCOPE
                ConclusionCheck.ACTIONABLE_IMPLICATION -> ConclusionField.IMPLICATION
            }
            ConclusionCheckResult(
                check = check,
                status = ConclusionStatus.PASS,
                field = field,
                anchorIds = when (check) {
                    ConclusionCheck.GOAL_CONNECTEDNESS -> listOf(case.aimFactId())
                    ConclusionCheck.EVIDENCE_ANCHORING -> draft.evidenceRefs
                    ConclusionCheck.SCOPE_UNCERTAINTY -> draft.limitationRefs + case.boundaryFactId()
                    ConclusionCheck.ACTIONABLE_IMPLICATION -> draft.limitationRefs
                },
                reason = "The input satisfies the bounded $check rule.",
            )
        } else {
            ConclusionCheckResult(
                check = check,
                status = issue.status,
                priority = issue.priority,
                field = issue.field,
                anchorIds = issue.anchorIds,
                reason = issue.why,
            )
        }
    }

    private fun EvaluationIssue.toFeedback(): ConclusionFeedbackItem = ConclusionFeedbackItem(
        code = code,
        status = status,
        priority = priority,
        field = field,
        anchorIds = anchorIds,
        message = message,
        why = why,
        nextAction = nextAction,
    )

    private fun fieldOrder(field: ConclusionField): Int = when (field) {
        ConclusionField.RELATION -> 0
        ConclusionField.EVIDENCE_REFS -> 1
        ConclusionField.SCOPE,
        ConclusionField.LIMITATION_REFS,
        ConclusionField.LIMITATION_NOTE,
        ConclusionField.CLAIM_TEXT,
        -> 2
        ConclusionField.IMPLICATION,
        ConclusionField.IMPLICATION_REASON,
        -> 3
        ConclusionField.CASE_ID -> 0
    }

    private fun supportedNumbers(): Set<String> = case
        .factsOfType(ConclusionFactType.OBSERVATION)
        .flatMap { observation ->
            NUMBER_PATTERN.findAll(observation.text).map { it.value }.toList()
        }
        .toSet()

    private companion object {
        val CLAIM_LENGTH_RANGE = 20..320
        val NOTE_LENGTH_RANGE = 10..240
        const val MAX_EVIDENCE_REFS = 3
        const val MAX_LIMITATION_REFS = 2
        val NUMBER_PATTERN = Regex("\\b\\d+(?:\\.\\d+)?\\b")
        val NEGATION_PATTERN = Regex("\\b(?:not|never|no|didn't|did not|cannot|can't)\\b")
        val CANNOT_CONCLUDE_MARKER_PATTERN = Regex(
            "\\b(?:cannot|can't|not enough|insufficient|limited|does not establish|doesn't establish|does not support|doesn't support)\\b",
        )
        val CAUSAL_MARKER_PATTERN = Regex("\\b(?:caused|causes|because|due to|definitely|always|proves)\\b")
    }
}
