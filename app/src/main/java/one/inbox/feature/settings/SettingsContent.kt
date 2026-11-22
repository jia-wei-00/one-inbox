package one.inbox.feature.settings

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.ui.semantics.role
import one.inbox.data.AUTO_DELETE_CHOICES
import one.inbox.ui.components.OneInboxTonalButton
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import one.inbox.ui.components.AppTile
import one.inbox.ui.components.SectionCard
import one.inbox.ui.theme.doneGreen
import one.inbox.ui.theme.OneInboxTheme

/**
 * Settings in four cards: the Claude API key (masked; the saved key is never shown), which apps
 * to capture, inbox cleanup, and re-running setup.
 */
@Composable
fun SettingsContent(
    state: SettingsUiState,
    icon: (String) -> ImageBitmap?,
    onBack: () -> Unit,
    onSaveKey: (String, () -> Unit) -> Unit,
    onClearKey: () -> Unit,
    onSetAllowed: (String, Boolean) -> Unit,
    onRunSetup: () -> Unit,
    onSetAutoDeleteDays: (Int) -> Unit = {},
    onClearReadChats: () -> Unit = {},
    onConfirmAutoDelete: () -> Unit = {},
    onCancelAutoDelete: () -> Unit = {},
) {
    // App icons are cached app-wide, per package and at display size, by AppInfoProvider.
    val iconOf = icon
    val colors = MaterialTheme.colorScheme

    Scaffold(
        containerColor = colors.background,
        contentWindowInsets = WindowInsets(0),
        topBar = { SettingsTopBar(onBack) },
    ) { padding ->
        LazyColumn(
            // imePadding: the key field stays above the keyboard (the activity is edge-to-edge).
            Modifier.padding(padding).fillMaxSize().imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "key") { ApiKeyCard(state, onSaveKey, onClearKey) }
            item(key = "apps") { AppsCard(state.apps, iconOf, onSetAllowed) }
            item(key = "cleanup") {
                CleanupCard(state, onSetAutoDeleteDays, onClearReadChats, onConfirmAutoDelete, onCancelAutoDelete)
            }
            item(key = "setup") { SetupCard(onRunSetup, Modifier.navigationBarsPadding()) }
        }
    }
}

@Composable
private fun SettingsTopBar(onBack: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().background(colors.background).statusBarsPadding()
            .padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
        }
        Spacer(Modifier.width(4.dp))
        Text(
            "Settings",
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = colors.onBackground,
        )
    }
}

@Composable
private fun ApiKeyCard(
    state: SettingsUiState,
    onSaveKey: (String, () -> Unit) -> Unit,
    onClearKey: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    // Not rememberSaveable: the typed key must never land in the saved-instance bundle.
    var draft by remember { mutableStateOf("") }
    SectionCard(
        containerColor = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
        modifier = Modifier.fillMaxWidth(),
        title = "Claude API key",
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            label = { Text(if (state.hasKey) "Replace key" else "API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                enabled = draft.isNotBlank(),
                // The draft is cleared only once the key is actually stored.
                onClick = { onSaveKey(draft) { draft = "" } },
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text("Save", fontWeight = FontWeight.Bold) }
            // Destructive and secondary: error-colored text, never the most prominent action.
            TextButton(
                enabled = state.hasKey,
                onClick = onClearKey,
                modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = colors.error),
            ) { Text("Clear", fontWeight = FontWeight.Bold) }
        }
        val status = state.keyMessage ?: if (state.hasKey) MSG_KEY_SAVED else null
        if (status != null) {
            val ok = status == MSG_KEY_SAVED
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = if (ok) colors.doneGreen else colors.error,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    status,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (ok) colors.onSurface else colors.error,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Only sent to Claude when you tap Summarize",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun AppsCard(
    apps: List<AppToggle>,
    iconOf: (String) -> ImageBitmap?,
    onSetAllowed: (String, Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    SectionCard(
        containerColor = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
        modifier = Modifier.fillMaxWidth(),
        title = "Apps to capture",
    ) {
        apps.forEach { app -> AppRow(app, iconOf(app.packageName), onSetAllowed) }
    }
}

/** The whole row toggles capture; the tile and switch are visuals only. */
@Composable
private fun AppRow(app: AppToggle, icon: ImageBitmap?, onSetAllowed: (String, Boolean) -> Unit) {
    val toggle = { allowed: Boolean -> onSetAllowed(app.packageName, allowed) }
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .toggleable(value = app.allowed, role = Role.Switch, onValueChange = toggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppTile(
            label = app.label,
            icon = icon,
            selected = false,
            unread = 0,
            // Not a target of its own (the row is), so no 48dp touch box: it lines up with the card title.
            onClick = null,
            // The row speaks for the app; the tile's own "N unread" description doesn't apply here.
            modifier = Modifier.clearAndSetSemantics {},
            size = 40.dp,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            app.label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(8.dp))
        Switch(checked = app.allowed, onCheckedChange = null)
    }
}

fun autoDeleteLabel(days: Int): String = if (days <= 0) "Off" else "$days days"

/** Auto-delete period for read chats, plus a one-off "Clear read chats" behind a confirm dialog. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CleanupCard(
    state: SettingsUiState,
    onSetAutoDeleteDays: (Int) -> Unit,
    onClearReadChats: () -> Unit,
    onConfirmAutoDelete: () -> Unit,
    onCancelAutoDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var confirming by remember { mutableStateOf(false) }
    SectionCard(
        containerColor = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
        modifier = Modifier.fillMaxWidth(),
        title = "Cleanup",
    ) {
        Text("Auto-delete read chats after", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        FlowRow(
            Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AUTO_DELETE_CHOICES.forEach { days ->
                val selected = state.autoDeleteDays == days
                FilterChip(
                    selected = selected,
                    onClick = { onSetAutoDeleteDays(days) },
                    label = { Text(autoDeleteLabel(days)) },
                    modifier = Modifier.semantics { role = Role.RadioButton },
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Chats with unread messages are never auto-deleted",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        OneInboxTonalButton(
            onClick = { confirming = true },
            enabled = state.readChats > 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Clear read chats (${state.readChats})", fontWeight = FontWeight.Bold)
        }
        state.cleanupMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
    if (confirming) {
        DeleteChatsDialog(
            title = chatCountQuestion(state.readChats),
            text = "Chats with unread messages are kept. This can't be undone.",
            onConfirm = {
                confirming = false
                onClearReadChats()
            },
            onDismiss = { confirming = false },
        )
    }
    state.confirmAutoDelete?.let { pending ->
        DeleteChatsDialog(
            title = chatCountQuestion(pending.chats),
            text = "Auto-delete after ${pending.days} days removes ${chatCount(pending.chats)} older than that " +
                "right away. Chats with unread messages are kept. This can't be undone.",
            onConfirm = onConfirmAutoDelete,
            onDismiss = onCancelAutoDelete,
        )
    }
}

private fun chatCount(n: Int) = if (n == 1) "1 read chat" else "$n read chats"

private fun chatCountQuestion(n: Int) = "Delete ${chatCount(n)}?"

@Composable
private fun DeleteChatsDialog(title: String, text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.DeleteSweep, contentDescription = null) },
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Delete", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SetupCard(onRunSetup: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    SectionCard(
        containerColor = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
        modifier = modifier.fillMaxWidth(),
        title = "Setup",
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClick = onRunSetup)
                .padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Run setup again", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
            )
        }
    }
}

@Preview(uiMode = UI_MODE_NIGHT_YES, widthDp = 360, heightDp = 780)
@Composable
private fun SettingsContentPreview() {
    OneInboxTheme(darkTheme = true) {
        SettingsContent(
            state = SettingsUiState(
                hasKey = true,
                apps = listOf(
                    AppToggle("jp.naver.line.android", "LINE", true),
                    AppToggle("com.facebook.orca", "Messenger", true),
                    AppToggle("org.thoughtcrime.securesms", "Signal", false),
                    AppToggle("org.telegram.messenger", "Telegram", true),
                    AppToggle("com.whatsapp", "WhatsApp", true),
                ),
            ),
            icon = { null },
            onBack = {},
            onSaveKey = { _, _ -> },
            onClearKey = {},
            onSetAllowed = { _, _ -> },
            onRunSetup = {},
        )
    }
}
