package dev.nextgen.mobile.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import dev.nextgen.mobile.EvidriloLanguage
import dev.nextgen.mobile.LocalEvidriloLanguage
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.nextgen.mobile.EvidriloOnboardingScreen
import dev.nextgen.mobile.EvidriloTheme
import dev.nextgen.mobile.domain.onboarding.GetStartedTourState
import dev.nextgen.mobile.domain.onboarding.GetStartedTourStep

/** Debug-only native preview. Never reads or writes the user's application stores. */
class GetStartedPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val systemBarColor = if (dark) android.graphics.Color.rgb(14, 18, 32) else android.graphics.Color.WHITE
        window.statusBarColor = systemBarColor
        window.navigationBarColor = systemBarColor
        window.decorView.systemUiVisibility = if (dark) 0 else android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContent {
            var section by rememberSaveable { mutableIntStateOf(0) }
            var language by rememberSaveable {mutableStateOf(EvidriloLanguage.ENGLISH)}
            CompositionLocalProvider(LocalEvidriloLanguage provides language) {
            EvidriloTheme {
                EvidriloOnboardingScreen(
                    language=language,onSetLanguage={language=it},
                    tourState = GetStartedTourState(step = GetStartedTourStep.entries[section]),
                    onNext = { section = (section + 1).coerceAtMost(GetStartedTourStep.entries.lastIndex) },
                    onBack = { section = (section - 1).coerceAtLeast(0) },
                    onSkip = { finish() },
                    onStartProject = { finish() },
                )
            }
            }
        }
    }
}
