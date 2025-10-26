package com.example.weatherforecast.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

/** CRUD cho alert_rules & alert_events (theo schema). */
public class AlertsRepository {
    private final WeatherDb db;

    public AlertsRepository(Context ctx) { this.db = WeatherDb.get(ctx); }

    // ===== Model =====
    public static class AlertRule {
        public long id;
        public String name;
        public Long locationId;       // null = vị trí hiện tại
        public String metric;         // temp | feels_like | wind | rain_prob | uvi | clouds
        public String op;             // > | >= | < | <= | == | !=
        public double threshold;      // Lưu theo đơn vị gốc của DB (°C, m/s, %, UVI)
        public String unit;           // Đơn vị hiển thị người dùng chọn (°C, °F, km/h, mph, %, UVI)
        public boolean active;
        public int rearmMinutes;
    }

    // ===== CRUD =====
    public long insert(AlertRule r) {
        SQLiteDatabase w = db.writable();
        ContentValues cv = new ContentValues();
        cv.put("name", r.name);
        if (r.locationId != null) cv.put("location_id", r.locationId);
        cv.put("metric", r.metric);
        cv.put("op", r.op);
        cv.put("threshold", r.threshold);
        cv.put("threshold_unit", r.unit);
        cv.put("active", r.active ? 1 : 0);
        cv.put("rearm_minutes", r.rearmMinutes);
        return w.insertOrThrow("alert_rules", null, cv);
    }

    public List<AlertRule> listAll() {
        ArrayList<AlertRule> list = new ArrayList<>();
        try (Cursor c = db.readable().rawQuery(
                "SELECT id,name,location_id,metric,op,threshold,threshold_unit,active,rearm_minutes " +
                        "FROM alert_rules ORDER BY id DESC", null)) {
            while (c.moveToNext()) {
                AlertRule a = new AlertRule();
                a.id = c.getLong(0);
                a.name = c.getString(1);
                a.locationId = c.isNull(2) ? null : c.getLong(2);
                a.metric = c.getString(3);
                a.op = c.getString(4);
                a.threshold = c.getDouble(5);
                a.unit = c.getString(6);
                a.active = c.getInt(7) != 0;
                a.rearmMinutes = c.getInt(8);
                list.add(a);
            }
        }
        return list;
    }

    public void delete(long id) {
        db.writable().delete("alert_rules", "id=?", new String[]{String.valueOf(id)});
    }

    public void setActive(long id, boolean on) {
        ContentValues cv = new ContentValues();
        cv.put("active", on ? 1 : 0);
        db.writable().update("alert_rules", cv, "id=?", new String[]{String.valueOf(id)});
    }

    // ===== Events & rate-limit =====
    public long lastFiredAt(long alertId) {
        try (Cursor c = db.readable().rawQuery(
                "SELECT fired_at FROM alert_events WHERE alert_id=? ORDER BY fired_at DESC LIMIT 1",
                new String[]{String.valueOf(alertId)})) {
            if (c.moveToFirst()) return c.getLong(0);
        }
        return 0L;
    }

    public void recordEvent(long alertId, Double observed, String channel) {
        ContentValues cv = new ContentValues();
        cv.put("alert_id", alertId);
        if (observed != null) cv.put("observed_value", observed);
        cv.put("channel", channel);
        db.writable().insert("alert_events", null, cv);
    }
}
