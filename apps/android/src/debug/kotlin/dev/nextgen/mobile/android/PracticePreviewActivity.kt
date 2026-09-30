package dev.nextgen.mobile.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.nextgen.mobile.EvidriloPracticeCourseScreen
import dev.nextgen.mobile.EvidriloTheme
import dev.nextgen.mobile.domain.conclusion.ConclusionEvent
import dev.nextgen.mobile.domain.conclusion.ConclusionReducer
import dev.nextgen.mobile.domain.conclusion.ConclusionState
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
        setContent {
            val reducer = remember { ConclusionReducer() }
            var state by remember { mutableStateOf<ConclusionState>(ConclusionState.Intro) }
            val courseStore = remember { InMemoryPracticeCourseStore() }
            EvidriloTheme {
                EvidriloPracticeCourseScreen(
                    tabletState = state,
                    onTabletEvent = { event -> state = reducer.reduce(state, event) },
                    onExit = { finish() },
                    onOpenProjects = { finish() },
                    store = courseStore,
                    temporaryPreview = true,
                )
            }
        }
    }
}
