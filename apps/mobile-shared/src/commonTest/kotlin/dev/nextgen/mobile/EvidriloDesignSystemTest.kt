package dev.nextgen.mobile

import kotlin.test.Test
import kotlin.test.assertEquals

class EvidriloDesignSystemTest {
    @Test
    fun primaryButtonUsesReadableContentWhenDisabled() {
        assertEquals(EvidriloColors.White, evidriloPrimaryButtonContentColor(enabled = true))
        assertEquals(EvidriloColors.Slate, evidriloPrimaryButtonContentColor(enabled = false))
    }
}
