/*
 * SPDX-FileCopyrightText: 2020 The LineageOS Project
 * SPDX-FileCopyrightText: 2025 Paranoid Android
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.thermal

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import androidx.annotation.StringRes
import org.lineageos.settings.R
import org.lineageos.settings.utils.FileUtils

/**
 * Picks the mi_thermald profile. mi_thermald reads the scene number from
 * /sys/class/thermal/thermal_message/sconfig and applies the matching /odm/etc/thermal-*.conf
 * (see /odm/etc/thermal-map.conf). On HyperOS, PowerKeeper writes that node; here this class does.
 */
class ThermalUtils private constructor(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var currentApp = ""
    private var screenOn = true
    private var lastWritten: String? = null

    /** Profile for every app without one of its own: [ThermalState.PERFORMANCE] or [ThermalState.BALANCED]. */
    var defaultProfile: ThermalState
        get() =
            when (prefs.getString(KEY_DEFAULT_PROFILE, null)) {
                ThermalState.BALANCED.key -> ThermalState.BALANCED
                else -> ThermalState.PERFORMANCE
            }
        set(value) {
            require(value == ThermalState.PERFORMANCE || value == ThermalState.BALANCED)
            prefs.edit().putString(KEY_DEFAULT_PROFILE, value.key).apply()
            applyCurrent()
        }

    var perAppEnabled: Boolean
        get() = prefs.getBoolean(KEY_PER_APP, true)
        set(value) {
            prefs.edit().putBoolean(KEY_PER_APP, value).apply()
            applyCurrent()
        }

    @Synchronized
    fun setCurrentApp(packageName: String) {
        if (packageName == currentApp) return
        currentApp = packageName
        applyCurrent()
    }

    @Synchronized
    fun setScreenOn(on: Boolean) {
        if (on == screenOn) return
        screenOn = on
        // Write again on every screen-on, in case something else changed the node.
        if (on) lastWritten = null
        applyCurrent()
    }

    fun getStateForPackage(packageName: String): ThermalState =
        ThermalState.fromKey(prefs.getString(KEY_APP_PREFIX + packageName, null))
            ?: getAutoState(packageName)

    fun setStateForPackage(packageName: String, state: ThermalState) {
        val editor = prefs.edit()
        if (state == getAutoState(packageName)) {
            editor.remove(KEY_APP_PREFIX + packageName)
        } else {
            editor.putString(KEY_APP_PREFIX + packageName, state.key)
        }
        editor.apply()
        applyCurrent()
    }

    fun resetProfiles() {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(KEY_APP_PREFIX) }.forEach { editor.remove(it) }
        editor.apply()
        applyCurrent()
    }

    /** Writes the profile for the current screen state and top app, if it changed. */
    @Synchronized
    fun applyCurrent() {
        val state =
            when {
                !screenOn -> ThermalState.BALANCED
                perAppEnabled && currentApp.isNotEmpty() -> getStateForPackage(currentApp)
                else -> ThermalState.DEFAULT
            }
        val sconfig = state.sconfig ?: defaultProfile.sconfig!!
        if (sconfig == lastWritten) return
        if (FileUtils.writeLine(SCONFIG, sconfig)) {
            Log.i(TAG, "sconfig $sconfig ($state, app '$currentApp', screen ${if (screenOn) "on" else "off"})")
            lastWritten = sconfig
        }
    }

    /** Games get the gaming profile; everything else follows the default profile. */
    private fun getAutoState(packageName: String): ThermalState =
        if (isGame(packageName)) ThermalState.GAMING else ThermalState.DEFAULT

    @Suppress("DEPRECATION")
    private fun isGame(packageName: String): Boolean {
        if (packageName in GAMING_PACKAGES) return true
        return try {
            val info = context.packageManager.getApplicationInfo(packageName, 0)
            info.category == ApplicationInfo.CATEGORY_GAME ||
                (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * The sconfig numbers come from klimt's /odm/etc/thermal-map.conf. CPU caps at a 30 C virtual
     * skin temperature (little/big/prime, MHz) are in the comments; every profile lowers them as
     * the phone heats up. BENCHMARK (thermal-nolimits) has no CPU cap until 48 C and no core
     * shutdown at 50 C, so it is never picked automatically.
     */
    enum class ThermalState(val key: String, val sconfig: String?, @StringRes val label: Int) {
        DEFAULT("default", null, R.string.thermal_state_default),
        PERFORMANCE("performance", "50", R.string.thermal_state_performance), // per-normal 2000/2800/2300
        BALANCED("balanced", "0", R.string.thermal_state_balanced), // normal 1400/2100/2000
        GAMING("gaming", "19", R.string.thermal_state_gaming), // mgame 1400/2500/2800
        BENCHMARK("benchmark", "6", R.string.thermal_state_benchmark), // nolimits
        CAMERA("camera", "15", R.string.thermal_state_camera), // camera 1100/2000/2000
        VIDEO("video", "11", R.string.thermal_state_video), // video 800/1700/2000
        NAVIGATION("navigation", "10", R.string.thermal_state_navigation), // navigation 900/1500/1500
        VIDEO_CALL("video_call", "14", R.string.thermal_state_video_call), // videochat 1000/1500/1300
        CALL("call", "5", R.string.thermal_state_call); // phone 900/1500/1200

        companion object {
            fun fromKey(key: String?): ThermalState? = values().find { it.key == key }
        }
    }

    companion object {
        private const val TAG = "XiaomiPartsThermal"
        private const val SCONFIG = "/sys/class/thermal/thermal_message/sconfig"

        private const val PREFS_NAME = "thermal"
        private const val KEY_DEFAULT_PROFILE = "default_profile"
        private const val KEY_PER_APP = "per_app_enabled"
        private const val KEY_APP_PREFIX = "app:"

        private val GAMING_PACKAGES =
            setOf(
                "com.activision.callofduty.shooter",
                "com.dts.freefireth",
                "com.miHoYo.GenshinImpact",
                "com.mojang.minecraftpe",
                "com.pubg.imobile",
                "com.riotgames.league.wildrift",
                "com.roblox.client",
                "com.tencent.ig",
            )

        @Volatile private var instance: ThermalUtils? = null

        @JvmStatic
        fun getInstance(context: Context): ThermalUtils =
            instance
                ?: synchronized(this) {
                    instance ?: ThermalUtils(context.applicationContext).also { instance = it }
                }
    }
}
