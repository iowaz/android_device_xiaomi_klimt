/*
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.thermal

import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.lineageos.settings.thermal.ThermalUtils.ThermalState
import org.lineageos.settings.thermal.model.AppThermalState
import org.lineageos.settings.thermal.model.ThermalUiState

class ThermalViewModel(
    private val thermalUtils: ThermalUtils,
    private val launcherApps: LauncherApps,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow(
            ThermalUiState(
                defaultProfile = thermalUtils.defaultProfile,
                perAppEnabled = thermalUtils.perAppEnabled,
            )
        )
    val uiState: StateFlow<ThermalUiState> = _uiState.asStateFlow()

    private val launcherAppsCallback =
        object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) {
                if (user != Process.myUserHandle()) return
                _uiState.update { state ->
                    state.copy(apps = state.apps.filter { it.packageName != packageName })
                }
            }

            override fun onPackageAdded(packageName: String, user: UserHandle) {
                if (user != Process.myUserHandle()) return
                viewModelScope.launch { addApp(packageName) }
            }

            override fun onPackageChanged(packageName: String, user: UserHandle) {}

            override fun onPackagesAvailable(
                packageNames: Array<String>,
                user: UserHandle,
                replacing: Boolean,
            ) {}

            override fun onPackagesUnavailable(
                packageNames: Array<String>,
                user: UserHandle,
                replacing: Boolean,
            ) {}
        }

    init {
        if (thermalUtils.perAppEnabled) loadApps()
        launcherApps.registerCallback(launcherAppsCallback)
    }

    fun setDefaultProfile(profile: ThermalState) {
        thermalUtils.defaultProfile = profile
        _uiState.update { it.copy(defaultProfile = profile) }
    }

    fun setPerAppEnabled(enabled: Boolean) {
        thermalUtils.perAppEnabled = enabled
        _uiState.update { it.copy(perAppEnabled = enabled) }
        if (enabled && _uiState.value.apps.isEmpty()) loadApps()
    }

    fun updateAppThermalState(packageName: String, state: ThermalState) {
        thermalUtils.setStateForPackage(packageName, state)
        _uiState.update { current ->
            current.copy(
                apps =
                    current.apps.map {
                        if (it.packageName == packageName) it.copy(currentState = state) else it
                    }
            )
        }
    }

    fun resetProfiles() {
        thermalUtils.resetProfiles()
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val apps =
                    withContext(Dispatchers.IO) {
                        launcherApps
                            .getActivityList(null, Process.myUserHandle())
                            .distinctBy { it.componentName.packageName }
                            .map { info ->
                                AppThermalState(
                                    packageName = info.componentName.packageName,
                                    label = info.label.toString(),
                                    icon = info.getIcon(0),
                                    currentState =
                                        thermalUtils.getStateForPackage(info.componentName.packageName),
                                )
                            }
                            .sortedBy { it.label.lowercase() }
                    }
                _uiState.update { it.copy(isLoading = false, apps = apps, error = null) }
            } catch (e: Exception) {
                Log.e(TAG, "Could not load the app list", e)
                _uiState.update { it.copy(isLoading = false, error = e.message ?: e.toString()) }
            }
        }
    }

    private suspend fun addApp(packageName: String) {
        val app =
            withContext(Dispatchers.IO) {
                launcherApps.getActivityList(packageName, Process.myUserHandle()).firstOrNull()?.let {
                    AppThermalState(
                        packageName = packageName,
                        label = it.label.toString(),
                        icon = it.getIcon(0),
                        currentState = thermalUtils.getStateForPackage(packageName),
                    )
                }
            } ?: return
        _uiState.update { current ->
            if (current.apps.any { it.packageName == packageName }) {
                current
            } else {
                current.copy(apps = (current.apps + app).sortedBy { it.label.lowercase() })
            }
        }
    }

    override fun onCleared() {
        launcherApps.unregisterCallback(launcherAppsCallback)
        super.onCleared()
    }

    companion object {
        private const val TAG = "XiaomiPartsThermal"
    }
}

class ThermalViewModelFactory(
    private val thermalUtils: ThermalUtils,
    private val launcherApps: LauncherApps,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ThermalViewModel::class.java))
        return ThermalViewModel(thermalUtils, launcherApps) as T
    }
}
