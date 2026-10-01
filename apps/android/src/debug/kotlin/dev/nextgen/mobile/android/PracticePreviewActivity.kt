package dev.nextgen.mobile.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import dev.nextgen.mobile.LocalEvidriloLanguage
import dev.nextgen.mobile.EvidriloLanguage
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.nextgen.mobile.EvidriloProjectMapVisualPreview
import dev.nextgen.mobile.EvidriloPracticeCourseScreen
import dev.nextgen.mobile.EvidriloTheme
import dev.nextgen.mobile.domain.conclusion.ConclusionEvent
import dev.nextgen.mobile.domain.conclusion.ConclusionReducer
import dev.nextgen.mobile.domain.conclusion.ConclusionState
import dev.nextgen.mobile.domain.practice.PracticeCourseState
import dev.nextgen.mobile.domain.practice.PracticeLessonId
import dev.nextgen.mobile.domain.practice.PracticeLessonSession
import dev.nextgen.mobile.storage.InMemoryPracticeCourseStore

/** Debug-only fixture. No application account, project, history or storage is accessed. */
class PracticePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val systemBarColor = if (dark) android.graphics.Color.rgb(14, 18, 32) else android.graphics.Color.WHITE
        window.statusBarColor = systemBarColor
        window.navigationBarColor = systemBarColor
        window.decorView.systemUiVisibility = if (dark) 0 else android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        // Debug-only access fixture. Never forwarded to the production application or billing.
        val previewPro = intent.getBooleanExtra("preview_pro", false)
        setContent {
            val reducer = remember { ConclusionReducer() }
            var state by remember { mutableStateOf<ConclusionState>(ConclusionState.Intro) }
            val courseStore = remember { InMemoryPracticeCourseStore().apply {
                if (intent.getBooleanExtra("preview_saved_pro_attempt", false)) {
                    save(PracticeCourseState(active = PracticeLessonId.STUDIES, sessions = mapOf(
                        PracticeLessonId.STUDIES to PracticeLessonSession(PracticeLessonId.STUDIES),
                    )))
                }
            } }
            CompositionLocalProvider(LocalEvidriloLanguage provides if(intent.getStringExtra("preview_language")=="id") EvidriloLanguage.INDONESIAN else EvidriloLanguage.ENGLISH) {
            EvidriloTheme {
                if (intent.getBooleanExtra("preview_project_map", false)) {
                    EvidriloProjectMapVisualPreview { finish() }
                } else EvidriloPracticeCourseScreen(
                    tabletState = state,
                    onTabletEvent = { event -> state = reducer.reduce(state, event) },
                    onExit = { finish() },
                    onOpenProjects = { finish() },
                    store = courseStore,
                    temporaryPreview = true,
                    hasVerifiedProAccess = previewPro,
                    onOpenPro = { startActivity(android.content.Intent(this, ProPreviewActivity::class.java).putExtra("comparison_only", true)) },
                )
            }
            }
        }
    }
}
