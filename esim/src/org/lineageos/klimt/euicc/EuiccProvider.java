// SPDX-FileCopyrightText: WitAqua
// SPDX-FileCopyrightText: The LineageOS Project
// SPDX-License-Identifier: Apache-2.0

package org.lineageos.klimt.euicc;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;

/** Privileged, fixed operations only; never exposes arbitrary modem commands. */
public final class EuiccProvider extends ContentProvider {
    @Override
    public boolean onCreate() { return true; }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        // ContentProvider.call() does not automatically enforce read/write permissions.
        getContext().enforceCallingOrSelfPermission(
                "android.permission.WRITE_EMBEDDED_SUBSCRIPTIONS", "eSIM controller");
        long identity = Binder.clearCallingIdentity();
        try {
            EuiccController controller = EuiccController.get(getContext());
            int result = switch (method) {
                case "getEsimGPIOState" -> controller.getGpioState();
                case "setEsimState" -> controller.setGpioState(Integer.parseInt(arg));
                default -> throw new IllegalArgumentException("Unknown eSIM operation");
            };
            Bundle reply = new Bundle();
            reply.putInt("result", result);
            return reply;
        } finally {
            Binder.restoreCallingIdentity(identity);
        }
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        throw new UnsupportedOperationException();
    }
    @Override
    public String getType(Uri uri) { return null; }
    @Override
    public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override
    public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }
}
