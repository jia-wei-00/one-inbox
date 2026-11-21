package one.inbox.feature.catchup

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.inbox.summary.CatchUpItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatchUpScreen(
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: CatchUpViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var lowExpanded by rememberSaveable { mutableStateOf(false) }
    val latest = state.latest
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Catch-up") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = viewModel::summarize, enabled = !state.running) {
                        if (state.running) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text("Summarize")
                        }
                    }
                    if (latest != null) {
                        OutlinedButton(onClick = viewModel::markAllRead) { Text("Mark all as read") }
                    }
                }
            }
            state.message?.let { msg ->
                item {
                    Column {
                        Text(msg, style = MaterialTheme.typography.bodyLarge)
                        if (msg == MSG_NO_API_KEY) {
                            TextButton(onClick = onOpenSettings) { Text("Open Settings") }
                        }
                    }
                }
            }
            if (latest != null) {
                item {
                    Column {
                        Text(
                            "Catch-up · ${latest.unreadCount} unread · ${latest.appCount} apps",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "generated ${DateUtils.getRelativeTimeSpanString(latest.generatedAt)}",
                            style = MaterialTheme.typography.labelMedium,
                        )
                        if (state.newSince > 0) {
                            AssistChip(onClick = {}, label = { Text("${state.newSince} new since") })
                        }
                        if (latest.droppedCount > 0) {
                            Text(
                                "Oldest ${latest.droppedCount} messages left out",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                val c = latest.catchUp
                section("⚠ Needs your reply", c.needsReply, state.labels, onOpenConversation)
                section("💬 Worth knowing", c.worthKnowing, state.labels, onOpenConversation)
                section(
                    title = "🔕 Low priority (${c.lowPriority.size})",
                    items = c.lowPriority,
                    labels = state.labels,
                    onOpen = onOpenConversation,
                    expanded = lowExpanded,
                    onToggle = { lowExpanded = !lowExpanded },
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    items: List<CatchUpItem>,
    labels: Map<String, ItemLabel>,
    onOpen: (String) -> Unit,
    expanded: Boolean = true,
    onToggle: (() -> Unit)? = null,
) {
    item {
        HorizontalDivider()
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                .let { if (onToggle != null) it.clickable(onClick = onToggle) else it },
        )
    }
    if (expanded) {
        items(items.size) { i ->
            val item = items[i]
            val label = labels[item.conversationKey]
            Text(
                buildAnnotatedString {
                    append("• ")
                    label?.title?.let {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(it) }
                        append(" ")
                    }
                    label?.appName?.let { append("($it): ") }
                    append(item.summary)
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().clickable { onOpen(item.conversationKey) },
            )
        }
    }
}
