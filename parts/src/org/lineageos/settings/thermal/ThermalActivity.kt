/*
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.thermal

import android.content.pm.LauncherApps
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import org.lineageos.settings.thermal.ui.ThermalScreen
import org.lineageos.settings.thermal.ui.theme.ThermalTheme

/** Settings > Battery > Performance & thermal. */
class ThermalActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val thermalUtils = ThermalUtils.getInstance(this)
        // The ViewModel outlives the activity (rotation): don't let it hold the activity context.
        val launcherApps = applicationContext.getSystemService(LauncherApps::class.java)!!

        setContent {
            ThermalTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    val viewModel: ThermalViewModel =
                        viewModel(factory = ThermalViewModelFactory(thermalUtils, launcherApps))
                    ThermalScreen(viewModel = viewModel, onBackPressed = { finish() })
                }
            }
        }
    }
}
