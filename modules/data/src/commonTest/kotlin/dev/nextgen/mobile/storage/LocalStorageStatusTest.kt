package dev.nextgen.mobile.storage

import dev.nextgen.mobile.domain.conclusion.ConclusionDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalStorageStatusTest {
    @Test
    fun readResultsDistinguishEmptyAndUnavailableWithoutExposingPayloads() {
        val empty = LocalStorageReadResult.Success<ConclusionSessionSnapshot>(null)
        val recovered = LocalStorageReadResult.Success("redacted snapshot")

        assertEquals(LocalStorageStatus.AVAILABLE, empty.status)
        assertNull(empty.value)
        assertEquals(LocalStorageStatus.RECOVERED, recovered.status)
        assertEquals(LocalStorageStatus.UNAVAILABLE, LocalStorageReadResult.Unavailable.status)
        assertEquals(LocalStorageStatus.CORRUPT, LocalStorageReadResult.Corrupt.status)
    }

    @Test
    fun writeResultsUseStableRedactedStatuses() {
        assertEquals(LocalStorageWriteResult.SAVED.status, LocalStorageStatus.SAVED)
        assertEquals(LocalStorageWriteResult.CLEARED.status, LocalStorageStatus.AVAILABLE)
        assertEquals(LocalStorageWriteResult.UNAVAILABLE.status, LocalStorageStatus.UNAVAILABLE)
        assertEquals(LocalStorageWriteResult.FAILED.status, LocalStorageStatus.FAILED)
    }

    @Test
    fun storageMessagesAreSafeAndNeverIncludeSavedContent() {
        assertNull(LocalStorageStatus.AVAILABLE.userMessage())
        assertEquals(
            "The latest local change could not be saved. Keep this screen open and try again.",
            LocalStorageStatus.FAILED.userMessage(),
        )
        assertEquals("Saved on this device.", LocalStorageStatus.SAVED.userMessage())
        assertEquals("Your saved practice was restored on this device.", LocalStorageStatus.RECOVERED.userMessage())
    }

    @Test
    fun storageNoticePrefersErrorsAndNeverIncludesDraftContent() {
        val notice = storageNoticeFor(
            LocalStorageStatus.SAVED,
            LocalStorageStatus.CORRUPT,
        )

        assertEquals("Saved practice not restored", notice?.title)
        assertTrue(notice?.isError == true)
        assertFalse(requireNotNull(notice).body.contains("redacted snapshot"))
    }

    @Test
    fun corrupt_records_are_marked_repaired_only_after_a_successful_clear() {
        var clearCalls = 0
        val repaired = recoverCorruptLocalStorage<ConclusionSessionSnapshot>(
            LocalStorageReadResult.Corrupt,
        ) {
            clearCalls += 1
            LocalStorageWriteResult.CLEARED
        }

        assertNull(repaired.value)
        assertEquals(LocalStorageStatus.REPAIRED, repaired.status)
        assertEquals(1, clearCalls)

        val stillCorrupt = recoverCorruptLocalStorage<ConclusionSessionSnapshot>(
            LocalStorageReadResult.Corrupt,
        ) { LocalStorageWriteResult.FAILED }
        assertNull(stillCorrupt.value)
        assertEquals(LocalStorageStatus.CORRUPT, stillCorrupt.status)
    }

    @Test
    fun healthy_records_are_not_cleared_during_startup_recovery() {
        var clearCalls = 0
        val value = "safe local value"
        val startup = recoverCorruptLocalStorage(
            LocalStorageReadResult.Success(value),
        ) {
            clearCalls += 1
            LocalStorageWriteResult.CLEARED
        }

        assertEquals(value, startup.value)
        assertEquals(LocalStorageStatus.RECOVERED, startup.status)
        assertEquals(0, clearCalls)
    }

    @Test
    fun noOpStoresFailClosed() {
        val sessionStore = NoopConclusionSessionStore()
        val historyStore = NoopConclusionHistoryStore()
        val snapshot = ConclusionSessionSnapshot(
            phase = ConclusionSessionPhase.DRAFTING,
            initialDraft = ConclusionDraft(),
            currentDraft = ConclusionDraft(),
        )

        assertEquals(LocalStorageStatus.UNAVAILABLE, sessionStore.load().status)
        assertEquals(LocalStorageWriteResult.UNAVAILABLE, sessionStore.save(snapshot))
        assertEquals(LocalStorageWriteResult.UNAVAILABLE, sessionStore.clear())
        assertEquals(LocalStorageStatus.UNAVAILABLE, historyStore.load().status)
        assertEquals(LocalStorageWriteResult.UNAVAILABLE, historyStore.save(snapshot))
        assertEquals(LocalStorageWriteResult.UNAVAILABLE, historyStore.clear())
    }
}
