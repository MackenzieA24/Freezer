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

public class MorningUmbrellaCheckWorker extends Worker {
    private static final double RAIN_THRESHOLD = 0.1; // mm of rain
    private static final double POP_THRESHOLD = 30; // 30% probability

    public MorningUmbrellaCheckWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        SharedPreferences prefs = getApplicationContext()
                .getSharedPreferences("weather_prefs", Context.MODE_PRIVATE);

        boolean alertsEnabled = prefs.getBoolean("umbrella_alerts_enabled", false);
        if (!alertsEnabled) {
            Log.d("UmbrellaCheck", "Umbrella alerts disabled, skipping check");
            return Result.success();
        }

        float lat = prefs.getFloat("last_lat", 0);
        float lon = prefs.getFloat("last_lon", 0);

        if (lat != 0 && lon != 0) {
            checkForRain(lat, lon);
        }

        return Result.success();
    }

    private void checkForRain(double lat, double lon) {
        WeatherAPI weatherAPI = RetrofitClient.getClient().create(WeatherAPI.class);
        Call<ForecastResponse> call = weatherAPI.getForecastByCoords(
                lat, lon, BuildConfig.WEATHER_API_KEY, "imperial");

        call.enqueue(new Callback<ForecastResponse>() {
            @Override
            public void onResponse(Call<ForecastResponse> call, Response<ForecastResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    analyzeMorningRain(response.body());
                }
            }

            @Override
            public void onFailure(Call<ForecastResponse> call, Throwable t) {
                Log.e("UmbrellaCheck", "Failed to fetch forecast", t);
            }
        });
    }

    private void analyzeMorningRain(ForecastResponse forecast) {
        Calendar now = Calendar.getInstance();
        Calendar morningStart = Calendar.getInstance();
        morningStart.set(Calendar.HOUR_OF_DAY, 7); // 7 AM
        morningStart.set(Calendar.MINUTE, 0);

        Calendar morningEnd = Calendar.getInstance();
        morningEnd.set(Calendar.HOUR_OF_DAY, 10); // 10 AM
        morningEnd.set(Calendar.MINUTE, 0);


        if (now.after(morningEnd)) {
            morningStart.add(Calendar.DAY_OF_YEAR, 1);
            morningEnd.add(Calendar.DAY_OF_YEAR, 1);
        }

        boolean rainExpected = false;
        String rainTime = "";

        for (ForecastResponse.Forecast item : forecast.getList()) {
            Calendar forecastTime = Calendar.getInstance();
            forecastTime.setTimeInMillis(item.getDt() * 1000);

            // Check if forecast is during morning commute hours
            if (forecastTime.after(morningStart) && forecastTime.before(morningEnd)) {
                boolean hasRain = item.getRain() != null && item.getRain().getH3() > RAIN_THRESHOLD;
                boolean highProbability = item.getPop() * 100 > POP_THRESHOLD;

                if (hasRain || highProbability) {
                    rainExpected = true;
                    rainTime = formatTime(forecastTime);
                    break;
                }
            }
        }

        if (rainExpected) {
            sendUmbrellaAlert(rainTime, forecast.getCity().getName());
        }
    }

    private String formatTime(Calendar calendar) {
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        return String.format("%d%s", hour == 0 || hour == 12 ? 12 : hour % 12, hour < 12 ? "AM" : "PM");
    }

    private void sendUmbrellaAlert(String rainTime, String cityName) {
        Context context = getApplicationContext();
        android.app.NotificationManager notificationManager =
                (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(
                    "umbrella_alerts",
                    "Umbrella Alerts",
                    android.app.NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Alerts for rainy weather");
            notificationManager.createNotificationChannel(channel);
        }

        androidx.core.app.NotificationCompat.Builder builder = new androidx.core.app.NotificationCompat.Builder(context, "umbrella_alerts")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentTitle("☔ Don't Forget Your Umbrella!")
                .setContentText(String.format(
                        "Rain expected around %s in %s",
                        rainTime,
                        cityName
                ))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        Log.d("UmbrellaCheck", "Umbrella alert sent for " + rainTime);
    }
}