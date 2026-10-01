package dev.nextgen.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import dev.nextgen.mobile.EvidriloUiText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.design.resources.Res
import dev.nextgen.mobile.design.resources.project_illustrations
import dev.nextgen.mobile.domain.project.ProjectTemplateFamily
import org.jetbrains.compose.resources.imageResource

/** One original atlas, cropped at render time. Decorative; never represents student evidence. */
@Composable
internal fun EvidriloProjectIllustration(family: ProjectTemplateFamily?, modifier: Modifier = Modifier) {
    val image = imageResource(Res.drawable.project_illustrations)
    val tile = family?.ordinal ?: 5
    val background = EvidriloColors.Atmosphere
    Canvas(modifier.clearAndSetSemantics {}) {
        val side = size.height.coerceAtMost(200.dp.toPx())
        drawImage(image, srcOffset = IntOffset((tile % 3)*image.width/3, (tile/3)*image.height/2),
            srcSize = IntSize(image.width/3,image.height/2),
            dstOffset = IntOffset((size.width-side).toInt(),((size.height-side)/2).toInt()),
            dstSize = IntSize(side.toInt(),side.toInt()), alpha = .85f)
        val top=(size.height-side)/2
        drawRect(Brush.horizontalGradient(listOf(background, background.copy(alpha=.98f), background.copy(alpha=0f)),
            startX=0f,endX=size.width))
        // Soften the atlas edges on dark surfaces without changing the original artwork.
        if(background.luminance()<.2f) {
            drawRect(Brush.verticalGradient(0f to background, .22f to background.copy(alpha=0f),
                .78f to background.copy(alpha=0f), 1f to background, startY=top,endY=top+side))
            drawRect(Brush.horizontalGradient(listOf(background.copy(alpha=0f),background),
                startX=size.width-side*.22f,endX=size.width))
        }
    }
}

@Composable
internal fun EvidriloCatalogCover(family: ProjectTemplateFamily, cue: String, onOpen: () -> Unit) {
    EvidriloPressableSurface(onClick = onOpen, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp), faceColor = EvidriloColors.Atmosphere,
        borderColor = null, lipColor = EvidriloColors.Tint) {
        Box(Modifier.fillMaxWidth().heightIn(min=174.dp)) {
            EvidriloProjectIllustration(family, Modifier.matchParentSize())
            Column(Modifier.padding(20.dp).padding(end=106.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(uiText(projectFamilyShortName(family)),style=MaterialTheme.typography.titleLarge)
                Text(uiText(cue),style=MaterialTheme.typography.bodyMedium,color=EvidriloColors.Slate)
                EvidriloIcon(EvidriloIconName.ARROW_FORWARD,tint=EvidriloColors.Cobalt)
            }
        }
    }
}


@Composable
internal fun EvidriloHomeCatalogCover(family: ProjectTemplateFamily, cue: String, onOpen: () -> Unit) {
    EvidriloPressableSurface(onClick=onOpen,modifier=Modifier.width(256.dp),shape=RoundedCornerShape(16.dp),
        faceColor=EvidriloColors.Atmosphere,borderColor=null,lipColor=EvidriloColors.Tint) {
        Box(Modifier.fillMaxWidth().heightIn(min=188.dp)) {
            EvidriloProjectIllustration(family,Modifier.matchParentSize())
            Column(Modifier.padding(18.dp).padding(end=70.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(uiText(projectFamilyShortName(family)),style=MaterialTheme.typography.titleLarge)
                Text(uiText(cue),style=MaterialTheme.typography.bodySmall,color=EvidriloColors.Slate)
                EvidriloIcon(EvidriloIconName.ARROW_FORWARD,tint=EvidriloColors.Cobalt)
            }
        }
    }
}
