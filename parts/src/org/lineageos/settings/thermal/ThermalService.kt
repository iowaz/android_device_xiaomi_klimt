/*
 * SPDX-FileCopyrightText: 2020 The LineageOS Project
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.thermal

import android.app.ActivityTaskManager
import android.app.Service
import android.app.TaskStackListener
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.PowerManager
import android.util.Log

/** Follows the screen state and the top (foreground) app, and applies the matching profile. */
class ThermalService : Service() {

    private lateinit var thermalUtils: ThermalUtils

    private val taskListener =
        object : TaskStackListener() {
            override fun onTaskStackChanged() {
                updateTopApp()
            }
        }

    private val screenReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON -> thermalUtils.setScreenOn(true)
                    Intent.ACTION_SCREEN_OFF -> thermalUtils.setScreenOn(false)
                }
            }
        }

    override fun onCreate() {
        super.onCreate()
        thermalUtils = ThermalUtils.getInstance(this)
        registerReceiver(
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            Context.RECEIVER_NOT_EXPORTED,
        )
        runCatching { ActivityTaskManager.getService().registerTaskStackListener(taskListener) }
            .onFailure { Log.e(TAG, "Could not register the task stack listener", it) }
        thermalUtils.setScreenOn(getSystemService(PowerManager::class.java)?.isInteractive ?: true)
        updateTopApp()
        thermalUtils.applyCurrent()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        runCatching { ActivityTaskManager.getService().unregisterTaskStackListener(taskListener) }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun updateTopApp() {
        runCatching { ActivityTaskManager.getService().focusedRootTaskInfo?.topActivity?.packageName }
            .getOrNull()
            ?.let { thermalUtils.setCurrentApp(it) }
    }

    companion object {
        private const val TAG = "XiaomiPartsThermal"
    }
}
