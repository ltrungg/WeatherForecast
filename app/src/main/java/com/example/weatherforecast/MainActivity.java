package com.example.weatherforecast;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.weatherforecast.data.WeatherDb;
import com.example.weatherforecast.data.WeatherRepository;
// import com.example.weatherforecast.network.OpenMeteoClient;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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
                } else if (itemId == R.id.nav_settings) {
                    startActivity(new Intent(this, SettingsActivity.class));
                    return true;
                }
                return false;
            });
        }

        repo = new WeatherRepository(this);

        // Tải & hiển thị dữ liệu
        WeatherDb.get(this).io().execute(() -> {
            try {
                long locId = repo.insertOrGetLocation(
                        "Ho Chi Minh City", "VN", "Ho Chi Minh", null,
                        10.776, 106.700, "Asia/Ho_Chi_Minh", true
                );
                repo.addFavorite(locId);
                currentLocationId = locId;

                // (Nếu đã thêm OpenMeteoClient, gọi API; nếu chưa thì comment dòng dưới)
                // new OpenMeteoClient().fetchAndStore(10.776, 106.700, "auto", locId, repo);

                var cards = repo.getFavoritesCards();
                runOnUiThread(() -> {
                    if (cards.isEmpty()) return;
                    var c = cards.get(0);

                    tvCity.setText(c.name);
                    if (c.updatedAt > 0) {
                        tvUpdatedAt.setText("Cập nhật lúc " + fmtTime(c.updatedAt));
                    }
                    tvTemp.setText(formatTemp(c.tempC)); // theo đơn vị đã chọn
                    tvCondition.setText(c.condition != null ? c.condition : "");
                    tvFeelsLike.setText(c.feelsLikeC != null
                            ? String.format(Locale.getDefault(), "Cảm giác như %s", formatTemp(c.feelsLikeC))
                            : "");
                    tvHumidity.setText(c.humidity != null
                            ? String.format(Locale.getDefault(), "%.0f%%", c.humidity) : "--");
                    tvWind.setText(formatWind(c.windKmh)); // theo đơn vị đã chọn
                    tvVisibility.setText(c.visibilityKm != null
                            ? String.format(Locale.getDefault(), "%.1f km", c.visibilityKm) : "--");
                });
            } catch (Exception e) {
                android.util.Log.e("APP", "Init error", e);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Khi quay lại từ Settings, cập nhật lại hiển thị theo đơn vị mới
        WeatherDb.get(this).io().execute(() -> {
            var cards = repo.getFavoritesCards();
            runOnUiThread(() -> {
                if (cards.isEmpty()) return;
                var c = cards.get(0);
                tvTemp.setText(formatTemp(c.tempC));
                tvFeelsLike.setText(c.feelsLikeC != null
                        ? String.format(Locale.getDefault(), "Cảm giác như %s", formatTemp(c.feelsLikeC))
                        : "");
                tvWind.setText(formatWind(c.windKmh));
            });
        });
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
}