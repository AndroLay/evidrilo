package dev.nextgen.mobile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.domain.onboarding.GetStartedTourState
import dev.nextgen.mobile.domain.onboarding.GetStartedTourStep
import dev.nextgen.mobile.navigation.EvidriloSystemBackHandler
import dev.nextgen.mobile.storage.LocalStorageNotice

/** Visual product orientation. Every example and gesture is local presentation state. */
@Composable
public fun EvidriloOnboardingScreen(
    tourState: GetStartedTourState,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    onStartProject: () -> Unit,
    storageNotice: LocalStorageNotice? = null,
) {
    val step = tourState.step
    val scene = getStartedScene(step)
    val preview = rememberGetStartedSceneSelections()
    EvidriloSystemBackHandler(enabled = !tourState.isComplete) {
        if (step == GetStartedTourStep.WELCOME) onSkip() else onBack()
    }
    Box(
        modifier = Modifier.fillMaxSize().background(EvidriloColors.Canvas)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EvidriloLogoMark(size = 28.dp)
                Text("Evidrilo", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onSkip, enabled = tourState.canContinue) {
                    Text("Skip", style = MaterialTheme.typography.labelLarge, color = EvidriloColors.Slate)
                }
            }
            GetStartedProgress(step)
            AnimatedContent(
                targetState = step,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally(tween(340, easing = FastOutSlowInEasing)) { direction * it / 3 } + fadeIn(tween(220)))
                        .togetherWith(slideOutHorizontally(tween(230)) { -direction * it / 5 } + fadeOut(tween(150)))
                },
                label = "Product tour shared axis",
            ) { visibleStep ->
                val visibleScene = getStartedScene(visibleStep)
                BoxWithConstraints(Modifier.fillMaxSize().semantics { paneTitle = visibleScene.section }) {
                    val fontScale = LocalDensity.current.fontScale.coerceIn(1f, 1.5f)
                    val artHeight = (maxHeight - 190.dp).coerceIn(270.dp, 350.dp) * fontScale
                    val compact = maxHeight < 440.dp || fontScale > 1.15f
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp).padding(top = 12.dp, bottom = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        storageNotice?.takeIf { it.isError }?.let { EvidriloRecoveryNotice(it) }
                        if (compact) GetStartedSceneText(visibleScene, compact = true)
                        GetStartedSceneArtwork(visibleStep, preview, artHeight)
                        if (!compact) GetStartedSceneText(visibleScene, compact = false)
                    }
                }
            }
            Column(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 10.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EvidriloPrimaryButton(
                    label = scene.action,
                    onClick = if (step == GetStartedTourStep.READY) onStartProject else onNext,
                    enabled = tourState.canContinue,
                    trailingIcon = if (step == GetStartedTourStep.READY) EvidriloIconName.FOLDER else EvidriloIconName.ARROW_FORWARD,
                )
                Text(
                    if (step == GetStartedTourStep.WELCOME) "About 1–2 minutes · at your pace"
                    else "Preview only · your work stays untouched",
                    style = MaterialTheme.typography.bodySmall,
                    color = EvidriloColors.Slate,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun GetStartedProgress(step: GetStartedTourStep) {
    val total = GetStartedTourStep.entries.size
    val current = step.ordinal + 1
    val progress by animateFloatAsState(current / total.toFloat(), tween(420), label = "Tour progress fill")
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)
            .semantics {
                contentDescription = "Get Started progress"
                stateDescription = "Section $current of $total: ${getStartedScene(step).section}"
                progressBarRangeInfo = ProgressBarRangeInfo(current.toFloat(), 0f..total.toFloat(), total - 1)
            }.height(9.dp).clip(RoundedCornerShape(50)).background(EvidriloColors.Separator),
    ) {
        Box(Modifier.fillMaxWidth(progress).height(9.dp).clip(RoundedCornerShape(50)).background(EvidriloColors.Cobalt))
    }
}

@Composable
private fun GetStartedSceneText(scene: GetStartedSceneCopy, compact: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            scene.title,
            style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.displayLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(scene.body, style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 350.dp))
    }
}

@Composable
private fun GetStartedSceneArtwork(step: GetStartedTourStep, preview: GetStartedSceneSelections, height: androidx.compose.ui.unit.Dp) {
    when (step) {
        GetStartedTourStep.WELCOME -> GetStartedWelcomeScene(preview, height)
        GetStartedTourStep.ORGANIZE -> GetStartedProjectsScene(preview, height)
        GetStartedTourStep.REVIEW -> GetStartedEvidenceScene(preview, height)
        GetStartedTourStep.ASSISTANCE -> GetStartedAiScene(preview, height)
        GetStartedTourStep.PORTABILITY -> GetStartedPortabilityScene(preview, height)
        GetStartedTourStep.READY -> GetStartedReadyScene(preview, height)
    }
}
