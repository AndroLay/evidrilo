package dev.nextgen.mobile

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import dev.nextgen.mobile.EvidriloUiText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun EvidriloWelcomeLanguage(language:EvidriloLanguage,failed:Boolean,onSelect:(EvidriloLanguage)->Unit,onContinue:()->Unit,onSkip:()->Unit) {
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            EvidriloLogoMark(size=34.dp)
            Text("Evidrilo",Modifier.weight(1f).padding(start=10.dp),style=MaterialTheme.typography.titleMedium)
            TextButton(onSkip) {Text(uiText("Skip","Lewati"))}
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            Box(Modifier.fillMaxWidth().height(128.dp)) {EvidriloProjectIllustration(null,Modifier.matchParentSize())}
            Text(uiText("Make yourself at home.","Mulai dengan nyaman."),style=MaterialTheme.typography.headlineLarge)
            Text(uiText("Choose your language. You can change it in Settings anytime.","Pilih bahasa Anda. Ubah kapan saja lewat Pengaturan."),style=MaterialTheme.typography.bodyLarge,color=EvidriloColors.Slate)
            EvidriloLanguageChoices(language,onSelect)
            if(failed) Text(uiText("Language could not be saved. Try again."),color=EvidriloColors.Error)
        }
        EvidriloPrimaryButton(uiText("Continue","Lanjutkan"),onContinue,enabled=!failed,trailingIcon=EvidriloIconName.ARROW_FORWARD)
        Text(uiText("About 2–3 minutes · at your pace","Sekitar 2–3 menit · sesuai ritme Anda"),Modifier.align(Alignment.CenterHorizontally).padding(bottom=20.dp),style=MaterialTheme.typography.bodySmall,color=EvidriloColors.Slate)
    }
}

@Composable
internal fun GetStartedWorkspaceScene(height:Dp) {
    var selected by remember {mutableIntStateOf(0)}
    val labels=listOf("Sources","Notes","Claims")
    GetStartedAtmosphere(Modifier.fillMaxWidth().height(height)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                labels.forEachIndexed {index,label -> FilterChip(selected==index,{selected=index},label={Text(label)})}
            }
            AnimatedContent(selected,label="Workspace section preview") {section ->
                Surface(shape=RoundedCornerShape(16.dp),color=EvidriloColors.Card,shadowElevation=3.dp) {
                    Box(Modifier.fillMaxWidth().heightIn(min=175.dp)) {
                        EvidriloProjectIllustration(null,Modifier.matchParentSize())
                        Column(Modifier.padding(20.dp).padding(end=64.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                            EvidriloIcon(listOf(EvidriloIconName.BOOK,EvidriloIconName.FILE,EvidriloIconName.LINK)[section],tint=EvidriloColors.Cobalt)
                            Text(labels[section],style=MaterialTheme.typography.titleLarge)
                            Text(listOf("Record who wrote it and where you found it.","Keep a useful passage and its source together.","Write what your notes support, then record the limits.")[section],style=MaterialTheme.typography.bodyMedium,color=EvidriloColors.Slate)
                        }
                    }
                }
            }
            Text("Choose a section to explore · temporary preview",style=MaterialTheme.typography.bodySmall,color=EvidriloColors.Slate)
        }
    }
}

@Composable
internal fun GetStartedMapScene(height:Dp) {
    var selected by remember {mutableIntStateOf(0)}
    val labels=listOf("Source","Note","Claim")
    val entry=rememberGetStartedReveal(selected,420)
    GetStartedAtmosphere(Modifier.fillMaxWidth().height(height)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Text("Illustrative map · not saved",style=MaterialTheme.typography.bodySmall,color=EvidriloColors.Slate)
            Box(Modifier.fillMaxWidth().height(100.dp)) {
                val line=EvidriloColors.Cobalt
                Canvas(Modifier.matchParentSize().clearAndSetSemantics {}) {drawLine(line.copy(alpha=.4f),Offset(0f,size.height/2),Offset(size.width,size.height/2),3.dp.toPx(),StrokeCap.Round)}
                Row(Modifier.fillMaxSize(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                    labels.forEachIndexed {index,label ->
                        EvidriloPressableSurface({selected=index},modifier=Modifier.weight(1f),shape=RoundedCornerShape(16.dp),faceColor=if(selected==index) EvidriloColors.PrimaryAction else EvidriloColors.Card,
                            borderColor=null,lipColor=if(selected==index) EvidriloColors.CobaltPressed else EvidriloColors.PatternBlue) {
                            Column(Modifier.fillMaxWidth().padding(vertical=16.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                                EvidriloIcon(listOf(EvidriloIconName.BOOK,EvidriloIconName.FILE,EvidriloIconName.LINK)[index],tint=if(selected==index) EvidriloColors.White else EvidriloColors.Cobalt)
                                Text(label,style=MaterialTheme.typography.labelSmall,color=if(selected==index) EvidriloColors.White else EvidriloColors.Ink)
                            }
                        }
                    }
                }
            }
            Surface(shape=RoundedCornerShape(16.dp),color=EvidriloColors.Tint) {
                Column(Modifier.padding(18.dp).graphicsLayer {translationY=(1-entry.value)*8.dp.toPx()},verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(labels[selected],style=MaterialTheme.typography.titleMedium)
                    Text(listOf("A source gives the note its origin. Keep its details traceable.","A note carries a passage or observation into your reasoning.","A claim connects to specific notes. Its scope and uncertainty remain visible.")[selected],style=MaterialTheme.typography.bodyMedium,color=EvidriloColors.Slate)
                }
            }
            Text("Tap each record to follow the reasoning.",style=MaterialTheme.typography.bodySmall,color=EvidriloColors.Slate)
        }
    }
}

@Composable
internal fun GetStartedPracticeScene(height:Dp) {
    var selected by remember {mutableIntStateOf(0)}
    val titles=listOf("Trace observations","Compare sources","Reconsider data")
    GetStartedAtmosphere(Modifier.fillMaxWidth().height(height)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(8.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            titles.forEachIndexed {index,title ->
                EvidriloPressableSurface({selected=index},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),faceColor=if(index==selected) EvidriloColors.Tint else EvidriloColors.Card,
                    borderColor=null,lipColor=EvidriloColors.PatternBlue) {
                    Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        EvidriloIcon(listOf(EvidriloIconName.LAYERS,EvidriloIconName.BOOK,EvidriloIconName.LIST)[index],tint=EvidriloColors.Cobalt,modifier=Modifier.size(28.dp))
                        Column(Modifier.weight(1f)) {
                            Text(title,style=MaterialTheme.typography.titleSmall)
                            Text(if(index==0) "Free" else "Pro",style=MaterialTheme.typography.labelSmall,color=EvidriloColors.Cobalt)
                        }
                        if(index==selected) EvidriloIcon(EvidriloIconName.CHECK,tint=EvidriloColors.Cobalt)
                    }
                }
            }
            Text(listOf("Trace a claim back to the supplied observations.","Compare findings without losing their differences.","Revise a claim when the supplied data changes.")[selected],style=MaterialTheme.typography.bodySmall,color=EvidriloColors.Slate)
        }
    }
}
