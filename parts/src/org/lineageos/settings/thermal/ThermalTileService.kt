/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.thermal

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import org.lineageos.settings.R
import org.lineageos.settings.thermal.ThermalUtils.ThermalState

/** Quick settings tile that switches the default profile between Performance and Balanced. */
class ThermalTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val thermalUtils = ThermalUtils.getInstance(this)
        thermalUtils.defaultProfile =
            if (thermalUtils.defaultProfile == ThermalState.PERFORMANCE) {
                ThermalState.BALANCED
            } else {
                ThermalState.PERFORMANCE
            }
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val profile = ThermalUtils.getInstance(this).defaultProfile
        tile.state =
            if (profile == ThermalState.PERFORMANCE) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = getString(profile.label)
        tile.updateTile()
    }
}
