package com.example.weatherforecast.utils;

import android.content.Context;
import android.content.SharedPreferences;

public final class Units {
    private Units(){}

    private static final String PREFS = "settings";
    private static final String KEY_TEMP_UNIT = "temp_unit"; // "C" | "F"
    private static final String KEY_WIND_UNIT = "wind_unit"; // "kmh" | "mph"

    public static boolean useF(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return "F".equalsIgnoreCase(sp.getString(KEY_TEMP_UNIT, "C"));
    }

    public static boolean useMph(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return "mph".equalsIgnoreCase(sp.getString(KEY_WIND_UNIT, "kmh"));
    }

    public static double cToF(double c) { return (c * 9.0/5.0) + 32.0; }
    public static double kmhToMph(double kmh) { return kmh * 0.621371; }

    public static String tempSuffix(Context ctx) { return useF(ctx) ? "°F" : "°C"; }
    public static String windSuffix(Context ctx) { return useMph(ctx) ? "mph" : "km/h"; }

    public static double toDisplayTemp(double tempC, Context ctx) {
        return useF(ctx) ? cToF(tempC) : tempC;
    }

    public static double toDisplayWind(double kmh, Context ctx) {
        return useMph(ctx) ? kmhToMph(kmh) : kmh;
    }
}
