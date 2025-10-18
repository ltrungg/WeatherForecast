package com.example.weatherforecast;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationChannelCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.Map;

public class SettingsActivity extends AppCompatActivity {
    private static final String PREFS = "settings";
    private static final String KEY_TEMP_UNIT = "temp_unit";     // "C" | "F"
    private static final String KEY_WIND_UNIT = "wind_unit";     // "kmh" | "mph"
    private static final String KEY_AUTO_LOC  = "auto_location"; // boolean
    private static final String KEY_NOTIFY    = "notify";        // boolean

    private static final String CHANNEL_ID = "weather_default";
    private static final int TEST_NOTIFY_ID = 1001;

    private TextView tvTempUnit, tvWindUnit;
    private MaterialSwitch swAutoLoc, swNotify;
    private SharedPreferences sp;

    // Launchers xin quyền
    private ActivityResultLauncher<String> notifPermLauncher;
    private ActivityResultLauncher<String[]> locPermLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settings_root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        sp = getSharedPreferences(PREFS, MODE_PRIVATE);

        // ===== Register permission launchers =====
        notifPermLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        setNotifyEnabled(true, true);
                    } else {
                        if (swNotify != null) swNotify.setChecked(false);
                        setNotifyEnabled(false, false);
                        Toast.makeText(this, "Bạn đã từ chối quyền thông báo", Toast.LENGTH_SHORT).show();
                    }
                });

        locPermLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                (Map<String, Boolean> result) -> {
                    boolean granted =
                            Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION)) ||
                                    Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                    if (granted) {
                        if (isLocationEnabled()) {
                            setAutoLocEnabled(true);
                            swAutoLoc.setChecked(true);
                            Toast.makeText(this, "Đã bật vị trí tự động", Toast.LENGTH_SHORT).show();
                        } else {
                            swAutoLoc.setChecked(false);
                            setAutoLocEnabled(false);
                            Toast.makeText(this, "Vui lòng bật dịch vụ vị trí trong hệ thống", Toast.LENGTH_LONG).show();
                            openLocationServices();
                        }
                    } else {
                        swAutoLoc.setChecked(false);
                        setAutoLocEnabled(false);
                        Toast.makeText(this, "Bạn đã từ chối quyền vị trí", Toast.LENGTH_SHORT).show();
                    }
                });

        // ===== Bind UI =====
        LinearLayout rowTemp = findViewById(R.id.rowTemp);
        tvTempUnit = findViewById(R.id.tvTempUnit);
        LinearLayout rowWind = findViewById(R.id.rowWind);
        tvWindUnit = findViewById(R.id.tvWindUnit);
        swAutoLoc = findViewById(R.id.swAutoLoc);
        swNotify  = findViewById(R.id.swNotify);

        // Load state -> đồng bộ với trạng thái thực tế (quyền + setting hệ thống)
        String temp = sp.getString(KEY_TEMP_UNIT, "C");
        String wind = sp.getString(KEY_WIND_UNIT, "kmh");
        boolean auto = sp.getBoolean(KEY_AUTO_LOC, true);
        boolean noti = sp.getBoolean(KEY_NOTIFY, false);

        tvTempUnit.setText(temp.equals("F") ? "°F" : "°C");
        tvWindUnit.setText(wind.equals("mph") ? "mph" : "km/h");
        swNotify.setChecked(noti && areNotificationsReallyEnabled());
        swAutoLoc.setChecked(auto && hasLocationPermission() && isLocationEnabled());

        // Đổi đơn vị
        rowTemp.setOnClickListener(v -> {
            String now = tvTempUnit.getText().toString();
            String next = now.equals("°C") ? "°F" : "°C";
            tvTempUnit.setText(next);
            sp.edit().putString(KEY_TEMP_UNIT, next.equals("°F") ? "F" : "C").apply();
        });
        rowWind.setOnClickListener(v -> {
            String now = tvWindUnit.getText().toString();
            String next = now.equals("km/h") ? "mph" : "km/h";
            tvWindUnit.setText(next);
            sp.edit().putString(KEY_WIND_UNIT, next.equals("mph") ? "mph" : "kmh").apply();
        });

        // ===== Mở màn Cảnh báo thời tiết =====
        findViewById(R.id.rowDisaster).setOnClickListener(v ->
                startActivity(new Intent(SettingsActivity.this, AlertsActivity.class)));

        // ===== Notifications switch + permission =====
        swNotify.setOnCheckedChangeListener((button, isChecked) -> {
            if (isChecked) {
                if (!areNotificationsReallyEnabled()) {
                    swNotify.setChecked(false);
                    Toast.makeText(this, "Thông báo đang bị tắt ở hệ thống. Hãy bật trong cài đặt.", Toast.LENGTH_LONG).show();
                    openAppNotificationSettings();
                    return;
                }
                if (Build.VERSION.SDK_INT >= 33) {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                            == PackageManager.PERMISSION_GRANTED) {
                        setNotifyEnabled(true, true);
                    } else {
                        notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                    }
                } else {
                    setNotifyEnabled(true, true);
                }
            } else {
                setNotifyEnabled(false, false);
            }
        });

        // ===== Auto Location switch + permission =====
        swAutoLoc.setOnCheckedChangeListener((button, isChecked) -> {
            if (isChecked) {
                if (!isLocationEnabled()) {
                    swAutoLoc.setChecked(false);
                    setAutoLocEnabled(false);
                    Toast.makeText(this, "Dịch vụ vị trí đang tắt. Hãy bật trong Cài đặt hệ thống.", Toast.LENGTH_LONG).show();
                    openLocationServices();
                    return;
                }
                if (hasLocationPermission()) {
                    setAutoLocEnabled(true);
                } else {
                    // xin cả FINE & COARSE, hệ thống có thể cấp Approximate (COARSE) trên Android 12+
                    locPermLauncher.launch(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    });
                }
            } else {
                setAutoLocEnabled(false);
            }
        });

        // Bottom nav
        BottomNavigationView bottom = findViewById(R.id.bottomNav);
        bottom.setSelectedItemId(R.id.nav_settings);
        bottom.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_now) {
                startActivity(new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                finish();
                return true;
            }
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Đồng bộ lại nếu user vừa đổi trong cài đặt hệ thống
        boolean notificationsOn = sp.getBoolean(KEY_NOTIFY, false) && areNotificationsReallyEnabled();
        if (swNotify != null && swNotify.isChecked() != notificationsOn) {
            swNotify.setChecked(notificationsOn);
        }
        boolean autoLocOn = sp.getBoolean(KEY_AUTO_LOC, false) && hasLocationPermission() && isLocationEnabled();
        if (swAutoLoc != null && swAutoLoc.isChecked() != autoLocOn) {
            swAutoLoc.setChecked(autoLocOn);
        }
    }

    // ===== Helpers (Notifications) =====
    private void setNotifyEnabled(boolean enabled, boolean showTestNotification) {
        sp.edit().putBoolean(KEY_NOTIFY, enabled).apply();

        if (!enabled) {
            NotificationManagerCompat.from(this).cancel(TEST_NOTIFY_ID);
            return;
        }
        NotificationManagerCompat nm = NotificationManagerCompat.from(this);
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannelCompat channel = new NotificationChannelCompat
                    .Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName("Thông báo thời tiết")
                    .setDescription("Cảnh báo và cập nhật thời tiết")
                    .build();
            nm.createNotificationChannel(channel);
        }
        if (showTestNotification) {
            if (Build.VERSION.SDK_INT >= 33 &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                            != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_bell_24)
                    .setContentTitle("Thông báo đã bật")
                    .setContentText("Bạn sẽ nhận được cập nhật thời tiết khi có.")
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);
            nm.notify(TEST_NOTIFY_ID, builder.build());
        }
    }

    private boolean areNotificationsReallyEnabled() {
        return NotificationManagerCompat.from(this).areNotificationsEnabled();
    }

    private void openAppNotificationSettings() {
        Intent intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        startActivity(intent);
    }

    // ===== Helpers (Location) =====
    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isLocationEnabled() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null) return false;
        return lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    private void openLocationServices() {
        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
    }

    private void setAutoLocEnabled(boolean enabled) {
        sp.edit().putBoolean(KEY_AUTO_LOC, enabled).apply();
    }
}
