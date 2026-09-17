package dev.nextgen.mobile.sync

import dev.nextgen.mobile.account.secureRandomBytes
import dev.nextgen.mobile.account.sha256
import dev.nextgen.mobile.domain.conclusion.ConclusionDraft
import dev.nextgen.mobile.domain.conclusion.EVIDRILO_M0_T2_EVIDENCE_CHANGE_CASE_ID
import dev.nextgen.mobile.domain.conclusion.EVIDRILO_M0_T2_CASE_ID
import dev.nextgen.mobile.domain.conclusion.EVIDRILO_M0_T2_REMOTE_CASE_VERSION_ID
import kotlin.time.Clock

internal fun syncCommandFor(
    commandType: SyncCommandType,
    attemptId: String,
    draft: ConclusionDraft,
    revisionNumber: Int,
    caseVersionId: String = canonicalCaseVersionIdFor(draft.caseId),
    commandId: String = newSyncCommandId(),
    clientOccurredAt: String = Clock.System.now().toString(),
): SyncCommandIntent = SyncCommandIntent(
    commandId = commandId,
    attemptId = attemptId,
    caseVersionId = caseVersionId,
    revisionNumber = revisionNumber,
    snapshotDigest = snapshotDigestFor(draft),
    commandType = commandType,
    clientOccurredAt = clientOccurredAt,
)

/**
 * Keeps local persistence identity separate from the version identity owned by
 * the API/database. Unknown local cases fail closed instead of being sent as
 * if they were published server content.
 */
internal fun canonicalCaseVersionIdFor(localOrCanonicalCaseId: String): String = when (localOrCanonicalCaseId) {
    EVIDRILO_M0_T2_CASE_ID,
    EVIDRILO_M0_T2_EVIDENCE_CHANGE_CASE_ID,
    EVIDRILO_M0_T2_REMOTE_CASE_VERSION_ID,
    -> EVIDRILO_M0_T2_REMOTE_CASE_VERSION_ID
    else -> error("No published backend version is registered for the local case.")
}

internal fun snapshotDigestFor(draft: ConclusionDraft): String {
    val canonical = buildString {
        append(draft.caseId)
        append('\u001f')
        append(draft.relation?.name.orEmpty())
        append('\u001f')
        append(draft.evidenceRefs.joinToString("\u001e"))
        append('\u001f')
        append(draft.claimText)
        append('\u001f')
        append(draft.scope?.name.orEmpty())
        append('\u001f')
        append(draft.limitationRefs.joinToString("\u001e"))
        append('\u001f')
        append(draft.limitationNote)
        append('\u001f')
        append(draft.implication?.name.orEmpty())
        append('\u001f')
        append(draft.implicationReason)
    }
    return sha256(canonical.encodeToByteArray())
        .joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
}

private fun newSyncCommandId(): String {
    val bytes = secureRandomBytes(16)
    bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x40).toByte()
    bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte()
    val hex = bytes.joinToString(separator = "") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    return listOf(
        hex.substring(0, 8),
        hex.substring(8, 12),
        hex.substring(12, 16),
        hex.substring(16, 20),
        hex.substring(20),
    ).joinToString("-")
}
