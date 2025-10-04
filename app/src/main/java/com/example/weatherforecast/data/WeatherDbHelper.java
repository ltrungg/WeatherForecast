package com.example.weatherforecast.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class WeatherDbHelper extends SQLiteOpenHelper {
    public static final String DB_NAME = "weather.db";
    private static final int DB_VERSION = 1; // khớp PRAGMA user_version=1

    private final Context appContext;

    public WeatherDbHelper(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
        this.appContext = context.getApplicationContext();
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        // Bật FK cho chắc (device cũ)
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        SqlScriptRunner.execSqlFromAsset(appContext, db, "sql/weather_schema.sql");
        try {
            db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS places_fts " +
                            "USING fts5(name, country, admin1)"
            );
        } catch (Exception e) {
            android.util.Log.w("DB", "FTS5 not available on this device. Skipping FTS table.");
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Dev: tạo lại (đơn giản). Prod: viết ALTER theo version.
        SqlScriptRunner.execSqlFromAsset(appContext, db, "sql/weather_schema.sql");
    }
}
