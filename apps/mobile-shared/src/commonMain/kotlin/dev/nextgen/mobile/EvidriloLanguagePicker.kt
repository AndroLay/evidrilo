package dev.nextgen.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import dev.nextgen.mobile.EvidriloUiText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

/** Language symbols, drawn as flags rather than font-dependent emoji. */
@Composable
internal fun EvidriloLanguageFlag(language: EvidriloLanguage, modifier: Modifier = Modifier) {
    Canvas(modifier.size(42.dp,30.dp).clip(RoundedCornerShape(5.dp)).clearAndSetSemantics {}) {
        if(language==EvidriloLanguage.INDONESIAN) {
            drawRect(Color(0xFFE12D3B),size=Size(size.width,size.height/2))
            drawRect(Color.White,topLeft=Offset(0f,size.height/2),size=Size(size.width,size.height/2))
        } else {
            drawRect(Color(0xFF143B80))
            drawLine(Color.White,Offset.Zero,Offset(size.width,size.height),size.height*.23f)
            drawLine(Color.White,Offset(0f,size.height),Offset(size.width,0f),size.height*.23f)
            drawLine(Color(0xFFD82E42),Offset.Zero,Offset(size.width,size.height),size.height*.07f)
            drawLine(Color(0xFFD82E42),Offset(0f,size.height),Offset(size.width,0f),size.height*.07f)
            drawLine(Color.White,Offset(size.width/2,0f),Offset(size.width/2,size.height),size.width*.25f)
            drawLine(Color.White,Offset(0f,size.height/2),Offset(size.width,size.height/2),size.height*.38f)
            drawLine(Color(0xFFD82E42),Offset(size.width/2,0f),Offset(size.width/2,size.height),size.width*.14f)
            drawLine(Color(0xFFD82E42),Offset(0f,size.height/2),Offset(size.width,size.height/2),size.height*.21f)
        }
    }
}

@Composable
internal fun EvidriloLanguageChoices(language:EvidriloLanguage,onSelect:(EvidriloLanguage)->Unit) {
    Column(Modifier.selectableGroup(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        EvidriloLanguage.entries.forEach { option ->
            Surface(shape=RoundedCornerShape(16.dp),color=if(option==language) EvidriloColors.Tint else EvidriloColors.Atmosphere) {
                Row(Modifier.fillMaxWidth().heightIn(min=82.dp).selectable(option==language,onClick={onSelect(option)},role=Role.RadioButton).padding(18.dp),
                    verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    EvidriloLanguageFlag(option)
                    Text(option.nativeName,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                    if(option==language) EvidriloIcon(EvidriloIconName.CHECK,tint=EvidriloColors.Cobalt)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EvidriloLanguageSheet(language:EvidriloLanguage,failed:Boolean,onSelect:(EvidriloLanguage)->Unit,onClose:()->Unit) {
    ModalBottomSheet(onDismissRequest=onClose,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true),containerColor=EvidriloColors.Card) {
        Column(Modifier.fillMaxWidth().padding(horizontal=24.dp).padding(bottom=24.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Text(uiText("Language","Bahasa"),style=MaterialTheme.typography.headlineSmall)
            EvidriloLanguageChoices(language,onSelect)
            Text(uiText("Your own project writing stays as you wrote it.","Tulisan Anda dalam proyek tetap seperti aslinya."),style=MaterialTheme.typography.bodySmall,color=EvidriloColors.Slate)
            if(failed) Text(uiText("Language could not be saved. Try again."),color=EvidriloColors.Error)
            EvidriloPrimaryButton(uiText("Done","Selesai"),onClose)
        }
    }
}
