package com.example.freezer;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.freezer.adapter.HourlyForecastAdapter;
import com.example.freezer.api.RetrofitClient;
import com.example.freezer.api.WeatherAPI;
import com.example.freezer.model.ForecastResponse;
import com.example.freezer.service.LocationService;
import com.example.freezer.workers.EveningFreezeCheckWorker;
import com.example.freezer.workers.MorningUmbrellaCheckWorker;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.concurrent.TimeUnit;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private TextView locationText, currentTempText, weatherDescriptionText;
    private RecyclerView hourlyForecastRecyclerView;
    private ProgressBar progressBar;
    private Button retryButton;
    private Switch freezeAlertToggle, umbrellaAlertToggle;

    private LocationService locationService;
    private WeatherAPI weatherAPI;
    private HourlyForecastAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupRecyclerView();
        initializeServices();

        requestLocationPermission();
    }

    private void initializeViews() {
        locationText = findViewById(R.id.locationText);
        currentTempText = findViewById(R.id.currentTempText);
        weatherDescriptionText = findViewById(R.id.weatherDescriptionText);
        hourlyForecastRecyclerView = findViewById(R.id.hourlyForecastRecyclerView);
        progressBar = findViewById(R.id.progressBar);
        retryButton = findViewById(R.id.retryButton);
        freezeAlertToggle = findViewById(R.id.freezeAlertToggle);
        umbrellaAlertToggle = findViewById(R.id.umbrellaAlertToggle);

        // Load saved toggle state
        SharedPreferences prefs = getSharedPreferences("weather_prefs", MODE_PRIVATE);
        boolean freezeAlertsEnabled = prefs.getBoolean("freeze_alerts_enabled", false);
        boolean umbrellaAlertsEnabled = prefs.getBoolean("umbrella_alerts_enabled", false);
        freezeAlertToggle.setChecked(freezeAlertsEnabled);
        umbrellaAlertToggle.setChecked(umbrellaAlertsEnabled);

        // Save toggle state when changed
        freezeAlertToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = getSharedPreferences("weather_prefs", MODE_PRIVATE).edit();
            editor.putBoolean("freeze_alerts_enabled", isChecked);
            editor.apply();

            if (isChecked) {
                Toast.makeText(MainActivity.this, "Freeze alerts enabled", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, "Freeze alerts disabled", Toast.LENGTH_SHORT).show();
                // Cancel any scheduled work if user turns off alerts
                WorkManager.getInstance(this).cancelUniqueWork("dailyEveningFreezeCheck");
            }
        });

        umbrellaAlertToggle.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = getSharedPreferences("weather_prefs", MODE_PRIVATE).edit();
            editor.putBoolean("umbrella_alerts_enabled", isChecked);
            editor.apply();

            if (isChecked) {
                Toast.makeText(MainActivity.this, "Umbrella alerts enabled", Toast.LENGTH_SHORT).show();
                scheduleMorningUmbrellaCheck();
            } else {
                Toast.makeText(MainActivity.this, "Umbrella alerts disabled", Toast.LENGTH_SHORT).show();
                WorkManager.getInstance(this).cancelUniqueWork("morningUmbrellaCheck");
            }
        });

        retryButton.setOnClickListener(v -> {
            retryButton.setVisibility(View.GONE);
            fetchWeatherData();
        });
    }

    private void setupRecyclerView() {
        hourlyForecastRecyclerView.setLayoutManager(new LinearLayoutManager(
                this, LinearLayoutManager.HORIZONTAL, false));
        adapter = new HourlyForecastAdapter(new ArrayList<>());
        hourlyForecastRecyclerView.setAdapter(adapter);
    }

    private void initializeServices() {
        locationService = new LocationService(this);
        weatherAPI = RetrofitClient.getClient().create(WeatherAPI.class);
    }

    private void requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
        } else {
            fetchWeatherData();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchWeatherData();
            } else {
                locationText.setText("Location permission denied");
                retryButton.setVisibility(View.VISIBLE);
            }
        }
    }

    private void fetchWeatherData() {
        progressBar.setVisibility(View.VISIBLE);

        locationService.getCurrentLocation(new LocationService.LocationListener() {
            @Override
            public void onLocationReceived(double lat, double lon) {
                getWeatherForecast(lat, lon);
            }

            @Override
            public void onLocationError(String error) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    locationText.setText("Error getting location: " + error);
                    retryButton.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void getWeatherForecast(double lat, double lon) {
        Call<ForecastResponse> call = weatherAPI.getForecastByCoords(
                lat, lon, BuildConfig.WEATHER_API_KEY, "imperial");

        call.enqueue(new Callback<ForecastResponse>() {
            @Override
            public void onResponse(Call<ForecastResponse> call, Response<ForecastResponse> response) {
                progressBar.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    updateUI(response.body());
                    scheduleEveningFreezeCheck(lat, lon);
                } else {
                    locationText.setText("Error fetching weather data");
                    retryButton.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(Call<ForecastResponse> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                locationText.setText("Network error");
                retryButton.setVisibility(View.VISIBLE);
            }
        });
    }

    private void updateUI(ForecastResponse forecastResponse) {
        // Update location
        String location = forecastResponse.getCity().getName() + ", " + forecastResponse.getCity().getCountry();
        locationText.setText(location);

        // Update current weather (first forecast item)
        if (!forecastResponse.getList().isEmpty()) {
            ForecastResponse.Forecast current = forecastResponse.getList().get(0);
            currentTempText.setText(String.format("%.0f°F", current.getMain().getTemp()));

            if (current.getWeather() != null && !current.getWeather().isEmpty()) {
                weatherDescriptionText.setText(current.getWeather().get(0).getDescription());
            }
        }

        // Need 9 items for 24hr forecast (24-27 hours bc OpenWeather fetches 3hr cycles)
        List<ForecastResponse.Forecast> next24Hours = forecastResponse.getList().subList(
                0, Math.min(9, forecastResponse.getList().size()));
        adapter = new HourlyForecastAdapter(next24Hours);
        hourlyForecastRecyclerView.setAdapter(adapter);
    }

    private void scheduleEveningFreezeCheck(double lat, double lon) {
        // Save coordinates for the background worker
        SharedPreferences prefs = getSharedPreferences("weather_prefs", MODE_PRIVATE);
        boolean alertsEnabled = prefs.getBoolean("freeze_alerts_enabled", true);

        if (!alertsEnabled) {
            Log.d("FreezeCheck", "Alerts disabled, not scheduling check");
            return;
        }
        prefs.edit()
                .putFloat("last_lat", (float) lat)
                .putFloat("last_lon", (float) lon)
                .apply();

        // 6 PM check
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 18);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        if (Calendar.getInstance().after(calendar)) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        long initialDelay = calendar.getTimeInMillis() - System.currentTimeMillis();

        PeriodicWorkRequest dailyCheck = new PeriodicWorkRequest.Builder(
                EveningFreezeCheckWorker.class, 24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "dailyEveningFreezeCheck",
                ExistingPeriodicWorkPolicy.REPLACE,
                dailyCheck);
    }

    private void scheduleMorningUmbrellaCheck() {
        SharedPreferences prefs = getSharedPreferences("weather_prefs", MODE_PRIVATE);
        boolean alertsEnabled = prefs.getBoolean("umbrella_alerts_enabled", false);

        if (!alertsEnabled) {
            return;
        }

        // 7 AM check
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 7);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        if (Calendar.getInstance().after(calendar)) {
            calendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        long initialDelay = calendar.getTimeInMillis() - System.currentTimeMillis();

        PeriodicWorkRequest morningCheck = new PeriodicWorkRequest.Builder(
                MorningUmbrellaCheckWorker.class, 24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build();

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "morningUmbrellaCheck",
                ExistingPeriodicWorkPolicy.REPLACE,
                morningCheck);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        locationService.stopLocationUpdates();
    }
}