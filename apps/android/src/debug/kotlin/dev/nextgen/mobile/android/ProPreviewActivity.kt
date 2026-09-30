package dev.nextgen.mobile.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.nextgen.mobile.EvidriloProVisualPreview
import dev.nextgen.mobile.EvidriloTheme

/** Debug-only preview. No account, project, payment or entitlement operation is performed. */
class ProPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val color = if (dark) android.graphics.Color.rgb(14, 18, 32) else android.graphics.Color.WHITE
        window.statusBarColor = color
        window.navigationBarColor = color
        window.decorView.systemUiVisibility = if (dark) 0 else android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContent {
            EvidriloTheme { EvidriloProVisualPreview(onClose = { finish() }) }
        }
    }
}
