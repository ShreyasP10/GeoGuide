package com.shreyaspawar.geoguide;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.shreyaspawar.geoguide.databinding.ActivityMainBinding;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private ActivityMainBinding binding;
    private SensorManager sensorManager;
    private Sensor accelerometer, magnetometer;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationCallback locationCallback;
    private Location currentLocation;
    private float[] gravity;
    private float[] geomagnetic;
    private float currentDegree = 0f;
    private RotateAnimation rotateAnimation;
    private static final float ALPHA = 0.15f;

    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    getLocation();
                } else {
                    binding.cityTV.setText("Location permission denied");
                    Toast.makeText(this, "Location permission required", Toast.LENGTH_LONG).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        initSensors();
        initLocationProvider();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            getLocation();
        } else {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        ImageButton infoButton = findViewById(R.id.infoButton);
        infoButton.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, infoActivity.class)));

        binding.cityTV.setOnClickListener(v -> {
            if (currentLocation != null) {
                Intent intent = new Intent(MainActivity.this, LocationMap.class);
                intent.putExtra("latitude", currentLocation.getLatitude());
                intent.putExtra("longitude", currentLocation.getLongitude());
                startActivity(intent);
            } else {
                Toast.makeText(this, "Fetching location...", Toast.LENGTH_SHORT).show();
            }
        });

        rotateAnimation = new RotateAnimation(
                0, 0,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );
        rotateAnimation.setDuration(250);
        rotateAnimation.setFillAfter(true);
    }

    private void initSensors() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        }
    }

    private void initLocationProvider() {
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
    }

    @SuppressLint("MissingPermission")
    private void getLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (locationResult == null) return;
                currentLocation = locationResult.getLastLocation();
                if (currentLocation != null) {
                    updateLocationUI();
                }
            }
        };

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY)
                .setIntervalMillis(5000)
                .setMinUpdateIntervalMillis(1000)
                .setMaxUpdates(3)
                .build();

        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null);
        fusedLocationProviderClient.getLastLocation().addOnSuccessListener(location -> {
            if (location != null) {
                currentLocation = location;
                updateLocationUI();
            }
        });
    }

    private void updateLocationUI() {
        if (currentLocation == null) return;

        binding.latitudeTV.setText(MessageFormat.format("Latitude: {0}", currentLocation.getLatitude()));
        binding.longitudeTV.setText(MessageFormat.format("Longitude: {0}", currentLocation.getLongitude()));
        double gravityValue = calculateGravity(currentLocation.getLatitude());
        binding.trueHeadingTV.setText(MessageFormat.format("Gravity: {0}", gravityValue));
        getCityName(currentLocation);
    }

    private void getCityName(Location location) {
        if (location == null || Geocoder.isPresent()) return;

        WeakReference<MainActivity> activityRef = new WeakReference<>(this);
        Executors.newSingleThreadExecutor().execute(() -> {
            Geocoder geocoder = new Geocoder(activityRef.get(), Locale.getDefault());
            try {
                List<Address> addresses = geocoder.getFromLocation(
                        location.getLatitude(), 
                        location.getLongitude(), 
                        1
                );
                if (activityRef.get() == null || activityRef.get().isFinishing()) return;
                
                runOnUiThread(() -> {
                    if (addresses != null && !addresses.isEmpty()) {
                        binding.cityTV.setText(addresses.get(0).getLocality());
                    } else {
                        binding.cityTV.setText("City not found");
                    }
                });
            } catch (IOException e) {
                runOnUiThread(() -> binding.cityTV.setText("Error fetching city"));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        if (fusedLocationProviderClient != null && locationCallback != null) {
            fusedLocationProviderClient.removeLocationUpdates(locationCallback);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event == null) return;

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            gravity = lowPass(event.values.clone(), gravity);
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            geomagnetic = lowPass(event.values.clone(), geomagnetic);
        }

        if (gravity != null && geomagnetic != null) {
            float[] rotationMatrix = new float[9];
            float[] inclinationMatrix = new float[9];

            if (SensorManager.getRotationMatrix(rotationMatrix, inclinationMatrix, gravity, geomagnetic)) {
                float[] orientation = new float[3];
                SensorManager.getOrientation(rotationMatrix, orientation);

                float azimuthInRadians = orientation[0];
                float azimuthInDegrees = (float) Math.toDegrees(azimuthInRadians);
                azimuthInDegrees = (azimuthInDegrees + 360) % 360;

                float declination = 0;
                if (currentLocation != null) {
                    GeomagneticField geomagneticField = new GeomagneticField(
                            (float) currentLocation.getLatitude(),
                            (float) currentLocation.getLongitude(),
                            (float) currentLocation.getAltitude(),
                            System.currentTimeMillis()
                    );
                    declination = geomagneticField.getDeclination();
                }

                float trueNorth = (azimuthInDegrees + declination + 360) % 360;

                binding.headingTV.setText(String.valueOf(Math.round(trueNorth)));
                binding.directionTV.setText(getDirection(trueNorth));

                if (rotateAnimation != null) {
                    rotateAnimation.cancel();
                }

                rotateAnimation = new RotateAnimation(
                        currentDegree,
                        -trueNorth,
                        Animation.RELATIVE_TO_SELF, 0.5f,
                        Animation.RELATIVE_TO_SELF, 0.5f
                );
                rotateAnimation.setDuration(250);
                rotateAnimation.setFillAfter(true);

                binding.compassView.startAnimation(rotateAnimation);
                currentDegree = -trueNorth;
            }
        }
    }

    private float[] lowPass(float[] input, float[] output) {

        if (output == null) return input;
        
        for (int i = 0; i < input.length; i++) {
            output[i] = output[i] + ALPHA * (input[i] - output[i]);
        }

        return output;
    }

    private String getDirection(float degree) {

        if (degree > 22.5 && degree <= 67.5) return "NE";
        if (degree > 67.5 && degree <= 112.5) return "E";
        if (degree > 112.5 && degree <= 157.5) return "SE";
        if (degree > 157.5 && degree <= 202.5) return "S";
        if (degree > 202.5 && degree <= 247.5) return "SW";
        if (degree > 247.5 && degree <= 292.5) return "W";
        if (degree > 292.5 && degree <= 337.5) return "NW";
        return "N";

    }

    public double calculateGravity(double latitudeValue) {
        double g0 = 9.780327;
        double k = 0.00193185138639;
        double e2 = 0.00669437999013;
        double sinLat = Math.sin(Math.toRadians(latitudeValue));
        return g0 * (1 + k * sinLat * sinLat) / Math.sqrt(1 - e2 * sinLat * sinLat);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}