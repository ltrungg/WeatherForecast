package com.example.weatherforecast.network;

import android.util.Log;

import com.example.weatherforecast.data.WeatherRepository;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.time.*;
import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.*;
import retrofit2.converter.gson.GsonConverterFactory;

public class OpenMeteoClient {
    private final OpenMeteoApi api;
    public static final String API_KEY = "2d8a6e9dc75a4023b2a103306252110";
    private static OpenMeteoApi apiService = null;

    public static OpenMeteoApi getApiService() {
        if (apiService == null) {
            // Tạo một interceptor để log các request và response ra Logcat. Rất hữu ích để gỡ lỗi.
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(logging)
                    .build();

            // Dùng GsonBuilder để linh hoạt hơn khi phân tích JSON
            Gson gson = new GsonBuilder()
                    .setLenient()
                    .create();

            // Xây dựng Retrofit
            Retrofit retrofit = new Retrofit.Builder()
                    .baseUrl("https://api.open-meteo.com/") // URL cơ sở cho Retrofit
                    .addConverterFactory(GsonConverterFactory.create(gson)) // Dùng Gson để chuyển đổi JSON
                    .client(client) // Sử dụng OkHttpClient đã cấu hình
                    .build();

            // Tạo ra thực thể của service API
            apiService = retrofit.create(OpenMeteoApi.class);
        }
        return apiService;
    }

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
                + "uv_index_max,precipitation_sum,precipitation_probability_max,weathercode,windspeed_10m_max,"
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
                        (double) b.current_weather.winddirection,
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

            // ====== THÊM: cập nhật current từ dữ liệu hourly tại thời điểm hiện tại ======
            try {
                if (b.current_weather != null && b.hourly != null && b.hourly.time != null) {
                    String nowIso = b.current_weather.time; // ví dụ "2025-10-04T09:00"
                    int idxNow = -1;
                    for (int i = 0; i < b.hourly.time.size(); i++) {
                        if (nowIso != null && nowIso.equals(b.hourly.time.get(i))) {
                            idxNow = i;
                            break;
                        }
                    }
                    if (idxNow != -1) {
                        // Lấy các trị số từ hourly
                        Double tempC2    = safeD(b.hourly.temperature_2m, idxNow, null);
                        Double feelsLike = safeD(b.hourly.apparent_temperature, idxNow, null);
                        Double humidity  = safeD(b.hourly.relativehumidity_2m, idxNow, null); // %
                        Double visM      = safeD(b.hourly.visibility, idxNow, null);          // mét
                        Double visKm     = (visM != null) ? (visM / 1000.0) : null;           // km
                        Double pressure  = safeD(b.hourly.pressure_msl, idxNow, null);        // hPa
                        Double windKmh2  = safeD(b.hourly.windspeed_10m, idxNow, null);
                        Double windMps2  = div(windKmh2, 3.6);
                        Double windDeg2  = safeD(b.hourly.winddirection_10m, idxNow, null);
                        Double clouds    = safeD(b.hourly.cloudcover, idxNow, null);          // %
                        Double precipMm  = safeD(b.hourly.precipitation, idxNow, null);
                        Integer code2    = safeI(b.hourly.weathercode, idxNow, null);
                        String codeStr   = (code2 != null) ? String.valueOf(code2) : null;
                        String codeTxt   = (code2 != null) ? codeToText(code2) : null;

                        Long ts2 = parseIsoSec(b.hourly.time.get(idxNow), tz);

                        // Ghi đè "current" bằng dữ liệu hourly tại giờ hiện tại
                        repo.upsertCurrent(
                                locationId,
                                (ts2 != null ? ts2 : System.currentTimeMillis() / 1000L),
                                (tempC2 != null ? tempC2 : (b.current_weather != null ? b.current_weather.temperature : 0)),
                                feelsLike,
                                humidity,
                                windMps2,
                                windDeg2,
                                visKm,
                                pressure,
                                null,        // uvi: chưa yêu cầu từ API hourly
                                clouds,
                                precipMm,
                                codeStr,
                                codeTxt,
                                null
                        );
                    }
                }
            } catch (Exception ignore) { /* không làm crash nếu thiếu field */ }
            // ====== HẾT PHẦN THÊM ======

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
                    e.popPct = safeD(b.daily.precipitation_probability_max, i, null);
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
            case 53: return "Mưa phùn";                         // Drizzle
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
