/*
 * SPDX-FileCopyrightText: WitAqua
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package miui.securityspace;

import android.compat.annotation.UnsupportedAppUsage;

import android.content.Context;

public class CrossUserUtils {
    @UnsupportedAppUsage
    public static boolean checkUidPermission(Context context, String packageName) {
        return false;
    }

    private CrossUserUtils() {}
}
