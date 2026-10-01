package dev.nextgen.mobile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import dev.nextgen.mobile.EvidriloUiText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nextgen.mobile.navigation.EvidriloDestination
import dev.nextgen.mobile.navigation.EvidriloSystemBackHandler

/** Registers the page's original exit callback; intentionally draws no control. */
@Suppress("UNUSED_PARAMETER")
@Composable
internal fun EvidriloBackGesture(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    EvidriloSystemBackHandler(enabled = true, onBack = onClick)
}

/** Root destinations only. Editing and active lessons keep their own protected exit. */
@Composable
internal fun EvidriloWorkspaceShell(
    destination: EvidriloDestination,
    showNavigation: Boolean,
    onSelect: (EvidriloDestination) -> Unit,
    content: @Composable () -> Unit,
) {
    val roots = listOf(EvidriloDestination.HOME, EvidriloDestination.PROJECTS, EvidriloDestination.PRACTICE, EvidriloDestination.CASES, EvidriloDestination.PROFILE)
    val navigationVisible = showNavigation && destination in roots && destination != EvidriloDestination.PRACTICE
    Column(Modifier.fillMaxSize().background(EvidriloColors.Canvas)) {
        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
        if (navigationVisible) {
            NavigationBar(containerColor = EvidriloColors.Card, tonalElevation = 0.dp) {
                roots.forEachIndexed { index, route ->
                    val selected = destination == route
                    val label = listOf("Home", "Projects", "Practice", "Cases", "Profile")[index]
                    val icon = listOf(EvidriloIconName.HOME, EvidriloIconName.FOLDER, EvidriloIconName.BOOK, EvidriloIconName.EVIDENCE_GRAPH, EvidriloIconName.ACCOUNT)[index]
                    NavigationBarItem(
                        selected = selected, onClick = { if (!selected) onSelect(route) },
                        icon = { EvidriloIcon(icon, tint = if (selected) EvidriloColors.Cobalt else EvidriloColors.Slate, modifier = Modifier.size(24.dp)) },
                        label = {
                            Text(uiText(label), Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp),
                                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = EvidriloColors.Cobalt, selectedTextColor = EvidriloColors.Cobalt,
                            unselectedTextColor = EvidriloColors.Slate, indicatorColor = EvidriloColors.Tint),
                    )
                }
            }
        }
    }
}

@Composable
internal fun EvidriloPageHeading(title: String, description: String? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(uiText(title), style = MaterialTheme.typography.headlineMedium, color = EvidriloColors.Ink, modifier = Modifier.semantics { heading() })
            description?.let { Text(uiText(it), style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate) }
        }
        action?.invoke()
    }
}

/** Secondary explanations stay available without dominating the task. */
@Composable
internal fun EvidriloExplanation(title: String, text: String, initiallyExpanded: Boolean = false) {
    var expanded by remember(title) { mutableStateOf(initiallyExpanded) }
    Column(Modifier.fillMaxWidth().animateContentSize(tween(200))) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp))
                .clickable(role = Role.Button, onClickLabel = if (expanded) "Hide details" else "Show details") { expanded = !expanded }
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            EvidriloIcon(EvidriloIconName.INFO, tint = EvidriloColors.Slate, modifier = Modifier.size(20.dp))
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = EvidriloColors.Slate)
            EvidriloIcon(if (expanded) EvidriloIconName.CHEVRON_DOWN else EvidriloIconName.CHEVRON_RIGHT, tint = EvidriloColors.Slate, modifier = Modifier.size(18.dp))
        }
        AnimatedVisibility(expanded, enter = expandVertically(tween(200)) + fadeIn(tween(150)), exit = shrinkVertically(tween(180)) + fadeOut(tween(120))) {
            Text(text, Modifier.padding(start = 30.dp, bottom = 14.dp), style = MaterialTheme.typography.bodyMedium, color = EvidriloColors.Slate)
        }
    }
}

@Composable
internal fun EvidriloWorkspaceRow(icon: EvidriloIconName, title: String, detail: String?, onClick: () -> Unit, selected: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(RoundedCornerShape(16.dp))
            .background(if (selected) EvidriloColors.Tint else EvidriloColors.Card)
            .clickable(role = Role.Button, onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(EvidriloColors.PaleBlue), contentAlignment = Alignment.Center) {
            EvidriloIcon(icon, tint = EvidriloColors.Cobalt, modifier = Modifier.size(23.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = EvidriloColors.Ink)
            detail?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate) }
        }
        EvidriloIcon(if (selected) EvidriloIconName.CHECK else EvidriloIconName.CHEVRON_RIGHT, tint = EvidriloColors.Cobalt, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun EvidriloSmallChatIcon(modifier: Modifier = Modifier) {
    Box(modifier.size(48.dp).clip(CircleShape).background(EvidriloColors.PrimaryAction), contentAlignment = Alignment.Center) {
        EvidriloIcon(EvidriloIconName.CHAT_BUBBLE, tint = EvidriloColors.White, modifier = Modifier.size(24.dp))
    }
}

/** A finite paper assembly, illustrating workflow rather than invented project data. */
@Composable
internal fun EvidriloWorkflowScene(icon: EvidriloIconName, labels: List<String>, trigger: Any) {
    val reveal = rememberGetStartedReveal(trigger, 650)
    Surface(shape = RoundedCornerShape(24.dp), color = EvidriloColors.Tint) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EvidriloIcon(icon, tint = EvidriloColors.Cobalt, modifier = Modifier.size(28.dp))
                Text("Make your reasoning visible", style = MaterialTheme.typography.labelLarge, color = EvidriloColors.Cobalt)
            }
            labels.forEachIndexed { index, label ->
                Row(Modifier.fillMaxWidth().graphicsLayer {
                    translationX = (1f - reveal.value) * (24 + index * 12).dp.toPx()
                    alpha = reveal.value
                    rotationZ = (1f - reveal.value) * (if (index % 2 == 0) -3f else 3f)
                }.clip(RoundedCornerShape(12.dp)).background(EvidriloColors.Card).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(EvidriloColors.PaleBlue), contentAlignment = Alignment.Center) {
                        Text("${index + 1}", style = MaterialTheme.typography.labelMedium, color = EvidriloColors.Cobalt)
                    }
                    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    EvidriloIcon(EvidriloIconName.LINK, tint = EvidriloColors.Slate, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
