package com.shreyaspawar.geoguide.repository;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SensorRepository implements SensorEventListener {
    private final SensorManager sensorManager;
    private final MutableLiveData<Float> azimuthData = new MutableLiveData<>();
    private final MutableLiveData<Float> pressureData = new MutableLiveData<>();
    private final MutableLiveData<Integer> sensorAccuracyData = new MutableLiveData<>();

    private float[] mGravity = new float[3];
    private float[] mGeomagnetic = new float[3];
    private boolean hasGravity = false;
    private boolean hasGeomagnetic = false;
    private boolean useRotationVector = false;

    @Inject
    public SensorRepository(SensorManager sensorManager) {
        this.sensorManager = sensorManager;
    }

    public LiveData<Float> getAzimuthData() { return azimuthData; }
    public LiveData<Float> getPressureData() { return pressureData; }
    public LiveData<Integer> getSensorAccuracy() { return sensorAccuracyData; }

    public void startListening() {
        Sensor rv = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (rv != null) {
            sensorManager.registerListener(this, rv, SensorManager.SENSOR_DELAY_GAME);
            useRotationVector = true;
        } else {
            Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            Sensor mag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            if (accel != null) sensorManager.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME);
            if (mag != null) sensorManager.registerListener(this, mag, SensorManager.SENSOR_DELAY_GAME);
            useRotationVector = false;
        }
        
        Sensor press = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        if (press != null) sensorManager.registerListener(this, press, SensorManager.SENSOR_DELAY_GAME);
        
        Sensor magAccuracy = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (magAccuracy != null) sensorManager.registerListener(this, magAccuracy, SensorManager.SENSOR_DELAY_GAME);
    }

    public void stopListening() {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            float[] rotationMatrix = new float[9];
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            updateAzimuth(rotationMatrix);
        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            if (!useRotationVector) {
                System.arraycopy(event.values, 0, mGravity, 0, 3);
                hasGravity = true;
                calculateFallback();
            }
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            if (!useRotationVector) {
                System.arraycopy(event.values, 0, mGeomagnetic, 0, 3);
                hasGeomagnetic = true;
                calculateFallback();
            }
        } else if (event.sensor.getType() == Sensor.TYPE_PRESSURE) {
            pressureData.postValue(event.values[0]);
        }
    }

    private void calculateFallback() {
        if (hasGravity && hasGeomagnetic) {
            float[] R = new float[9];
            float[] I = new float[9];
            if (SensorManager.getRotationMatrix(R, I, mGravity, mGeomagnetic)) {
                updateAzimuth(R);
            }
        }
    }

    private void updateAzimuth(float[] rotationMatrix) {
        float[] orientation = new float[3];
        SensorManager.getOrientation(rotationMatrix, orientation);
        float azimuth = (float) Math.toDegrees(orientation[0]);
        azimuth = (azimuth + 360) % 360;
        azimuthData.postValue(azimuth);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            sensorAccuracyData.postValue(accuracy);
        }
    }
}
