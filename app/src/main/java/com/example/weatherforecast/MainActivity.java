package com.example.weatherforecast;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.MenuItem;
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

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    // UI: Section 1
    private TextView tvCity, tvDate, tvTemperature, tvDescription, tvMinMax;
    private ImageView imgWeatherIcon;

    // UI: Section 2
    private TextView tvWindLabel, tvWindValue;
    private TextView tvHumidityLabel, tvHumidityValue;
    private TextView tvVisibilityLabel, tvVisibilityValue;
    private TextView tvPressureLabel, tvPressureValue;

    // UI: Sun cards
    private TextView tvSunriseLabel, tvSunriseTime, tvSunsetLabel, tvSunsetTime;

    // Containers
    private ScrollView scrollMain;
    private BottomNavigationView bottomNavigationView;

    // Permissions
    private ActivityResultLauncher<String[]> locationPermissionLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;

    // Threading
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    // Simple cache
    @Nullable private WeatherData cached;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bindViews();
        initPermissionsLaunchers();
        initBottomNav();
        initStaticHeader();

        // Quyền: xin nếu cần rồi load dữ liệu
        requestNeededPermissionsThenLoad();

        // Khôi phục UI nếu đã có
        if (savedInstanceState != null) {
            WeatherData restore = (WeatherData) savedInstanceState.getSerializable("cache");
            if (restore != null) {
                cached = restore;
                render(restore);
            }
        }
    }

    private void bindViews() {
        scrollMain = findViewById(R.id.scrollMain);

        // Section 1
        tvCity = findViewById(R.id.tvCity);
        tvDate = findViewById(R.id.tvDate);
        tvTemperature = findViewById(R.id.tvTemperature);
        tvDescription = findViewById(R.id.tvDescription);
        tvMinMax = findViewById(R.id.tvMinMax);
        imgWeatherIcon = findViewById(R.id.imgWeatherIcon);

        // Section 2
        tvWindLabel = findViewById(R.id.tvWindLabel);
        tvWindValue = findViewById(R.id.tvWindValue);

        tvHumidityLabel = findViewById(R.id.tvHumidityLabel);
        tvHumidityValue = findViewById(R.id.tvHumidityValue);

        tvVisibilityLabel = findViewById(R.id.tvVisibilityLabel);
        tvVisibilityValue = findViewById(R.id.tvVisibilityValue);

        tvPressureLabel = findViewById(R.id.tvPressureLabel);
        tvPressureValue = findViewById(R.id.tvPressureValue);

        // Sun
        tvSunriseLabel = findViewById(R.id.tvSunriseLabel);
        tvSunriseTime = findViewById(R.id.tvSunriseTime);
        tvSunsetLabel = findViewById(R.id.tvSunsetLabel);
        tvSunsetTime = findViewById(R.id.tvSunsetTime);

        bottomNavigationView = findViewById(R.id.bottomNavigation);
    }

    private void initStaticHeader() {
        // City giữ nguyên text hiện tại (“Hà Nội”) nếu bạn chưa có location
        String today = new SimpleDateFormat("EEE, d 'tháng' MM yyyy", new Locale("vi"))
                .format(new Date());
        tvDate.setText(today);
    }

    private void initBottomNav() {
        // Đặt tab mặc định
        bottomNavigationView.setSelectedItemId(R.id.nav_now);

        bottomNavigationView.setOnItemSelectedListener(new BottomNavigationView.OnItemSelectedListener() {
            @Override public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int id = item.getItemId();
                if (id == R.id.nav_now) {
                    // Cuộn lên đầu
                    scrollMain.smoothScrollTo(0, 0);
                    return true;
                } else if (id == R.id.nav_hourly) {
                    // Theo giờ (mở Activity khác nếu đã có)
                    startActivity(new Intent(MainActivity.this, com.example.weatherforecast.network.DailyActivity.class));
                    return true;
                } else if (id == R.id.nav_daily) {
                    // 7 ngày
                    startActivity(new Intent(MainActivity.this, com.example.weatherforecast.network.DailyActivity.class));
                    return true;
                } else if (id == R.id.nav_fav) {
                    startActivity(new Intent(MainActivity.this, FavoritesActivity.class));
                    return true;
                } else if (id == R.id.nav_settings) {
                    startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                    return true;
                }
                return false;
            }
        });
    }

    private void initPermissionsLaunchers() {
        locationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    // Nếu được cấp, load; nếu từ chối, vẫn load data mặc định
                    loadWeatherWithBestEffort();
                });

        if (Build.VERSION.SDK_INT >= 33) {
            notificationPermissionLauncher = registerForActivityResult(
                    new ActivityResultContracts.RequestPermission(),
                    granted -> {
                        // Không bắt buộc cho màn hình này
                    });
        }
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

        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    private void loadWeatherWithBestEffort() {
        // 1) Thử lấy last known location nhanh
        Location loc = getLastKnownLocationSafe();
        // 2) Chạy IO giả lập gọi API, sau đó render
        io.execute(() -> {
            // Giả lập latency 600ms
            try { Thread.sleep(600); } catch (InterruptedException ignored) { }

            // TODO: thay bằng gọi API thực tế (Retrofit/HttpUrlConnection)
            WeatherData data = mockFetch(loc);

            cached = data;
            main.post(() -> render(data));
        });
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
                if (best == null || l.getAccuracy() < best.getAccuracy()) {
                    best = l;
                }
            }
            return best;
        } catch (SecurityException se) {
            return null;
        }
    }

    // Render UI từ model
    private void render(@NonNull WeatherData d) {
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

        // Icon mây đơn giản (có thể map theo code thời tiết)
        imgWeatherIcon.setImageResource(R.drawable.ic_cloud_24);
    }

    // Giả lập dữ liệu API
    private WeatherData mockFetch(@Nullable Location loc) {
        String cityName = "Hà Nội";
        if (loc != null) {
            // Thực tế: reverse geocoding để đổi lat/lon -> city
            // Ở đây chỉ minh họa
            cityName = cityName; // giữ nguyên
        }
        WeatherData w = new WeatherData();
        w.city = cityName;
        w.temperature = "28°";
        w.description = "Nhiều mây";
        w.maxTemp = "32°";
        w.minTemp = "24°";
        w.wind = "12 km/h";
        w.humidity = "75%";
        w.visibility = "10 km";
        w.pressure = "1013 mb";
        w.sunrise = "05:45";
        w.sunset = "17:32";
        return w;
    }

    // Save/restore cache qua xoay màn hình
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (cached != null) {
            outState.putSerializable("cache", cached);
        }
    }

    // Optional: hướng dẫn bật location nếu tắt
    private void ensureLocationEnabledHint() {
        LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
        if (lm == null) return;
        boolean enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        if (!enabled) {
            Toast.makeText(this, "Bật dịch vụ vị trí để định vị chính xác", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Có thể refetch nhẹ khi người dùng quay lại
        if (cached == null) loadWeatherWithBestEffort();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    // ===== Model đơn giản =====
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
