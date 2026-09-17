/*
 * SPDX-FileCopyrightText: WitAqua
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package miui.telephony;

import android.compat.annotation.UnsupportedAppUsage;

/** MIUI's wrapper around the platform SubscriptionManager. */
public class SubscriptionManager {
    private static final SubscriptionManager sInstance = new SubscriptionManager();

    @UnsupportedAppUsage
    public static SubscriptionManager getDefault() {
        return sInstance;
    }

    @UnsupportedAppUsage
    public int getDefaultDataSlotId() {
        return android.telephony.SubscriptionManager.getSlotIndex(
                android.telephony.SubscriptionManager.getDefaultDataSubscriptionId());
    }

    protected SubscriptionManager() {}
}
