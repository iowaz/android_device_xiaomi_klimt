/*
 * SPDX-FileCopyrightText: WitAqua
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.klimt.touchbridge;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;

/**
 * Mirrors the double tap to wake and screen-off fingerprint unlock settings into
 * system properties. libudfpshandler (fingerprint HAL) watches them and switches
 * the touch panel's gesture modes, which the stock power HAL does not do.
 */
public class TouchBridgeApp extends Application {
    private static final String PROP_DOUBLE_TAP = "persist.sys.klimt.dt2w";
    private static final String PROP_SCREEN_OFF_FOD = "persist.sys.klimt.fod_screen_off";

    @Override
    public void onCreate() {
        super.onCreate();
        final Handler handler = new Handler(Looper.getMainLooper());
        final ContentObserver observer = new ContentObserver(handler) {
            @Override
            public void onChange(boolean selfChange) {
                update();
            }
        };
        getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.DOUBLE_TAP_TO_WAKE),
                false, observer, UserHandle.USER_ALL);
        getContentResolver().registerContentObserver(
                Settings.Secure.getUriFor(Settings.Secure.SCREEN_OFF_UNLOCK_UDFPS_ENABLED),
                false, observer, UserHandle.USER_ALL);
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                update();
            }
        }, new IntentFilter(Intent.ACTION_USER_SWITCHED), Context.RECEIVER_NOT_EXPORTED);
        update();
    }

    private void update() {
        final boolean doubleTap = Settings.Secure.getIntForUser(getContentResolver(),
                Settings.Secure.DOUBLE_TAP_TO_WAKE, 0, UserHandle.USER_CURRENT) != 0;
        final boolean fodDefault = getResources().getBoolean(
                com.android.internal.R.bool.config_screen_off_udfps_default_on);
        final boolean screenOffFod = Settings.Secure.getIntForUser(getContentResolver(),
                Settings.Secure.SCREEN_OFF_UNLOCK_UDFPS_ENABLED, fodDefault ? 1 : 0,
                UserHandle.USER_CURRENT) != 0;
        SystemProperties.set(PROP_DOUBLE_TAP, doubleTap ? "1" : "0");
        SystemProperties.set(PROP_SCREEN_OFF_FOD, screenOffFod ? "1" : "0");
    }
}
