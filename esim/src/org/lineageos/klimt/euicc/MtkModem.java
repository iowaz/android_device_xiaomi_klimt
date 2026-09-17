// SPDX-FileCopyrightText: WitAqua
// SPDX-FileCopyrightText: The LineageOS Project
// SPDX-License-Identifier: Apache-2.0

package org.lineageos.klimt.euicc;

import android.hardware.radio.RadioResponseInfo;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.Log;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** The OEM string request subset of MTK's stable modem AIDL interface. */
final class MtkModem {
    private static final String TAG = "KlimtEuicc";
    private static final String DESCRIPTOR =
            "vendor.mediatek.hardware.mtkradioex.modem.IMtkRadioExModem";
    // Stock MiuiEsimManager uses Phone(1), i.e. the slot2 HAL instance.
    private static final String SERVICE = DESCRIPTOR + "/slot2";
    private static final int SEND_STRINGS = 11;
    private static final int ACKNOWLEDGE = 25;
    private static final int SET_CALLBACKS = 26;
    private static final int GET_HASH = 16777214;
    private static final int GET_VERSION = 16777215;
    private static final int RESPONSE_STRINGS = 7;
    private static final int CLIENT_MTK = 0;
    private final AtomicInteger mSerial = new AtomicInteger();
    private final ConcurrentHashMap<Integer, CompletableFuture<String[]>> mPending =
            new ConcurrentHashMap<>();
    private IBinder mRemote;
    // Keep the callback objects alive for the lifetime of the HAL connection.
    private Callback mResponse;
    private Callback mIndication;

    private synchronized IBinder connect() throws IOException, RemoteException {
        if (mRemote != null && mRemote.isBinderAlive()) return mRemote;
        IBinder remote = ServiceManager.checkService(SERVICE);
        if (remote == null) throw new IOException("slot2 MTK modem HAL is not ready");
        int version;
        String hash;
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            if (!remote.transact(GET_VERSION, data, reply, 0)) {
                throw new IOException("MTK modem has no stable AIDL version");
            }
            reply.readException();
            version = reply.readInt();
        } finally {
            data.recycle();
            reply.recycle();
        }
        data = Parcel.obtain();
        reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            if (!remote.transact(GET_HASH, data, reply, 0)) {
                throw new IOException("MTK modem has no stable AIDL hash");
            }
            reply.readException();
            hash = reply.readString();
        } finally {
            data.recycle();
            reply.recycle();
        }
        // The shipped modem library is V3. Reject unexamined wire protocols.
        if (version != 3) throw new IOException("Unsupported MTK modem version " + version);
        Callback response = new Callback(DESCRIPTOR + "Response", version, hash, remote, true);
        Callback indication = new Callback(DESCRIPTOR + "Indication", version, hash, remote, false);
        remote.linkToDeath(() -> {
            synchronized (MtkModem.this) {
                if (mRemote != remote) return;
                mRemote = null;
                mPending.values().forEach(f -> f.completeExceptionally(
                        new IOException("MTK modem HAL died")));
            }
        }, 0);
        data = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            data.writeStrongBinder(response);
            data.writeStrongBinder(indication);
            if (!remote.transact(SET_CALLBACKS, data, null, IBinder.FLAG_ONEWAY)) {
                throw new IOException("Unable to register MTK modem callbacks");
            }
        } finally {
            data.recycle();
        }
        mResponse = response;
        mIndication = indication;
        mRemote = remote;
        Log.i(TAG, "Connected to slot2 MTK modem AIDL V" + version);
        return remote;
    }

    int request(String... command) throws IOException {
        int serial = mSerial.incrementAndGet();
        CompletableFuture<String[]> result = new CompletableFuture<>();
        try {
            IBinder remote = connect();
            mPending.put(serial, result);
            Parcel data = Parcel.obtain();
            try {
                data.writeInterfaceToken(DESCRIPTOR);
                data.writeInt(serial);
                data.writeStringArray(command);
                data.writeInt(CLIENT_MTK);
                if (!remote.transact(SEND_STRINGS, data, null, IBinder.FLAG_ONEWAY)) {
                    throw new IOException("MTK OEM request is unsupported");
                }
            } finally {
                data.recycle();
            }
            String[] values = result.get(5, TimeUnit.SECONDS);
            if (values == null || values.length != 1 || values[0] == null) {
                throw new IOException("Malformed MTK eSIM response");
            }
            // Stock parses the first comma-separated integer in the single returned string.
            int value = Integer.parseInt(values[0].split(",", -1)[0].trim());
            Log.i(TAG, command[0] + " returned " + value);
            return value;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted MTK eSIM request", e);
        } catch (Exception e) {
            throw new IOException("Failed " + command[0], e);
        } finally {
            mPending.remove(serial);
        }
    }

    private void acknowledge(IBinder remote) throws RemoteException {
        Parcel data = Parcel.obtain();
        try {
            data.writeInterfaceToken(DESCRIPTOR);
            remote.transact(ACKNOWLEDGE, data, null, IBinder.FLAG_ONEWAY);
        } finally {
            data.recycle();
        }
    }

    private final class Callback extends Binder {
        private final String mDescriptor;
        private final int mVersion;
        private final String mHash;
        private final IBinder mModem;
        private final boolean mIsResponse;

        Callback(String descriptor, int version, String hash, IBinder modem, boolean response) {
            mDescriptor = descriptor;
            mVersion = version;
            mHash = hash;
            mModem = modem;
            mIsResponse = response;
            attachInterface(null, descriptor);
            markVintfStability();
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags)
                throws RemoteException {
            if (code == INTERFACE_TRANSACTION) {
                reply.writeString(mDescriptor);
                return true;
            }
            if (code < FIRST_CALL_TRANSACTION || code > LAST_CALL_TRANSACTION) {
                return super.onTransact(code, data, reply, flags);
            }
            data.enforceInterface(mDescriptor);
            if (code == GET_VERSION || code == GET_HASH) {
                reply.writeNoException();
                if (code == GET_VERSION) reply.writeInt(mVersion);
                else reply.writeString(mHash);
                return true;
            }
            if (!mIsResponse) {
                // All modem indications start with RadioIndicationType. The AOSP radio
                // interfaces deliver the SIM state updates; no OEM indication is needed here.
                if (data.readInt() == 1) acknowledge(mModem);
                return true;
            }
            RadioResponseInfo info = data.readTypedObject(RadioResponseInfo.CREATOR);
            if (info == null) return false;
            if (info.type == 2) acknowledge(mModem);
            if (code == RESPONSE_STRINGS && info.type != 1) {
                CompletableFuture<String[]> future = mPending.get(info.serial);
                if (future != null) {
                    if (info.error == 0) future.complete(data.createStringArray());
                    else future.completeExceptionally(new IOException("Radio error " + info.error));
                }
            }
            return true;
        }
    }
}
