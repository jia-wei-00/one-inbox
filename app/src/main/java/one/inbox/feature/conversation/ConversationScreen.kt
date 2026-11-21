package one.inbox.feature.conversation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import one.inbox.data.db.MessageEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private sealed interface Item {
    data class Day(val label: String) : Item
    data class Bubble(val message: MessageEntity, val showSender: Boolean) : Item
}

private fun buildItems(messages: List<MessageEntity>, isGroup: Boolean): List<Item> {
    val zone = ZoneId.systemDefault()
    val dayFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    val items = mutableListOf<Item>()
    var lastDay: LocalDate? = null
    var lastSender: String? = null
    for (m in messages) {
        val day = Instant.ofEpochMilli(m.timestamp).atZone(zone).toLocalDate()
        if (day != lastDay) {
            items += Item.Day(dayFormat.format(day))
            lastDay = day
            lastSender = null
        }
        items += Item.Bubble(m, showSender = isGroup && !m.isFromMe && m.sender != lastSender)
        lastSender = if (m.isFromMe) null else m.sender
    }
    return items
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(onBack: () -> Unit, viewModel: ConversationViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val items = buildItems(state.messages, state.isGroup)
    val listState = rememberLazyListState()
    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) listState.scrollToItem(items.lastIndex)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.title, style = MaterialTheme.typography.titleMedium)
                        Text(state.appName, style = MaterialTheme.typography.labelSmall)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(items) { item ->
                when (item) {
                    is Item.Day -> Text(
                        item.label,
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    is Item.Bubble -> BubbleRow(item)
                }
            }
        }
    }
}

@Composable
private fun BubbleRow(item: Item.Bubble) {
    val m = item.message
    val time = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        .format(Instant.ofEpochMilli(m.timestamp).atZone(ZoneId.systemDefault()))
    Box(Modifier.fillMaxWidth(), contentAlignment = if (m.isFromMe) Alignment.CenterEnd else Alignment.CenterStart) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (m.isFromMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (item.showSender) {
                    Text(m.sender, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Text(m.text)
                Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
