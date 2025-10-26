package com.example.weatherforecast.network;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.LayoutAnimationController;
import android.widget.TextView;
import android.widget.ScrollView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.weatherforecast.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.BarChart;

import com.example.weatherforecast.data.WeatherRepository;
import java.util.List;

public class DailyActivity extends AppCompatActivity {

    private WeatherRepository repo;
    private DailyAdapter adapter;
    private BottomNavigationView bottomNav;

    // UI components for toggle functionality
    private MaterialButton btnList;
    private MaterialButton btnChart;
    private RecyclerView recyclerDaily;
    private ScrollView chartContainer;
    private LineChart temperatureChart;
    private BarChart precipitationChart;
    private TextView tvTitle;

    // State management
    private boolean isChartView = false;
    private List<WeatherRepository.DailyForecastData> currentData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("DailyActivity", "DailyActivity onCreate started");
        setContentView(R.layout.daily_weather);

        final androidx.constraintlayout.widget.ConstraintLayout root = findViewById(R.id.rootDaily);

        // Initialize UI components
        recyclerDaily = findViewById(R.id.recyclerDaily);
        recyclerDaily.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DailyAdapter();
        recyclerDaily.setAdapter(adapter);

        // Initialize chart components
        chartContainer = findViewById(R.id.chartContainer);
        temperatureChart = findViewById(R.id.temperatureChart);
        precipitationChart = findViewById(R.id.precipitationChart);
        btnList = findViewById(R.id.btnList);
        btnChart = findViewById(R.id.btnChart);
        tvTitle = findViewById(R.id.tvTitle);

        // Setup toggle buttons
        setupToggleButtons();

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
                List<WeatherRepository.DailyForecastData> list = repo.getDailyForecast(locationId);

                Log.d("DailyActivity", "Forecast loaded from DB, size=" + (list != null ? list.size() : 0));
                runOnUiThread(() -> {
                    // Store data for charts
                    currentData = list;
                    adapter.setData(list);
                    // Sử dụng nền bg_sky cố định cho tất cả trường hợp
                    root.setBackgroundResource(R.drawable.bg_sky);
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

    private void setupToggleButtons() {
        btnList.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchToListView();
            }

        });

        btnChart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchToChartView();
            }
        });

        // Set initial state
        switchToListView();
    }

    private void switchToListView() {
        isChartView = false;

        // Update UI
        recyclerDaily.setVisibility(View.VISIBLE);
        chartContainer.setVisibility(View.GONE);
        tvTitle.setText("Dự báo thời tiết trong 7 ngày");

        // Update button states
        btnList.setBackgroundResource(R.drawable.toggle_button_selected);
        btnList.setTextColor(Color.parseColor("#FFFFFF"));
        btnChart.setBackgroundResource(R.drawable.toggle_button_unselected);
        btnChart.setTextColor(Color.parseColor("#666666"));

        // Thêm hiệu ứng cho danh sách
        LayoutAnimationController animation = android.view.animation.AnimationUtils.loadLayoutAnimation(
                this, R.anim.layout_animation_fall_down);
        recyclerDaily.setLayoutAnimation(animation);
        recyclerDaily.scheduleLayoutAnimation();
    }

    private void switchToChartView() {
        isChartView = true;

        // Update UI
        recyclerDaily.setVisibility(View.GONE);
        chartContainer.setVisibility(View.VISIBLE);
        tvTitle.setText("Dự báo thời tiết trong 7 ngày");

        // Update button states
        btnChart.setBackgroundResource(R.drawable.toggle_button_selected);
        btnChart.setTextColor(Color.parseColor("#FFFFFF"));
        btnList.setBackgroundResource(R.drawable.toggle_button_unselected);
        btnList.setTextColor(Color.parseColor("#666666"));

        // Setup charts with current data
        if (currentData != null && !currentData.isEmpty()) {
            ChartHelper.setupTemperatureChart(temperatureChart, currentData);
            ChartHelper.setupPrecipitationChart(precipitationChart, currentData);
        }
    }
}

