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
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private ActivityMainBinding binding;
    private SensorManager sensorManager;
    private Sensor accelerometer, magnetometer;
    private FusedLocationProviderClient fusedLocationProviderClient;
    private Location currentLocation;
    private float[] gravity;
    private float[] geomagnetic;
    private float currentDegree = 0f;

    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    getLocation();
                } else {
                    Log.e("Permission", "Location permission denied.");
                    binding.cityTV.setText("Location permission denied");
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

        // Check for location permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        } else {
            getLocation();
        }

        ImageButton infoButton = findViewById(R.id.infoButton);

        infoButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, infoActivity.class);
            startActivity(intent);
        });

        binding.cityTV.setOnClickListener(v -> {
            if (currentLocation != null) {
                Intent intentGoToMap = new Intent(MainActivity.this, LocationMap.class);
                intentGoToMap.putExtra("latitude", currentLocation.getLatitude());
                intentGoToMap.putExtra("longitude", currentLocation.getLongitude());
                startActivity(intentGoToMap);
            } else {
                Toast.makeText(MainActivity.this, "Fetching location. Please try again.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void initSensors() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }

    private void initLocationProvider() {
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
    }

    @SuppressLint("MissingPermission")
    private void getLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY)
                    .setIntervalMillis(5000)
                    .setMinUpdateIntervalMillis(1000)
                    .setMaxUpdates(1)
                    .build();

            fusedLocationProviderClient.requestLocationUpdates(locationRequest, new LocationCallback() {
                @Override
                public void onLocationResult(@NonNull LocationResult locationResult) {
                    if (locationResult != null && locationResult.getLastLocation() != null) {
                        currentLocation = locationResult.getLastLocation();
                        Log.d("LocationUpdate", "Latitude: " + currentLocation.getLatitude() +
                                ", Longitude: " + currentLocation.getLongitude());
                        updateLocationUI();
                    } else {
                        Log.e("LocationError", "Location is null.");
                        binding.cityTV.setText("Unable to fetch location.");
                    }
                }
            }, null);
        } else {
            Log.e("PermissionError", "Location permission is not granted.");
            binding.cityTV.setText("Location permission not granted.");
        }
    }

    private void updateLocationUI() {
        if (currentLocation == null) {
            Log.e("LocationError", "Location data is not available yet.");
            return;
        }

        binding.latitudeTV.setText(MessageFormat.format("Latitude: {0}", currentLocation.getLatitude()));
        binding.longitudeTV.setText(MessageFormat.format("Longitude: {0}", currentLocation.getLongitude()));
        double latitudeValue = currentLocation.getLatitude();
        double gravityValue = calculateGravity(latitudeValue);
        binding.trueHeadingTV.setText(MessageFormat.format("Gravity: {0}", gravityValue));
        getCityName(currentLocation);
    }

    private void getCityName(Location location) {
        if (location == null) {
            Log.e("LocationError", "No location available to fetch city.");
            return;
        }

        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
            if (addresses != null && !addresses.isEmpty()) {
                binding.cityTV.setText(addresses.get(0).getLocality());
            } else {
                binding.cityTV.setText("City not found");
            }
        } catch (IOException e) {
            Log.e("Geocoder", "Error getting city name", e);
            binding.cityTV.setText("Error fetching city");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
            sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event == null) return;

        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            gravity = event.values;
        } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            geomagnetic = event.values;
        }

        if (gravity != null && geomagnetic != null) {
            float[] R = new float[9];
            float[] I = new float[9];
            if (SensorManager.getRotationMatrix(R, I, gravity, geomagnetic)) {
                float[] orientation = new float[3];
                SensorManager.getOrientation(R, orientation);

                float declination = 0;
                if (currentLocation != null) {
                    GeomagneticField geomagneticField = new GeomagneticField(
                            (float) currentLocation.getLatitude(),
                            (float) currentLocation.getLongitude(),
                            (float) currentLocation.getAltitude(),
                            System.currentTimeMillis()
                    );
                    declination = geomagneticField.getDeclination();
                } else {
                    Log.e("CompassError", "Current location is null.");
                    return;
                }

                float azimuthInDegrees = (float) Math.toDegrees(orientation[0]);
                azimuthInDegrees = (azimuthInDegrees + 360) % 360; // Normalize to [0, 360]
                float trueNorth = (azimuthInDegrees + declination) % 360;

                float trueHeading = (azimuthInDegrees + declination + 360) % 360;

                // Update UI
                binding.headingTV.setText(MessageFormat.format("{0}°", Math.round(azimuthInDegrees)));
                binding.directionTV.setText(getDirection(azimuthInDegrees));

                // Compass animation
                RotateAnimation rotateAnimation = new RotateAnimation(
                        currentDegree,
                        trueHeading,
                        RotateAnimation.RELATIVE_TO_SELF, 0.5f,
                        RotateAnimation.RELATIVE_TO_SELF, 0.5f
                );
                currentDegree = trueHeading;

                rotateAnimation.setDuration(50);
                rotateAnimation.setFillAfter(true);

                binding.compassView.startAnimation(rotateAnimation);
                currentDegree = -azimuthInDegrees;
            }
        }
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

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // Handle changes in sensor accuracy if needed.
    }

    public static double calculateGravity(double latitudeValue) {
        double g0 = 9.780327; // Gravity at the equator (m/s^2)
        double k = 0.00193185138639; // Flattening coefficient
        double e2 = 0.00669437999013; // Eccentricity squared
        double sinLat = Math.sin(Math.toRadians(latitudeValue));

        return g0 * (1 + k * sinLat * sinLat) / Math.sqrt(1 - e2 * sinLat * sinLat);
    }
}
