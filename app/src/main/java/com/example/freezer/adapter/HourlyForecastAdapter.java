package com.example.freezer.adapter;


import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.freezer.R;
import com.example.freezer.model.ForecastResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Calendar;

public class HourlyForecastAdapter extends RecyclerView.Adapter<HourlyForecastAdapter.ViewHolder> {
    private List<ForecastResponse.Forecast> hourlyForecasts;

    public HourlyForecastAdapter(List<ForecastResponse.Forecast> hourlyForecasts) {
        this.hourlyForecasts = hourlyForecasts;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_hourly_forecast, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ForecastResponse.Forecast forecast = hourlyForecasts.get(position);

        // Format time
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(forecast.getDt() * 1000);
        String time = formatTime(calendar);

        holder.timeText.setText(time);
        holder.tempText.setText(String.format("%.0f°F", forecast.getMain().getTemp()));

        if (forecast.getWeather() != null && !forecast.getWeather().isEmpty()) {
            holder.weatherText.setText(forecast.getWeather().get(0).getMain());
        }
        String precipDisplay = getPrecipitationDisplay(forecast);
        holder.precipText.setText(precipDisplay);
    }

    @Override
    public int getItemCount() {
        return hourlyForecasts.size();
    }

    private String formatTime(Calendar calendar) {
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        return String.format("%d%s",
                hour == 0 || hour == 12 ? 12 : hour % 12,
                hour < 12 ? "AM" : "PM");
    }
    private String getPrecipitationDisplay(ForecastResponse.Forecast forecast) {
        double pop = forecast.getPop() * 100; // Convert to percentage
        boolean hasRain = forecast.getRain() != null && forecast.getRain().getH3() > 0;
        boolean hasSnow = forecast.getSnow() != null && forecast.getSnow().getH3() > 0;

        if (pop > 0) {
            if (hasRain && hasSnow) {
                return String.format("%.0f%% ☔❄️", pop);
            } else if (hasRain) {
                return String.format("%.0f%% ☔", pop);
            } else if (hasSnow) {
                return String.format("%.0f%% ❄️", pop);
            } else {
                return String.format("%.0f%%", pop);
            }
        }
        return ""; // No precipitation
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView timeText, tempText, weatherText, precipText;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            timeText = itemView.findViewById(R.id.timeText);
            tempText = itemView.findViewById(R.id.tempText);
            weatherText = itemView.findViewById(R.id.weatherText);
            precipText = itemView.findViewById(R.id.precipText);
        }
    }
}
