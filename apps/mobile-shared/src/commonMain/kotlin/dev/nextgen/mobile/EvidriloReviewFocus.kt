package dev.nextgen.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import dev.nextgen.mobile.EvidriloUiText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/** Maximizing changes presentation only; proposal edits/selection remain owned by the caller. */
@Composable
internal fun EvidriloReviewFocus(title: String, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    if (expanded) {
        Dialog(onDismissRequest = { expanded = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            dev.nextgen.mobile.navigation.EvidriloBackGestureHost {
                EvidriloBackGesture("Close full-screen review", { expanded = false })
                Surface(Modifier.fillMaxSize(), color = EvidriloColors.Canvas) {
                    EvidriloContentColumn {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                            TextButton({ expanded = false }) { Text(uiText("Close")) }
                        }
                        content()
                    }
                }
            }
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            TextButton({ expanded = true }) { Text("Full screen") }
        }
        content()
    }
}
