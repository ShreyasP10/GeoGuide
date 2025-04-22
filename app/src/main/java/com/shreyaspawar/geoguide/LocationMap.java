package com.shreyaspawar.geoguide;

import android.content.Intent;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.shreyaspawar.geoguide.databinding.ActivityLocationMapBinding;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class LocationMap extends AppCompatActivity implements OnMapReadyCallback {

    private ActivityLocationMapBinding binding;
    private double latitude = 0.0;
    private double longitude = 0.0;

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
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        LatLng location = new LatLng(latitude, longitude);

        // Get city name and gravity
        String cityName = getCityName(latitude, longitude);
        double gravity = calculateGravity(latitude);

        // Format latitude, longitude, and gravity to 2 decimal places
        String latFormatted = String.format("%.2f", latitude);
        String lngFormatted = String.format("%.2f", longitude);
        String gravityFormatted = String.format("%.2f", gravity);

        // Debugging: Log values
        Log.d("MarkerDebug", "City: " + cityName + ", Lat: " + latFormatted + ", Lng: " + lngFormatted + ", Gravity: " + gravityFormatted);

        // Add marker with formatted values
        googleMap.addMarker(new MarkerOptions()
                        .position(location)
                        .title("Your Location")
                        .snippet("City: " + cityName + "  Lat: " + latFormatted + " Lng: " + lngFormatted + "  Gravity: " + gravityFormatted + " m/s²"))
                .showInfoWindow(); // Show info window immediately

        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 15));
        googleMap.setMapType(GoogleMap.MAP_TYPE_SATELLITE);
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
            e.printStackTrace();
        }
        return cityName;
    }
    private double calculateGravity(double latitude) {
        double g0 = 9.780327;  // Standard gravity at the equator (m/s²)
        double k = 0.00193185; // Constant factor
        double e2 = 0.00669438; // Eccentricity squared of Earth

        // Convert latitude to radians
        double latRad = Math.toRadians(latitude);

        // Gravity formula
        return g0 * (1 + k * Math.pow(Math.sin(latRad), 2)) / Math.sqrt(1 - e2 * Math.pow(Math.sin(latRad), 2));
    }



}