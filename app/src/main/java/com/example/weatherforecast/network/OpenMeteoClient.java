package com.example.weatherforecast.network;

import android.util.Log;

import com.example.weatherforecast.data.WeatherRepository;

import java.time.*;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.*;

public class OpenMeteoClient {
    private final OpenMeteoApi api;

    public OpenMeteoClient() {
        HttpLoggingInterceptor log = new HttpLoggingInterceptor();
        log.setLevel(HttpLoggingInterceptor.Level.BASIC);
        OkHttpClient ok = new OkHttpClient.Builder().addInterceptor(log).build();

        Retrofit r = new Retrofit.Builder()
                .baseUrl("https://api.open-meteo.com/")
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .client(ok)
                .build();
        api = r.create(OpenMeteoApi.class);
    }

    public void fetchAndStore(double lat, double lon, String tzOrAuto,
                              long locationId, WeatherRepository repo) {
        String hourly = "temperature_2m,relativehumidity_2m,apparent_temperature,"
                + "precipitation,weathercode,windspeed_10m,winddirection_10m,"
                + "pressure_msl,visibility,cloudcover";
        String daily = "temperature_2m_max,temperature_2m_min,sunrise,sunset,"
                + "uv_index_max,precipitation_sum,weathercode,windspeed_10m_max,"
                + "winddirection_10m_dominant";

        Call<WeatherResponse> call = api.forecast(
                lat, lon, tzOrAuto, true, hourly, daily, 7);

        try {
            Response<WeatherResponse> res = call.execute();
            if (!res.isSuccessful() || res.body() == null) {
                Log.e("API", "OpenMeteo error: " + res.code());
                return;
            }
            WeatherResponse b = res.body();
            String tz = "auto".equalsIgnoreCase(tzOrAuto) || b.timezone == null ? "UTC" : b.timezone;

            // ==== current ====
            if (b.current_weather != null) {
                long ts = parseIsoSec(b.current_weather.time, tz);
                double tempC = b.current_weather.temperature;
                Double windMps = b.current_weather.windspeed != 0
                        ? b.current_weather.windspeed / 3.6 : null; // km/h -> m/s

                repo.upsertCurrent(
                        locationId,
                        ts,
                        tempC,
                        null,  // feels like: có trong hourly
                        null,  // humidity: lấy từ hourly gần nhất nếu muốn
                        windMps,
                        b.current_weather.winddirection,
                        null,  // visibility
                        null,  // pressure
                        null,  // uvi
                        null,  // clouds
                        null,  // precip
                        String.valueOf(b.current_weather.weathercode),
                        codeToText(b.current_weather.weathercode),
                        null
                );
            }

            // ==== hourly (48–72 entries tuỳ API) ====
            if (b.hourly != null && b.hourly.time != null) {
                List<WeatherRepository.HourlyEntry> list = new ArrayList<>();
                for (int i = 0; i < b.hourly.time.size(); i++) {
                    WeatherRepository.HourlyEntry e = new WeatherRepository.HourlyEntry();
                    e.ts = parseIsoSec(b.hourly.time.get(i), tz);
                    e.tempC = safeD(b.hourly.temperature_2m, i, null);
                    e.humidity = safeD(b.hourly.relativehumidity_2m, i, null);
                    e.windMps  = div(safeD(b.hourly.windspeed_10m, i, null), 3.6);
                    e.windDeg  = safeD(b.hourly.winddirection_10m, i, null);
                    e.clouds   = safeD(b.hourly.cloudcover, i, null);
                    e.popPct   = null; // Open-Meteo có precipitation_probability ở số model khác; để null nếu chưa lấy
                    e.precipMm = safeD(b.hourly.precipitation, i, null);
                    e.uvi      = null;
                    e.pressure = safeD(b.hourly.pressure_msl, i, null);
                    Integer code = safeI(b.hourly.weathercode, i, null);
                    e.code = code != null ? String.valueOf(code) : null;
                    e.text = code != null ? codeToText(code) : null;
                    e.icon = null;
                    list.add(e);
                }
                repo.upsertHourly(locationId, list);
            }

            // ==== daily ====
            if (b.daily != null && b.daily.time != null) {
                // bạn có thể map tiếp sang bảng weather_daily (ở đây mình demo current + hourly là đủ cho flow)
                // TODO: thêm hàm repo.upsertDaily(...) tương tự hourly nếu muốn.
            }
        } catch (Exception e) {
            Log.e("API", "fetchAndStore failed", e);
        }
    }

    private static Long parseIsoSec(String iso, String tz) {
        if (iso == null) return null;
        try {
            java.text.SimpleDateFormat sdf =
                    new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", java.util.Locale.US);
            java.util.TimeZone zone = java.util.TimeZone.getTimeZone(tz);
            sdf.setTimeZone(zone);
            java.util.Date d = sdf.parse(iso);
            return d != null ? d.getTime() / 1000L : null;
        } catch (java.text.ParseException e) {
            return null;
        }
    }


    private static Double safeD(java.util.List<Double> l, int i, Double def) {
        return l != null && i < l.size() && l.get(i) != null ? l.get(i) : def;
    }
    private static Integer safeI(java.util.List<Integer> l, int i, Integer def) {
        return l != null && i < l.size() && l.get(i) != null ? l.get(i) : def;
    }
    private static Double div(Double v, double d) { return v == null ? null : v / d; }

    private static String codeToText(int code) {
        // mapping đơn giản; bạn có thể mở rộng theo bảng WMO
        switch (code) {
            case 0: return "Clear";
            case 1: case 2: case 3: return "Partly cloudy";
            case 45: case 48: return "Fog";
            case 51: case 53: case 55: return "Drizzle";
            case 61: case 63: case 65: return "Rain";
            case 71: case 73: case 75: return "Snow";
            case 80: case 81: case 82: return "Rain showers";
            case 95: return "Thunderstorm";
            default: return "Unknown";
        }
    }
}
