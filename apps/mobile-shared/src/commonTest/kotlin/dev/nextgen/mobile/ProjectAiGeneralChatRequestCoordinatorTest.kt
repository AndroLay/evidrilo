package dev.nextgen.mobile

import dev.nextgen.mobile.projectcatalog.ProjectAiGeneralChatRequest
import dev.nextgen.mobile.projectcatalog.ProjectAiGeneralChatResult
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class ProjectAiGeneralChatRequestCoordinatorTest {
    @Test
    fun `account-bound cancellation prevents queued request reply and credit refresh`() {
        val dispatcher = QueuedTestDispatcher()
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        var currentAccountId: String? = "account-a"
        var sendCalls = 0
        var creditRefreshCalls = 0
        var resultCallbacks = 0
        val providerResponse = CompletableDeferred<ProjectAiGeneralChatResult>()
        val coordinator = ProjectAiGeneralChatRequestCoordinator(
            scope = scope,
            currentAccountId = { currentAccountId },
            sendMessage = { _, _ ->
                sendCalls += 1
                providerResponse.await()
            },
            refreshCredits = {
                creditRefreshCalls += 1
                null
            },
            onCreditsUpdated = {},
        )

        coordinator.send(
            accountId = "account-a",
            request = ProjectAiGeneralChatRequest(
                installationId = "installation-1",
                locale = "en",
                message = "Synthetic test question",
                consentConfirmed = true,
            ),
            idempotencyKey = "request-key-0001",
            onResult = { resultCallbacks += 1 },
        )
        dispatcher.runCurrent()
        assertEquals(1, sendCalls)

        currentAccountId = "account-b"
        coordinator.cancel()
        providerResponse.complete(ProjectAiGeneralChatResult.Unavailable("TEST_PROVIDER_DISABLED"))
        dispatcher.runCurrent()

        assertEquals(1, sendCalls)
        assertEquals(0, creditRefreshCalls)
        assertEquals(0, resultCallbacks)
        scope.cancel()
    }
}

private class QueuedTestDispatcher : CoroutineDispatcher() {
    private val pending = mutableListOf<Runnable>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        pending += block
    }

    fun runCurrent() {
        while (pending.isNotEmpty()) pending.removeAt(0).run()
    }
}
