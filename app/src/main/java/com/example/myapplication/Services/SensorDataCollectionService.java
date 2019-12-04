package com.example.myapplication.Services;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.TriggerEvent;
import android.hardware.TriggerEventListener;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

import com.example.myapplication.Activities.MainMenuActivity;
import com.example.myapplication.Model.DataWriter;
import com.example.myapplication.Model.DesiredSensorsList;
import com.example.myapplication.Model.SettingsManager;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class SensorDataCollectionService extends Service implements SensorEventListener {

    private final TriggerEventListener triggerEventListener = new MyTriggerEventListener();
    private HashMap<String, FileOutputStream> outputStreams = new HashMap<>();
    private long ii = 0;
    private HandlerThread mSensorThread;
    private Handler mSensorHandler;
    public static final String CHANNEL_ID = "ForegroundServiceChannel";
    private final float[] mRotationMatrix = new float[16];


    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.e("Sensor service", "onStartCommand");

        createNotificationChannel();
        Intent notificationIntent = new Intent(this, MainMenuActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0, notificationIntent, 0);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Foreground Service")
                .setContentText("asda")
                .setContentIntent(pendingIntent)
                .build();

        startForeground(1, notification);


        // TODO: I have changed the sensor list settings; it now records everything

        // read settings (sensorsList, duration)
//        HashMap<Integer, Boolean> sensorsList = (HashMap<Integer, Boolean>)
//                SettingsManager.loadSettings(getFilesDir(), SettingsManager.SENSORS);

//        int duration = (int) SettingsManager
//                .loadSettings(getFilesDir(), SettingsManager.RECORDING_DURATION);



//        if(sensorsList == null) {
//            Log.e("Error: (DataCollectionService)", "No sensor list available");
//            stopSelf();
//            return START_STICKY;
//        }

        // get sensors list
        SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        List<Integer> sensorsType = DesiredSensorsList.getSensorsList();
        List<Sensor> sensors = new ArrayList<>();
        for (int type : sensorsType) {
            sensors.add(sensorManager.getDefaultSensor(type));
        }

        // create files
        try {
            outputStreams = DataWriter.createSensorFiles(_getSelectedSensors(sensors));
        } catch (Exception e) {
            stopSelf();
            e.printStackTrace();
        }

        if(outputStreams == null) {
            stopSelf();
            Log.e("Error: (DataCollectionService)", "sensor files creation failed");
            return START_STICKY;
        }

        registerSensors(sensors, sensorManager);

        return START_STICKY;
    }

    private void registerSensors(List<Sensor> sensors, SensorManager sensorManager) {
        // initialize the rotation matrix to identity
        mRotationMatrix[ 0] = 1;
        mRotationMatrix[ 4] = 1;
        mRotationMatrix[ 8] = 1;
        mRotationMatrix[12] = 1;
        // start a new thread
        mSensorThread = new HandlerThread("Sensor thread", Thread.MAX_PRIORITY);
        mSensorThread.start();
        mSensorHandler = new Handler(mSensorThread.getLooper());

        for (Sensor sensor : sensors) {
            if (sensor == null) {continue;}
            if(sensor.getReportingMode() == Sensor.REPORTING_MODE_ONE_SHOT) {
                sensorManager.requestTriggerSensor(triggerEventListener, sensor);
                continue;
            }
            // TODO: I have changed the Sensor delay
            if(DesiredSensorsList.shouldRecord(sensor.getType())) {
                System.out.println(sensor.getName());
                sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_FASTEST, mSensorHandler);
            }

        }
    }

    private void unregisterSensors() {
        SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        Log.e("Sensor service", "unregistered");
        mSensorThread.quitSafely();
        sensorManager.unregisterListener(this);
    }


    // sensor event listener methods
    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor sensor = event.sensor;
            if (ii % 1000 == 0) {
                sendMessage(ii);
                System.out.println(Thread.currentThread());
            }
            ii += 1;
            new SensorEventLoggerTask().execute(event);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private List<String> _getSelectedSensors(List<Sensor> sensors) {
        List<String> sensorNames = new ArrayList<>();

//        SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
//        for(Map.Entry<Integer, Boolean> entry : sensorsList.entrySet()) {
//            if(entry.getValue()) {
//                Sensor sensor = sensorManager.getDefaultSensor(entry.getKey());
//                if (sensor != null) {
//                    sensorNames.add(sensor.getName());
//                }
//            }
//        }

        for (Sensor sensor : sensors) {
            if(DesiredSensorsList.shouldRecord(sensor.getType())) {
                sensorNames.add(sensor.getName());
            }
        }


        return sensorNames;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.e("Sensor service", "destroyed");
        //Toast.makeText(getApplicationContext(), "sensor", Toast.LENGTH_LONG).show();
        unregisterSensors();

    }

    // Supposing that your value is an integer declared somewhere as: int myInteger;
    private void sendMessage(long i) {
        // The string "my-integer" will be used to filer the intent
        Intent intent = new Intent("running_flag");
        // Adding some data
        intent.putExtra("message", i);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    //---------private classes--------------//
    private class MyTriggerEventListener extends TriggerEventListener {
        @Override
        public void onTrigger(TriggerEvent event) {
            Sensor sensor = event.sensor;
            try {
                // write timestamp
                outputStreams.get(sensor.getName())
                        .write(((event.timestamp / 1000000) + ",").getBytes());
                outputStreams.get(sensor.getName())
                        .write((System.currentTimeMillis() + ",").getBytes());
                // write sensor values
                float[] values = event.values;
                for (int i = 0; i < values.length; i++) {
                    outputStreams.get(sensor.getName()).write(Float.toString(values[i]).getBytes());
                    if (i != values.length - 1) {
                        outputStreams.get(sensor.getName()).write(", ".getBytes());
                    } else {
                        outputStreams.get(sensor.getName()).write("\n".getBytes());
                    }
                }
            } catch (IOException e) {
                Log.e("Error (DataCollectionService)",
                        "failure in writing into the file (onTrigger)");
                e.printStackTrace();
            }
        }
    }

    private class SensorEventLoggerTask extends AsyncTask<SensorEvent, Void, Void> {

        @Override
        protected Void doInBackground(SensorEvent... events) {
            SensorEvent event = events[0];
            Sensor sensor = event.sensor;
            try {
                // write timestamp
                outputStreams.get(sensor.getName())
                        .write(((event.timestamp / 1000000) + ",").getBytes());
                outputStreams.get(sensor.getName())
                        .write((System.currentTimeMillis() + ",").getBytes());

                // get sensor values
                float[] values;
                if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                    SensorManager.getRotationMatrixFromVector(
                            mRotationMatrix , event.values);
                    values = mRotationMatrix;
                } else {
                    values = event.values;
                }

                // write sensor values
                for (int i = 0; i < values.length; i++) {
                    outputStreams.get(sensor.getName()).write(Float.toString(values[i]).getBytes());
                    if (i != values.length - 1) {
                        outputStreams.get(sensor.getName()).write(", ".getBytes());
                    } else {
                        outputStreams.get(sensor.getName()).write("\n".getBytes());
                    }
                }
            } catch (IOException e) {
                Log.e("Error (DataCollectionService)",
                        "failure in writing into the file (onTrigger)");
                e.printStackTrace();
            }
            return null;
        }
    }
}
