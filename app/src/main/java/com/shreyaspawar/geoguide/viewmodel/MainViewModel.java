package com.shreyaspawar.geoguide.viewmodel;

import android.location.Location;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import javax.inject.Inject;
import dagger.hilt.android.lifecycle.HiltViewModel;

import com.shreyaspawar.geoguide.repository.SensorRepository;

@HiltViewModel
public class MainViewModel extends ViewModel {

    private final SensorRepository sensorRepository;
    private final MutableLiveData<Location> _currentLocation = new MutableLiveData<>();

    public LiveData<Location> getCurrentLocation() {
        return _currentLocation;
    }

    public LiveData<Float> getAzimuth() {
        return sensorRepository.getAzimuthData();
    }

    public LiveData<Float> getPressure() {
        return sensorRepository.getPressureData();
    }

    public LiveData<Integer> getSensorAccuracy() {
        return sensorRepository.getSensorAccuracy();
    }

    @Inject
    public MainViewModel(SensorRepository sensorRepository) {
        this.sensorRepository = sensorRepository;
    }

    public void resumeSensors() {
        sensorRepository.startListening();
    }

    public void pauseSensors() {
        sensorRepository.stopListening();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        sensorRepository.stopListening();
    }

    public void updateLocation(Location location) {
        _currentLocation.setValue(location);
    }
}
