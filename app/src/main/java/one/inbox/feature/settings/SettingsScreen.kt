package one.inbox.feature.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onRunSetupAgain: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { Text("Claude API key", style = MaterialTheme.typography.titleMedium) }
            item {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text(if (state.hasKey) "Replace key" else "API key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        enabled = draft.isNotBlank(),
                        onClick = {
                            viewModel.saveKey(draft)
                            draft = ""
                        },
                    ) { Text("Save") }
                    OutlinedButton(enabled = state.hasKey, onClick = viewModel::clearKey) { Text("Clear") }
                    val status = state.keyMessage ?: if (state.hasKey) MSG_KEY_SAVED else null
                    if (status != null) Text(status, style = MaterialTheme.typography.bodyMedium)
                }
            }
            item {
                Text(
                    "Apps to capture",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            items(state.apps, key = { it.packageName }) { app ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    val icon = remember(app.packageName) { viewModel.icon(app.packageName) }
                    if (icon != null) {
                        Image(icon, contentDescription = null, modifier = Modifier.size(32.dp))
                    } else {
                        Box(Modifier.size(32.dp))
                    }
                    Text(app.label, Modifier.weight(1f).padding(horizontal = 12.dp))
                    Switch(checked = app.allowed, onCheckedChange = { viewModel.setAllowed(app.packageName, it) })
                }
            }
            item {
                TextButton(onClick = onRunSetupAgain, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Run setup again")
                }
            }
        }
    }
}
