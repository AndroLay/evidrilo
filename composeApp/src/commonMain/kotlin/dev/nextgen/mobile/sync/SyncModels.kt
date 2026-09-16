package dev.nextgen.mobile.sync

import kotlin.time.Clock
import kotlin.time.Instant

internal const val MAX_SYNC_COMMANDS: Int = 50
internal const val MAX_SYNC_CURSOR: Long = 1_000_000_000_000_000L

private const val MIN_IDEMPOTENCY_KEY_LENGTH: Int = 8
private const val MAX_IDEMPOTENCY_KEY_LENGTH: Int = 128

private val idempotencyKeyPattern = Regex("^[A-Za-z0-9_-]{8,128}$")
private val uuidPattern = Regex(
    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$",
)
private val caseVersionPattern = Regex("^[A-Za-z0-9._:-]{1,128}$")
private val digestPattern = Regex("^[a-f0-9]{64}$")

internal enum class SyncConsent {
    GRANTED,
    NOT_GRANTED,
}

internal enum class SyncCommandType(
    val wireName: String,
) {
    ATTEMPT_STARTED("attempt_started"),
    ATTEMPT_SUBMITTED("attempt_submitted"),
    REVISION_RECORDED("revision_recorded"),
    ;

    companion object {
        fun fromWire(value: String): SyncCommandType? = entries.firstOrNull { it.wireName == value }
    }
}

/**
 * Deliberately redacted command shape. Learner-authored text never enters the
 * future sync transport; a digest is enough to identify a local snapshot.
 */
internal data class SyncCommandIntent(
    val commandId: String,
    val attemptId: String,
    val caseVersionId: String,
    val revisionNumber: Int,
    val snapshotDigest: String,
    val commandType: SyncCommandType = SyncCommandType.ATTEMPT_SUBMITTED,
    val clientOccurredAt: String = Clock.System.now().toString(),
)

internal data class SyncPushEnvelope(
    val cursor: Long,
    val consent: SyncConsent,
    val idempotencyKey: String,
    val commands: List<SyncCommandIntent>,
) {
    fun validate(): SyncValidationResult {
        if (cursor !in 0..MAX_SYNC_CURSOR) {
            return SyncValidationResult.invalid("INVALID_CURSOR", "The sync cursor is invalid.")
        }
        if (consent != SyncConsent.GRANTED) {
            return SyncValidationResult.invalid("CONSENT_REQUIRED", "Sync consent is required.")
        }
        if (idempotencyKey.length !in MIN_IDEMPOTENCY_KEY_LENGTH..MAX_IDEMPOTENCY_KEY_LENGTH ||
            !idempotencyKeyPattern.matches(idempotencyKey)
        ) {
            return SyncValidationResult.invalid(
                "INVALID_IDEMPOTENCY_KEY",
                "The sync idempotency key is invalid.",
            )
        }
        if (commands.isEmpty() || commands.size > MAX_SYNC_COMMANDS) {
            return SyncValidationResult.invalid(
                "INVALID_COMMAND_BATCH",
                "The sync command batch is invalid.",
            )
        }
        if (commands.map { it.commandId }.toSet().size != commands.size) {
            return SyncValidationResult.invalid(
                "INVALID_COMMAND_BATCH",
                "The sync command batch is invalid.",
            )
        }
        commands.forEach { command ->
            val result = command.validate()
            if (!result.isValid) return result
        }
        return SyncValidationResult.Valid
    }
}

internal fun SyncCommandIntent.validate(): SyncValidationResult {
    if (!uuidPattern.matches(commandId) || !uuidPattern.matches(attemptId)) {
        return SyncValidationResult.invalid("INVALID_ID", "A sync identifier is invalid.")
    }
    if (!caseVersionPattern.matches(caseVersionId)) {
        return SyncValidationResult.invalid("INVALID_CASE_VERSION", "The case version is invalid.")
    }
    if (revisionNumber !in 0..1) {
        return SyncValidationResult.invalid("INVALID_REVISION", "The revision number is invalid.")
    }
    if (!digestPattern.matches(snapshotDigest)) {
        return SyncValidationResult.invalid(
            "INVALID_SNAPSHOT_DIGEST",
            "The snapshot digest is invalid.",
        )
    }
    if (clientOccurredAt.isBlank() || clientOccurredAt.length > 64 ||
        runCatching { Instant.parse(clientOccurredAt) }.isFailure
    ) {
        return SyncValidationResult.invalid(
            "INVALID_CLIENT_OCCURRED_AT",
            "The sync occurrence time is invalid.",
        )
    }
    return SyncValidationResult.Valid
}

internal fun isValidSyncUuid(value: String): Boolean = uuidPattern.matches(value)

internal fun isValidSyncCaseVersion(value: String): Boolean = caseVersionPattern.matches(value)

internal fun isValidSyncDigest(value: String): Boolean = digestPattern.matches(value)

internal fun isValidSyncRequestId(value: String): Boolean =
    value.length in 8..128 && Regex("^[A-Za-z0-9_-]+$").matches(value)

internal data class SyncValidationResult(
    val isValid: Boolean,
    val errorCode: String?,
    val message: String?,
) {
    companion object {
        val Valid: SyncValidationResult = SyncValidationResult(true, null, null)

        fun invalid(code: String, message: String): SyncValidationResult =
            SyncValidationResult(false, code, message)
    }
}
