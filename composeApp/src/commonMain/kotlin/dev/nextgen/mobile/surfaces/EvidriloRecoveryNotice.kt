package dev.nextgen.mobile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.storage.LocalStorageNotice

@Composable
internal fun EvidriloRecoveryNotice(
    notice: LocalStorageNotice,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (notice.isError) {
                EvidriloColors.ErrorSurface
            } else {
                EvidriloColors.SuccessSurface
            },
        ),
        border = BorderStroke(1.dp, EvidriloColors.Separator),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            EvidriloIcon(
                name = if (notice.isError) EvidriloIconName.ALERT else EvidriloIconName.CHECK,
                tint = if (notice.isError) EvidriloColors.Error else EvidriloColors.Success,
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(notice.title, style = MaterialTheme.typography.titleMedium)
                Text(notice.body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
