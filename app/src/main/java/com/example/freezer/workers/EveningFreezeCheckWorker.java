package com.example.freezer.workers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.freezer.BuildConfig;
import com.example.freezer.api.RetrofitClient;
import com.example.freezer.api.WeatherAPI;
import com.example.freezer.model.ForecastResponse;

import java.util.Calendar;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class EveningFreezeCheckWorker extends Worker {
    private static final double FREEZING_TEMP = 32.0;

    public EveningFreezeCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        SharedPreferences prefs = getApplicationContext()
                .getSharedPreferences("weather_prefs", Context.MODE_PRIVATE);


        boolean alertsEnabled = prefs.getBoolean("freeze_alerts_enabled", false);
        if (!alertsEnabled) {
            Log.d("FreezeCheck", "Alerts disabled, skipping check");
            return Result.success();
        }

        float lat = prefs.getFloat("last_lat", 0);
        float lon = prefs.getFloat("last_lon", 0);

        if (lat != 0 && lon != 0) {
            checkForFreezingOvernight(lat, lon);
        }

        return Result.success();
    }

    private void checkForFreezingOvernight(double lat, double lon) {
        WeatherAPI weatherAPI = RetrofitClient.getClient().create(WeatherAPI.class);
        Call<ForecastResponse> call = weatherAPI.getForecastByCoords(lat, lon, BuildConfig.WEATHER_API_KEY, "imperial");

        call.enqueue(new Callback<ForecastResponse>() {
            @Override
            public void onResponse(Call<ForecastResponse> call, Response<ForecastResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    analyzeOvernightTemperatures(response.body());
                }
            }

            @Override
            public void onFailure(Call<ForecastResponse> call, Throwable t) {
                Log.e("FreezeCheck", "Failed to fetch forecast", t);
            }
        });
    }

    private void analyzeOvernightTemperatures(ForecastResponse forecast) {
        Calendar now = Calendar.getInstance();
        Calendar tonight = Calendar.getInstance();
        tonight.set(Calendar.HOUR_OF_DAY, 22);
        tonight.set(Calendar.MINUTE, 0);
        tonight.set(Calendar.SECOND, 0);

        Calendar tomorrowMorning = Calendar.getInstance();
        tomorrowMorning.add(Calendar.DAY_OF_YEAR, 1);
        tomorrowMorning.set(Calendar.HOUR_OF_DAY, 8); //
        tomorrowMorning.set(Calendar.MINUTE, 0);
        tomorrowMorning.set(Calendar.SECOND, 0);

        double lowestTemp = Double.MAX_VALUE;
        ForecastResponse.Forecast coldestForecast = null;

        // Check all forecasts for the overnight period
        for (ForecastResponse.Forecast item : forecast.getList()) {
            Calendar forecastTime = Calendar.getInstance();
            forecastTime.setTimeInMillis(item.getDt() * 1000);

            // If forecast is between 10 PM tonight and 8 AM tomorrow
            if (forecastTime.after(tonight) && forecastTime.before(tomorrowMorning)) {
                double temp = item.getMain().getTemp();
                if (temp < lowestTemp) {
                    lowestTemp = temp;
                    coldestForecast = item;
                }
            }
        }


        if (lowestTemp <= FREEZING_TEMP && coldestForecast != null) {
            sendFreezeAlert(lowestTemp, forecast.getCity().getName());
        } else {
            Log.d("FreezeCheck", "No freezing temperatures expected. Lowest: " + lowestTemp + "°F");
        }
    }

    private void sendFreezeAlert(double lowestTemp, String cityName) {
        Context context = getApplicationContext();
        android.app.NotificationManager notificationManager =
                (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // notification channel
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    "freeze_alerts",
                    "Freeze Alerts",
                    android.app.NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Alerts for freezing temperatures overnight");
            notificationManager.createNotificationChannel(channel);
        }


        androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(context, "freeze_alerts")
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("❄️ Freezing Alert Overnight!")
                .setContentText(String.format(
                        "Low of %.1f°F in %s. Protect plants/pipes!",
                        lowestTemp,
                        cityName
                ))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        Log.d("FreezeCheck", "Freeze alert sent for " + lowestTemp + "°F");
    }
}