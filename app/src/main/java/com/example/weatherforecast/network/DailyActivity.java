package com.example.weatherforecast.network;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.weatherforecast.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import com.example.weatherforecast.data.WeatherRepository;

public class DailyActivity extends AppCompatActivity {

    private WeatherRepository repo;
    private DailyAdapter adapter;
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("DailyActivity", "DailyActivity onCreate started");
        setContentView(R.layout.daily_weather);

        final androidx.constraintlayout.widget.ConstraintLayout root = findViewById(R.id.rootDaily);
        RecyclerView rv = findViewById(R.id.recyclerDaily);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DailyAdapter();
        rv.setAdapter(adapter);

        bottomNav = findViewById(R.id.bottomNav);
        setupBottomNavigation();

        // Set location name
        TextView tvLocation = findViewById(R.id.tvLocation);
        tvLocation.setText("Hồ Chí Minh");

        repo = new WeatherRepository(this);

        // Load từ database
        final long locationId = getIntent().getLongExtra("location_id", -1) == -1 ? 1 : getIntent().getLongExtra("location_id", -1);
        if (locationId == -1) {
            Log.e("DailyActivity", "Missing location_id! Using default location.");
        }

        new Thread(() -> {
            try {
                Log.d("DailyActivity", "Loading forecast for locationId: " + locationId);

                // Lấy thông tin location (lat/lon/timezone)
                WeatherRepository.LocationInfo info = repo.getLocation(locationId);
                double lat = info != null ? info.lat : 10.776;
                double lon = info != null ? info.lon : 106.700;
                String timezone = (info != null && info.timezone != null && !info.timezone.isEmpty())
                        ? info.timezone : "Asia/Ho_Chi_Minh";

                // Cập nhật tên địa điểm nếu có trong DB
                if (info != null && info.name != null) {
                    runOnUiThread(() -> tvLocation.setText(info.name));
                }

                // Gọi Open-Meteo và lưu DB
                Log.d("DailyActivity", "Calling OpenMeteo for lat=" + lat + ", lon=" + lon + ", tz=" + timezone);
                new OpenMeteoClient().fetchAndStore(lat, lon, timezone, locationId, repo);
                Log.d("DailyActivity", "OpenMeteo fetch completed");

                // Đọc lại DB và hiển thị
                var list = repo.getDailyForecast(locationId);

                // Áp theme theo thời gian/ngày đêm và điều kiện mưa
                boolean isRaining = false;
                if (list != null && !list.isEmpty()) {
                    for (var d : list) {
                        if (d.precipMm != null && d.precipMm > 0) { isRaining = true; break; }
                    }
                }

                // Xác định ngày/đêm theo giờ hiện tại so với sunrise/sunset của ngày đầu tiên
                boolean isNight = false;
                if (list != null && !list.isEmpty()) {
                    var today = list.get(0);
                    long now = System.currentTimeMillis() / 1000L;
                    if (today.sunriseTs != null && today.sunsetTs != null) {
                        isNight = !(now >= today.sunriseTs && now < today.sunsetTs);
                    }
                }

                Log.d("DailyActivity", "Forecast loaded from DB, size=" + (list != null ? list.size() : 0));
                boolean finalIsRaining = isRaining;
                boolean finalIsNight = isNight;
                runOnUiThread(() -> {
                    adapter.setData(list);
                    if (finalIsRaining) {
                        root.setBackgroundResource(R.drawable.bg_daily_rain);
                    } else if (finalIsNight) {
                        root.setBackgroundResource(R.drawable.bg_daily_night);
                    } else {
                        root.setBackgroundResource(R.drawable.bg_daily_day);
                    }
                });
            } catch (Exception e) {
                Log.e("DailyActivity", "Error loading forecast", e);
                runOnUiThread(() -> Log.e("DailyActivity", "Failed to load forecast: " + e.getMessage()));
            }
        }).start();


//        new Thread(() -> {
//            try {
//                var list = repo.getDailyForecast(locationId);
//                Log.d("DailyActivity", "Forecast size = " + (list == null ? "null" : list.size()));
//
//                runOnUiThread(() -> {
//                    if (list == null || list.isEmpty()) {
//                        Log.w("DailyActivity", "No forecast data found for locationId=" + locationId);
//                    }
//                    adapter.setData(list);
//                });
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }).start();



    }


    private void setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_now) {
                // Navigate back to MainActivity
                Log.d("DailyActivity", "Navigating back to MainActivity");
                Intent intent = new Intent(this, com.example.weatherforecast.MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return true;
            } else if (itemId == R.id.nav_daily) {
                // Đang ở trang 7 ngày, đánh dấu đã chọn
                return true;
            } else if (itemId == R.id.nav_settings) {
                // Navigate to SettingsActivity
                Log.d("DailyActivity", "Navigating to SettingsActivity");
                Intent intent = new Intent(this, com.example.weatherforecast.SettingsActivity.class);
                startActivity(intent);
                return true;
            }

            return false;
        });

        // Đánh dấu "7 ngày" đã chọn khi mở màn hình này
        bottomNav.post(() -> bottomNav.setSelectedItemId(R.id.nav_daily));
    }
}

