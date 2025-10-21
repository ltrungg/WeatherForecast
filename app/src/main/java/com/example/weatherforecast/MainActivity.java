package com.example.weatherforecast;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;


import com.example.weatherforecast.data.WeatherDb;
import com.example.weatherforecast.data.WeatherRepository;
// import com.example.weatherforecast.network.OpenMeteoClient;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    // ==== Preferences keys (đồng bộ với SettingsActivity) ====
    private static final String PREFS = "settings";
    private static final String KEY_TEMP_UNIT = "temp_unit"; // "C" | "F"
    private static final String KEY_WIND_UNIT = "wind_unit"; // "kmh" | "mph"

    private SharedPreferences prefs;
    private WeatherRepository repo;

    // view refs
    private TextView tvCity, tvUpdatedAt, tvTemp, tvCondition, tvFeelsLike, tvHumidity, tvWind, tvVisibility;
    private BottomNavigationView bottomNav;
    private long currentLocationId = -1;

    // KHAI BÁO LAUNCHER MỚI, THAY THẾ CHO onActivityResult
    ActivityResultLauncher<Intent> favoritesActivityLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK) {
                    Intent data = result.getData();
                    if (data != null && data.hasExtra("SELECTED_LOCATION_ID")) {
                        long selectedLocationId = data.getLongExtra("SELECTED_LOCATION_ID", -1);
                        if (selectedLocationId != -1) {
                            currentLocationId = selectedLocationId;
                            loadAndDisplayWeather(currentLocationId);
                        }
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // bind views
        tvCity = findViewById(R.id.tvCity);
        tvUpdatedAt = findViewById(R.id.tvUpdatedAt);
        tvTemp = findViewById(R.id.tvTemp);
        tvCondition = findViewById(R.id.tvCondition);
        tvFeelsLike = findViewById(R.id.tvFeelsLike);
        tvHumidity = findViewById(R.id.tvHumidity);
        tvWind = findViewById(R.id.tvWind);
        tvVisibility = findViewById(R.id.tvVisibility);

        // prefs
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        // ==== Bottom navigation ====
        bottomNav = findViewById(R.id.bottomNav);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_now);
            bottomNav.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.nav_now) {
                    return true; // đang ở màn "Hiện tại"
                } else if (itemId == R.id.nav_daily) {
                    Intent intent = new Intent(this, com.example.weatherforecast.network.DailyActivity.class);
                    intent.putExtra("location_id", currentLocationId > 0 ? currentLocationId : 1);
                    startActivity(intent);
                    return true;
                } else if (itemId == R.id.nav_fav) {
                    Intent intent = new Intent(this, FavoritesActivity.class);
                    favoritesActivityLauncher.launch(intent);
                    return true;
                } else if (itemId == R.id.nav_settings) {
                    startActivity(new Intent(this, SettingsActivity.class));
                    return true;
                }
                return false;
            });
        }

        repo = new WeatherRepository(this);

        WeatherDb.get(this).io().execute(() -> {
            List<WeatherRepository.FavoriteCard> favorites = repo.getFavoritesCards();
            if (!favorites.isEmpty()) {
                currentLocationId = favorites.get(0).locationId;
                loadAndDisplayWeather(currentLocationId);
            } else {
                String defaultCityName = "Hà Nội";
                String defaultCountry = "VN";

                long locId = repo.insertOrGetLocation(
                        defaultCityName, defaultCountry, "", null,
                        21.0285, 105.8542, "Asia/Ho_Chi_Minh", true
                );
                repo.addFavorite(locId, defaultCityName, defaultCountry);
                currentLocationId = locId;
                loadAndDisplayWeather(currentLocationId);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Khi quay lại từ Settings, cập nhật lại hiển thị theo đơn vị mới
        if (currentLocationId != -1) {
            loadAndDisplayWeather(currentLocationId);
        }
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_now);
        }
    }

    // ==== Helpers: format theo Settings ====
    private String formatTemp(Double tempC) {
        if (tempC == null) return "--";
        String unit = prefs.getString(KEY_TEMP_UNIT, "C");
        if ("F".equals(unit)) {
            double f = cToF(tempC);
            return String.format(Locale.getDefault(), "%.0f°", f);
        }
        return String.format(Locale.getDefault(), "%.0f°", tempC);
    }

    private String formatWind(Double windKmh) {
        if (windKmh == null) return "--";
        String unit = prefs.getString(KEY_WIND_UNIT, "kmh");
        if ("mph".equals(unit)) {
            double mph = kmhToMph(windKmh);
            return String.format(Locale.getDefault(), "%.0f mph", mph);
        }
        return String.format(Locale.getDefault(), "%.0f km/h", windKmh);
    }

    private static double cToF(double c) { return c * 9 / 5.0 + 32; }
    private static double kmhToMph(double kmh) { return kmh * 0.621371; }

    private static String fmtTime(long epochSec) {
        Date d = new Date(epochSec * 1000L);
        return new SimpleDateFormat("HH:mm:ss d/M/yyyy", Locale.getDefault()).format(d);
    }

    private void loadAndDisplayWeather(long locationId) {
        WeatherDb.get(this).io().execute(() -> {
            // Lấy thông tin chi tiết của địa điểm (tên,...) và thời tiết hiện tại
            WeatherRepository.LocationInfo locationInfo = repo.getLocation(locationId);
            WeatherRepository.CurrentWeatherData weatherData = repo.getCurrentWeather(locationId);

            runOnUiThread(() -> {
                if (locationInfo == null || weatherData == null) {
                    Toast.makeText(this, "Không tìm thấy dữ liệu thời tiết.", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Cập nhật giao diện với dữ liệu mới
                tvCity.setText(locationInfo.name);
                if (weatherData.updatedAt > 0) {
                    tvUpdatedAt.setText("Cập nhật lúc " + fmtTime(weatherData.updatedAt));
                } else {
                    tvUpdatedAt.setText("Chưa có dữ liệu");
                }

                tvTemp.setText(formatTemp(weatherData.tempC));
                tvCondition.setText(weatherData.condition != null ? weatherData.condition : "");
                tvFeelsLike.setText(weatherData.feelsLikeC != null
                        ? String.format(Locale.getDefault(), "Cảm giác như %s", formatTemp(weatherData.feelsLikeC))
                        : "");
                tvHumidity.setText(weatherData.humidity != null
                        ? String.format(Locale.getDefault(), "%.0f%%", weatherData.humidity) : "--");
                tvWind.setText(formatWind(weatherData.windKmh));
                tvVisibility.setText(weatherData.visibilityKm != null
                        ? String.format(Locale.getDefault(), "%.1f km", weatherData.visibilityKm) : "--");
            });
        });
    }
}