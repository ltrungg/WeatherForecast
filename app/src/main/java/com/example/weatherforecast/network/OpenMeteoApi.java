package com.example.weatherforecast.network;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface OpenMeteoApi {
    @GET("v1/forecast")
    Call<WeatherResponse> forecast(
            @Query("latitude") double lat,
            @Query("longitude") double lon,
            @Query("timezone") String timezone,            // "auto" hoặc "Asia/Ho_Chi_Minh"
            @Query("current_weather") boolean currentWeather,
            @Query("hourly") String hourly,                // CSV
            @Query("daily") String daily,                  // CSV
            @Query("forecast_days") int days               // 7
    );
}
