package dev.nextgen.mobile

import dev.nextgen.mobile.ai.AiCredits
import kotlin.test.Test
import kotlin.test.assertEquals

class EvidriloAiCreditBalanceTest {
    private val serverCredits = AiCredits(
        consentRecorded = true,
        available = 23,
        grants = emptyList(),
        requestId = "credits-request",
    )

    @Test
    fun `signed out state never exposes a cached account balance`() {
        assertEquals(
            AiCreditBalancePresentation.SignInRequired,
            presentAiCreditBalance(
                accountReady = false,
                refreshing = false,
                credits = serverCredits,
                failureMessage = null,
                canRetry = false,
            ),
        )
    }

    @Test
    fun `refresh state hides old balance until server confirms the latest value`() {
        assertEquals(
            AiCreditBalancePresentation.Refreshing,
            presentAiCreditBalance(
                accountReady = true,
                refreshing = true,
                credits = serverCredits,
                failureMessage = null,
                canRetry = true,
            ),
        )
    }

    @Test
    fun `ready balance comes from the server credit projection`() {
        assertEquals(
            AiCreditBalancePresentation.Ready(serverCredits),
            presentAiCreditBalance(
                accountReady = true,
                refreshing = false,
                credits = serverCredits,
                failureMessage = null,
                canRetry = true,
            ),
        )
    }

    @Test
    fun `failed refresh does not present the stale balance as current`() {
        assertEquals(
            AiCreditBalancePresentation.Unavailable("Balance could not be refreshed", canRetry = true),
            presentAiCreditBalance(
                accountReady = true,
                refreshing = false,
                credits = serverCredits,
                failureMessage = "Balance could not be refreshed",
                canRetry = true,
            ),
        )
    }
}
