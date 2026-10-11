/*
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.thermal.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.lineageos.settings.R
import org.lineageos.settings.thermal.ThermalUtils.ThermalState
import org.lineageos.settings.thermal.ThermalViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThermalScreen(viewModel: ThermalViewModel, onBackPressed: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    var showResetDialog by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.thermal_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.thermal_navigate_up),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showResetDialog = true },
                        enabled = uiState.perAppEnabled && uiState.apps.isNotEmpty(),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.thermal_reset),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            item {
                DefaultProfileCard(
                    selected = uiState.defaultProfile,
                    onSelected = { viewModel.setDefaultProfile(it) },
                )
            }
            item {
                PerAppCard(
                    enabled = uiState.perAppEnabled,
                    onEnabledChange = { viewModel.setPerAppEnabled(it) },
                )
            }
            if (uiState.perAppEnabled) {
                item {
                    Text(
                        text = stringResource(R.string.thermal_apps_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 32.dp, top = 16.dp, bottom = 8.dp),
                    )
                }
                when {
                    uiState.isLoading -> item { CenteredMessage { LoadingState() } }
                    uiState.error != null ->
                        item {
                            CenteredMessage {
                                Text(
                                    text = uiState.error!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    uiState.apps.isEmpty() ->
                        item {
                            CenteredMessage {
                                Text(
                                    text = stringResource(R.string.thermal_no_apps),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    else ->
                        items(items = uiState.apps, key = { it.packageName }) { app ->
                            AppThermalItem(
                                app = app,
                                onStateChanged = {
                                    viewModel.updateAppThermalState(app.packageName, it)
                                },
                            )
                        }
                }
            }
        }
    }

    if (showResetDialog) {
        ResetConfirmationDialog(
            onConfirm = {
                viewModel.resetProfiles()
                showResetDialog = false
            },
            onDismiss = { showResetDialog = false },
        )
    }
}

@Composable
private fun DefaultProfileCard(selected: ThermalState, onSelected: (ThermalState) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(modifier = Modifier.padding(vertical = 16.dp).selectableGroup()) {
            Text(
                text = stringResource(R.string.thermal_default_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Text(
                text = stringResource(R.string.thermal_default_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(modifier = Modifier.height(8.dp))
            ProfileOption(
                profile = ThermalState.PERFORMANCE,
                summary = R.string.thermal_performance_summary,
                selected = selected == ThermalState.PERFORMANCE,
                onSelected = onSelected,
            )
            ProfileOption(
                profile = ThermalState.BALANCED,
                summary = R.string.thermal_balanced_summary,
                selected = selected == ThermalState.BALANCED,
                onSelected = onSelected,
            )
        }
    }
}

@Composable
private fun ProfileOption(
    profile: ThermalState,
    @StringRes summary: Int,
    selected: Boolean,
    onSelected: (ThermalState) -> Unit,
) {
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .selectable(
                    selected = selected,
                    onClick = { onSelected(profile) },
                    role = Role.RadioButton,
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(text = stringResource(profile.label), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = stringResource(summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PerAppCard(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.thermal_per_app_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.thermal_per_app_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                thumbContent = {
                    Icon(
                        imageVector = if (enabled) Icons.Filled.Check else Icons.Filled.Close,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
        }
    }
}

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun LoadingState() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.thermal_loading),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
