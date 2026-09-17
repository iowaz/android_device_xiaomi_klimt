// SPDX-FileCopyrightText: WitAqua
// SPDX-FileCopyrightText: The LineageOS Project
// SPDX-License-Identifier: Apache-2.0

package org.lineageos.klimt.euicc;

import android.content.Context;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.UiccSlotMapping;
import android.util.Log;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

final class EuiccController {
    private static final String TAG = "KlimtEuicc";
    private static final String SETTING = "is_enable_esim_for_user";
    private static EuiccController sInstance;
    private final Context mContext;
    private final MtkModem mModem = new MtkModem();
    private final ScheduledExecutorService mWorker = Executors.newSingleThreadScheduledExecutor();
    private boolean mBootStarted;
    private boolean mEnableRequested;

    static synchronized EuiccController get(Context context) {
        if (sInstance == null) sInstance = new EuiccController(context.getApplicationContext());
        return sInstance;
    }

    private EuiccController(Context context) {
        mContext = context;
    }

    synchronized void onBoot() {
        if (mBootStarted) return;
        mBootStarted = true;
        mWorker.execute(() -> initialize(0));
    }

    private synchronized void initialize(int attempt) {
        // Preserve an explicit opt-out. An unset value gets the device's eSIM default.
        if (Settings.Secure.getInt(mContext.getContentResolver(), SETTING, -1) == 0) return;
        try {
            int state = readModemState();
            if (state == 1) {
                saveState(true);
                Log.i(TAG, "eSIM enabled and confirmed by modem");
                return;
            }
            if (!mEnableRequested) {
                // Set the flag before sending: a timeout may mean the modem accepted the
                // request but its response was lost. Poll instead of repeatedly switching.
                mEnableRequested = true;
                int result = requestSwitch(true);
                if (result != 0 && result != 1) {
                    Log.e(TAG, "eSIM enable rejected: " + result + "; leaving settings unchanged");
                    return;
                }
            }
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "eSIM initialization attempt " + (attempt + 1) + " failed", e);
        }
        if (attempt < 23) {
            mWorker.schedule(() -> initialize(attempt + 1), 5, TimeUnit.SECONDS);
        } else {
            Log.e(TAG, "eSIM initialization timed out; modem enablement was not confirmed");
        }
    }

    private int readModemState() throws IOException {
        int state = mModem.request("MIPC_GET_ESIM_STATE");
        if (state != 0 && state != 1) throw new IOException("Invalid modem eSIM state " + state);
        return state;
    }

    synchronized int getGpioState() {
        try {
            // The MIUI GPIO convention is the inverse of the MIPC convention.
            return 1 - readModemState();
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Cannot read eSIM state", e);
            return -1;
        }
    }

    synchronized int setGpioState(int state) {
        if (state != 0 && state != 1) return -1;
        boolean enable = state == 0;
        try {
            if (readModemState() == (enable ? 1 : 0)) {
                saveState(enable);
                return 0;
            }
            // Active profiles must be disabled by the LPA before powering down the eUICC.
            // Do not implement Stock's separate MEP profile/mapping workflow implicitly.
            if (!enable) {
                List<SubscriptionInfo> subscriptions = mContext
                        .getSystemService(SubscriptionManager.class).getActiveSubscriptionInfoList();
                if (subscriptions != null && subscriptions.stream().anyMatch(SubscriptionInfo::isEmbedded)) {
                    Log.w(TAG, "Disable eSIM profiles before disabling the eUICC");
                    return -1;
                }
            }
            int result = requestSwitch(enable);
            if (result != 0 && result != 1) return -1;
            if (readModemState() != (enable ? 1 : 0)) return -1;
            saveState(enable);
            return 0;
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Cannot switch eSIM", e);
            return -1;
        }
    }

    private void saveState(boolean enabled) throws IOException {
        if (!Settings.Secure.putInt(mContext.getContentResolver(), SETTING, enabled ? 1 : 0)) {
            throw new IOException("Cannot save confirmed eSIM state");
        }
    }

    private int requestSwitch(boolean enable) throws IOException {
        String value = enable ? "1" : "0";
        int result = mModem.request("MIPC_SET_ESIM_STATE", value);
        if (result == 4 || result == 5) {
            // Stock MiuiEsimManager.retrySetEsimState restores these mappings on
            // results 4/5, waits one second, and retries the same request once.
            mContext.getSystemService(TelephonyManager.class).setSimSlotMapping(List.of(
                    new UiccSlotMapping(0, 0, 0), new UiccSlotMapping(0, 1, 1)));
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted eSIM mapping recovery", e);
            }
            result = mModem.request("MIPC_SET_ESIM_STATE", value);
        }
        return result;
    }
}
