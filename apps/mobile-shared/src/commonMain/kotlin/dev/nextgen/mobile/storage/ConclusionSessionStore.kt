package dev.nextgen.mobile.storage

import dev.nextgen.mobile.domain.conclusion.ConclusionDraft
import dev.nextgen.mobile.domain.conclusion.ConclusionCases
import dev.nextgen.mobile.domain.conclusion.ConclusionImplication
import dev.nextgen.mobile.domain.conclusion.ConclusionRelation
import dev.nextgen.mobile.domain.conclusion.ConclusionScope

internal enum class ConclusionSessionPhase {
    DRAFTING,
    FEEDBACK,
    REVISION,
    SUMMARY,
    EVIDENCE_CHANGE_DRAFTING,
    EVIDENCE_CHANGE_FEEDBACK,
    EVIDENCE_CHANGE_SUMMARY,
}

internal data class ConclusionSessionSnapshot(
    val phase: ConclusionSessionPhase,
    val initialDraft: ConclusionDraft,
    val currentDraft: ConclusionDraft,
)

internal interface ConclusionSessionStore {
    fun load(): LocalStorageReadResult<ConclusionSessionSnapshot>

    fun save(snapshot: ConclusionSessionSnapshot): LocalStorageWriteResult

    fun clear(): LocalStorageWriteResult
}

internal class NoopConclusionSessionStore : ConclusionSessionStore {
    override fun load(): LocalStorageReadResult<ConclusionSessionSnapshot> = LocalStorageReadResult.Unavailable

    override fun save(snapshot: ConclusionSessionSnapshot): LocalStorageWriteResult =
        LocalStorageWriteResult.UNAVAILABLE

    override fun clear(): LocalStorageWriteResult = LocalStorageWriteResult.UNAVAILABLE
}

internal object ConclusionSessionCodec {
    fun encode(snapshot: ConclusionSessionSnapshot): String = listOf(
        snapshot.phase.name,
        escape(encodeDraft(snapshot.initialDraft)),
        escape(encodeDraft(snapshot.currentDraft)),
    ).joinToString(RECORD_DELIMITER.toString())

    /**
     * Returns only a record that this decoder can safely restore later. The
     * platform adapters use this before reporting a successful write so a
     * bounded storage failure cannot masquerade as a durable save.
     */
    fun encodeForStorage(snapshot: ConclusionSessionSnapshot): String? {
        if (!isSemanticallyValid(snapshot)) return null
        return encode(snapshot).takeIf { it.length <= MAX_ENCODED_LENGTH }
    }

    fun decode(value: String): ConclusionSessionSnapshot? {
        if (value.length > MAX_ENCODED_LENGTH) return null

        val records = splitEscaped(value, RECORD_DELIMITER) ?: return null
        if (records.size != 3) return null

        val phase = runCatching { ConclusionSessionPhase.valueOf(records[0]) }.getOrNull()
            ?: return null
        val initialDraft = decodeDraft(records[1]) ?: return null
        val currentDraft = decodeDraft(records[2]) ?: return null
        val snapshot = ConclusionSessionSnapshot(phase, initialDraft, currentDraft)
        return snapshot.takeIf(::isSemanticallyValid)
    }

    /**
     * Local storage is not a trusted source of workflow state. Keep a
     * syntactically valid but cross-case snapshot from restoring into a
     * different evaluator or exposing challenge state as the main case.
     */
    private fun isSemanticallyValid(snapshot: ConclusionSessionSnapshot): Boolean {
        val mainCaseId = ConclusionCases.M0_T2.id
        val challengeCaseId = ConclusionCases.EVIDENCE_CHANGE.id
        return when (snapshot.phase) {
            ConclusionSessionPhase.DRAFTING,
            ConclusionSessionPhase.FEEDBACK,
            ConclusionSessionPhase.REVISION,
            ConclusionSessionPhase.SUMMARY,
            -> snapshot.initialDraft.caseId == mainCaseId &&
                snapshot.currentDraft.caseId == mainCaseId

            ConclusionSessionPhase.EVIDENCE_CHANGE_DRAFTING,
            ConclusionSessionPhase.EVIDENCE_CHANGE_FEEDBACK,
            ConclusionSessionPhase.EVIDENCE_CHANGE_SUMMARY,
            -> snapshot.initialDraft.caseId == mainCaseId &&
                snapshot.currentDraft.caseId == challengeCaseId
        }
    }

    private fun encodeDraft(draft: ConclusionDraft): String = listOf(
        draft.caseId,
        draft.relation?.name.orEmpty(),
        draft.evidenceRefs.joinToString(LIST_DELIMITER.toString()),
        draft.claimText,
        draft.scope?.name.orEmpty(),
        draft.limitationRefs.joinToString(LIST_DELIMITER.toString()),
        draft.limitationNote,
        draft.implication?.name.orEmpty(),
        draft.implicationReason,
    ).joinToString(FIELD_DELIMITER.toString()) { escape(it) }

    private fun decodeDraft(value: String): ConclusionDraft? {
        val fields = splitEscaped(value, FIELD_DELIMITER) ?: return null
        if (fields.size != 9) return null

        val relation = fields[1].toEnumOrNull<ConclusionRelation>()
        if (fields[1].isNotEmpty() && relation == null) return null
        val scope = fields[4].toEnumOrNull<ConclusionScope>()
        if (fields[4].isNotEmpty() && scope == null) return null
        val implication = fields[7].toEnumOrNull<ConclusionImplication>()
        if (fields[7].isNotEmpty() && implication == null) return null
        val evidenceRefs = decodeList(fields[2]) ?: return null
        val limitationRefs = decodeList(fields[5]) ?: return null
        return ConclusionDraft(
            caseId = fields[0],
            relation = relation,
            evidenceRefs = evidenceRefs,
            claimText = fields[3],
            scope = scope,
            limitationRefs = limitationRefs,
            limitationNote = fields[6],
            implication = implication,
            implicationReason = fields[8],
        )
    }

    private fun decodeList(value: String): List<String>? =
        if (value.isEmpty()) emptyList() else splitEscaped(value, LIST_DELIMITER)

    private inline fun <reified T : Enum<T>> String.toEnumOrNull(): T? =
        if (isEmpty()) null else runCatching { enumValueOf<T>(this) }.getOrNull()

    private fun escape(value: String): String = buildString {
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '|', ';', ',' -> append('\\').append(character)
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(character)
            }
        }
    }

    private fun splitEscaped(value: String, delimiter: Char): List<String>? {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var escaped = false
        value.forEach { character ->
            if (escaped) {
                when (character) {
                    'n' -> current.append('\n')
                    'r' -> current.append('\r')
                    else -> current.append(character)
                }
                escaped = false
            } else if (character == '\\') {
                escaped = true
            } else if (character == delimiter) {
                result += current.toString()
                current.clear()
            } else {
                current.append(character)
            }
        }
        if (escaped) return null
        result += current.toString()
        return result
    }

    private const val RECORD_DELIMITER = '|'
    private const val FIELD_DELIMITER = ';'
    private const val LIST_DELIMITER = ','
    private const val MAX_ENCODED_LENGTH = 8_192
}
