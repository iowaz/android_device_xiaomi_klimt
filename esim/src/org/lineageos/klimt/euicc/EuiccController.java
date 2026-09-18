// SPDX-FileCopyrightText: WitAqua
// SPDX-FileCopyrightText: The LineageOS Project
// SPDX-License-Identifier: Apache-2.0

package org.lineageos.klimt.euicc;

import android.content.Context;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.UiccCardInfo;
import android.telephony.UiccSlotMapping;
import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
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
    private final EsimPortTracker mPortTracker;
    private boolean mBootStarted;
    private boolean mEnableRequested;

    static synchronized EuiccController get(Context context) {
        if (sInstance == null) sInstance = new EuiccController(context.getApplicationContext());
        return sInstance;
    }

    private EuiccController(Context context) {
        mContext = context;
        mPortTracker = new EsimPortTracker(context, mWorker, this::restoreEsimPort);
    }

    synchronized void onBoot() {
        if (mBootStarted) return;
        mBootStarted = true;
        mPortTracker.start();
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
                mPortTracker.restoreAfterBoot();
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

    private synchronized int restoreEsimPort(int portIndex) {
        // Recheck after taking the controller lock: the user may have disabled the
        // eUICC since the worker inspected its slot mapping.
        if (Settings.Secure.getInt(mContext.getContentResolver(), SETTING, -1) == 0) return -1;
        try {
            if (readModemState() != 1) return -1;
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "Cannot confirm eSIM state before restoring its port", e);
            return -1;
        }
        return restorePortMapping(portIndex);
    }

    // Never disconnect a port that reports a subscription or change profile state.
    private int restorePortMapping(int portIndex) {
        if (portIndex != 0 && portIndex != 1) return -1;
        TelephonyManager telephony = mContext.getSystemService(TelephonyManager.class);
        try {
            UiccCardInfo card = telephony.getUiccCardsInfo().stream()
                    .filter(info -> info.isEuicc() && info.getPhysicalSlotIndex() == 1)
                    .findFirst().orElse(null);
            if (card == null || !card.isMultipleEnabledProfilesSupported()
                    || card.getPorts().stream().noneMatch(p -> p.getPortIndex() == portIndex)) {
                Log.w(TAG, "Requested eSIM port is unavailable");
                return -1;
            }
            List<UiccSlotMapping> mapping = new ArrayList<>(telephony.getSimSlotMapping());
            // Restore only klimt's physical SIM + single eSIM mapping.
            if (mapping.size() != 2 || mapping.stream().noneMatch(m ->
                    m.getPhysicalSlotIndex() == 0 && m.getLogicalSlotIndex() == 0
                            && m.getPortIndex() == 0)) return -1;
            int index = -1;
            for (int i = 0; i < mapping.size(); i++) {
                UiccSlotMapping entry = mapping.get(i);
                if (entry.getPhysicalSlotIndex() == 1 && entry.getLogicalSlotIndex() == 1) {
                    index = i;
                }
            }
            if (index == -1) return -1;
            if (mapping.get(index).getPortIndex() == portIndex) return 0;
            List<SubscriptionInfo> subscriptions = mContext
                    .getSystemService(SubscriptionManager.class).getCompleteActiveSubscriptionInfoList();
            if (subscriptions == null
                    || subscriptions.stream().anyMatch(s -> s.getSimSlotIndex() == 1)
                    || card.getPorts().stream().anyMatch(p ->
                            p.isActive() && !TextUtils.isEmpty(p.getIccId()))
                    || telephony.getCallState() != TelephonyManager.CALL_STATE_IDLE) {
                Log.w(TAG, "Refusing eSIM port selection while a subscription or call is active"
                        + " or subscription state is unavailable");
                return -1;
            }
            mapping.set(index, new UiccSlotMapping(portIndex, 1, 1));
            telephony.setSimSlotMapping(mapping);
            for (int attempt = 0; attempt < 10; attempt++) {
                if (telephony.getSimSlotMapping().stream().anyMatch(m ->
                        m.getPhysicalSlotIndex() == 1 && m.getLogicalSlotIndex() == 1
                                && m.getPortIndex() == portIndex)) {
                    Log.i(TAG, "eSIM port selection confirmed: " + portIndex);
                    return 0;
                }
                Thread.sleep(500);
            }
            Log.w(TAG, "eSIM port selection not confirmed; inspect the current mapping");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot select eSIM port", e);
        }
        return -1;
    }

    private int requestSwitch(boolean enable) throws IOException {
        String value = enable ? "1" : "0";
        int port = mPortTracker.fallbackPort();
        int result = mModem.request("MIPC_SET_ESIM_STATE", value);
        if (result == 4 || result == 5) {
            // Stock retries results 4/5 after restoring slot mappings. Preserve the
            // eSIM port rather than stranding an enabled profile by forcing port 0.
            mContext.getSystemService(TelephonyManager.class).setSimSlotMapping(List.of(
                    new UiccSlotMapping(0, 0, 0), new UiccSlotMapping(port, 1, 1)));
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
