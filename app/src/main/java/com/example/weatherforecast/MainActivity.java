package com.example.weatherforecast;

import com.example.weatherforecast.data.WeatherRepository;
import com.example.weatherforecast.utils.HourlyForecastActivity; // << LƯU Ý: Dòng này có vẻ sai đường dẫn
import com.example.weatherforecast.network.OpenMeteoClient;

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

import com.example.weatherforecast.data.WeatherRepository;
import com.example.weatherforecast.utils.HourlyForecastActivity;
import com.example.weatherforecast.network.OpenMeteoClient;
public class MainActivity extends AppCompatActivity {

    private long currentLocationId = -1; // Biến mới
    private WeatherRepository repo;

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
        repo = new WeatherRepository(this);
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
        bottomNavigationView.setSelectedItemId(R.id.nav_now);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.nav_now) {
                scrollMain.smoothScrollTo(0, 0);
                return true;
            }

            // === LOGIC MỚI: KIỂM TRA ID TRƯỚC KHI CHUYỂN TRANG ===
            // Các trang "Theo giờ" và "7 ngày" cần có ID vị trí
            if (id == R.id.nav_hourly || id == R.id.nav_daily) {
                if (currentLocationId == -1) {
                    Toast.makeText(this, "Chưa có dữ liệu vị trí", Toast.LENGTH_SHORT).show();
                    return true; // Dừng lại nếu chưa có ID
                }
            }

            // === LOGIC MỚI: TẠO INTENT VỚI ĐƯỜNG DẪN ĐÚNG ===
            Intent intent = null;
            if (id == R.id.nav_hourly) {
                // 🛑 LƯU Ý: Dòng này trong Code A của bạn bị sai đường dẫn
                intent = new Intent(this, HourlyForecastActivity.class);
                intent.putExtra("LOCATION_ID", currentLocationId);
            } else if (id == R.id.nav_daily) {
                // Sửa đường dẫn đúng cho DailyActivity
                // Giả định DailyActivity nằm trong package 'network'
                intent = new Intent(this, com.example.weatherforecast.network.DailyActivity.class);
                intent.putExtra("LOCATION_ID", currentLocationId);
            } else if (id == R.id.nav_fav) {
                // Giả định FavoritesActivity nằm trong package chính
                intent = new Intent(this, com.example.weatherforecast.FavoritesActivity.class);
            } else if (id == R.id.nav_settings) {
                // Giả định SettingsActivity nằm trong package chính
                intent = new Intent(this, com.example.weatherforecast.SettingsActivity.class);
            }

            if (intent != null) {
                startActivity(intent);
            }

            return true;
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
        // 1) Thử lấy last known location nhanh (Logic từ A)
        Location loc = getLastKnownLocationSafe();

        // 2) Chạy IO (Threading từ A)
        io.execute(() -> {
            try {
                // --- Logic data từ B ---
                // Dùng tọa độ GPS (A), nếu không có thì fallback về tọa độ (B)
                double lat = (loc != null) ? loc.getLatitude() : 10.776; // HCM default
                double lon = (loc != null) ? loc.getLongitude() : 106.700; // HCM default
                String timezone = "auto";
                boolean isCurrent = (loc != null); // Đánh dấu đây là vị trí GPS

                // Vấn đề: Code B cần City/Country để insert, nhưng Code A (GPS) không có.
                // Tạm thời dùng logic của B (hardcode) để chạy.
                // Nâng cấp: Cần một Reverse Geocoder ở đây để đổi lat/lon -> city.
                String cityName = "Ho Chi Minh City";
                String country = "VN";
                String admin1 = "Ho Chi Minh";

                long locId = repo.insertOrGetLocation(
                        cityName, country, admin1, null,
                        lat, lon, timezone, isCurrent
                );
                this.currentLocationId = locId; // <-- Cập nhật ID
                repo.addFavorite(locId); // <-- Thêm yêu thích

                // Gọi API để lấy dữ liệu (từ B)
                new OpenMeteoClient().fetchAndStore(lat, lon, "auto", locId, repo); // <-- GỌI API THẬT

                // Lấy dữ liệu từ DB (từ B)
                var cards = repo.getFavoritesCards(); // <-- LẤY TỪ DB
                if (cards.isEmpty()) {
                    main.post(() -> Toast.makeText(MainActivity.this, "Không có dữ liệu", Toast.LENGTH_SHORT).show());
                    return;
                }
                var c = cards.get(0); // Lấy card đầu tiên

                // --- Bước Thích Ứng (Adapt) ---
                // Chuyển model của B (FavoriteCard) về model của A (WeatherData)
                WeatherData data = new WeatherData();
                data.city = c.name;
                data.temperature = (c.tempC != null) ? String.format(Locale.getDefault(),"%.0f°", c.tempC) : "--";
                data.description = (c.condition != null) ? c.condition : "";
                data.wind = (c.windKmh != null) ? String.format(Locale.getDefault(),"%.0f km/h", c.windKmh) : "--";
                data.humidity = (c.humidity != null) ? String.format(Locale.getDefault(),"%.0f%%", c.humidity) : "--";
                data.visibility = (c.visibilityKm != null) ? String.format(Locale.getDefault(),"%.1f km", c.visibilityKm) : "--";

                // Các trường A cần nhưng B (repo.getFavoritesCards) không cung cấp:
                data.maxTemp = "--°";     // Logic B (FavoriteCard) không có
                data.minTemp = "--°";     // Logic B (FavoriteCard) không có
                data.pressure = "-- mb";  // Logic B (FavoriteCard) không có
                data.sunrise = "--:--";   // Logic B (FavoriteCard) không có
                data.sunset = "--:--";    // Logic B (FavoriteCard) không có

                // Cập nhật cache và render (Logic từ A)
                cached = data;
                main.post(() -> render(data));

            } catch (Exception e) {
                android.util.Log.e("APP", "Fetch error", e);
                main.post(() -> Toast.makeText(MainActivity.this, "Lỗi tải dữ liệu", Toast.LENGTH_SHORT).show());
            }
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
