package com.shreyaspawar.geoguide;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.shreyaspawar.geoguide.databinding.ActivityLocationMapBinding;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import dagger.hilt.android.AndroidEntryPoint;
import com.shreyaspawar.geoguide.viewmodel.LocationMapViewModel;
import androidx.lifecycle.ViewModelProvider;

@AndroidEntryPoint
public class LocationMap extends AppCompatActivity implements OnMapReadyCallback {

    private ActivityLocationMapBinding binding;
    private GoogleMap mMap;
    private double latitude = 0.0;
    private double longitude = 0.0;
    private LocationMapViewModel viewModel;
    
    // UI state for measurement (markers and lines still need to be handled by activity/fragment due to GoogleMap reference)
    private final List<Marker> measureMarkers = new ArrayList<>();
    private Polyline measureLine;

    // Waypoints
    private static final String PREFS_NAME = "GeoGuideWaypoints";
    private static final String KEY_WAYPOINTS = "waypoints";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityLocationMapBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        Intent intent = getIntent();
        if (intent != null) {
            latitude = intent.getDoubleExtra("latitude", 0.0);
            longitude = intent.getDoubleExtra("longitude", 0.0);
        }

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.gmap);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        viewModel = new ViewModelProvider(this).get(LocationMapViewModel.class);
        observeViewModel();
        setupUIListeners();
    }

    private void observeViewModel() {
        viewModel.getIsMeasurementMode().observe(this, active -> {
            if (active) {
                binding.distanceCard.setVisibility(View.VISIBLE);
                binding.fabMeasure.setColorFilter(Color.RED);
            } else {
                binding.fabMeasure.clearColorFilter();
                clearMeasurementUI();
            }
        });

        viewModel.getTotalDistance().observe(this, dist -> {
            binding.distanceText.setText(String.format(Locale.getDefault(), "Distance: %.2f km", dist));
        });
    }

    private void clearMeasurementUI() {
        for (Marker marker : measureMarkers) {
            marker.remove();
        }
        measureMarkers.clear();
        if (measureLine != null) {
            measureLine.remove();
        }
        binding.distanceCard.setVisibility(View.GONE);
    }

    private void setupUIListeners() {
        binding.btnNormal.setOnClickListener(v -> mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL));
        binding.btnSatellite.setOnClickListener(v -> mMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE));
        binding.btnTerrain.setOnClickListener(v -> mMap.setMapType(GoogleMap.MAP_TYPE_TERRAIN));
        binding.btnHybrid.setOnClickListener(v -> mMap.setMapType(GoogleMap.MAP_TYPE_HYBRID));

        binding.fabMeasure.setOnClickListener(v -> {
            viewModel.toggleMeasurementMode();
            if (Boolean.TRUE.equals(viewModel.getIsMeasurementMode().getValue())) {
                Toast.makeText(this, "Measurement Mode ON: Tap map to add points", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Measurement Mode OFF", Toast.LENGTH_SHORT).show();
            }
        });

        binding.fabClear.setOnClickListener(v -> {
            if (Boolean.TRUE.equals(viewModel.getIsMeasurementMode().getValue())) {
                viewModel.clearMeasurements();
                clearMeasurementUI();
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("Clear Map")
                        .setMessage("Do you want to clear measurements or all saved waypoints?")
                        .setPositiveButton("Measurements", (d, w) -> clearMeasurement())
                        .setNegativeButton("Waypoints", (d, w) -> clearWaypoints())
                        .setNeutralButton("Cancel", null)
                        .show();
            }
        });

        // Offline Cache Info
        binding.btnNormal.setOnLongClickListener(v -> {
            Toast.makeText(this, "Maps are automatically cached by Google Maps API for recent areas.", Toast.LENGTH_LONG).show();
            return true;
        });
    }

    private void clearMeasurement() {
        viewModel.clearMeasurements();
        clearMeasurementUI();
    }

    private void clearWaypoints() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        prefs.edit().remove(KEY_WAYPOINTS).apply();
        onMapReady(mMap); // Reload map
        Toast.makeText(this, "All waypoints cleared", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        mMap = googleMap;
        mMap.clear(); 
        
        LatLng location = new LatLng(latitude, longitude);

        String cityName = getCityName(latitude, longitude);
        double gravity = viewModel.calculateGravity(latitude);

        String latFormatted = String.format(Locale.getDefault(), "%.2f", latitude);
        String lngFormatted = String.format(Locale.getDefault(), "%.2f", longitude);
        String gravityFormatted = String.format(Locale.getDefault(), "%.2f", gravity);

        Marker marker = mMap.addMarker(new MarkerOptions()
                        .position(location)
                        .title("Your Location")
                        .snippet("City: " + cityName + "  Lat: " + latFormatted + " Lng: " + lngFormatted + "  Gravity: " + gravityFormatted + " m/s²")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
        if (marker != null) {
            marker.showInfoWindow();
        }

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 15));
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);
        mMap.getUiSettings().setMyLocationButtonEnabled(true);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        }

        loadWaypoints();

        mMap.setOnMapClickListener(latLng -> {
            if (Boolean.TRUE.equals(viewModel.getIsMeasurementMode().getValue())) {
                addMeasurementPoint(latLng);
            }
        });

        mMap.setOnMapLongClickListener(this::showSaveWaypointDialog);
        
        mMap.setOnInfoWindowClickListener(m -> {
            if (m.getTitle() != null && !m.getTitle().equals("Your Location")) {
                new AlertDialog.Builder(this)
                        .setTitle("Navigate to " + m.getTitle())
                        .setMessage("Start navigation to this waypoint?")
                        .setPositiveButton("Yes", (d, w) -> {
                            Uri gmmIntentUri = Uri.parse("google.navigation:q=" + m.getPosition().latitude + "," + m.getPosition().longitude);
                            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
                            mapIntent.setPackage("com.google.android.apps.maps");
                            startActivity(mapIntent);
                        })
                        .setNegativeButton("No", null)
                        .show();
            } else {
                showGravityInsights(m.getPosition().latitude);
            }
        });
    }

    private void showSaveWaypointDialog(LatLng latLng) {
        EditText input = new EditText(this);
        input.setHint("e.g., Camp Base");
        new AlertDialog.Builder(this)
                .setTitle("Save Waypoint")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) name = "Waypoint";
                    saveWaypoint(name, latLng);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveWaypoint(String name, LatLng latLng) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Set<String> waypoints = prefs.getStringSet(KEY_WAYPOINTS, new HashSet<>());
        Set<String> newWaypoints = new HashSet<>(waypoints);
        newWaypoints.add(name + "|" + latLng.latitude + "|" + latLng.longitude);
        prefs.edit().putStringSet(KEY_WAYPOINTS, newWaypoints).apply();
        
        mMap.addMarker(new MarkerOptions()
                .position(latLng)
                .title(name)
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
        
        Toast.makeText(this, "Waypoint saved: " + name, Toast.LENGTH_SHORT).show();
    }

    private void loadWaypoints() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        Set<String> waypoints = prefs.getStringSet(KEY_WAYPOINTS, new HashSet<>());
        for (String wp : waypoints) {
            String[] parts = wp.split("\\|");
            if (parts.length == 3) {
                String name = parts[0];
                double lat = Double.parseDouble(parts[1]);
                double lon = Double.parseDouble(parts[2]);
                mMap.addMarker(new MarkerOptions()
                        .position(new LatLng(lat, lon))
                        .title(name)
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
            }
        }
    }

    private void addMeasurementPoint(LatLng latLng) {
        viewModel.addMeasurementPoint(latLng);
        Marker marker = mMap.addMarker(new MarkerOptions().position(latLng).title("Point " + (measureMarkers.size() + 1)));
        if (marker != null) {
            measureMarkers.add(marker);
        }

        if (measureMarkers.size() > 1) {
            updatePolyline();
        }
    }

    private void updatePolyline() {
        if (measureLine != null) {
            measureLine.remove();
        }
        PolylineOptions options = new PolylineOptions().color(Color.RED).width(5);
        for (Marker marker : measureMarkers) {
            options.add(marker.getPosition());
        }
        measureLine = mMap.addPolyline(options);
    }

    private String getCityName(double lat, double lng) {
        String cityName = "Unknown City";
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
            if (addresses != null && !addresses.isEmpty()) {
                cityName = addresses.get(0).getLocality(); // City name
            }
        } catch (IOException e) {
            Log.e("Geocoder", "Error getting city name", e);
        }
        return cityName;
    }

    private void showGravityInsights(double lat) {
        double currentG = viewModel.calculateGravity(lat);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.getDefault(), "Current Local Gravity: %.4f m/s²\n\n", currentG));
        sb.append("The Physics:\n");
        sb.append("Gravity isn't 9.8 everywhere! Earth is an 'oblate spheroid'—fatter at the equator and flatter at the poles. ");
        sb.append("This means you're further from the center at the equator.\n\n");

        sb.append("Why it varies:\n");
        sb.append("1. Centrifugal Force: Pushes you 'out' at the equator, reducing gravity.\n");
        sb.append("2. Shape: The poles are closer to Earth's core.\n\n");

        sb.append("Comparisons:\n");
        sb.append("• Equator: 9.7803 m/s² (Weakest)\n");
        sb.append("• This Location: ").append(String.format(Locale.getDefault(), "%.4f", currentG)).append(" m/s²\n");
        sb.append("• North Pole: 9.8322 m/s² (Strongest)\n");
        sb.append("• Mt. Everest: ~9.7640 m/s² (Altitude effect)\n\n");

        sb.append("Weak ← [ g ] → Strong\n");
        sb.append("Visual: ");
        int progress = (int) ((currentG - 9.78) / (9.832 - 9.78) * 10);
        progress = Math.max(0, Math.min(10, progress));
        for(int i=0; i<10; i++) sb.append(i <= progress ? "■" : "□");

        new AlertDialog.Builder(this)
                .setTitle("Gravity Insights")
                .setMessage(sb.toString())
                .setPositiveButton("Fascinating!", null)
                .show();
    }
}
