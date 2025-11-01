package com.example.freezer.service;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;

import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;

public class LocationService {
    private Context context;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    public LocationService(Context context) {
        this.context = context;
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
    }

    public interface LocationListener {
        void onLocationReceived(double lat, double lon);
        void onLocationError(String error);
    }

    public void getCurrentLocation(LocationListener listener) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            listener.onLocationError("Location permission not granted");
            return;
        }

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    listener.onLocationError("Location result is null");
                    return;
                }

                Location location = locationResult.getLastLocation();
                if (location != null) {
                    listener.onLocationReceived(location.getLatitude(), location.getLongitude());
                } else {
                    listener.onLocationError("Location is null");
                }

                // Stop location updates after getting one result
                fusedLocationClient.removeLocationUpdates(locationCallback);
            }
        };

        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(10000)
                .setFastestInterval(5000)
                .setNumUpdates(1);

        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
    }

    public void stopLocationUpdates() {
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }
}