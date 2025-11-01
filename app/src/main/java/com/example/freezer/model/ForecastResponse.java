package com.example.freezer.model;

import java.util.List;

public class ForecastResponse {
    private List<Forecast> list;
    private City city;

    public List<Forecast> getList() { return list; }
    public void setList(List<Forecast> list) { this.list = list; }
    public City getCity() { return city; }
    public void setCity(City city) { this.city = city; }

    public static class Forecast {
        private long dt;
        private Main main;
        private List<Weather> weather;
        private String dt_txt;
        private Rain rain;
        private Snow snow;
        private double pop; // probability of precipitation

        public long getDt() { return dt; }
        public void setDt(long dt) { this.dt = dt; }
        public Main getMain() { return main; }
        public void setMain(Main main) { this.main = main; }
        public List<Weather> getWeather() { return weather; }
        public void setWeather(List<Weather> weather) { this.weather = weather; }
        public String getDt_txt() { return dt_txt; }
        public void setDt_txt(String dt_txt) { this.dt_txt = dt_txt; }
        public Rain getRain() { return rain; }
        public void setRain(Rain rain) { this.rain = rain; }
        public Snow getSnow() { return snow; }
        public void setSnow(Snow snow) { this.snow = snow; }
        public double getPop() { return pop; }
        public void setPop(double pop) { this.pop = pop; }
    }

    public static class Main {
        private double temp;
        private double feels_like;
        private int humidity;

        public double getTemp() { return temp; }
        public void setTemp(double temp) { this.temp = temp; }
        public double getFeels_like() { return feels_like; }
        public void setFeels_like(double feels_like) { this.feels_like = feels_like; }
        public int getHumidity() { return humidity; }
        public void setHumidity(int humidity) { this.humidity = humidity; }
    }

    public static class Weather {
        private String main;
        private String description;
        private String icon;

        public String getMain() { return main; }
        public void setMain(String main) { this.main = main; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        public String getIcon() { return icon; }
        public void setIcon(String icon) { this.icon = icon; }
    }

    public static class City {
        private String name;
        private String country;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getCountry() { return country; }
        public void setCountry(String country) { this.country = country; }
    }
    public static class Rain {
        private double h3; // Precipitation volume for last 3 hours, mm

        public double getH3() { return h3; }
        public void setH3(double h3) { this.h3 = h3; }
    }

    public static class Snow {
        private double h3; // Snow volume for last 3 hours, mm

        public double getH3() { return h3; }
        public void setH3(double h3) { this.h3 = h3; }
    }
}
