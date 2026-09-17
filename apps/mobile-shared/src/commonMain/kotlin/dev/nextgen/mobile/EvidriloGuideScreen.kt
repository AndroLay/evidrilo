package dev.nextgen.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.audio.AudioPlaybackState
import dev.nextgen.mobile.audio.EvidriloAudioListenControl
import dev.nextgen.mobile.surfaces.disclosureActionLabel
import dev.nextgen.mobile.surfaces.disclosureStateDescription

@Composable
internal fun EvidriloGuideScreen(
    backLabel: String,
    onBack: () -> Unit,
    onOpenPractice: () -> Unit,
    onReplayOnboarding: () -> Unit,
    audioState: AudioPlaybackState = AudioPlaybackState.Idle,
    onListen: () -> Unit = {},
    onPauseOrResumeAudio: () -> Unit = {},
    onStopAudio: () -> Unit = {},
) {
    var expandedTopic by remember { mutableStateOf<EvidriloGuideTopic?>(null) }
    var questionsExpanded by remember { mutableStateOf(false) }

    EvidriloContentColumn {
        EvidriloBackButton(label = backLabel, onClick = onBack)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Practice guide", style = MaterialTheme.typography.displayMedium)
            Text(
                "A short way to move from an observation to a claim you can explain.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        EvidriloAudioListenControl(
            state = audioState,
            onListen = onListen,
            onPauseOrResume = onPauseOrResumeAudio,
            onStopAudio = onStopAudio,
        )

        EvidriloGuideProcess()

        EvidriloSectionHeading("Three things to practice")
        EvidriloSettingsGroup {
            evidriloGuideTopics.forEachIndexed { index, topic ->
                val copy = guideTopicCopy(topic)
                EvidriloGuideDisclosure(
                    topic = topic,
                    copy = copy,
                    expanded = expandedTopic == topic,
                    onClick = {
                        expandedTopic = if (expandedTopic == topic) null else topic
                    },
                )
                if (index < evidriloGuideTopics.lastIndex) {
                    EvidriloDivider()
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = EvidriloColors.Surface),
            border = BorderStroke(1.dp, EvidriloColors.Separator),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                EvidriloGuideQuestionHeader(
                    expanded = questionsExpanded,
                    onClick = { questionsExpanded = !questionsExpanded },
                )
                if (questionsExpanded) {
                    HorizontalDivider(color = EvidriloColors.Separator)
                    Column(
                        modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        EvidriloGuideFaq(
                            question = "Does a selected fact prove the claim?",
                            answer = "No. It gives the claim an explicit evidence anchor. The evaluator still checks scope and limitations.",
                        )
                        EvidriloGuideFaq(
                            question = "What if the evidence is incomplete?",
                            answer = "Keep the claim bounded to what remains observable and say which limitation prevents a broader conclusion.",
                        )
                        EvidriloGuideFaq(
                            question = "Who writes the conclusion?",
                            answer = "You do. Evidrilo gives reasons and anchors; it does not generate or grade the conclusion for you.",
                        )
                    }
                }
            }
        }

        EvidriloPrimaryButton(
            label = "Open free practice",
            onClick = onOpenPractice,
        )
        EvidriloSecondaryButton(
            label = "See the introduction again",
            onClick = onReplayOnboarding,
        )
        Text(
            "No account is required. The supplied case and your draft stay on this device.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun EvidriloGuideProcess() {
    val steps = listOf(
        "Evidence" to EvidriloIconName.FILE,
        "Claim" to EvidriloIconName.CHECKLIST,
        "Limits" to EvidriloIconName.SHIELD,
        "Revision" to EvidriloIconName.ARROW_FORWARD,
    )

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // Keep the labels readable in the narrow desktop window used by the
        // JVM target as well as on phones. Four equal-width cards otherwise
        // split short words such as "Evidence" and "Revision".
        if (maxWidth < 620.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                steps.forEachIndexed { index, (label, icon) ->
                    EvidriloGuideProcessStep(label = label, icon = icon, vertical = true)
                    if (index < steps.lastIndex) {
                        EvidriloIcon(
                            name = EvidriloIconName.CHEVRON_DOWN,
                            tint = EvidriloColors.Cobalt,
                            modifier = Modifier.padding(start = 20.dp),
                        )
                    }
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                steps.forEachIndexed { index, (label, icon) ->
                    EvidriloGuideProcessStep(
                        label = label,
                        icon = icon,
                        vertical = false,
                        modifier = Modifier.weight(1f),
                    )
                    if (index < steps.lastIndex) {
                        EvidriloIcon(
                            name = EvidriloIconName.ARROW_FORWARD,
                            tint = EvidriloColors.Cobalt,
                            modifier = Modifier.width(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EvidriloGuideProcessStep(
    label: String,
    icon: EvidriloIconName,
    vertical: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .then(if (vertical) Modifier.fillMaxWidth() else Modifier)
            .clip(RoundedCornerShape(16.dp))
            .background(EvidriloColors.Tint)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(EvidriloColors.White),
            contentAlignment = Alignment.Center,
        ) {
            EvidriloIcon(name = icon, tint = EvidriloColors.Cobalt, modifier = Modifier.size(20.dp))
        }
        Text(label, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun EvidriloGuideDisclosure(
    topic: EvidriloGuideTopic,
    copy: EvidriloGuideTopicCopy,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = disclosureActionLabel(copy.title, expanded)
                role = Role.Button
                stateDescription = disclosureStateDescription(expanded)
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 82.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            EvidriloIcon(
                name = when (topic) {
                    EvidriloGuideTopic.SUPPORTING_EVIDENCE -> EvidriloIconName.FILE
                    EvidriloGuideTopic.CLAIM_BOUNDS -> EvidriloIconName.SHIELD
                    EvidriloGuideTopic.FEEDBACK_REVISION -> EvidriloIconName.CHECKLIST
                },
                tint = EvidriloColors.Cobalt,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(copy.title, style = MaterialTheme.typography.titleMedium)
                Text(copy.subtitle, style = MaterialTheme.typography.bodyMedium)
            }
            EvidriloIcon(
                name = if (expanded) EvidriloIconName.CHEVRON_DOWN else EvidriloIconName.CHEVRON_RIGHT,
                tint = EvidriloColors.Slate,
            )
        }
        if (expanded) {
            Text(
                copy.body,
                modifier = Modifier.padding(start = 54.dp, end = 16.dp, bottom = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun EvidriloGuideQuestionHeader(
    expanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = disclosureActionLabel("common questions", expanded)
                role = Role.Button
                stateDescription = disclosureStateDescription(expanded)
            }
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        EvidriloIcon(EvidriloIconName.QUESTION, tint = EvidriloColors.Cobalt)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Common questions", style = MaterialTheme.typography.titleMedium)
            Text("A few boundaries worth remembering", style = MaterialTheme.typography.bodyMedium)
        }
        EvidriloIcon(
            name = if (expanded) EvidriloIconName.CHEVRON_DOWN else EvidriloIconName.CHEVRON_RIGHT,
            tint = EvidriloColors.Slate,
        )
    }
}

@Composable
private fun ColumnScope.EvidriloGuideFaq(
    question: String,
    answer: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(question, style = MaterialTheme.typography.titleSmall)
        Text(answer, style = MaterialTheme.typography.bodyMedium)
    }
}
