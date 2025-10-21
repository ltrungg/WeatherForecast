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
                List<WeatherRepository.DailyEntry> dailyList = new ArrayList<>();
                for (int i = 0; i < b.daily.time.size(); i++) {
                    WeatherRepository.DailyEntry e = new WeatherRepository.DailyEntry();
                    e.dateTs = parseIsoDate(b.daily.time.get(i), tz);
                    e.tempMinC = safeD(b.daily.temperature_2m_min, i, null);
                    e.tempMaxC = safeD(b.daily.temperature_2m_max, i, null);
                    e.sunriseTs = parseIsoSec(b.daily.sunrise.get(i), tz);
                    e.sunsetTs = parseIsoSec(b.daily.sunset.get(i), tz);
                    e.precipMm = safeD(b.daily.precipitation_sum, i, null);
                    e.windMps = div(safeD(b.daily.windspeed_10m_max, i, null), 3.6);
                    e.windDeg = safeD(b.daily.winddirection_10m_dominant, i, null);
                    Integer code = safeI(b.daily.weathercode, i, null);
                    e.conditionCode = code != null ? String.valueOf(code) : null;
                    e.condition = code != null ? codeToText(code) : null;
                    e.icon = null;
                    dailyList.add(e);
                }
                repo.upsertDaily(locationId, dailyList);
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

    private static Long parseIsoDate(String date, String tz) {
        if (date == null) return null;
        try {
            java.text.SimpleDateFormat sdf =
                    new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
            java.util.TimeZone zone = java.util.TimeZone.getTimeZone(tz);
            sdf.setTimeZone(zone);
            java.util.Date d = sdf.parse(date);
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
        // Mapping WMO sang tiếng Việt
        switch (code) {
            case 0: return "Trời quang mây";                    // Clear sky
            case 1: return "Ít mây";                            // Mainly clear
            case 2: return "Nhiều mây";                         // Partly cloudy
            case 3: return "U ám";                              // Overcast
            case 45: case 48: return "Sương mù";                // Fog
            case 51: return "Mưa phùn nhẹ";                     // Light drizzle
            case 53: return "Mưa phùn";                          // Drizzle
            case 55: return "Mưa phùn dày";                     // Heavy drizzle
            case 56: return "Mưa phùn đóng băng";               // Freezing drizzle (light)
            case 57: return "Mưa phùn đóng băng dày";           // Freezing drizzle (heavy)
            case 61: return "Mưa nhẹ";                          // Light rain
            case 63: return "Mưa vừa";                          // Rain
            case 65: return "Mưa to";                           // Heavy rain
            case 66: return "Mưa lạnh";                         // Freezing rain (light)
            case 67: return "Mưa lạnh nặng hạt";                // Freezing rain (heavy)
            case 71: return "Tuyết nhẹ";                        // Light snow
            case 73: return "Tuyết rơi";                        // Snow
            case 75: return "Tuyết dày";                        // Heavy snow
            case 77: return "Hạt tuyết";                        // Snow grains
            case 80: return "Mưa rào nhẹ";                      // Rain showers: slight
            case 81: return "Mưa rào";                          // Rain showers: moderate
            case 82: return "Mưa rào rất to";                   // Rain showers: violent
            case 85: return "Mưa tuyết rào";                    // Snow showers: slight
            case 86: return "Mưa tuyết rào dày";                // Snow showers: heavy
            case 95: return "Dông";                             // Thunderstorm
            case 96: return "Dông kèm mưa đá";                  // Thunderstorm with hail (slight)
            case 99: return "Dông mạnh kèm mưa đá";             // Severe thunderstorm with hail
            default: return "Không xác định";
        }
    }
}
