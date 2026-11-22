package one.inbox.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Wires [SettingsViewModel] into [SettingsContent]. */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onRunSetupAgain: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val icon = remember(viewModel) { viewModel::icon }
    SettingsContent(
        state = state,
        icon = icon,
        onBack = onBack,
        onSaveKey = viewModel::saveKey,
        onClearKey = viewModel::clearKey,
        onSetAllowed = viewModel::setAllowed,
        onRunSetup = onRunSetupAgain,
        onSetAutoDeleteDays = viewModel::setAutoDeleteDays,
        onClearReadChats = viewModel::clearReadChats,
    )
}
