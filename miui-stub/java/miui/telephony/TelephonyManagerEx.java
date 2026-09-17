// SPDX-FileCopyrightText: WitAqua
// SPDX-FileCopyrightText: The LineageOS Project
// SPDX-License-Identifier: Apache-2.0

package miui.telephony;

import android.compat.annotation.UnsupportedAppUsage;

import android.app.AppGlobals;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

/** MIUI LPA compatibility backed by the device's privileged eSIM controller. */
public final class TelephonyManagerEx {
    private static final TelephonyManagerEx INSTANCE = new TelephonyManagerEx();
    private static final Uri CONTROLLER = Uri.parse("content://org.lineageos.klimt.euicc");

    @UnsupportedAppUsage
    public static TelephonyManagerEx getDefault() { return INSTANCE; }

    @UnsupportedAppUsage
    public int getEsimGPIOState() { return call("getEsimGPIOState", null); }

    // MIUI: 0 enables eSIM, 1 disables eSIM; return 0 only for confirmed success.
    @UnsupportedAppUsage
    public int setEsimState(int state) { return call("setEsimState", Integer.toString(state)); }

    private int call(String method, String argument) {
        Context context = AppGlobals.getInitialApplication();
        if (context == null) return -1;
        try {
            Bundle result = context.getContentResolver().call(CONTROLLER, method, argument, null);
            return result == null ? -1 : result.getInt("result", -1);
        } catch (RuntimeException e) {
            Log.w("KlimtEuicc", "Failed " + method, e);
            return -1;
        }
    }
}
