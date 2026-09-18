// SPDX-FileCopyrightText: WitAqua
// SPDX-FileCopyrightText: The LineageOS Project
// SPDX-License-Identifier: Apache-2.0

package org.lineageos.klimt.euicc;

import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.telephony.UiccCardInfo;
import android.telephony.UiccPortInfo;
import android.telephony.UiccSlotMapping;
import android.text.TextUtils;
import android.util.Log;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.IntUnaryOperator;

/** Remember working MEP mappings; restore an empty mapping only during boot. */
final class EsimPortTracker {
    private static final String TAG = "KlimtEuicc";
    private final Context mContext;
    private final TelephonyManager mTelephony;
    private final SharedPreferences mPreferences;
    private final ScheduledExecutorService mWorker;
    private final IntUnaryOperator mSelectPort;
    private boolean mRestoreStarted;
    private final SubscriptionManager.OnSubscriptionsChangedListener mListener =
            new SubscriptionManager.OnSubscriptionsChangedListener(Looper.getMainLooper()) {
                @Override
                public void onSubscriptionsChanged() {
                    rememberCurrentPort();
                }
            };

    EsimPortTracker(Context context, ScheduledExecutorService worker, IntUnaryOperator selectPort) {
        mContext = context;
        mTelephony = context.getSystemService(TelephonyManager.class);
        mPreferences = context.createDeviceProtectedStorageContext()
                .getSharedPreferences("esim_port", Context.MODE_PRIVATE);
        mWorker = worker;
        mSelectPort = selectPort;
    }

    void start() {
        try {
            // Subscription changes can arrive before SIM records finish loading.
            mContext.registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    mWorker.execute(() -> rememberCurrentPort());
                }
            }, new IntentFilter(TelephonyManager.ACTION_SIM_APPLICATION_STATE_CHANGED),
                    Context.RECEIVER_EXPORTED);
            mContext.getSystemService(SubscriptionManager.class)
                    .addOnSubscriptionsChangedListener(mWorker, mListener);
        } catch (RuntimeException e) {
            // Port tracking must not prevent the modem's eSIM initialization.
            Log.w(TAG, "Cannot register eSIM port tracking", e);
        }
    }

    private void rememberCurrentPort() {
        try {
            rememberActivePort(findCard());
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot remember eSIM port", e);
        }
    }

    // Called after modem eSIM enablement is confirmed. Never run on subscription changes.
    void restoreAfterBoot() {
        if (mRestoreStarted) return;
        mRestoreStarted = true;
        mWorker.schedule(() -> restore(0), 5, TimeUnit.SECONDS);
    }

    private UiccCardInfo findCard() {
        return mTelephony.getUiccCardsInfo().stream()
                .filter(card -> card.isEuicc() && card.getPhysicalSlotIndex() == 1)
                .findFirst().orElse(null);
    }

    private boolean rememberActivePort(UiccCardInfo card) {
        if (card == null) return false;
        // Do not collapse a dual-eSIM mapping into the single-port recovery path.
        if (card.getPorts().stream().filter(UiccPortInfo::isActive).count() != 1) return false;
        for (UiccPortInfo port : card.getPorts()) {
            if (!port.isActive() || TextUtils.isEmpty(port.getIccId())) continue;
            if (!TextUtils.isEmpty(card.getEid())
                    && port.getLogicalSlotIndex() == 1
                    && mTelephony.getSimApplicationState(1, port.getPortIndex())
                            == TelephonyManager.SIM_STATE_LOADED
                    && mContext.getSystemService(SubscriptionManager.class)
                            .getCompleteActiveSubscriptionInfoList().stream().anyMatch(sub ->
                                    sub.isEmbedded() && sub.getSimSlotIndex() == 1
                                            && sub.getPortIndex() == port.getPortIndex())) {
                if (!card.getEid().equals(mPreferences.getString("eid", ""))
                        || port.getPortIndex() != mPreferences.getInt("port", -1)) {
                    mPreferences.edit().putString("eid", card.getEid())
                            .putInt("port", port.getPortIndex()).apply();
                    Log.i(TAG, "Remembered active eSIM port: " + port.getPortIndex());
                }
            }
            return true;
        }
        return false;
    }

    private int savedPort(UiccCardInfo card) {
        if (card == null || TextUtils.isEmpty(card.getEid())
                || !card.getEid().equals(mPreferences.getString("eid", ""))) return -1;
        int port = mPreferences.getInt("port", -1);
        return (port == 0 || port == 1)
                && card.getPorts().stream().anyMatch(p -> p.getPortIndex() == port) ? port : -1;
    }

    private void restore(int attempt) {
        if (Settings.Secure.getInt(mContext.getContentResolver(),
                "is_enable_esim_for_user", -1) == 0) return;
        try {
            UiccCardInfo card = findCard();
            if (rememberActivePort(card)) return;
            if (card != null && !TextUtils.isEmpty(card.getEid())) {
                int port = savedPort(card);
                if (port < 0 || !card.isMultipleEnabledProfilesSupported()) return;
                if (card.getPorts().stream().anyMatch(p -> p.isActive()
                        && p.getPortIndex() == port && p.getLogicalSlotIndex() == 1)) return;
                // Allow startup SIM discovery to settle before touching an empty port.
                if (attempt >= 2) {
                    int result = mSelectPort.applyAsInt(port);
                    Log.i(TAG, "Boot eSIM port restoration: port=" + port + ", result=" + result);
                    return;
                }
            }
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot inspect boot eSIM mapping", e);
        }
        if (attempt < 23) {
            mWorker.schedule(() -> restore(attempt + 1), 5, TimeUnit.SECONDS);
        }
    }

    // Preserve the current port when Stock's modem fallback needs a slot mapping reset.
    int fallbackPort() {
        try {
            for (UiccSlotMapping mapping : mTelephony.getSimSlotMapping()) {
                if (mapping.getPhysicalSlotIndex() == 1 && mapping.getLogicalSlotIndex() == 1
                        && (mapping.getPortIndex() == 0 || mapping.getPortIndex() == 1)) {
                    return mapping.getPortIndex();
                }
            }
            int saved = savedPort(findCard());
            if (saved >= 0) return saved;
        } catch (RuntimeException e) {
            Log.w(TAG, "Cannot determine eSIM fallback port", e);
        }
        return 0;
    }
}
