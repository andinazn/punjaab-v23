/*
** Created by Andina Zahra Nabilla on 10 April 2018
*
* Activity berfungsi untuk:
* 1. Mengaktifkan Remote Sensor Manager
* 2. Pemetaan Data Sinkronisasi Antara Wear dan Handheld
* 3. Pengiriman Sensor Data Yang Dideteksi Menuju MainActivity Melalui Intent Broadcast
* 4. Penerimaan Intent Broadcast Berisi START_TIME
*/

package com.andinazn.sensordetectionv2;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import com.github.pocmo.sensordashboard.shared.DataMapKeys;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.WearableListenerService;
import com.andinazn.sensordetectionv2.database.DatabaseHandler;
import com.andinazn.sensordetectionv2.model.MonitorDataModel;

import java.util.Arrays;
import java.util.List;


public class SensorReceiverService extends WearableListenerService {
    private static final String TAG = "SensorDashboard/SensorReceiverService";

    private static final String FALLSTATE = "com.andinazn.sensordetectionv2.fallstate";

    private RemoteSensorManager sensorManager;
    long startTime;
    boolean isRunning = false;

    boolean fallstate;

    @Override
    public void onCreate() {
        super.onCreate();

        //1. Mengaktifkan Remote Sensor Manager
        sensorManager = RemoteSensorManager.getInstance(this);
        SharedPreferences pref = getSharedPreferences("START_TIME", Activity.MODE_PRIVATE);
        startTime = pref.getLong("START_TIME", 0L);
        registerReceiver(mMessageReceiver, new IntentFilter("com.example.Broadcast1"));
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mMessageReceiver);
    }
    @Override
    public void onPeerConnected(Node peer) {
        super.onPeerConnected(peer);
        Log.i(TAG, "Connected: " + peer.getDisplayName() + " (" + peer.getId() + ")");
    }

    @Override
    public void onPeerDisconnected(Node peer) {
        super.onPeerDisconnected(peer);
        Log.i(TAG, "Disconnected: " + peer.getDisplayName() + " (" + peer.getId() + ")");
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        super.onDataChanged(dataEvents);
        Log.d(TAG, "onDataChanged()");
        for (DataEvent dataEvent : dataEvents) {
            if (dataEvent.getType() == DataEvent.TYPE_CHANGED) {
                isRunning = true;
                DataItem dataItem = dataEvent.getDataItem();
                DataMap dataMap = DataMapItem.fromDataItem(dataEvent.getDataItem()).getDataMap();
                Uri uri = dataItem.getUri();
                String path = uri.getPath();
                if (path.equals("/fall")) {
                    fallstate = dataMap.getBoolean(FALLSTATE);
                    Log.d("Fall State", "Successfully received fall state: " + fallstate);
                    fallstate = true;
                }

                if (path.startsWith("/sensors/")) {
                    unpackSensorData(fallstate,
                            Integer.parseInt(uri.getLastPathSegment()),
                            DataMapItem.fromDataItem(dataItem).getDataMap()
                    );
                }

            } else {
                isRunning = false;
            }
        }
    }

    //2. Pemetaan Data Sinkronisasi Antara Wear dan Handheld
    public void unpackSensorData(boolean fallstate, int sensorType, DataMap dataMap) {
        isRunning = true;
        int accuracy = dataMap.getInt(DataMapKeys.ACCURACY);
        long timestamp = dataMap.getLong(DataMapKeys.TIMESTAMP);
        float[] values = dataMap.getFloatArray(DataMapKeys.VALUES);
        Log.d(TAG, "Received sensor data " + sensorType + " = " + Arrays.toString(values) + " timestamp:" + timestamp + " start time:" + startTime);
        // Save to database if Start time more than 0
        if (startTime > 0) {
            DatabaseHandler db = new DatabaseHandler(this);
            db.addMonitorData(new MonitorDataModel(0, String.valueOf(sensorType), "anonymous", Arrays.toString(values), String.valueOf(timestamp), String.valueOf(accuracy), String.valueOf(startTime)));
            List<MonitorDataModel> list = db.getAllUserMonitorData();
        } else {
            isRunning = false;
        }

        boolean fallconfirmation = fallstate;

        Log.d("Fall state", "Fall state di Intent: " + fallstate);
        //3. Pengiriman Sensor Data Yang Dideteksi Menuju MainActivity Melalui Intent Broadcast
        //Broadcasting sensor data
        Log.d(TAG,"Broadcast Heartrate Value.");
        Intent intent = new Intent();
        intent.setAction("com.example.Broadcast");
        intent.putExtra("HR", values);
        intent.putExtra("CURRENT", values);
        intent.putExtra("ACCR", accuracy);
        intent.putExtra("TIME", timestamp);
        intent.putExtra("SENSOR_TYPE", sensorType);
        intent.putExtra("IS_RUNNING", isRunning);
        intent.putExtra("FALLSTATE", fallconfirmation);
        sendBroadcast(intent);
        sensorManager.addSensorData(sensorType, accuracy, timestamp, values);
    }

    //4. Penerimaan Intent Broadcast Berisi START_TIME
    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long message1 = intent.getLongExtra("START_TIME",0L);
            startTime = message1;// set start time
            SharedPreferences pref = getSharedPreferences("START_TIME", Activity.MODE_PRIVATE);
            startTime = pref.getLong("START_TIME", 0L);
            Log.d("Receiver", "Got START_TIMEB: " + String.valueOf(startTime));
        }
    };
}
