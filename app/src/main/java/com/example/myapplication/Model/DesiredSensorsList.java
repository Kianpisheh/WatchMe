package com.example.myapplication.Model;

import android.hardware.Sensor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class DesiredSensorsList {

    private static List<Integer> sensorsList = new ArrayList<>(Arrays.asList(
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_GRAVITY,
            Sensor.TYPE_ROTATION_VECTOR,
            Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_PRESSURE, Sensor.TYPE_STEP_DETECTOR, Sensor.TYPE_STEP_COUNTER,
            Sensor.TYPE_LINEAR_ACCELERATION, Sensor.TYPE_GAME_ROTATION_VECTOR));

    public static boolean shouldRecord(int sensorTYpe) {
        return sensorsList.contains(sensorTYpe);
    }

    public static List<Integer> getSensorsList() {
        return sensorsList;
    }

}
