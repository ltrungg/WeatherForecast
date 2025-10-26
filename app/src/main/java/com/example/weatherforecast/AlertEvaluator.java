package com.example.weatherforecast;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;

import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.weatherforecast.data.AlertsRepository;
import com.example.weatherforecast.data.WeatherDb;

import java.util.List;

/** Đọc dữ liệu thời tiết, so điều kiện rule và bắn local notification. */
public final class AlertEvaluator {
    public static final String CHANNEL_ID = "weather_default"; // dùng chung channel

    /** locationId: id địa điểm dữ liệu vừa cập nhật. */
    public static void evaluateAndNotify(Context ctx, long locationId) {
        AlertsRepository repo = new AlertsRepository(ctx);
        List<AlertsRepository.AlertRule> rules = repo.listAll();
        if (rules.isEmpty()) return;

        SQLiteDatabase r = WeatherDb.get(ctx).readable();

        // ---- Current: lấy bản ghi mới nhất theo obs_time ----
        Double tempC = null, feelsLikeC = null, windMps = null, cloudsPct = null, uvi = null;
        try (Cursor c = r.rawQuery(
                "SELECT obs_time, temp_c, feels_like_c, wind_mps, clouds_pct, uvi " +
                        "FROM weather_current WHERE location_id=? " +
                        "ORDER BY obs_time DESC LIMIT 1",
                new String[]{String.valueOf(locationId)})) {
            if (c.moveToFirst()) {
                if (!c.isNull(1)) tempC = c.getDouble(1);
                if (!c.isNull(2)) feelsLikeC = c.getDouble(2);
                if (!c.isNull(3)) windMps = c.getDouble(3);       // <-- GIỮ m/s
                if (!c.isNull(4)) cloudsPct = c.getDouble(4);
                if (!c.isNull(5)) uvi = c.getDouble(5);
            }
        }

        // ---- Hourly gần “hiện tại” cho POP% (xác suất mưa) ----
        Double popPct = null;
        try (Cursor c = r.rawQuery(
                "SELECT ts, pop_pct FROM weather_hourly WHERE location_id=? " +
                        "ORDER BY ABS(ts - strftime('%s','now')) ASC LIMIT 1",
                new String[]{String.valueOf(locationId)})) {
            if (c.moveToFirst() && !c.isNull(1)) popPct = c.getDouble(1);
        }

        long now = System.currentTimeMillis() / 1000L;

        NotificationManagerCompat nm = NotificationManagerCompat.from(ctx);
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                    new NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                            .setName("Thông báo thời tiết")
                            .setDescription("Cảnh báo và cập nhật thời tiết")
                            .build()
            );
        }

        for (AlertsRepository.AlertRule a : rules) {
            if (!a.active) continue;
            if (a.locationId != null && a.locationId != locationId) continue;

            Double value = null;
            switch (a.metric) {
                case "temp":       value = tempC; break;
                case "feels_like": value = feelsLikeC; break;
                case "wind":       value = windMps; break;      // so sánh theo m/s
                case "rain_prob":  value = popPct; break;       // %
                case "uvi":        value = uvi; break;
                case "clouds":     value = cloudsPct; break;    // %
            }
            if (value == null) continue;

            if (shouldFire(a, value, now, repo)) {
                String title = "Cảnh báo thời tiết";
                String text = a.name != null ? a.name :
                        a.metric + " " + a.op + " " + a.threshold + (a.unit == null ? "" : a.unit);

                NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_bell_24)
                        .setContentTitle(title)
                        .setContentText(text)
                        .setAutoCancel(true)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT);

                nm.notify((int) (10000 + a.id), b.build());
                repo.recordEvent(a.id, value, "local_notification");
            }
        }
    }

    private static boolean shouldFire(AlertsRepository.AlertRule a, double v, long now, AlertsRepository repo) {
        long last = repo.lastFiredAt(a.id);
        if (last > 0 && now - last < a.rearmMinutes * 60L) return false;
        switch (a.op) {
            case ">":  return v >  a.threshold;
            case ">=": return v >= a.threshold;
            case "<":  return v <  a.threshold;
            case "<=": return v <= a.threshold;
            case "==": return v == a.threshold;
            case "!=": return v != a.threshold;
        }
        return false;
    }
}
