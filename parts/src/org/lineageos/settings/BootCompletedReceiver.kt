/*
 * SPDX-FileCopyrightText: 2017-2019 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.util.Log
import org.lineageos.settings.thermal.ThermalService
import org.lineageos.settings.thermal.ThermalUtils

class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // sconfig is global and owned by the system uid of user 0: other users and profiles
        // (work profile, guest) must not run a second copy.
        if (context.getSystemService(UserManager::class.java)?.isSystemUser != true) return
        when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED, Intent.ACTION_BOOT_COMPLETED -> {
                try {
                    // Apply the default profile right away, then follow the screen and the top app.
                    ThermalUtils.getInstance(context).applyCurrent()
                    context.startService(Intent(context, ThermalService::class.java))
                } catch (e: Exception) {
                    Log.e(TAG, "Could not start the thermal profiles", e)
                }
            }
        }
    }

    companion object {
        private const val TAG = "XiaomiParts"
    }
}
