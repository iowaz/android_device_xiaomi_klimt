/*
 * SPDX-FileCopyrightText: 2016 The CyanogenMod Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.utils

import android.util.Log
import java.io.File
import java.io.IOException

object FileUtils {
    private const val TAG = "XiaomiPartsFileUtils"

    @Volatile private var loggedTrace = false

    /** Writes [value] to [path]; returns false (and logs, with a stack trace once) on failure. */
    fun writeLine(path: String, value: String): Boolean =
        try {
            File(path).writeText(value)
            true
        } catch (e: IOException) {
            logFailure("Could not write '$value' to $path", e)
            false
        } catch (e: SecurityException) {
            logFailure("Not allowed to write $path", e)
            false
        }

    private fun logFailure(message: String, e: Exception) {
        if (loggedTrace) {
            Log.e(TAG, "$message: $e")
        } else {
            loggedTrace = true
            Log.e(TAG, message, e)
        }
    }
}
