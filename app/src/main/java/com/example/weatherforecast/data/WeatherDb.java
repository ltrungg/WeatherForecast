package com.example.weatherforecast.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class WeatherDb {
    private static WeatherDb instance;
    private final WeatherDbHelper helper;
    private final ExecutorService io;

    private WeatherDb(Context ctx) {
        this.helper = new WeatherDbHelper(ctx);
        this.io = Executors.newSingleThreadExecutor();
    }

    public static synchronized WeatherDb get(Context ctx) {
        if (instance == null) instance = new WeatherDb(ctx.getApplicationContext());
        return instance;
    }

    public SQLiteDatabase readable() { return helper.getReadableDatabase(); }
    public SQLiteDatabase writable() { return helper.getWritableDatabase(); }
    public ExecutorService io() { return io; }
}
