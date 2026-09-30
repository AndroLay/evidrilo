package dev.nextgen.mobile.navigation

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

internal class EvidriloBackDispatcher {
    class Registration(val callback: () -> Unit)
    val registrations = mutableStateListOf<Registration>()
    fun dispatch(): Boolean {
        val registration = registrations.lastOrNull() ?: return false
        registration.callback()
        return true
    }
}

internal val LocalEvidriloBackDispatcher = staticCompositionLocalOf<EvidriloBackDispatcher?> { null }
internal expect val evidriloUsesEdgeBackGesture: Boolean

@Composable
internal fun RegisterEvidriloBackGesture(enabled: Boolean, onBack: () -> Unit) {
    val dispatcher = LocalEvidriloBackDispatcher.current
    val callback by rememberUpdatedState(onBack)
    DisposableEffect(dispatcher, enabled) {
        val registration = if (enabled) EvidriloBackDispatcher.Registration { callback() } else null
        if (registration != null) dispatcher?.registrations?.add(registration)
        onDispose { if (registration != null) dispatcher?.registrations?.remove(registration) }
    }
}

/** Android uses OS Back. iPhone supplies an edge swipe; desktop accepts Escape. */
@Composable
internal fun EvidriloBackGestureHost(content: @Composable () -> Unit) {
    val dispatcher = remember { EvidriloBackDispatcher() }
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }
    CompositionLocalProvider(LocalEvidriloBackDispatcher provides dispatcher) {
        Box(Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            event.key == Key.Escape && event.type == KeyEventType.KeyUp && dispatcher.dispatch()
        }) {
            content()
            if (evidriloUsesEdgeBackGesture && dispatcher.registrations.isNotEmpty()) {
                Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().width(20.dp).clearAndSetSemantics {}
                    .pointerInput(dispatcher, threshold) {
                        var distance = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { distance = 0f }, onDragCancel = { distance = 0f },
                            onDragEnd = { if (distance >= threshold) dispatcher.dispatch(); distance = 0f },
                            onHorizontalDrag = { change, amount -> distance += amount; if (distance > 0f) change.consume() },
                        )
                    })
            }
        }
    }
}
