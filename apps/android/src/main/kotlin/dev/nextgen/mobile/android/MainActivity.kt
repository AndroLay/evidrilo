package dev.nextgen.mobile.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.nextgen.mobile.App
import dev.nextgen.mobile.account.AndroidAccountAuthStorage
import dev.nextgen.mobile.account.submitAccountAuthRedirect
import dev.nextgen.mobile.security.AndroidSecureSessionStorage
import dev.nextgen.mobile.storage.AndroidConclusionStorage
import dev.nextgen.mobile.storage.AndroidConclusionHistoryStorage
import dev.nextgen.mobile.storage.AndroidOnboardingStorage
import dev.nextgen.mobile.sync.AndroidSyncQueueStorage
import dev.nextgen.mobile.sync.AndroidSyncConsentStorage
import dev.nextgen.mobile.analytics.AndroidAnalyticsConsentStorage
import dev.nextgen.mobile.audio.AndroidAudioStorage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidConclusionStorage.initialize(applicationContext)
        AndroidConclusionHistoryStorage.initialize(applicationContext)
        AndroidOnboardingStorage.initialize(applicationContext)
        AndroidSyncQueueStorage.initialize(applicationContext)
        AndroidSyncConsentStorage.initialize(applicationContext)
        AndroidAnalyticsConsentStorage.initialize(applicationContext)
        AndroidAudioStorage.initialize(applicationContext)
        AndroidSecureSessionStorage.initialize(applicationContext)
        AndroidAccountAuthStorage.initialize(applicationContext)
        setContent { App() }
        handleAuthIntent(intent)
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAuthIntent(intent)
    }

    private fun handleAuthIntent(intent: android.content.Intent?) {
        if (intent?.action == android.content.Intent.ACTION_VIEW) {
            intent.dataString?.let(::submitAccountAuthRedirect)
        }
    }
}
