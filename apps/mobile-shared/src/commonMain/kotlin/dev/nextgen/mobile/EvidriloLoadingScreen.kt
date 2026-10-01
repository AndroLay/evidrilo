package dev.nextgen.mobile

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import dev.nextgen.mobile.EvidriloUiText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.design.resources.evidriloLogoDrawable
import org.jetbrains.compose.resources.painterResource

/** The two places where a full-screen loading state is truthful and useful. */
internal enum class EvidriloLoadingMode {
    APP_BOOTSTRAP,
    NETWORK_PAGE,
}

internal const val EvidriloLoadingLogoScaleMin = 0.92f
internal const val EvidriloLoadingLogoScaleMax = 1.08f

internal data class EvidriloLoadingPresentation(
    val title: String,
    val message: String,
    val accessibilityLabel: String,
    val canGoBack: Boolean,
)

internal fun evidriloLoadingPresentation(
    mode: EvidriloLoadingMode,
): EvidriloLoadingPresentation = when (mode) {
    EvidriloLoadingMode.APP_BOOTSTRAP -> EvidriloLoadingPresentation(
        title = "Evidrilo",
        message = "Preparing your evidence workspace…",
        accessibilityLabel = "Loading Evidrilo",
        canGoBack = false,
    )

    EvidriloLoadingMode.NETWORK_PAGE -> EvidriloLoadingPresentation(
        title = "Connecting securely",
        message = "This page needs a network connection. Your local work stays safe.",
        accessibilityLabel = "Loading network page",
        canGoBack = true,
    )
}

/**
 * Branded loading state for app bootstrap and network-only destinations.
 *
 * This deliberately has no fake percentage or artificial delay. The caller
 * owns the actual asynchronous work and replaces this surface when it has a
 * truthful result. Offline-capable destinations should not use this screen.
 */
@Composable
internal fun EvidriloLoadingScreen(
    mode: EvidriloLoadingMode,
    onBack: (() -> Unit)? = null,
) {
    val presentation = evidriloLoadingPresentation(mode)
    val logoTransition = rememberInfiniteTransition(label = "evidrilo-loading-logo")
    val logoScale = logoTransition.animateFloat(
        initialValue = EvidriloLoadingLogoScaleMin,
        targetValue = EvidriloLoadingLogoScaleMax,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 1_500,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(
                offsetMillis = 750,
                offsetType = StartOffsetType.FastForward,
            ),
        ),
        label = "evidrilo-loading-logo-scale",
    )
    // Full Cobalt splash: a deep-to-bright vertical gradient, the Evidrilo logo
    // resting on a soft translucent halo (no boxed border), and white text.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        EvidriloColors.CobaltPressed,
                        EvidriloColors.PrimaryAction,
                        EvidriloColors.CobaltPressed,
                    ),
                ),
            )
            .safeDrawingPadding()
            .semantics(mergeDescendants = true) {
                contentDescription = presentation.accessibilityLabel
                stateDescription = "Loading"
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(176.dp)
                    .graphicsLayer {
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                    }
                    .drawBehind {
                        // Soft white halo so the logo lifts off the blue field
                        // without any hard box or ring around it.
                        val glowRadius = size.minDimension * 0.62f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    EvidriloColors.White.copy(alpha = 0.22f),
                                    EvidriloColors.White.copy(alpha = 0f),
                                ),
                                center = center,
                                radius = glowRadius,
                            ),
                            radius = glowRadius,
                            center = center,
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                // Transparent-background logo mark so it sits cleanly on the
                // blue field — no baked-in square or ring.
                Image(
                    painter = painterResource(evidriloLogoDrawable),
                    contentDescription = null,
                    modifier = Modifier.size(140.dp),
                )
            }
            Text(
                text = presentation.title,
                style = MaterialTheme.typography.headlineSmall,
                color = EvidriloColors.White,
            )
            Text(
                text = presentation.message,
                modifier = Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = EvidriloColors.White.copy(alpha = 0.85f),
            )
            if (presentation.canGoBack && onBack != null) {
                EvidriloBackGesture(label = "Leave loading screen", onClick = onBack)
            }
        }
    }
}
