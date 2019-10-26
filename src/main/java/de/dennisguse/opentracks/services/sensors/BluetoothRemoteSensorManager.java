/*
 * Copyright 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package de.dennisguse.opentracks.services.sensors;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.content.sensor.SensorDataSet;
import de.dennisguse.opentracks.util.PreferencesUtils;
import de.dennisguse.opentracks.util.UnitConversions;

/**
 * Bluetooth sensor manager.
 *
 * @author Sandor Dornbush
 */
public class BluetoothRemoteSensorManager {

    public static final long MAX_SENSOR_DATE_SET_AGE_MS = 5000;

    private static final String TAG = BluetoothConnectionManager.class.getSimpleName();

    private static final BluetoothAdapter bluetoothAdapter = getDefaultBluetoothAdapter();

    private final Context context;

    // Handler that gets information back from the bluetoothConnectionManager
    private final Handler messageHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message message) {
            String toastMessage;
            switch (message.what) {
                case BluetoothConnectionManager.MESSAGE_CONNECTING:
                    //Ignore for now.
                    break;
                case BluetoothConnectionManager.MESSAGE_CONNECTED:
                    toastMessage = context.getString(R.string.settings_sensor_connected, message.obj);
                    Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show();
                    break;
                case BluetoothConnectionManager.MESSAGE_READ:
                    if (!(message.obj instanceof SensorDataSet)) {
                        Log.e(TAG, "Received message did not contain a SensorDataSet.");
                        sensorDataSet = null;
                    } else {
                        sensorDataSet = (SensorDataSet) message.obj;
                    }
                    break;
                case BluetoothConnectionManager.MESSAGE_DISCONNECTED:
                    toastMessage = context.getString(R.string.settings_sensor_disconnected, message.obj);
                    Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show();
                    break;
                default:
                    Log.e(TAG, "Got an undefined case. Please check.");
                    break;
            }
        }
    };

    private SensorDataSet sensorDataSet = null;
    private BluetoothConnectionManager bluetoothConnectionManager;

    /**
     * @param context the context
     */
    BluetoothRemoteSensorManager(Context context) {
        this.context = context;
    }

    private static BluetoothAdapter getDefaultBluetoothAdapter() {
        // If from the main application thread, return directly
        if (Thread.currentThread().equals(Looper.getMainLooper().getThread())) {
            return BluetoothAdapter.getDefaultAdapter();
        }

        // Get the default adapter from the main application thread
        final ArrayList<BluetoothAdapter> adapters = new ArrayList<>(1);
        final Object mutex = new Object();

        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                adapters.add(BluetoothAdapter.getDefaultAdapter());
                synchronized (mutex) {
                    mutex.notify();
                }
            }
        });

        while (adapters.isEmpty()) {
            synchronized (mutex) {
                try {
                    mutex.wait(UnitConversions.ONE_SECOND);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Interrupted while waiting for default bluetooth adapter", e);
                }
            }
        }

        if (adapters.get(0) == null) {
            Log.w(TAG, "No bluetooth adapter found.");
        }
        return adapters.get(0);
    }

    public void startSensor() {
        if (!isEnabled()) {
            Log.w(TAG, "Bluetooth not enabled.");
            return;
        }

        String address = PreferencesUtils.getString(context, R.string.bluetooth_sensor_key, PreferencesUtils.BLUETOOTH_SENSOR_DEFAULT);
        if (PreferencesUtils.BLUETOOTH_SENSOR_DEFAULT.equals(address)) {
            Log.w(TAG, "No bluetooth address.");
            return;
        }
        Log.i(TAG, "Connecting to bluetooth address: " + address);

        BluetoothDevice device;
        try {
            device = bluetoothAdapter.getRemoteDevice(address);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Unable to get remote device for: " + address, e);

            String toastMessage = context.getString(R.string.sensor_not_known, address);
            Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show();

            return;
        }

        stopSensor();

        bluetoothConnectionManager = new BluetoothConnectionManager(context, device, messageHandler);
        bluetoothConnectionManager.connect();
    }

    public void stopSensor() {
        if (bluetoothConnectionManager != null) {
            bluetoothConnectionManager.disconnect();
            bluetoothConnectionManager = null;
        }
    }

    public boolean isEnabled() {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled();
    }

    public SensorDataSet getSensorDataSet() {
        return sensorDataSet;
    }

    public boolean isSensorDataSetValid() {
        SensorDataSet sensorDataSet = getSensorDataSet();
        if (sensorDataSet == null) {
            return false;
        }
        return sensorDataSet.isRecent(MAX_SENSOR_DATE_SET_AGE_MS);
    }
}
