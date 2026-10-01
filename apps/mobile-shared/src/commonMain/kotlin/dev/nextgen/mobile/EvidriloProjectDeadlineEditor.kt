package dev.nextgen.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import dev.nextgen.mobile.EvidriloUiText as Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.domain.project.StudentProjectDeadlineDate

private const val MILLIS_PER_DAY = 86_400_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StudentProjectDeadlineEditor(
    deadlineDate: String?,
    onDeadlineDateChange: (String?) -> Unit,
) {
    var datePickerVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Deadline", style = MaterialTheme.typography.titleSmall)
        Text(
            "Optional · no reminder is scheduled.",
            style = MaterialTheme.typography.bodySmall,
            color = EvidriloColors.Slate,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                deadlineDate ?: "Not set",
                style = MaterialTheme.typography.bodyMedium,
                color = EvidriloColors.Slate,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { datePickerVisible = true }) {
                Text(if (deadlineDate == null) "Add date" else "Change")
            }
            if (deadlineDate != null) {
                TextButton(onClick = { onDeadlineDateChange(null) }) {
                    Text("Remove")
                }
            }
        }
    }

    if (datePickerVisible) {
        val initialDateMillis = deadlineDate
            ?.let(StudentProjectDeadlineDate::parse)
            ?.toLong()
            ?.times(MILLIS_PER_DAY)
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialDateMillis,
            yearRange = 1..9_999,
        )
        DatePickerDialog(
            onDismissRequest = { datePickerVisible = false },
            confirmButton = {
                TextButton(
                    enabled = pickerState.selectedDateMillis != null,
                    onClick = {
                        val date = pickerState.selectedDateMillis
                            ?.div(MILLIS_PER_DAY)
                            ?.toInt()
                            ?.let(StudentProjectDeadlineDate::format)
                        if (date != null) onDeadlineDateChange(date)
                        datePickerVisible = false
                    },
                ) {
                    Text("Save date")
                }
            },
            dismissButton = {
                TextButton(onClick = { datePickerVisible = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
