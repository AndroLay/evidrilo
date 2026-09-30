package dev.nextgen.mobile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvidriloGeneralAiChatTest {
    @Test
    fun `empty project picker explains AI eligibility instead of claiming no projects exist`() {
        val message = projectAiEntryEmptyStateMessage()

        assertEquals(
            "No project with a supported AI stage is available yet. Your saved projects remain available in Home.",
            message,
        )
        assertNotEquals("No saved projects yet.", message)
    }

    @Test
    fun `global AI entry requires an explicit project or unlinked general choice`() {
        assertEquals(
            ProjectAiEntrySurface.MODE_PICKER,
            reduceProjectAiEntrySurface(ProjectAiEntrySurface.CLOSED, ProjectAiEntryEvent.OPEN),
        )
        assertEquals(
            ProjectAiEntrySurface.PROJECT_PICKER,
            reduceProjectAiEntrySurface(ProjectAiEntrySurface.MODE_PICKER, ProjectAiEntryEvent.CHOOSE_PROJECT),
        )
        assertEquals(
            ProjectAiEntrySurface.GENERAL_CHAT,
            reduceProjectAiEntrySurface(ProjectAiEntrySurface.MODE_PICKER, ProjectAiEntryEvent.CHOOSE_GENERAL),
        )
        assertEquals(
            ProjectAiEntrySurface.ACTIVITY,
            reduceProjectAiEntrySurface(ProjectAiEntrySurface.GENERAL_CHAT, ProjectAiEntryEvent.CHOOSE_ACTIVITY),
        )
        assertEquals(
            ProjectAiEntrySurface.GENERAL_CHAT,
            reduceProjectAiEntrySurface(ProjectAiEntrySurface.ACTIVITY, ProjectAiEntryEvent.BACK),
        )
    }

    @Test
    fun `AI workspace mode selector switches directly without mixing modes`() {
        assertEquals(
            ProjectAiEntrySurface.PROJECT_PICKER,
            reduceProjectAiEntrySurface(ProjectAiEntrySurface.GENERAL_CHAT, ProjectAiEntryEvent.CHOOSE_PROJECT),
        )
        assertEquals(
            ProjectAiEntrySurface.GENERAL_CHAT,
            reduceProjectAiEntrySurface(ProjectAiEntrySurface.PROJECT_PICKER, ProjectAiEntryEvent.CHOOSE_GENERAL),
        )
    }

    @Test
    fun `floating AI shortcut starts at the established bottom end position`() {
        val bounds = AiShortcutBounds(minXPx = 16f, minYPx = 16f, maxXPx = 290f, maxYPx = 700f)

        assertEquals(
            AiShortcutPosition(xPx = 286f, yPx = 646f),
            initialAiShortcutPosition(
                viewportWidthPx = 360f,
                viewportHeightPx = 800f,
                shortcutSizePx = 54f,
                endInsetPx = 20f,
                bottomInsetPx = 100f,
                bounds = bounds,
            ),
        )
    }

    @Test
    fun `dragging floating AI shortcut preserves an in-bounds position`() {
        assertEquals(
            AiShortcutPosition(xPx = 130f, yPx = 95f),
            dragAiShortcutPosition(
                current = AiShortcutPosition(xPx = 100f, yPx = 120f),
                deltaXPx = 30f,
                deltaYPx = -25f,
                bounds = AiShortcutBounds(minXPx = 16f, minYPx = 16f, maxXPx = 286f, maxYPx = 646f),
            ),
        )
    }

    @Test
    fun `dragging floating AI shortcut clamps it inside safe bounds`() {
        val bounds = AiShortcutBounds(minXPx = 16f, minYPx = 16f, maxXPx = 286f, maxYPx = 646f)

        assertEquals(
            AiShortcutPosition(xPx = 16f, yPx = 16f),
            dragAiShortcutPosition(AiShortcutPosition(20f, 30f), -40f, -40f, bounds),
        )
        assertEquals(
            AiShortcutPosition(xPx = 286f, yPx = 646f),
            dragAiShortcutPosition(AiShortcutPosition(280f, 620f), 40f, 40f, bounds),
        )
    }

    @Test
    fun `back from project selection returns to mode choice and dismiss closes entry`() {
        assertEquals(
            ProjectAiEntrySurface.MODE_PICKER,
            reduceProjectAiEntrySurface(ProjectAiEntrySurface.PROJECT_PICKER, ProjectAiEntryEvent.BACK),
        )
        assertEquals(
            ProjectAiEntrySurface.CLOSED,
            reduceProjectAiEntrySurface(ProjectAiEntrySurface.PROJECT_PICKER, ProjectAiEntryEvent.DISMISS),
        )
    }

    @Test
    fun `shared AI credit balance refreshes when chat or project assistance opens`() {
        assertTrue(shouldRefreshAiCreditBalance(ProjectAiEntrySurface.MODE_PICKER))
        assertTrue(shouldRefreshAiCreditBalance(ProjectAiEntrySurface.PROJECT_PICKER))
        assertTrue(shouldRefreshAiCreditBalance(ProjectAiEntrySurface.GENERAL_CHAT))
        assertFalse(shouldRefreshAiCreditBalance(ProjectAiEntrySurface.ACTIVITY))
        assertFalse(shouldRefreshAiCreditBalance(ProjectAiEntrySurface.CLOSED))
    }

    @Test
    fun `project picker excludes inactive projects and templates without published AI operations`() {
        assertNull(projectAiEntryProjectOption("project-1", "Manual", isActive = true, templatePublished = false, hasDeclaredAiOperation = true))
        assertNull(projectAiEntryProjectOption("project-2", "Starter", isActive = true, templatePublished = true, hasDeclaredAiOperation = false))
        assertNull(projectAiEntryProjectOption("project-3", "Archived", isActive = false, templatePublished = true, hasDeclaredAiOperation = true))
        assertEquals(
            ProjectAiEntryProject("project-4", "Reviewed project"),
            projectAiEntryProjectOption("project-4", "Reviewed project", isActive = true, templatePublished = true, hasDeclaredAiOperation = true),
        )
    }

    @Test
    fun `general chat send requires configured API signed in account saved consent and per-message consent`() {
        val granted = ProjectAiConsentUiState.Granted
        assertTrue(
            canSendGeneralAiMessage(
                input = "What does this result mean?",
                consentConfirmed = true,
                consentState = granted,
                accessMessage = null,
                apiConfigured = true,
                sending = false,
            ),
        )
        assertFalse(
            canSendGeneralAiMessage("question", true, granted, "Sign in", apiConfigured = true, sending = false),
        )
        assertFalse(
            canSendGeneralAiMessage("question", true, ProjectAiConsentUiState.NotGranted, null, true, false),
        )
        assertFalse(
            canSendGeneralAiMessage("question", true, ProjectAiConsentUiState.Checking, null, true, false),
        )
        assertFalse(
            canSendGeneralAiMessage("question", true, granted, null, apiConfigured = false, sending = false),
        )
        assertFalse(
            canSendGeneralAiMessage("question", false, granted, null, apiConfigured = true, sending = false),
        )
        assertFalse(
            canSendGeneralAiMessage("question", true, granted, null, apiConfigured = true, sending = true),
        )
    }

    @Test
    fun `provider usage notice reports server charge without claiming the balance refresh succeeded`() {
        val charged = generalChatProviderUsageNotice(creditCost = 3, consentMustBeReviewed = false)
        assertContains(charged, "charged 3 shared AI credits")
        assertContains(charged, "Check your current balance")
        assertFalse(charged.contains("balance has been refreshed", ignoreCase = true))

        val consentChanged = generalChatProviderUsageNotice(creditCost = 1, consentMustBeReviewed = true)
        assertContains(consentChanged, "charged 1 shared AI credit")
        assertContains(consentChanged, "review consent")
        assertFalse(consentChanged.contains("balance has been refreshed", ignoreCase = true))
    }
}
