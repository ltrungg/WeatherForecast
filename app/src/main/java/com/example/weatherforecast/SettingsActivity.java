package com.example.weatherforecast;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
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

import com.example.weatherforecast.data.WeatherRepository;
import com.example.weatherforecast.network.DailyActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.materialswitch.MaterialSwitch;

import java.util.List;
import java.util.Locale;
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

    private ActivityResultLauncher<String> notifPermLauncher;
    private ActivityResultLauncher<String[]> locPermLauncher;

    // Ngưỡng cập nhật khi di chuyển > 1km
    private static final float MOVE_THRESHOLD_M = 1000f;

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

        // ===== Permission launchers =====
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
                            // 🔹 Lưu current (ưu tiên quận/huyện VN) – chạy nền
                            saveLastKnownLocationAsCurrent();
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

        // >>> About app
        findViewById(R.id.rowAbout).setOnClickListener(
                v -> startActivity(new Intent(this, AboutActivity.class)));

        // ===== Load initial state =====
        String temp = sp.getString(KEY_TEMP_UNIT, "C");
        String wind = sp.getString(KEY_WIND_UNIT, "kmh");
        boolean auto = sp.getBoolean(KEY_AUTO_LOC, true);
        boolean noti = sp.getBoolean(KEY_NOTIFY, false);

        tvTempUnit.setText(temp.equals("F") ? "°F" : "°C");
        tvWindUnit.setText(wind.equals("mph") ? "mph" : "km/h");
        swNotify.setChecked(noti && areNotificationsReallyEnabled());
        swAutoLoc.setChecked(auto && hasLocationPermission() && isLocationEnabled());

        // ===== Unit toggles =====
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

        // ===== Weather alerts screen =====
        findViewById(R.id.rowDisaster).setOnClickListener(
                v -> startActivity(new Intent(SettingsActivity.this, AlertsActivity.class)));

        // ===== Notifications switch =====
        swNotify.setOnCheckedChangeListener((button, isChecked) -> {
            if (isChecked) {
                if (!areNotificationsReallyEnabled()) {
                    swNotify.setChecked(false);
                    Toast.makeText(this, "Thông báo đang tắt ở hệ thống. Hãy bật trong Cài đặt.", Toast.LENGTH_LONG).show();
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

        // ===== Auto-location switch =====
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
                    saveLastKnownLocationAsCurrent(); // 🔹 cập nhật DB (ưu tiên quận/huyện VN)
                } else {
                    locPermLauncher.launch(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    });
                }
            } else {
                setAutoLocEnabled(false);
            }
        });

        // ===== Bottom navigation mapping =====
        BottomNavigationView bottom = findViewById(R.id.bottomNav);
        bottom.setSelectedItemId(R.id.nav_settings);
        bottom.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            long locId = resolveOrCreateDefaultLocationId(); // 🔹 luôn có id hợp lệ

            if (id == R.id.nav_now) {
                if (!getClass().equals(MainActivity.class)) {
                    Intent i = new Intent(this, MainActivity.class);
                    i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    i.putExtra(HourlyForecastActivity.EXTRA_LOCATION_ID, locId);
                    i.putExtra("location_id", locId);
                    startActivity(i);
                    finish();
                }
                return true;

            } else if (id == R.id.nav_hourly) {
                Intent it = new Intent(this, HourlyForecastActivity.class);
                it.putExtra(HourlyForecastActivity.EXTRA_LOCATION_ID, locId);
                it.putExtra("location_id", locId);
                startActivity(it);
                finish();
                return true;

            } else if (id == R.id.nav_daily) {
                Intent it = new Intent(this, DailyActivity.class);
                it.putExtra(HourlyForecastActivity.EXTRA_LOCATION_ID, locId);
                it.putExtra("location_id", locId);
                startActivity(it);
                finish();
                return true;

            } else if (id == R.id.nav_fav) {
                startActivity(new Intent(this, FavoritesActivity.class));
                finish();
                return true;

            } else if (id == R.id.nav_settings) {
                return true; // đang ở Settings
            }
            return false;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
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

    /** Gọi nền: reverse geocode → ưu tiên Quận/Huyện (VN), so sánh với current → upsert nếu khác đáng kể. */
    @SuppressLint("MissingPermission")
    private void saveLastKnownLocationAsCurrent() {
        new Thread(() -> {
            WeatherRepository repo = new WeatherRepository(this);
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (lm == null) {
                long id = resolveOrCreateDefaultLocationId();
                postPlaceToast(repo.getLocation(id) != null ? repo.getLocation(id).name : "Hồ Chí Minh");
                return;
            }

            Location best = null;
            for (String p : new String[]{
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER}) {
                try {
                    Location l = lm.getLastKnownLocation(p);
                    if (l != null && (best == null || l.getTime() > best.getTime())) best = l;
                } catch (SecurityException ignored) {}
            }

            if (best == null) {
                long id = resolveOrCreateDefaultLocationId();
                postPlaceToast(repo.getLocation(id) != null ? repo.getLocation(id).name : "Hồ Chí Minh");
                return;
            }

            double lat = best.getLatitude();
            double lon = best.getLongitude();

            // ===== Lấy tên ưu tiên Quận/Huyện VN
            String prettyName = resolveVietnamesePlaceName(lat, lon); // ưu tiên subAdminArea
            String tz = java.util.TimeZone.getDefault().getID();

            // ===== So sánh với current
            long currentId = repo.getCurrentLocationIdOrAny();
            WeatherRepository.LocationInfo cur = currentId != -1 ? repo.getLocation(currentId) : null;

            boolean needUpdate = true;
            if (cur != null) {
                float dist = distanceMeters(cur.lat, cur.lon, lat, lon);
                String curPretty = resolveVietnamesePlaceName(cur.lat, cur.lon);
                // Cập nhật nếu khác quận/huyện (chuỗi) hoặc di chuyển xa > 1km
                needUpdate = dist > MOVE_THRESHOLD_M || !safeEqualsIgnoreCase(prettyName, curPretty);
            }

            if (needUpdate) {
                repo.upsertCurrentLocation(lat, lon, prettyName, tz);
            }

            postPlaceToast(prettyName);
        }).start();
    }

    /** Ưu tiên trả về: Quận/Huyện (subAdminArea) + Thành phố/Tỉnh (adminArea), chỉ nếu countryCode = VN. */
    private String resolveVietnamesePlaceName(double lat, double lon) {
        try {
            Geocoder geocoder = new Geocoder(this, new Locale("vi", "VN"));
            List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address a = addresses.get(0);
                String cc = a.getCountryCode();
                String country = a.getCountryName();

                String subAdmin = nullSafe(a.getSubAdminArea());  // Quận/Huyện/TP Thủ Đức...
                String locality = nullSafe(a.getLocality());      // Thị xã/Thành phố…
                String admin    = nullSafe(a.getAdminArea());     // Tỉnh/TP trực thuộc TW

                if ("VN".equalsIgnoreCase(cc)) {
                    // Ưu tiên quận/huyện nếu có
                    String district = !subAdmin.isEmpty() ? subAdmin : (!locality.isEmpty() ? locality : "");
                    String province = !admin.isEmpty() ? admin : "";
                    if (!district.isEmpty() && !province.isEmpty()) {
                        return normalizeVietnamDistrictLine(district, province);
                    } else if (!province.isEmpty()) {
                        return province;
                    } else if (!district.isEmpty()) {
                        return district;
                    } else {
                        return "Việt Nam";
                    }
                } else {
                    // Ngoài VN: locality, admin, country
                    if (!locality.isEmpty() && !admin.isEmpty()) return locality + ", " + admin;
                    if (!admin.isEmpty() && country != null) return admin + ", " + country;
                    if (country != null) return country;
                }
            }
        } catch (Exception ignore) { /* Geocoder có thể null trên vài ROM */ }
        // Fallback
        return String.format(Locale.getDefault(), "Vị trí hiện tại (%.4f, %.4f)", lat, lon);
    }

    /** Chuẩn hóa hiển thị Quận/Huyện với TP trực thuộc TW */
    private String normalizeVietnamDistrictLine(String district, String provinceOrCity) {
        // Một vài tinh chỉnh tên thường gặp
        String prov = provinceOrCity
                .replace("Thành phố Hồ Chí Minh", "TP. Hồ Chí Minh")
                .replace("Thành phố Hà Nội", "Hà Nội");
        // Ví dụ: "Quận 1, TP. Hồ Chí Minh" / "Huyện Đông Anh, Hà Nội"
        return district + ", " + prov;
    }

    private String nullSafe(String s) {
        return s == null ? "" : s.trim();
    }

    private boolean safeEqualsIgnoreCase(String a, String b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.trim().equalsIgnoreCase(b.trim());
    }

    private float distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        float[] out = new float[1];
        android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, out);
        return out[0];
    }

    private void postPlaceToast(String place) {
        runOnUiThread(() -> Toast.makeText(this, "Vị trí hiện tại: " + place, Toast.LENGTH_LONG).show());
    }

    /** Đảm bảo luôn có 1 locationId hợp lệ để truyền giữa các màn. */
    private long resolveOrCreateDefaultLocationId() {
        WeatherRepository repo = new WeatherRepository(this);
        long id = repo.getCurrentLocationIdOrAny();
        if (id != -1) return id;
        return repo.insertOrGetLocation(
                "Hồ Chí Minh", "VN", null, null,
                10.776, 106.700, "Asia/Ho_Chi_Minh", true
        );
    }
}