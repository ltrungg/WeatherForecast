package com.example.weatherforecast.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

public class WeatherRepository {
    private final WeatherDb db;

    public WeatherRepository(Context ctx) {
        this.db = WeatherDb.get(ctx);
    }

    // Thêm/hoặc lấy id theo (lat, lon)
    public long insertOrGetLocation(String name, String country, String admin1, String admin2,
                                    double lat, double lon, String timezone, boolean isCurrent) {
        SQLiteDatabase w = db.writable();
        w.beginTransaction();
        try {
            ContentValues cv = new ContentValues();
            cv.put("name", name);
            cv.put("country", country);
            cv.put("admin1", admin1);
            cv.put("admin2", admin2);
            cv.put("lat", lat);
            cv.put("lon", lon);
            cv.put("timezone", timezone);
            cv.put("is_current_location", isCurrent ? 1 : 0);
            w.insertWithOnConflict("locations", null, cv, SQLiteDatabase.CONFLICT_IGNORE);

            try (Cursor c = w.rawQuery(
                    "SELECT id FROM locations WHERE lat=? AND lon=? LIMIT 1",
                    new String[]{String.valueOf(lat), String.valueOf(lon)})) {
                if (c.moveToFirst()) {
                    long id = c.getLong(0);
                    w.setTransactionSuccessful();
                    return id;
                }
                throw new IllegalStateException("insertOrGetLocation failed");
            }
        } finally {
            w.endTransaction();
        }
    }

//    public void addFavorite(long locationId) {
//        SQLiteDatabase w = db.writable();
//        w.execSQL(
//                "INSERT OR IGNORE INTO favorites(location_id, sort_order) " +
//                        "VALUES (?, IFNULL((SELECT MAX(sort_order)+1 FROM favorites), 0))",
//                new Object[]{locationId}
//        );
//    }
    public void addFavorite(long locationId, String name, String country) {
    SQLiteDatabase w = db.writable();
    ContentValues cv = new ContentValues();
    cv.put("location_id", locationId);

    // Tìm giá trị sort_order lớn nhất hiện tại và cộng thêm 1
    // IFNULL được dùng để xử lý trường hợp bảng favorites chưa có dòng nào (kết quả là NULL)
    try (Cursor c = w.rawQuery("SELECT MAX(sort_order) FROM favorites", null)) {
        int maxSortOrder = -1;
        if (c.moveToFirst()) {
            maxSortOrder = c.getInt(0);
        }
        cv.put("sort_order", maxSortOrder + 1);
    }

    // Thêm vào bảng favorites, nếu location_id đã tồn tại thì bỏ qua (IGNORE)
    w.insertWithOnConflict("favorites", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }

    public void removeFavorite(long locationId) {
        SQLiteDatabase w = db.writable();
        w.delete("favorites", "location_id = ?", new String[]{String.valueOf(locationId)});
    }

    public void upsertCurrent(long locationId, long obsTime, double tempC, Double feelsLikeC,
                              Double humidity, Double windMps, Double windDeg, Double visibilityKm,
                              Double pressure, Double uvi, Double clouds, Double precip,
                              String code, String text, String icon) {
        SQLiteDatabase w = db.writable();
        ContentValues cv = new ContentValues();
        cv.put("location_id", locationId);
        cv.put("obs_time", obsTime);
        cv.put("temp_c", tempC);
        if (feelsLikeC != null) cv.put("feels_like_c", feelsLikeC);
        if (humidity   != null) cv.put("humidity_pct", humidity);
        if (windMps    != null) cv.put("wind_mps", windMps);
        if (windDeg    != null) cv.put("wind_deg", windDeg);
        if (visibilityKm != null) cv.put("visibility_km", visibilityKm);
        if (pressure   != null) cv.put("pressure_hpa", pressure);
        if (uvi        != null) cv.put("uvi", uvi);
        if (clouds     != null) cv.put("clouds_pct", clouds);
        if (precip     != null) cv.put("precip_mm", precip);
        cv.put("condition_code", code);
        cv.put("condition_text", text);
        cv.put("icon_code", icon);
        w.insertWithOnConflict("weather_current", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void upsertHourly(long locationId, List<HourlyEntry> list) {
        SQLiteDatabase w = db.writable();
        w.beginTransaction();
        try {
            for (HourlyEntry e : list) {
                ContentValues cv = new ContentValues();
                cv.put("location_id", locationId);
                cv.put("ts", e.ts);
                cv.put("temp_c", e.tempC);
                if (e.humidity != null) cv.put("humidity_pct", e.humidity);
                if (e.windMps  != null) cv.put("wind_mps", e.windMps);
                if (e.windDeg  != null) cv.put("wind_deg", e.windDeg);
                if (e.clouds   != null) cv.put("clouds_pct", e.clouds);
                if (e.popPct   != null) cv.put("pop_pct", e.popPct);
                if (e.precipMm != null) cv.put("precip_mm", e.precipMm);
                if (e.uvi      != null) cv.put("uvi", e.uvi);
                if (e.pressure != null) cv.put("pressure_hpa", e.pressure);
                cv.put("condition_code", e.code);
                cv.put("condition_text", e.text);
                cv.put("icon_code", e.icon);
                w.insertWithOnConflict("weather_hourly", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            }
            w.setTransactionSuccessful();
        } finally {
            w.endTransaction();
        }
    }

    public List<FavoriteCard> getFavoritesCards() {
        SQLiteDatabase r = db.readable();
        List<FavoriteCard> out = new ArrayList<>();
        String query = "SELECT " +
                "vfc.sort_order, vfc.location_id, vfc.name, vfc.country, vfc.temp_c, vfc.feels_like_c, " +
                "vfc.humidity_pct, vfc.wind_kmh, vfc.visibility_km, vfc.condition_text, vfc.icon_code, vfc.updated_at, " +
                "(SELECT temp_max_c FROM weather_daily wd WHERE wd.location_id = vfc.location_id ORDER BY wd.date_ts ASC LIMIT 1) AS maxTempC, " +
                "(SELECT temp_min_c FROM weather_daily wd WHERE wd.location_id = vfc.location_id ORDER BY wd.date_ts ASC LIMIT 1) AS minTempC " +
                "FROM v_favorites_current AS vfc " +
                "ORDER BY vfc.sort_order ASC, vfc.name ASC";

        try (Cursor c = r.rawQuery(query, null)) {
            int sortOrderCol = c.getColumnIndexOrThrow("sort_order");
            int locationIdCol = c.getColumnIndexOrThrow("location_id");
            int nameCol = c.getColumnIndexOrThrow("name");
            int countryCol = c.getColumnIndexOrThrow("country");
            int tempCCol = c.getColumnIndexOrThrow("temp_c");
            int feelsLikeCCol = c.getColumnIndexOrThrow("feels_like_c");
            int humidityCol = c.getColumnIndexOrThrow("humidity_pct");
            int windKmhCol = c.getColumnIndexOrThrow("wind_kmh");
            int visibilityKmCol = c.getColumnIndexOrThrow("visibility_km");
            int conditionCol = c.getColumnIndexOrThrow("condition_text");
            int iconCol = c.getColumnIndexOrThrow("icon_code");
            int updatedAtCol = c.getColumnIndexOrThrow("updated_at");
            int maxTempCCol = c.getColumnIndexOrThrow("maxTempC"); // Cột mới
            int minTempCCol = c.getColumnIndexOrThrow("minTempC"); // Cột mới

            while (c.moveToNext()) {
                FavoriteCard f = new FavoriteCard();
                f.sortOrder   = c.getInt(sortOrderCol);
                f.locationId  = c.getLong(locationIdCol);
                f.name        = c.getString(nameCol);
                f.country     = c.getString(countryCol);
                f.condition   = c.getString(conditionCol);
                f.icon        = c.getString(iconCol);
                f.updatedAt   = c.getLong(updatedAtCol);
                f.tempC       = c.isNull(tempCCol) ? null : c.getDouble(tempCCol);
                f.feelsLikeC  = c.isNull(feelsLikeCCol) ? null : c.getDouble(feelsLikeCCol);
                f.humidity    = c.isNull(humidityCol) ? null : c.getDouble(humidityCol);
                f.windKmh     = c.isNull(windKmhCol) ? null : c.getDouble(windKmhCol);
                f.visibilityKm= c.isNull(visibilityKmCol) ? null : c.getDouble(visibilityKmCol);
                f.maxTempC    = c.isNull(maxTempCCol) ? null : c.getDouble(maxTempCCol);
                f.minTempC    = c.isNull(minTempCCol) ? null : c.getDouble(minTempCCol);

                out.add(f);
            }
        }
        return out;
    }

    // Lấy thông tin location theo id để gọi API (lat, lon, timezone, name)
    public LocationInfo getLocation(long locationId) {
        SQLiteDatabase r = db.readable();
        try (Cursor c = r.rawQuery(
                "SELECT name, lat, lon, timezone FROM locations WHERE id = ? LIMIT 1",
                new String[]{String.valueOf(locationId)})) {
            if (c.moveToFirst()) {
                LocationInfo info = new LocationInfo();
                info.name = c.getString(0);
                info.lat = c.getDouble(1);
                info.lon = c.getDouble(2);
                info.timezone = c.getString(3);
                return info;
            }
        }
        return null;
    }

    // Lấy thời tiết hiện tại cho một location
    public CurrentWeatherData getCurrentWeather(long locationId) {
        SQLiteDatabase r = db.readable();
        try (Cursor c = r.rawQuery(
                "SELECT temp_c, feels_like_c, humidity_pct, wind_mps, wind_deg, " +
                        "visibility_km, pressure_hpa, uvi, clouds_pct, precip_mm, " +
                        "condition_code, condition_text, icon_code, updated_at " +
                        "FROM weather_current WHERE location_id = ?",
                new String[]{String.valueOf(locationId)})) {

            if (c.moveToFirst()) {
                CurrentWeatherData data = new CurrentWeatherData();
                data.tempC = c.isNull(0) ? null : c.getDouble(0);
                data.feelsLikeC = c.isNull(1) ? null : c.getDouble(1);
                data.humidity = c.isNull(2) ? null : c.getDouble(2);
                data.windMps = c.isNull(3) ? null : c.getDouble(3);
                data.windDeg = c.isNull(4) ? null : c.getDouble(4);
                data.visibilityKm = c.isNull(5) ? null : c.getDouble(5);
                data.pressure = c.isNull(6) ? null : c.getDouble(6);
                data.uvi = c.isNull(7) ? null : c.getDouble(7);
                data.clouds = c.isNull(8) ? null : c.getDouble(8);
                data.precipMm = c.isNull(9) ? null : c.getDouble(9);
                data.conditionCode = c.getString(10);
                data.condition = c.getString(11);
                data.icon = c.getString(12);
                data.updatedAt = c.getLong(13);

                // Convert wind from m/s to km/h
                if (data.windMps != null) {
                    data.windKmh = data.windMps * 3.6;
                }

                return data;
            }
        }
        return null;
    }

    // Lấy dự báo 7 ngày
    public List<DailyForecastData> getDailyForecast(long locationId) {
        SQLiteDatabase r = db.readable();
        List<DailyForecastData> out = new ArrayList<>();

        try (Cursor c = r.rawQuery(
                "SELECT date_ts, temp_min_c, temp_max_c, sunrise_ts, sunset_ts, " +
                        "pop_pct, precip_mm, wind_mps, wind_deg, condition_code, " +
                        "condition_text, icon_code " +
                        "FROM weather_daily WHERE location_id = ? " +
                        "ORDER BY date_ts ASC LIMIT 7",
                new String[]{String.valueOf(locationId)})) {

            while (c.moveToNext()) {
                DailyForecastData data = new DailyForecastData();
                data.dateTs = c.getLong(0);
                data.tempMinC = c.isNull(1) ? null : c.getDouble(1);
                data.tempMaxC = c.isNull(2) ? null : c.getDouble(2);
                data.sunriseTs = c.isNull(3) ? null : c.getLong(3);
                data.sunsetTs = c.isNull(4) ? null : c.getLong(4);
                data.popPct = c.isNull(5) ? null : c.getDouble(5);
                data.precipMm = c.isNull(6) ? null : c.getDouble(6);
                data.windMps = c.isNull(7) ? null : c.getDouble(7);
                data.windDeg = c.isNull(8) ? null : c.getDouble(8);
                data.conditionCode = c.getString(9);
                data.condition = c.getString(10);
                data.icon = c.getString(11);

                // Convert wind from m/s to km/h
                if (data.windMps != null) {
                    data.windKmh = data.windMps * 3.6;
                }

                out.add(data);
            }
        }

        return out;
    }

    // Upsert daily forecast data
    public void upsertDaily(long locationId, List<DailyEntry> list) {
        SQLiteDatabase w = db.writable();
        w.beginTransaction();

        try {
            // Xóa dữ liệu cũ để đảm bảo text mới được ghi đè
            w.delete("weather_daily", "location_id = ?", new String[]{String.valueOf(locationId)});

            for (DailyEntry e : list) {
                ContentValues cv = new ContentValues();
                cv.put("location_id", locationId);
                cv.put("date_ts", e.dateTs);
                cv.put("temp_min_c", e.tempMinC);
                cv.put("temp_max_c", e.tempMaxC);

                if (e.sunriseTs != null) cv.put("sunrise_ts", e.sunriseTs);
                if (e.sunsetTs != null) cv.put("sunset_ts", e.sunsetTs);
                if (e.popPct != null) cv.put("pop_pct", e.popPct);
                if (e.precipMm != null) cv.put("precip_mm", e.precipMm);
                if (e.windMps != null) cv.put("wind_mps", e.windMps);
                if (e.windDeg != null) cv.put("wind_deg", e.windDeg);

                cv.put("condition_code", e.conditionCode);
                cv.put("condition_text", e.condition);
                cv.put("icon_code", e.icon);

                w.insertWithOnConflict("weather_daily", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
            }
            w.setTransactionSuccessful();
        } finally {
            w.endTransaction();
        }
    }

// ===========================
// Model phụ
// ===========================
    public static class FavoriteCard {
        public int sortOrder;
        public long locationId;
        public String name, country, condition, icon;
        public Double tempC, feelsLikeC, humidity, windKmh, visibilityKm;
        public long updatedAt;
        public Double maxTempC;
        public Double minTempC;
    }

    public static class HourlyEntry {
        public long ts;
        public double tempC;
        public Double humidity, windMps, windDeg, clouds, popPct, precipMm, uvi, pressure;
        public String code, text, icon;
    }

    public static class DailyEntry {
        public long dateTs;
        public double tempMinC, tempMaxC;
        public Long sunriseTs, sunsetTs;
        public Double popPct, precipMm, windMps, windDeg;
        public String conditionCode, condition, icon;
    }

    public static class CurrentWeatherData {
        public Double tempC, feelsLikeC, humidity, windMps, windDeg, visibilityKm, pressure, uvi, clouds, precipMm, windKmh;
        public String conditionCode, condition, icon;
        public long updatedAt;
    }

    public static class DailyForecastData {
        public long dateTs;
        public Double tempMinC, tempMaxC, popPct, precipMm, windMps, windDeg, windKmh;
        public Long sunriseTs, sunsetTs;
        public String conditionCode, condition, icon;
    }

    public static class LocationInfo {
        public String name;
        public double lat;
        public double lon;
        public String timezone;
    }

}
