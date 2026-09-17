/*
 * SPDX-FileCopyrightText: WitAqua
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package miui.util;

import android.compat.annotation.UnsupportedAppUsage;

import android.content.Context;
import android.net.Uri;
import android.os.VibrationAttributes;

/**
 * MIUI's linear motor haptics. Only miuix's HapticFeedbackCompat calls this, and it
 * treats an unsupported motor as "fall back to the platform's own haptics", so every
 * capability query answers no and every playback request is a no-op.
 */
public class HapticFeedbackUtil {
    @UnsupportedAppUsage
    public HapticFeedbackUtil(Context context, boolean useSystemVibrator) {}

    @UnsupportedAppUsage
    public static boolean isSupportLinearMotorVibrate() {
        return false;
    }

    @UnsupportedAppUsage
    public static boolean isSupportLinearMotorVibrate(int effectId) {
        return false;
    }

    @UnsupportedAppUsage
    public boolean isSupportExtHapticFeedback(int effectId) {
        return false;
    }

    @UnsupportedAppUsage
    public boolean performExtHapticFeedback(int effectId) {
        return false;
    }

    @UnsupportedAppUsage
    public boolean performExtHapticFeedback(int effectId, boolean always) {
        return false;
    }

    @UnsupportedAppUsage
    public boolean performExtHapticFeedback(int effectId, int repeat) {
        return false;
    }

    @UnsupportedAppUsage
    public boolean performExtHapticFeedback(int effectId, int repeat, boolean always) {
        return false;
    }

    @UnsupportedAppUsage
    public boolean performExtHapticFeedback(int effectId, double amplitude, String reason) {
        return false;
    }

    @UnsupportedAppUsage
    public boolean performExtHapticFeedback(Uri uri) {
        return false;
    }

    @UnsupportedAppUsage
    public boolean performExtHapticFeedback(Uri uri, boolean always) {
        return false;
    }

    @UnsupportedAppUsage
    public boolean performExtHapticFeedback(VibrationAttributes attributes, int effectId) {
        return false;
    }

    @UnsupportedAppUsage
    public boolean performExtHapticFeedback(VibrationAttributes attributes, int effectId,
            boolean always) {
        return false;
    }

    @UnsupportedAppUsage
    public boolean performHapticFeedback(int effectId, boolean always) {
        return false;
    }

    @UnsupportedAppUsage
    public boolean performHapticFeedback(int effectId, boolean always, int flags) {
        return false;
    }

    @UnsupportedAppUsage
    public boolean performHapticFeedback(int effectId, double amplitude, String reason) {
        return false;
    }

    @UnsupportedAppUsage
    public boolean performHapticFeedback(VibrationAttributes attributes, int effectId,
            boolean always) {
        return false;
    }

    @UnsupportedAppUsage
    public boolean performHapticFeedback(VibrationAttributes attributes, int effectId,
            boolean always, int flags) {
        return false;
    }

    @UnsupportedAppUsage
    public boolean performHapticFeedback(VibrationAttributes attributes, int effectId,
            double amplitude, String reason) {
        return false;
    }

    @UnsupportedAppUsage
    public void stop() {}

    @UnsupportedAppUsage
    public void release() {}
}
