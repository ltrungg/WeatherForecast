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

    public void addFavorite(long locationId) {
        SQLiteDatabase w = db.writable();
        w.execSQL(
                "INSERT OR IGNORE INTO favorites(location_id, sort_order) " +
                        "VALUES (?, IFNULL((SELECT MAX(sort_order)+1 FROM favorites), 0))",
                new Object[]{locationId}
        );
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
        try (Cursor c = r.rawQuery(
                "SELECT sort_order, location_id, name, country, temp_c, feels_like_c, " +
                        "humidity_pct, wind_kmh, visibility_km, condition_text, icon_code, updated_at " +
                        "FROM v_favorites_current ORDER BY sort_order ASC, name ASC", null)) {
            while (c.moveToNext()) {
                FavoriteCard f = new FavoriteCard();
                f.sortOrder   = c.getInt(0);
                f.locationId  = c.getLong(1);
                f.name        = c.getString(2);
                f.country     = c.getString(3);
                f.tempC       = c.isNull(4) ? null : c.getDouble(4);
                f.feelsLikeC  = c.isNull(5) ? null : c.getDouble(5);
                f.humidity    = c.isNull(6) ? null : c.getDouble(6);
                f.windKmh     = c.isNull(7) ? null : c.getDouble(7);
                f.visibilityKm= c.isNull(8) ? null : c.getDouble(8);
                f.condition   = c.getString(9);
                f.icon        = c.getString(10);
                f.updatedAt   = c.getLong(11);
                out.add(f);
            }
        }
        return out;
    }

    // Model phụ
    public static class FavoriteCard {
        public int sortOrder;
        public long locationId;
        public String name, country, condition, icon;
        public Double tempC, feelsLikeC, humidity, windKmh, visibilityKm;
        public long updatedAt;
    }

    public static class HourlyEntry {
        public long ts;
        public double tempC;
        public Double humidity, windMps, windDeg, clouds, popPct, precipMm, uvi, pressure;
        public String code, text, icon;
    }
}
