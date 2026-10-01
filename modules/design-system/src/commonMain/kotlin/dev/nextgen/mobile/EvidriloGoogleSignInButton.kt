package dev.nextgen.mobile

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import dev.nextgen.mobile.EvidriloUiText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.design.resources.Res
import dev.nextgen.mobile.design.resources.google_g
import org.jetbrains.compose.resources.painterResource

/** Standard-color asset from Google Identity's branding guidelines, rendered without tint. */
@Composable
public fun EvidriloGoogleSignInButton(onClick: () -> Unit, enabled: Boolean = true) {
    val spokenLabel=uiText("Continue with Google")
    EvidriloPressableSurface(onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = spokenLabel },
        shape = RoundedCornerShape(16.dp), faceColor = EvidriloColors.White,
        borderColor = EvidriloColors.Separator, lipColor = EvidriloColors.Separator) {
        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center) {
            Image(painterResource(Res.drawable.google_g), null, Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(uiText("Continue with Google"), style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF202124).copy(alpha=if(enabled) 1f else .5f))
        }
    }
}
