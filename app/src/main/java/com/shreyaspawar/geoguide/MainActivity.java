package com.shreyaspawar.geoguide;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.hardware.GeomagneticField;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
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
import com.google.android.gms.location.Priority;
import com.shreyaspawar.geoguide.databinding.ActivityMainBinding;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;
import com.shreyaspawar.geoguide.viewmodel.MainViewModel;
import androidx.lifecycle.ViewModelProvider;

@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    @Inject
    SensorManager sensorManager;
    @Inject
    FusedLocationProviderClient fusedLocationProviderClient;
    private MainViewModel viewModel;
    private Location currentLocation;
    private boolean isTrueNorth = true;
    private int sensorAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE;

    private SpringAnimation compassAnimation;

    private boolean isFlashlightOn = false;
    private boolean isStrobeOn = false;
    private Handler strobeHandler = new Handler(Looper.getMainLooper());
    private String cameraId;
    private CameraManager cameraManager;

    // FIX 1: store last azimuth to force update on north‑type toggle
    private float lastAzimuth = 0f;
    private boolean isInitialRotationSet = false;

    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    getLocation();
                } else {
                    Log.e("Permission", "Location permission denied.");
                    binding.cityTV.setText("Location permission denied");
                    Toast.makeText(this, "Location permission is required for this app to function.", Toast.LENGTH_LONG).show();
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("MainActivity", "onCreate started");

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        // REMOVED: AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        observeViewModel();

        // initSensors() is no longer needed – barometer handled by repository
        initCompassAnimation();

        setSupportActionBar(binding.toolbar);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        } else {
            getLocation();
        }

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

        binding.trueHeadingTV.setOnClickListener(v -> showGravityInsights());
        binding.directionTV.setOnClickListener(v -> {
            isTrueNorth = !isTrueNorth;
            Toast.makeText(this, isTrueNorth ? "True North Mode" : "Magnetic North Mode", Toast.LENGTH_SHORT).show();
            // FIX 2: force an immediate update with the last known azimuth
            updateCompass(lastAzimuth);
        });

        binding.sosFab.setOnClickListener(v -> shareSOSLocation());

        initFlashlight();
        binding.flashlightFab.setOnClickListener(v -> toggleFlashlight());
        binding.flashlightFab.setOnLongClickListener(v -> {
            toggleStrobe();
            return true;
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_themes) {
            showThemeDialog();
            return true;
        } else if (id == R.id.action_info) {
            Intent intent = new Intent(MainActivity.this, InfoActivity.class);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initFlashlight() {
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraId = cameraManager.getCameraIdList()[0];
        } catch (CameraAccessException | ArrayIndexOutOfBoundsException e) {
            Log.e("Flashlight", "Camera not accessible or no camera found", e);
        }
    }

    private void toggleFlashlight() {
        if (cameraId == null) return;
        try {
            isFlashlightOn = !isFlashlightOn;
            isStrobeOn = false;
            strobeHandler.removeCallbacksAndMessages(null);
            cameraManager.setTorchMode(cameraId, isFlashlightOn);
            updateFlashlightUI();
        } catch (CameraAccessException e) {
            Log.e("Flashlight", "Error toggling flashlight", e);
        }
    }

    private void toggleStrobe() {
        if (cameraId == null) return;
        isStrobeOn = !isStrobeOn;
        isFlashlightOn = false;
        if (isStrobeOn) {
            startStrobe();
        } else {
            strobeHandler.removeCallbacksAndMessages(null);
            try {
                cameraManager.setTorchMode(cameraId, false);
            } catch (CameraAccessException e) {
                Log.e("Flashlight", "Error turning off torch", e);
            }
        }
        updateFlashlightUI();
    }

    private void startStrobe() {
        strobeHandler.post(new Runnable() {
            private boolean torchState = false;
            @Override
            public void run() {
                if (!isStrobeOn) return;
                try {
                    torchState = !torchState;
                    cameraManager.setTorchMode(cameraId, torchState);
                    strobeHandler.postDelayed(this, 100); // 10Hz strobe
                } catch (CameraAccessException e) {
                    Log.e("Flashlight", "Strobe error", e);
                }
            }
        });
    }

    private void updateFlashlightUI() {
        if (isStrobeOn) {
            binding.flashlightFab.setImageResource(android.R.drawable.stat_notify_sync);
            Toast.makeText(this, "Strobe Mode Active", Toast.LENGTH_SHORT).show();
        } else if (isFlashlightOn) {
            binding.flashlightFab.setImageResource(android.R.drawable.btn_star_big_on);
            Toast.makeText(this, "Flashlight On", Toast.LENGTH_SHORT).show();
        } else {
            binding.flashlightFab.setImageResource(android.R.drawable.ic_menu_compass);
            Toast.makeText(this, "Flashlight Off", Toast.LENGTH_SHORT).show();
        }
    }

    private void initCompassAnimation() {
        compassAnimation = new SpringAnimation(binding.compassView, SpringAnimation.ROTATION);
        SpringForce springForce = new SpringForce();
        springForce.setStiffness(SpringForce.STIFFNESS_HIGH); // Snappier response
        springForce.setDampingRatio(SpringForce.DAMPING_RATIO_LOW_BOUNCY); // Less wobble
        compassAnimation.setSpring(springForce);
    }

    private void showThemeDialog() {
        String[] themes = {"Default", "Military", "Minimal", "Hiking", "Scientific"};
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Select Compass Theme")
                .setItems(themes, (dialog, which) -> {
                    applyTheme(themes[which]);
                })
                .show();
    }

    private void applyTheme(String theme) {
        Toast.makeText(this, "Theme: " + theme, Toast.LENGTH_SHORT).show();
        int colorOnSurface = ContextCompat.getColor(this, android.R.color.white);
        
        switch (theme) {
            case "Default":
                binding.compassView.setImageResource(R.drawable.compass);
                binding.compassView.setColorFilter(null);
                break;
            case "Military":
                binding.compassView.setImageResource(R.drawable.compass_military);
                binding.compassView.setColorFilter(null);
                break;
            case "Minimal":
                binding.compassView.setImageResource(R.drawable.compass_minimal);
                binding.compassView.setColorFilter(colorOnSurface);
                break;
            case "Hiking":
                binding.compassView.setImageResource(R.drawable.compass_hiking);
                binding.compassView.setColorFilter(null);
                break;
            case "Scientific":
                binding.compassView.setImageResource(R.drawable.compass_scientific);
                binding.compassView.setColorFilter(colorOnSurface);
                break;
            default:
                binding.compassView.setImageResource(R.drawable.compass);
                binding.compassView.setColorFilter(null);
                break;
        }
        
        // Ensure the new drawable immediately reflects the current heading
        updateCompass(lastAzimuth);
    }

    private void shareSOSLocation() {
        if (currentLocation == null) {
            Toast.makeText(this, "Location not available yet. Please wait.", Toast.LENGTH_SHORT).show();
            return;
        }

        double lat = currentLocation.getLatitude();
        double lon = currentLocation.getLongitude();
        double alt = currentLocation.getAltitude();
        float acc = currentLocation.getAccuracy();
        long time = currentLocation.getTime();
        String timestamp = new java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new java.util.Date(time));

        String mapsUrl = "https://www.google.com/maps/search/?api=1&query=" + lat + "," + lon;

        String message = String.format(Locale.getDefault(),
                "🚨 EMERGENCY SOS - GeoGuide 🚨\n\n" +
                        "My current location (as of %s):\n" +
                        "Lat: %.6f\n" +
                        "Lng: %.6f\n" +
                        "Alt: %.1fm\n" +
                        "Accuracy: ±%.1fm\n\n" +
                        "Google Maps: %s",
                timestamp, lat, lon, alt, acc, mapsUrl);

        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, message);
        sendIntent.setType("text/plain");

        Intent shareIntent = Intent.createChooser(sendIntent, "Share SOS via");
        startActivity(shareIntent);
    }

    private void showGravityInsights() {
        if (currentLocation == null) return;

        double lat = currentLocation.getLatitude();
        double currentG = calculateGravity(lat);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.getDefault(), "Current Local Gravity: %.4f m/s²\n\n", currentG));
        sb.append("The Physics:\n");
        sb.append("Gravity isn't 9.8 everywhere! It changes because Earth isn't a perfect sphere. ");
        sb.append("It's an 'oblate spheroid'—fatter at the equator and flatter at the poles.\n\n");

        sb.append("Why it varies:\n");
        sb.append("1. Centrifugal Force: Strongest at the equator, pushing you 'out' and slightly reducing gravity.\n");
        sb.append("2. Distance: You're further from Earth's center at the Equator than at the Poles.\n\n");

        sb.append("Global Comparisons:\n");
        sb.append("• Equator: 9.7803 m/s² (Weakest)\n");
        sb.append("• Your Location: ").append(String.format(Locale.getDefault(), "%.4f", currentG)).append(" m/s²\n");
        sb.append("• North Pole: 9.8322 m/s² (Strongest)\n");
        sb.append("• Mt. Everest: ~9.7640 m/s² (Altitude effect)\n\n");

        sb.append("Weak ← [ g ] → Strong\n");
        sb.append("Visual: ");
        int progress = (int) ((currentG - 9.78) / (9.832 - 9.78) * 10);
        progress = Math.max(0, Math.min(10, progress));
        for(int i=0; i<10; i++) sb.append(i <= progress ? "■" : "□");
        sb.append("\n\nFormula: International Gravity Formula");

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Gravity Insights")
                .setMessage(sb.toString())
                .setPositiveButton("Cool!", null)
                .show();
    }

    private void observeViewModel() {
        viewModel.getCurrentLocation().observe(this, location -> {
            currentLocation = location;
            updateLocationUI();
        });

        viewModel.getPressure().observe(this, pressure -> {
            binding.pressureTV.setText(MessageFormat.format("Pres: {0} hPa", Math.round(pressure)));
        });

        viewModel.getAzimuth().observe(this, azimuth -> {
            Log.d("MainActivity", "Azimuth observed: " + azimuth);
            lastAzimuth = azimuth;   // FIX 4: store last known azimuth
            updateCompass(azimuth);
        });

        viewModel.getSensorAccuracy().observe(this, accuracy -> {
            sensorAccuracy = accuracy;
            updateAccuracyUI();
            if (accuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
                binding.calibrationOverlay.setVisibility(android.view.View.VISIBLE);
                binding.closeCalibrationBtn.setOnClickListener(v ->
                        binding.calibrationOverlay.setVisibility(android.view.View.GONE));
            } else {
                binding.calibrationOverlay.setVisibility(android.view.View.GONE);
            }
        });
    }

    private void updateCompass(float azimuth) {
        if (binding.compassView == null) return;

        Log.d("MainActivity", "updateCompass input: " + azimuth);
        float finalHeading = azimuth;

        if (isTrueNorth && currentLocation != null) {
            GeomagneticField geomagneticField = new GeomagneticField(
                    (float) currentLocation.getLatitude(),
                    (float) currentLocation.getLongitude(),
                    (float) currentLocation.getAltitude(),
                    System.currentTimeMillis()
            );
            float declination = geomagneticField.getDeclination();
            finalHeading = (azimuth + declination + 360) % 360;
        }

        binding.headingTV.setText(String.valueOf(Math.round(finalHeading)));
        binding.directionTV.setText(MessageFormat.format("{0} ({1})",
                getDirection(finalHeading), isTrueNorth ? "True" : "Mag"));

        // Spring Animation
        float rotation = -finalHeading;

        // Jump to position on first update to avoid 0 -> heading sweep
        if (!isInitialRotationSet) {
            binding.compassView.setRotation(rotation);
            isInitialRotationSet = true;
            return;
        }

        float currentRotation = binding.compassView.getRotation();
        float diff = rotation - currentRotation;
        while (diff > 180) diff -= 360;
        while (diff < -180) diff += 360;

        float target = currentRotation + diff;
        Log.d("MainActivity", "Compass rotation target: " + target);

        // Use post to ensure view is attached/laid out
        binding.compassView.post(() -> {
            if (compassAnimation != null) {
                if (compassAnimation.isRunning()) {
                    // Avoid jumpy behavior by not canceling if the target is very close
                    // But for responsiveness, we usually want to update the target
                }
                compassAnimation.animateToFinalPosition(target);
            }
        });
    }

    @SuppressLint("MissingPermission")
    private void getLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY)
                    .setIntervalMillis(5000)
                    .setMinUpdateIntervalMillis(1000)
                    .setMaxUpdates(3)
                    .build();

            fusedLocationProviderClient.requestLocationUpdates(locationRequest, new LocationCallback() {
                @Override
                public void onLocationResult(@NonNull LocationResult locationResult) {
                    if (locationResult != null && locationResult.getLastLocation() != null) {
                        currentLocation = locationResult.getLastLocation();
                        viewModel.updateLocation(currentLocation);
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

        binding.latitudeTV.setText(MessageFormat.format("Lat: {0}", String.format(Locale.getDefault(), "%.4f", currentLocation.getLatitude())));
        binding.longitudeTV.setText(MessageFormat.format("Lng: {0}", String.format(Locale.getDefault(), "%.4f", currentLocation.getLongitude())));

        double altitude = currentLocation.getAltitude();
        float speed = currentLocation.getSpeed() * 3.6f;

        binding.altitudeTV.setText(MessageFormat.format("Alt: {0}m", Math.round(altitude)));
        binding.speedTV.setText(MessageFormat.format("Speed: {0} km/h", Math.round(speed)));

        double latitudeValue = currentLocation.getLatitude();
        double gravityValue = calculateGravity(latitudeValue);
        binding.trueHeadingTV.setText(MessageFormat.format("Gravity: {0}", String.format(Locale.getDefault(), "%.4f", gravityValue)));

        getCityName(currentLocation);
        updateOutdoorInsights();
    }

    private void updateOutdoorInsights() {
        if (currentLocation == null) return;

        // Solar insights (Approximate)
        int dayOfYear = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_YEAR);
        double latRad = Math.toRadians(currentLocation.getLatitude());
        double declination = 23.45 * Math.sin(Math.toRadians(360.0/365.0 * (dayOfYear - 81)));
        double cosHourAngle = -Math.tan(latRad) * Math.tan(Math.toRadians(declination));

        String sunInfo = "Loading...";
        if (cosHourAngle >= -1 && cosHourAngle <= 1) {
            double hourAngle = Math.toDegrees(Math.acos(cosHourAngle));
            double sunrise = 12.0 - (hourAngle / 15.0);
            double sunset = 12.0 + (hourAngle / 15.0);
            sunInfo = String.format(Locale.getDefault(), "Rise: %02d:%02d\nSet: %02d:%02d",
                    (int)sunrise, (int)((sunrise%1)*60), (int)sunset, (int)((sunset%1)*60));
        }
        binding.sunTV.setText(sunInfo);

        // Moon Phase (Approximate 29.53 days cycle)
        long now = System.currentTimeMillis();
        long newMoonRef = 947163600000L; // Jan 6, 2000
        double phase = ((now - newMoonRef) / 1000.0 / 86400.0) % 29.530588853;
        String moonPhase;
        if (phase < 1.84) moonPhase = "New";
        else if (phase < 5.53) moonPhase = "Waxing Cres";
        else if (phase < 9.22) moonPhase = "First Qtr";
        else if (phase < 12.91) moonPhase = "Waxing Gibb";
        else if (phase < 16.61) moonPhase = "Full";
        else if (phase < 20.30) moonPhase = "Waning Gibb";
        else if (phase < 23.99) moonPhase = "Last Qtr";
        else if (phase < 27.68) moonPhase = "Waning Cres";
        else moonPhase = "New";
        binding.moonTV.setText(moonPhase);

        binding.weatherTV.setText("Tap for details");
        binding.weatherContainer.setOnClickListener(v -> showWeatherDialog());
    }

    private void showWeatherDialog() {
        if (currentLocation == null) return;
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Outdoor Conditions")
                .setMessage("Environment Metrics:\n\n" +
                        "• Pressure: " + binding.pressureTV.getText() + "\n" +
                        "• Altitude: " + binding.altitudeTV.getText() + "\n" +
                        "• Local Gravity: " + binding.trueHeadingTV.getText() + "\n\n" +
                        "Tip: Significant pressure drops often precede stormy weather.")
                .setPositiveButton("Got it", null)
                .show();
    }

    private void getCityName(Location location) {
        if (location == null) {
            Log.e("LocationError", "No location available to fetch city.");
            return;
        }

        if (!Geocoder.isPresent()) {
            binding.cityTV.setText("Geocoder not available");
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            try {
                List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                runOnUiThread(() -> {
                    if (addresses != null && !addresses.isEmpty()) {
                        binding.cityTV.setText(addresses.get(0).getLocality());
                    } else {
                        binding.cityTV.setText("City not found");
                    }
                });
            } catch (IOException e) {
                Log.e("Geocoder", "Error getting city name", e);
                runOnUiThread(() -> binding.cityTV.setText("Error fetching city"));
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (viewModel != null) {
            viewModel.resumeSensors();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (viewModel != null) {
            viewModel.pauseSensors();
        }
        if (isFlashlightOn || isStrobeOn) {
            try {
                isFlashlightOn = false;
                isStrobeOn = false;
                strobeHandler.removeCallbacksAndMessages(null);
                if (cameraId != null) {
                    cameraManager.setTorchMode(cameraId, false);
                }
            } catch (CameraAccessException e) {
                Log.e("Flashlight", "Error turning off torch on pause", e);
            }
        }
    }

    // Unused SensorEventListener methods removed – sensor logic is in the ViewModel/Repository

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

    private void updateAccuracyUI() {
        String accuracyStr;
        int color;
        switch (sensorAccuracy) {
            case SensorManager.SENSOR_STATUS_ACCURACY_HIGH:
                accuracyStr = "Accuracy: High";
                color = ContextCompat.getColor(this, android.R.color.holo_green_dark);
                break;
            case SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM:
                accuracyStr = "Accuracy: Medium";
                color = ContextCompat.getColor(this, android.R.color.holo_orange_dark);
                break;
            case SensorManager.SENSOR_STATUS_ACCURACY_LOW:
                accuracyStr = "Accuracy: Low - Move phone in ∞";
                color = ContextCompat.getColor(this, android.R.color.holo_red_dark);
                break;
            default:
                accuracyStr = "Accuracy: Unreliable - Calibrate!";
                color = ContextCompat.getColor(this, android.R.color.holo_red_light);
                break;
        }
        binding.accuracyTV.setText(accuracyStr);
        binding.accuracyTV.setTextColor(color);
    }
}