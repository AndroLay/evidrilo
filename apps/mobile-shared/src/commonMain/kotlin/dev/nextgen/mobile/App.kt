package dev.nextgen.mobile

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.nextgen.mobile.account.TEMPORARY_GUEST_MODE_ENABLED
import dev.nextgen.mobile.billing.BillingGateway
import dev.nextgen.mobile.billing.billingGatewayForAccessMode
import dev.nextgen.mobile.billing.createPlatformBillingGateway

@Composable
fun App() {
    // Theme choice is owned above the theme so switching it recomposes the whole
    // tree with the new palette. Settings drives it via themeController.
    val themeController = remember { EvidriloThemeController() }
    EvidriloTheme(mode = themeController.mode) {
        Surface(modifier = Modifier.fillMaxSize()) {
            val billingGateway: BillingGateway = remember {
                billingGatewayForAccessMode(TEMPORARY_GUEST_MODE_ENABLED, ::createPlatformBillingGateway)
            }
            EvidriloApp(billingGateway, themeController = themeController)
        }
    }
}
