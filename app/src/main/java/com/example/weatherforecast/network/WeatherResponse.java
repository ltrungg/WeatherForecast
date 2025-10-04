package com.example.weatherforecast.network;

import java.util.List;

public class WeatherResponse {
    public String timezone;

    public CurrentWeather current_weather;
    public Hourly hourly;
    public Daily daily;

    public static class CurrentWeather {
        public String time;          // "2025-10-04T09:00"
        public double temperature;   // °C
        public double windspeed;     // km/h
        public double winddirection; // °
        public int weathercode;
    }

    public static class Hourly {
        public List<String> time;
        public List<Double> temperature_2m;
        public List<Double> apparent_temperature;
        public List<Double> relativehumidity_2m;
        public List<Double> precipitation;
        public List<Integer> weathercode;
        public List<Double> windspeed_10m;     // km/h
        public List<Double> winddirection_10m; // °
        public List<Double> pressure_msl;      // hPa
        public List<Double> visibility;        // m
        public List<Double> cloudcover;        // %
    }

    public static class Daily {
        public List<String> time;                // "YYYY-MM-DD"
        public List<Double> temperature_2m_max;
        public List<Double> temperature_2m_min;
        public List<String> sunrise;             // ISO
        public List<String> sunset;
        public List<Double> uv_index_max;
        public List<Double> precipitation_sum;
        public List<Integer> weathercode;
        public List<Double> windspeed_10m_max;   // km/h
        public List<Double> winddirection_10m_dominant;
    }
}
