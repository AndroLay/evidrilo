package dev.nextgen.mobile.storage

internal enum class LocalStorageStatus {
    AVAILABLE,
    SAVED,
    RECOVERED,
    REPAIRED,
    UNAVAILABLE,
    CORRUPT,
    FAILED,
}

internal data class LocalStorageNotice(
    val title: String,
    val body: String,
    val isError: Boolean,
)

internal fun LocalStorageStatus.notice(): LocalStorageNotice? = when (this) {
    LocalStorageStatus.AVAILABLE -> null
    LocalStorageStatus.SAVED -> LocalStorageNotice(
        title = "Saved on this device",
        body = "Saved on this device.",
        isError = false,
    )
    LocalStorageStatus.RECOVERED -> LocalStorageNotice(
        title = "Practice restored",
        body = "Your saved practice was restored on this device.",
        isError = false,
    )
    LocalStorageStatus.REPAIRED -> LocalStorageNotice(
        title = "Local storage recovered",
        body = "An unreadable saved record was removed safely. Start a fresh practice; the free core is still available.",
        isError = true,
    )
    LocalStorageStatus.UNAVAILABLE -> LocalStorageNotice(
        title = "Local storage unavailable",
        body = "This build cannot access local storage. Keep a copy of important text before leaving.",
        isError = true,
    )
    LocalStorageStatus.CORRUPT -> LocalStorageNotice(
        title = "Saved practice not restored",
        body = "A saved practice could not be read safely. It was not restored; start a fresh practice.",
        isError = true,
    )
    LocalStorageStatus.FAILED -> LocalStorageNotice(
        title = "Local save failed",
        body = "The latest local change could not be saved. Keep this screen open and try again.",
        isError = true,
    )
}

internal fun LocalStorageStatus.userMessage(): String? = notice()?.body

internal fun storageNoticeFor(vararg statuses: LocalStorageStatus): LocalStorageNotice? {
    val status = statuses.firstOrNull { current ->
        current == LocalStorageStatus.UNAVAILABLE ||
            current == LocalStorageStatus.CORRUPT ||
            current == LocalStorageStatus.FAILED
            || current == LocalStorageStatus.REPAIRED
    } ?: statuses.firstOrNull { current ->
        current == LocalStorageStatus.SAVED || current == LocalStorageStatus.RECOVERED
    }
    return status?.notice()
}

internal data class LocalStorageStartup<out T>(
    val value: T?,
    val status: LocalStorageStatus,
)

/**
 * Recover only local records that are already known to be unreadable. A
 * failed clear keeps the CORRUPT status so the UI does not imply that data was
 * discarded when the platform could not complete the repair.
 */
internal fun <T> recoverCorruptLocalStorage(
    result: LocalStorageReadResult<T>,
    clear: () -> LocalStorageWriteResult,
): LocalStorageStartup<T> = if (result.status == LocalStorageStatus.CORRUPT) {
    LocalStorageStartup(
        value = null,
        status = if (clear() == LocalStorageWriteResult.CLEARED) {
            LocalStorageStatus.REPAIRED
        } else {
            LocalStorageStatus.CORRUPT
        },
    )
} else {
    LocalStorageStartup(result.value, result.status)
}

internal sealed interface LocalStorageReadResult<out T> {
    val status: LocalStorageStatus
    val value: T?

    data class Success<T>(override val value: T?) : LocalStorageReadResult<T> {
        override val status: LocalStorageStatus = if (value == null) {
            LocalStorageStatus.AVAILABLE
        } else {
            LocalStorageStatus.RECOVERED
        }
    }

    data object Unavailable : LocalStorageReadResult<Nothing> {
        override val status: LocalStorageStatus = LocalStorageStatus.UNAVAILABLE
        override val value: Nothing? = null
    }

    data object Corrupt : LocalStorageReadResult<Nothing> {
        override val status: LocalStorageStatus = LocalStorageStatus.CORRUPT
        override val value: Nothing? = null
    }

    data object Failed : LocalStorageReadResult<Nothing> {
        override val status: LocalStorageStatus = LocalStorageStatus.FAILED
        override val value: Nothing? = null
    }
}

internal enum class LocalStorageWriteResult(
    val status: LocalStorageStatus,
) {
    SAVED(LocalStorageStatus.SAVED),
    CLEARED(LocalStorageStatus.AVAILABLE),
    UNAVAILABLE(LocalStorageStatus.UNAVAILABLE),
    FAILED(LocalStorageStatus.FAILED),
}
