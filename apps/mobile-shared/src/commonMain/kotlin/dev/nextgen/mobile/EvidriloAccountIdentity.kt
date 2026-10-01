package dev.nextgen.mobile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Provider badges reflect verified identities, never inferred from an email domain. */
@Composable
internal fun EvidriloAccountIdentity(
    email: String, signedIn: Boolean, hasPro: Boolean,
    modifier: Modifier = Modifier, compact: Boolean = false,
) {
    val name = profileDisplayName(email).ifBlank { uiText("Your account", "Akun Anda") }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = CircleShape, color = EvidriloColors.Cobalt) {
                Box(Modifier.size(if (compact) 60.dp else 76.dp), contentAlignment = Alignment.Center) {
                    Text(name.split(' ').filter(String::isNotBlank).take(2).map { it.first().uppercaseChar() }.joinToString(""),
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = EvidriloColors.White)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
                if (signedIn && '@' in email) Text(email, style = MaterialTheme.typography.bodySmall,
                    color = EvidriloColors.Slate, maxLines = if (compact) 1 else 2, overflow = TextOverflow.Ellipsis)
                Surface(shape = RoundedCornerShape(8.dp), color = EvidriloColors.Tint) {
                    Text(if (hasPro) "Evidrilo Pro" else if (signedIn) "Free" else uiText("Guest", "Tamu"),
                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium, color = EvidriloColors.Cobalt)
                }
            }
            if (compact) EvidriloIcon(EvidriloIconName.CHEVRON_RIGHT, tint = EvidriloColors.Cobalt, modifier = Modifier.size(20.dp))
        }
    }
}

/** Null is unverified, never treated as a connected provider. */
@Composable
internal fun EvidriloGoogleConnection(signedIn: Boolean, linked: Boolean?, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = CircleShape, color = EvidriloColors.White) {
            Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                EvidriloGoogleMark(Modifier.size(24.dp))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Google", style = MaterialTheme.typography.titleSmall)
            Text(when {
                !signedIn -> uiText("Sign in to connect", "Masuk untuk menghubungkan")
                linked == true -> uiText("Connected", "Terhubung")
                linked == false -> uiText("Not connected", "Belum terhubung")
                else -> uiText("Connection not verified", "Koneksi belum diverifikasi")
            }, style = MaterialTheme.typography.bodySmall, color = EvidriloColors.Slate)
        }
        EvidriloIcon(if (signedIn && linked == true) EvidriloIconName.CHECK_FILLED else EvidriloIconName.LINK,
            tint = EvidriloColors.Cobalt, modifier = Modifier.size(22.dp))
    }
}
