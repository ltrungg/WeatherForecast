// com/example/weatherforecast/data/SqlScriptRunner.java
package com.example.weatherforecast.data;

import android.content.Context;
import android.content.res.AssetManager;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

public final class SqlScriptRunner {
    private SqlScriptRunner() {}

    public static void execSqlFromAsset(Context ctx, SQLiteDatabase db, String assetPath) {
        try {
            AssetManager am = ctx.getAssets();
            try (InputStream is = am.open(assetPath);
                 BufferedReader br = new BufferedReader(new InputStreamReader(is))) {

                StringBuilder sb = new StringBuilder();
                boolean inBlockComment = false;
                String line;
                while ((line = br.readLine()) != null) {
                    String t = line.trim();
                    if (!inBlockComment && (t.isEmpty() || t.startsWith("--"))) continue;
                    if (t.startsWith("/*")) { inBlockComment = true; if (t.endsWith("*/")) inBlockComment = false; continue; }
                    if (inBlockComment) { if (t.endsWith("*/")) inBlockComment = false; continue; }

                    sb.append(line).append('\n');
                    if (t.endsWith(";")) {
                        String sql = sb.toString().trim();
                        sb.setLength(0);
                        if (sql.isEmpty()) continue;
                        try {
                            db.execSQL(sql);
                        } catch (Exception e) {
                            // LOG thay vì crash (VD: no such module: fts5)
                            Log.e("SQL", "Failed: " + sql, e);
                        }
                    }
                }
                if (sb.length() > 0) {
                    String sql = sb.toString().trim();
                    if (!sql.isEmpty()) {
                        try { db.execSQL(sql); } catch (Exception e) {
                            Log.e("SQL", "Failed: " + sql, e);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Chỉ throw khi không đọc được asset (sai path)
            throw new RuntimeException("Failed to open SQL asset: " + assetPath, e);
        }
    }
}
