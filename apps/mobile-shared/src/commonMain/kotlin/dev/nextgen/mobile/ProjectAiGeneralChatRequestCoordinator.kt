package dev.nextgen.mobile

import dev.nextgen.mobile.ai.AiCredits
import dev.nextgen.mobile.projectcatalog.ProjectAiGeneralChatRequest
import dev.nextgen.mobile.projectcatalog.ProjectAiGeneralChatResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Owns one account-bound request; cancellation invalidates both the reply and its credit refresh. */
internal class ProjectAiGeneralChatRequestCoordinator(
    private val scope: CoroutineScope,
    private val currentAccountId: () -> String?,
    private val sendMessage: suspend (ProjectAiGeneralChatRequest, String) -> ProjectAiGeneralChatResult,
    private val refreshCredits: suspend () -> AiCredits?,
    private val onCreditsUpdated: (AiCredits) -> Unit,
) {
    private var generation = 0L
    private var activeJob: Job? = null

    fun send(
        accountId: String,
        request: ProjectAiGeneralChatRequest,
        idempotencyKey: String,
        onResult: (ProjectAiGeneralChatResult) -> Unit,
    ) {
        cancel()
        val requestGeneration = ++generation
        val job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                val result = try {
                    sendMessage(request, idempotencyKey)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    ProjectAiGeneralChatResult.Failed(
                        code = "PROJECT_AI_OUTCOME_UNKNOWN",
                        retryable = false,
                        outcomeUnknown = true,
                    )
                }
                if (!isCurrent(accountId, requestGeneration)) return@launch
                onResult(result)

                if (!isCurrent(accountId, requestGeneration)) return@launch
                val latestCredits = try {
                    refreshCredits()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    null
                }
                if (isCurrent(accountId, requestGeneration) && latestCredits != null) {
                    onCreditsUpdated(latestCredits)
                }
            } finally {
                if (generation == requestGeneration) activeJob = null
            }
        }
        activeJob = job
        job.start()
    }

    fun cancel() {
        generation += 1
        activeJob?.cancel()
        activeJob = null
    }

    private fun isCurrent(accountId: String, requestGeneration: Long): Boolean =
        generation == requestGeneration && currentAccountId() == accountId
}
