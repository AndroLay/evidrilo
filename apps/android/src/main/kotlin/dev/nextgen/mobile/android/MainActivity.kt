package dev.nextgen.mobile.android

import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.nextgen.mobile.App
import dev.nextgen.mobile.account.AndroidAccountAuthStorage
import dev.nextgen.mobile.account.submitAccountAuthRedirect
import dev.nextgen.mobile.security.AndroidSecureSessionStorage
import dev.nextgen.mobile.storage.AndroidAccountOfferStorage
import dev.nextgen.mobile.storage.AndroidConclusionStorage
import dev.nextgen.mobile.storage.AndroidConclusionHistoryStorage
import dev.nextgen.mobile.storage.AndroidOnboardingStorage
import dev.nextgen.mobile.sync.AndroidSyncQueueStorage
import dev.nextgen.mobile.sync.AndroidSyncConsentStorage
import dev.nextgen.mobile.analytics.AndroidAnalyticsConsentStorage
import dev.nextgen.mobile.audio.AndroidAudioStorage
import dev.nextgen.mobile.notifications.AndroidLocalNotificationPlatform
import dev.nextgen.mobile.notifications.AndroidNotificationPreferencesStorage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureSystemBars()
        AndroidConclusionStorage.initialize(applicationContext)
        AndroidConclusionHistoryStorage.initialize(applicationContext)
        AndroidOnboardingStorage.initialize(applicationContext)
        AndroidAccountOfferStorage.initialize(applicationContext)
        AndroidSyncQueueStorage.initialize(applicationContext)
        AndroidSyncConsentStorage.initialize(applicationContext)
        AndroidAnalyticsConsentStorage.initialize(applicationContext)
        AndroidAudioStorage.initialize(applicationContext)
        AndroidSecureSessionStorage.initialize(applicationContext)
        AndroidAccountAuthStorage.initialize(applicationContext)
        AndroidNotificationPreferencesStorage.initialize(applicationContext)
        AndroidLocalNotificationPlatform.initialize(this)
        setContent { App() }
        handleAuthIntent(intent)
    }

    private fun configureSystemBars() {
        window.statusBarColor = android.graphics.Color.WHITE
        window.navigationBarColor = android.graphics.Color.WHITE
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        AndroidLocalNotificationPlatform.onActivityAvailable(this)
        handleAuthIntent(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        AndroidLocalNotificationPlatform.onRequestPermissionsResult(requestCode, grantResults)
    }

    private fun handleAuthIntent(intent: android.content.Intent?) {
        if (intent?.action == android.content.Intent.ACTION_VIEW) {
            intent.dataString?.let(::submitAccountAuthRedirect)
        }
    }
}
