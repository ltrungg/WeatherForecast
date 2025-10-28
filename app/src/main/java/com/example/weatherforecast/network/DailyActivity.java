package com.example.weatherforecast.network;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.animation.LayoutAnimationController;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weatherforecast.FavoritesActivity;
import com.example.weatherforecast.HourlyForecastActivity;
import com.example.weatherforecast.MainActivity;
import com.example.weatherforecast.R;
import com.example.weatherforecast.SettingsActivity;
import com.example.weatherforecast.data.WeatherRepository;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

import java.util.List;

public class DailyActivity extends AppCompatActivity {

    private WeatherRepository repo;
    private DailyAdapter adapter;
    private BottomNavigationView bottomNav;

    // UI
    private MaterialButton btnList;
    private MaterialButton btnChart;
    private RecyclerView recyclerDaily;
    private ScrollView chartContainer;
    private LineChart temperatureChart;
    private BarChart precipitationChart;
    private TextView tvTitle;

    // State
    private List<WeatherRepository.DailyForecastData> currentData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("DailyActivity", "DailyActivity onCreate started");
        setContentView(R.layout.daily_weather);

        final androidx.constraintlayout.widget.ConstraintLayout root = findViewById(R.id.rootDaily);

        // Init UI
        recyclerDaily = findViewById(R.id.recyclerDaily);
        recyclerDaily.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DailyAdapter();
        recyclerDaily.setAdapter(adapter);

        chartContainer = findViewById(R.id.chartContainer);
        temperatureChart = findViewById(R.id.temperatureChart);
        precipitationChart = findViewById(R.id.precipitationChart);
        btnList = findViewById(R.id.btnList);
        btnChart = findViewById(R.id.btnChart);
        tvTitle = findViewById(R.id.tvTitle);

        setupToggleButtons();

        bottomNav = findViewById(R.id.bottomNav);
        setupBottomNavigation();

        // Location label
        TextView tvLocation = findViewById(R.id.tvLocation);
        tvLocation.setText("Hồ Chí Minh");

        repo = new WeatherRepository(this);

        // Nhận cả 2 key; nếu không có → tạo/lấy mặc định
        long locationId = getIntent().getLongExtra("location_id", -1L);
        if (locationId == -1L)
            locationId = getIntent().getLongExtra(HourlyForecastActivity.EXTRA_LOCATION_ID, -1L);
        if (locationId == -1L)
            locationId = resolveOrCreateDefaultLocationId();

        final long finalLocationId = locationId;

        new Thread(() -> {
            try {
                Log.d("DailyActivity", "Loading forecast for locationId: " + finalLocationId);

                WeatherRepository.LocationInfo info = repo.getLocation(finalLocationId);
                double lat = info != null ? info.lat : 10.776;
                double lon = info != null ? info.lon : 106.700;
                String timezone = (info != null && info.timezone != null && !info.timezone.isEmpty())
                        ? info.timezone : "Asia/Ho_Chi_Minh";

                if (info != null && info.name != null) {
                    String name = info.name;
                    runOnUiThread(() -> tvLocation.setText(name));
                }

                // Gọi OpenMeteo và lưu DB
                Log.d("DailyActivity", "Calling OpenMeteo for lat=" + lat + ", lon=" + lon + ", tz=" + timezone);
                new OpenMeteoClient().fetchAndStore(lat, lon, timezone, finalLocationId, repo);
                Log.d("DailyActivity", "OpenMeteo fetch completed");

                // Đọc lại DB và hiển thị
                List<WeatherRepository.DailyForecastData> list = repo.getDailyForecast(finalLocationId);
                Log.d("DailyActivity", "Forecast loaded from DB, size=" + (list != null ? list.size() : 0));

                runOnUiThread(() -> {
                    currentData = list;
                    adapter.setData(list);
                    root.setBackgroundResource(R.drawable.bg_sky);
                });
            } catch (Exception e) {
                Log.e("DailyActivity", "Error loading forecast", e);
            }
        }).start();
    }

    private void setupBottomNavigation() {
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            long locId = resolveOrCreateDefaultLocationId();

            if (itemId == R.id.nav_now) {
                startActivity(new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                finish();
                return true;

            } else if (itemId == R.id.nav_hourly) {
                // sang Theo giờ (truyền cả 2 key)
                Intent it = new Intent(this, HourlyForecastActivity.class);
                it.putExtra(HourlyForecastActivity.EXTRA_LOCATION_ID, locId);
                it.putExtra("location_id", locId);
                startActivity(it);
                finish();
                return true;

            } else if (itemId == R.id.nav_daily) {
                return true; // đang ở Daily

            } else if (itemId == R.id.nav_fav) {
                startActivity(new Intent(this, FavoritesActivity.class));
                finish();
                return true;

            } else if (itemId == R.id.nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                finish();
                return true;
            }
            return false;
        });

        bottomNav.post(() -> bottomNav.setSelectedItemId(R.id.nav_daily));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Đảm bảo nút "7 ngày" được highlight khi quay lại
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.nav_daily);
        }
    }

    private long resolveOrCreateDefaultLocationId() {
        WeatherRepository r = new WeatherRepository(this);
        long id = r.getCurrentLocationIdOrAny();
        if (id != -1) return id;
        return r.insertOrGetLocation(
                "Hồ Chí Minh", "VN", null, null,
                10.776, 106.700, "Asia/Ho_Chi_Minh", true
        );
    }

    private void setupToggleButtons() {
        btnList.setOnClickListener(v -> switchToListView());
        btnChart.setOnClickListener(v -> switchToChartView());
        switchToListView();
    }

    private void switchToListView() {
        recyclerDaily.setVisibility(android.view.View.VISIBLE);
        chartContainer.setVisibility(android.view.View.GONE);
        tvTitle.setText("Dự báo thời tiết trong 7 ngày");

        btnList.setBackgroundResource(R.drawable.toggle_button_selected);
        btnList.setTextColor(Color.parseColor("#FFFFFF"));
        btnChart.setBackgroundResource(R.drawable.toggle_button_unselected);
        btnChart.setTextColor(Color.parseColor("#666666"));

        LayoutAnimationController animation =
                android.view.animation.AnimationUtils.loadLayoutAnimation(this, R.anim.layout_animation_fall_down);
        recyclerDaily.setLayoutAnimation(animation);
        recyclerDaily.scheduleLayoutAnimation();
    }

    private void switchToChartView() {
        recyclerDaily.setVisibility(android.view.View.GONE);
        chartContainer.setVisibility(android.view.View.VISIBLE);
        tvTitle.setText("Dự báo thời tiết trong 7 ngày");

        btnChart.setBackgroundResource(R.drawable.toggle_button_selected);
        btnChart.setTextColor(Color.parseColor("#FFFFFF"));
        btnList.setBackgroundResource(R.drawable.toggle_button_unselected);
        btnList.setTextColor(Color.parseColor("#666666"));

        if (currentData != null && !currentData.isEmpty()) {
            ChartHelper.setupTemperatureChart(temperatureChart, currentData);
            ChartHelper.setupPrecipitationChart(precipitationChart, currentData);
        }
    }
}
