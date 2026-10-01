package dev.nextgen.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import dev.nextgen.mobile.EvidriloUiText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.nextgen.mobile.projectcatalog.ProjectAiActivityHistoryEntry

internal sealed interface ProjectAiActivityHistoryUiState {
    data object NotRequested : ProjectAiActivityHistoryUiState
    data class Loading(val accountId: String, val projectId: String?) : ProjectAiActivityHistoryUiState
    data class Loaded(
        val accountId: String,
        val projectId: String?,
        val entries: List<ProjectAiActivityHistoryEntry>,
        val nextCursor: String?,
        val loadingMore: Boolean = false,
        val loadMoreError: String? = null,
    ) : ProjectAiActivityHistoryUiState
    data class Unavailable(val accountId: String?, val projectId: String?, val message: String) : ProjectAiActivityHistoryUiState
}

@Composable
internal fun EvidriloProjectAiActivityHistory(
    state: ProjectAiActivityHistoryUiState,
    accountId: String?,
    projectId: String?,
    onRefresh: () -> Unit,
    onLoadMore: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleState = when (state) {
        is ProjectAiActivityHistoryUiState.Loading -> state.takeIf { it.accountId == accountId && it.projectId == projectId }
        is ProjectAiActivityHistoryUiState.Loaded -> state.takeIf { it.accountId == accountId && it.projectId == projectId }
        is ProjectAiActivityHistoryUiState.Unavailable -> state.takeIf {
            (it.accountId == null || it.accountId == accountId) && (it.projectId == null || it.projectId == projectId)
        }
        ProjectAiActivityHistoryUiState.NotRequested -> state
    } ?: ProjectAiActivityHistoryUiState.NotRequested
    EvidriloTargetCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (projectId == null) "AI activity" else "Project AI activity", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onRefresh, enabled = visibleState !is ProjectAiActivityHistoryUiState.Loading) {
                    Text(if (visibleState is ProjectAiActivityHistoryUiState.Loading) "Checking…" else "Refresh")
                }
            }
            Text(
                "Metadata only: stage, revision, and outcome. Prompts, replies, and project text are not shown here.",
                style = MaterialTheme.typography.bodySmall,
                color = EvidriloColors.Slate,
            )
            when (visibleState) {
                ProjectAiActivityHistoryUiState.NotRequested -> Text(
                    if (projectId == null) "Refresh to check account activity metadata."
                    else "Refresh to check this account's activity for the current project.",
                    style = MaterialTheme.typography.bodySmall,
                )
                is ProjectAiActivityHistoryUiState.Loading -> Text("Loading activity metadata…", style = MaterialTheme.typography.bodySmall)
                is ProjectAiActivityHistoryUiState.Unavailable -> Text(
                    "${visibleState.message} The local project remains available.",
                    style = MaterialTheme.typography.bodySmall,
                )
                is ProjectAiActivityHistoryUiState.Loaded -> if (visibleState.entries.isEmpty()) {
                    Text(
                        if (projectId == null) "No AI activity metadata is recorded for this account."
                        else "No Project AI activity is recorded for this project.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(visibleState.entries, key = ProjectAiActivityHistoryEntry::activityId) { entry ->
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                val modeLabel = if (entry.mode == "GENERAL") {
                                    "General Chat · unlinked"
                                } else if (projectId == null) {
                                    "Project AI · ${entry.projectId?.take(8) ?: "project"}"
                                } else {
                                    "Project AI"
                                }
                                Text(
                                    "$modeLabel · ${entry.stageId?.replace('-', ' ') ?: "assistance"}" +
                                        (entry.operationId?.let { " · ${it.replace('_', ' ')}" } ?: "") +
                                        " · ${entry.outcome.lowercase().replace('_', ' ')}",
                                    style = MaterialTheme.typography.labelLarge,
                                )
                                Text(
                                    (entry.baseProjectRevision?.let { "Revision $it" } ?: "Metadata only") +
                                        (entry.resultProjectRevision?.let { " → $it" } ?: "") +
                                        " · ${entry.createdAt}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = EvidriloColors.Slate,
                                )
                            }
                        }
                        if (visibleState.nextCursor != null) {
                            item(key = "ai-activity-more") {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    visibleState.loadMoreError?.let {
                                        Text("Older activity could not be loaded ($it).", style = MaterialTheme.typography.bodySmall)
                                    }
                                    TextButton(
                                        onClick = { onLoadMore(visibleState.nextCursor) },
                                        enabled = !visibleState.loadingMore,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(if (visibleState.loadingMore) "Loading older activity…" else "Show older activity")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
