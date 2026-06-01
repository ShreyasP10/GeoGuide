package com.shreyaspawar.geoguide.di;

import android.content.Context;
import android.hardware.SensorManager;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

import javax.inject.Singleton;

@Module
@InstallIn(SingletonComponent.class)
public class SensorModule {

    @Provides
    @Singleton
    public static SensorManager provideSensorManager(@ApplicationContext Context context) {
        return (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
    }
}
