package com.shreyaspawar.geoguide.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class LocationMapViewModel extends ViewModel {

    private final MutableLiveData<Boolean> isMeasurementMode = new MutableLiveData<>(false);
    private final MutableLiveData<Double> totalDistance = new MutableLiveData<>(0.0);
    private final List<LatLng> measurementPoints = new ArrayList<>();

    @Inject
    public LocationMapViewModel() {
    }

    public LiveData<Boolean> getIsMeasurementMode() {
        return isMeasurementMode;
    }

    public LiveData<Double> getTotalDistance() {
        return totalDistance;
    }

    public void toggleMeasurementMode() {
        isMeasurementMode.setValue(Boolean.FALSE.equals(isMeasurementMode.getValue()));
        if (Boolean.FALSE.equals(isMeasurementMode.getValue())) {
            clearMeasurements();
        }
    }

    public void addMeasurementPoint(LatLng latLng) {
        measurementPoints.add(latLng);
        calculateDistance();
    }

    public void clearMeasurements() {
        measurementPoints.clear();
        totalDistance.setValue(0.0);
    }

    private void calculateDistance() {
        if (measurementPoints.size() < 2) {
            totalDistance.setValue(0.0);
            return;
        }

        float totalDist = 0;
        for (int i = 0; i < measurementPoints.size() - 1; i++) {
            android.location.Location loc1 = new android.location.Location("");
            loc1.setLatitude(measurementPoints.get(i).latitude);
            loc1.setLongitude(measurementPoints.get(i).longitude);

            android.location.Location loc2 = new android.location.Location("");
            loc2.setLatitude(measurementPoints.get(i + 1).latitude);
            loc2.setLongitude(measurementPoints.get(i + 1).longitude);

            totalDist += loc1.distanceTo(loc2);
        }
        totalDistance.setValue((double) (totalDist / 1000.0));
    }
    
    public double calculateGravity(double latitude) {
        double g0 = 9.780327;
        double k = 0.00193185;
        double e2 = 0.00669438;
        double latRad = Math.toRadians(latitude);
        double sinLat = Math.sin(latRad);
        return g0 * (1 + k * sinLat * sinLat) / Math.sqrt(1 - e2 * sinLat * sinLat);
    }
}
