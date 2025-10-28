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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.weatherforecast.data.WeatherRepository;
import com.example.weatherforecast.network.DailyActivity;
import com.example.weatherforecast.network.OpenMeteoClient;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    // ===== Pref keys (nhất quán SettingsActivity) =====
    private static final String PREFS = "settings";
    private static final String KEY_TEMP_UNIT = "temp_unit";     // "C" | "F"
    private static final String KEY_WIND_UNIT = "wind_unit";     // "kmh" | "mph"
    private static final String KEY_AUTO_LOC  = "auto_location"; // boolean

    // ===== State =====
    private long currentLocationId = -1;
    private WeatherRepository repo;
    private SharedPreferences sp;

    // ===== UI =====
    private TextView tvCity, tvDate, tvTemperature, tvDescription, tvMinMax;
    private ImageView imgWeatherIcon;
    private TextView tvWindValue, tvHumidityValue, tvVisibilityValue, tvPressureValue;
    private TextView tvSunriseTime, tvSunsetTime;
    private ScrollView scrollMain;
    private BottomNavigationView bottomNavigationView;

    // ===== Perms / threading =====
    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Nullable
    private WeatherData cached;

    // Lắng nghe thay đổi Setting để cập nhật UI/logic
    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            (prefs, key) -> {
                if (KEY_TEMP_UNIT.equals(key) || KEY_WIND_UNIT.equals(key)) {
                    if (currentLocationId != -1) renderFromDb(currentLocationId);
                } else if (KEY_AUTO_LOC.equals(key)) {
                    requestNeededPermissionsThenLoad();
                }
            };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        repo = new WeatherRepository(this);
        sp   = getSharedPreferences(PREFS, MODE_PRIVATE);
        sp.registerOnSharedPreferenceChangeListener(prefListener);

        bindViews();
        initStaticHeader();
        initBottomNav();
        initPermissionsLaunchers();

        requestNeededPermissionsThenLoad();

        if (savedInstanceState != null) {
            WeatherData restore = (WeatherData) savedInstanceState.getSerializable("cache");
            if (restore != null) {
                cached = restore;
                renderLegacy(restore);
            }
        }
    }

    private void bindViews() {
        scrollMain = findViewById(R.id.scrollMain);

        tvCity = findViewById(R.id.tvCity);
        tvDate = findViewById(R.id.tvDate);
        tvTemperature = findViewById(R.id.tvTemperature);
        tvDescription = findViewById(R.id.tvDescription);
        tvMinMax = findViewById(R.id.tvMinMax);
        imgWeatherIcon = findViewById(R.id.imgWeatherIcon);

        tvWindValue = findViewById(R.id.tvWindValue);
        tvHumidityValue = findViewById(R.id.tvHumidityValue);
        tvVisibilityValue = findViewById(R.id.tvVisibilityValue);
        tvPressureValue = findViewById(R.id.tvPressureValue);

        tvSunriseTime = findViewById(R.id.tvSunriseTime);
        tvSunsetTime = findViewById(R.id.tvSunsetTime);

        bottomNavigationView = findViewById(R.id.bottomNavigation);
    }

    private void initStaticHeader() {
        String today = new SimpleDateFormat("EEE, d 'tháng' MM yyyy", new Locale("vi"))
                .format(new Date());
        tvDate.setText(today);
    }

    private void initBottomNav() {
        bottomNavigationView.setSelectedItemId(R.id.nav_now);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            long locId = resolveOrCreateDefaultLocationId(); // luôn có id hợp lệ

            if (id == R.id.nav_now) {
                // Ở ngay trang hiện tại: cuộn lên đầu
                scrollMain.smoothScrollTo(0, 0);
                return true;
            }

            if (id == R.id.nav_hourly) {
                Intent it = new Intent(MainActivity.this, HourlyForecastActivity.class);
                it.putExtra(HourlyForecastActivity.EXTRA_LOCATION_ID, locId);
                it.putExtra("location_id", locId);
                startActivity(it);
                return true;
            }

            if (id == R.id.nav_daily) {
                Intent it = new Intent(MainActivity.this, DailyActivity.class);
                it.putExtra(HourlyForecastActivity.EXTRA_LOCATION_ID, locId);
                it.putExtra("location_id", locId);
                startActivity(it);
                return true;
            }

            if (id == R.id.nav_fav) {
                startActivity(new Intent(MainActivity.this, FavoritesActivity.class));
                    return true;
                }

            if (id == R.id.nav_settings) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                return true;
            }

            return false;
        });
    }

    private void initPermissionsLaunchers() {
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> requestNeededPermissionsThenLoad()
        );
    }

    private void requestNeededPermissionsThenLoad() {
        boolean fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;

        if (!fine && !coarse) {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        } else {
            loadWeatherWithBestEffort();
        }
    }

    /** Lấy vị trí theo Setting (LocationManager giống SettingsActivity), gọi API, lưu DB và render */
    private void loadWeatherWithBestEffort() {
        io.execute(() -> {
            try {
                boolean auto = sp.getBoolean(KEY_AUTO_LOC, true);

                Location loc = null;
                if (auto && isLocationEnabled() && hasLocationPermission()) {
                    loc = getLastKnownLocationSafe();
                    if (looksLikeBogusEmulatorLocation(loc)) loc = null;
                }

                double lat, lon;
                String tz = "auto";
                String name;

                if (loc != null) {
                    lat = loc.getLatitude();
                    lon = loc.getLongitude();
                    name = resolveVietnamesePlaceName(lat, lon);
                } else {
                    // Fallback đồng bộ với Settings/Daily/Hourly → Hồ Chí Minh
                    lat = 10.776;
                    lon = 106.700;
                    tz  = "Asia/Ho_Chi_Minh";
                    name = "TP. Hồ Chí Minh";
                }

                long locId = repo.insertOrGetLocation(
                        name, "VN", null, null, lat, lon, tz, loc != null /*isCurrent*/
                );
                currentLocationId = locId;

                new OpenMeteoClient().fetchAndStore(lat, lon, tz, locId, repo);
                renderFromDb(locId);

            } catch (Exception e) {
                if (currentLocationId != -1) {
                    renderFromDb(currentLocationId);
                } else {
                    main.post(() ->
                            Toast.makeText(MainActivity.this, "Không thể tải dữ liệu", Toast.LENGTH_SHORT).show()
                    );
                }
            }
        });
    }

    // ===== Helpers: Permission & Location =====
    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isLocationEnabled() {
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            return lm != null && (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
        } catch (Exception e) {
            return true;
        }
    }

    @SuppressLint("MissingPermission")
    @Nullable
    private Location getLastKnownLocationSafe() {
        try {
            LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (lm == null) return null;

            Location best = null;
            for (String provider : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(provider);
                if (l == null) continue;
                if (best == null || l.getTime() > best.getTime()) {
                    best = l;
                }
            }
            return best;
        } catch (SecurityException se) {
            return null;
        }
    }

    /** Ưu tiên: Quận/Huyện (subAdminArea hoặc locality) + Tỉnh/TP (adminArea) khi ở VN */
    private String resolveVietnamesePlaceName(double lat, double lon) {
        try {
            Geocoder geocoder = new Geocoder(this, new Locale("vi", "VN"));
            List<Address> addresses = geocoder.getFromLocation(lat, lon, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address a = addresses.get(0);
                String cc = a.getCountryCode();
                String subAdmin = nullSafe(a.getSubAdminArea());
                String locality = nullSafe(a.getLocality());
                String admin    = nullSafe(a.getAdminArea());

                if ("VN".equalsIgnoreCase(cc)) {
                    String district = !subAdmin.isEmpty() ? subAdmin : (!locality.isEmpty() ? locality : "");
                    String province = !admin.isEmpty() ? admin : "";
                    if (!province.isEmpty()) {
                        // Chuẩn hóa vài tên thường gặp
                        province = province.replace("Thành phố Hồ Chí Minh", "TP. Hồ Chí Minh")
                                .replace("Thành phố Hà Nội", "Hà Nội");
                    }
                    if (!district.isEmpty() && !province.isEmpty()) return district + ", " + province;
                    if (!province.isEmpty()) return province;
                    if (!district.isEmpty()) return district;
                    return "Việt Nam";
                } else {
                    // Ngoài VN
                    if (!locality.isEmpty() && !admin.isEmpty()) return locality + ", " + admin;
                    if (!admin.isEmpty() && a.getCountryName() != null) return admin + ", " + a.getCountryName();
                    if (a.getCountryName() != null) return a.getCountryName();
                }
            }
        } catch (Exception ignore) { }
        return String.format(Locale.getDefault(), "Vị trí hiện tại (%.4f, %.4f)", lat, lon);
    }

    private String nullSafe(String s) { return s == null ? "" : s.trim(); }

    /** Lọc vị trí ảo: quá cũ/độ chính xác kém/ngoài VN (khi locale VN) */
    private boolean looksLikeBogusEmulatorLocation(@Nullable Location l) {
        if (l == null) return true;
        long ageMs = Math.abs(System.currentTimeMillis() - l.getTime());
        if (ageMs > 6L * 3600_000L) return true;          // > 6 giờ
        if (l.hasAccuracy() && l.getAccuracy() > 2000f) return true;
        if (Locale.getDefault().getCountry().equalsIgnoreCase("VN")) {
            double lat = l.getLatitude(), lon = l.getLongitude();
            boolean inVN = (lat >= 8.0 && lat <= 23.5 && lon >= 102.0 && lon <= 110.5);
            if (!inVN) return true;
        }
        return false;
    }

    /** Đảm bảo luôn có 1 locationId hợp lệ (ưu tiên current; nếu chưa có → HCM) */
    private long resolveOrCreateDefaultLocationId() {
        WeatherRepository r = new WeatherRepository(this);
        long id = r.getCurrentLocationIdOrAny();
        if (id != -1) return id;
        return r.insertOrGetLocation(
                "Hồ Chí Minh", "VN", null, null,
                10.776, 106.700, "Asia/Ho_Chi_Minh", true
        );
    }

    /** Đọc DB và hiển thị theo đơn vị trong Setting */
    private void renderFromDb(long locationId) {
        if (locationId == -1) return;

        WeatherRepository.LocationInfo info = repo.getLocation(locationId);
        WeatherRepository.CurrentWeatherData cur = repo.getCurrentWeather(locationId);
        java.util.List<WeatherRepository.DailyForecastData> daily = repo.getDailyForecast(locationId);

        main.post(() -> {
            // Tên địa điểm
            tvCity.setText(info != null && info.name != null ? info.name : "Vị trí của tôi");

            // Ngày hôm nay
            String today = new SimpleDateFormat("EEE, d 'tháng' MM yyyy", new Locale("vi"))
                    .format(new Date());
            tvDate.setText(today);

            // Đơn vị
            String tempUnit = sp.getString(KEY_TEMP_UNIT, "C");   // "C" | "F"
            String windUnit = sp.getString(KEY_WIND_UNIT, "kmh"); // "kmh" | "mph"

            // Nhiệt độ hiện tại
            if (cur != null && cur.tempC != null) {
                double t = cur.tempC;
                String tStr = tempUnit.equals("F")
                        ? String.format(Locale.getDefault(), "%.0f°", (t * 9 / 5) + 32)
                        : String.format(Locale.getDefault(), "%.0f°", t);
                tvTemperature.setText(tStr);
            } else {
                tvTemperature.setText("--°");
            }

            // Mô tả
            tvDescription.setText(cur != null && cur.condition != null ? cur.condition : "");

            // Cao/Thấp + Bình minh/Hoàng hôn (ngày 1)
            if (daily != null && !daily.isEmpty()) {
                WeatherRepository.DailyForecastData d0 = daily.get(0);

                String maxStr = "--", minStr = "--";
                if (d0.tempMaxC != null) {
                    double v = d0.tempMaxC;
                    maxStr = tempUnit.equals("F")
                            ? String.format(Locale.getDefault(), "%.0f°", (v * 9 / 5) + 32)
                            : String.format(Locale.getDefault(), "%.0f°", v);
                }
                if (d0.tempMinC != null) {
                    double v = d0.tempMinC;
                    minStr = tempUnit.equals("F")
                            ? String.format(Locale.getDefault(), "%.0f°", (v * 9 / 5) + 32)
                            : String.format(Locale.getDefault(), "%.0f°", v);
                }
                tvMinMax.setText("Cao: " + maxStr + " · Thấp: " + minStr);

                if (d0.sunriseTs != null)
                    tvSunriseTime.setText(new SimpleDateFormat("HH:mm", Locale.getDefault())
                            .format(new Date(d0.sunriseTs * 1000)));
                else tvSunriseTime.setText("");

                if (d0.sunsetTs != null)
                    tvSunsetTime.setText(new SimpleDateFormat("HH:mm", Locale.getDefault())
                            .format(new Date(d0.sunsetTs * 1000)));
                else tvSunsetTime.setText("");
            } else {
                tvMinMax.setText("");
                tvSunriseTime.setText("");
                tvSunsetTime.setText("");
            }

            // Gió
            if (cur != null && (cur.windKmh != null || cur.windMps != null)) {
                double kmh = cur.windKmh != null ? cur.windKmh : (cur.windMps * 3.6);
                String wind = windUnit.equals("mph")
                        ? String.format(Locale.getDefault(), "%.0f mph", kmh * 0.621371)
                        : String.format(Locale.getDefault(), "%.0f km/h", kmh);
                tvWindValue.setText(wind);
            } else tvWindValue.setText("--");

            // Độ ẩm
            tvHumidityValue.setText(cur != null && cur.humidity != null
                    ? String.format(Locale.getDefault(), "%.0f%%", cur.humidity) : "--");

            // Tầm nhìn
            tvVisibilityValue.setText(cur != null && cur.visibilityKm != null
                    ? String.format(Locale.getDefault(), "%.0f km", cur.visibilityKm) : "--");

            // Hiển thị lượng mưa (mm) thay vì áp suất
            tvPressureValue.setText(cur != null && cur.precipMm != null
                    ? String.format(Locale.getDefault(), "%.1f mm", cur.precipMm)
                    : "-- mm");

            // Icon demo
            imgWeatherIcon.setImageResource(R.drawable.ic_cloud_24);
        });
    }

    // ====== Phần cũ để tương thích lưu/restore nhanh ======
    private void renderLegacy(@NonNull WeatherData d) {
        tvCity.setText(d.city);
        tvTemperature.setText(d.temperature);
        tvDescription.setText(d.description);
        tvMinMax.setText(String.format(Locale.getDefault(), "Cao: %s · Thấp: %s", d.maxTemp, d.minTemp));
        tvWindValue.setText(d.wind);
        tvHumidityValue.setText(d.humidity);
        tvVisibilityValue.setText(d.visibility);
        tvPressureValue.setText(d.pressure);
        tvSunriseTime.setText(d.sunrise);
        tvSunsetTime.setText(d.sunset);
        imgWeatherIcon.setImageResource(R.drawable.ic_cloud_24);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (cached != null) {
            outState.putSerializable("cache", cached);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Đảm bảo nút "Hiện tại" được highlight khi quay lại
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_now);
        }
        loadWeatherWithBestEffort();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        sp.unregisterOnSharedPreferenceChangeListener(prefListener);
        io.shutdownNow();
    }

    // ===== Model cũ =====
    public static class WeatherData implements java.io.Serializable {
        public String city;
        public String temperature;
        public String description;
        public String maxTemp;
        public String minTemp;
        public String wind;
        public String humidity;
        public String visibility;
        public String pressure;
        public String sunrise;
        public String sunset;
    }
}
