package dev.nextgen.mobile

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.nextgen.mobile.billing.BillingGateway
import dev.nextgen.mobile.billing.createPlatformBillingGateway

@Composable
fun App() {
    EvidriloTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            val billingGateway: BillingGateway = remember { createPlatformBillingGateway() }
            EvidriloApp(billingGateway)
        }
    }
}
