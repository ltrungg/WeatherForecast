package com.example.weatherforecast;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weatherforecast.adapter.HourlyAdapter;
import com.example.weatherforecast.data.WeatherRepository;
import com.example.weatherforecast.network.ChartHelperHourly;
import com.example.weatherforecast.network.DailyActivity;
import com.example.weatherforecast.network.OpenMeteoClient;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HourlyForecastActivity extends AppCompatActivity {

    public static final String EXTRA_LOCATION_ID = "LOCATION_ID";

    private TextView tvLocation, tvDate, tvTitle;
    private RecyclerView recyclerView;
    private MaterialButton btnList, btnChart;
    private android.view.View chartContainer;
    private LineChart tempChartHourly;
    private BarChart precipChartHourly;

    private WeatherRepository repo;
    private HourlyAdapter adapter;
    private long locationId = -1L;

    private List<WeatherRepository.HourlyEntry> currentHourly; // để vẽ chart

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hourly_forecast);

        repo = new WeatherRepository(this);

        // Bind views
        tvTitle = findViewById(R.id.tvTitle);
        tvLocation = findViewById(R.id.tvLocation);
        tvDate = findViewById(R.id.tvDate);

        btnList = findViewById(R.id.btnList);
        btnChart = findViewById(R.id.btnChart);
        chartContainer = findViewById(R.id.chartContainer);
        tempChartHourly = findViewById(R.id.tempChartHourly);
        precipChartHourly = findViewById(R.id.precipChartHourly);

        recyclerView = findViewById(R.id.recyclerView);

        // Header
        tvTitle.setText("Dự báo theo giờ");
        String today = new SimpleDateFormat("EEEE, dd/MM/yyyy", new Locale("vi")).format(new Date());
        tvDate.setText(Character.toUpperCase(today.charAt(0)) + today.substring(1));

        // RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HourlyAdapter(new java.util.ArrayList<>());
        recyclerView.setAdapter(adapter);

        // Toggle actions
        btnList.setOnClickListener(v -> switchToList());
        btnChart.setOnClickListener(v -> switchToChart());
        switchToList(); // mặc định mở tab danh sách

        // BottomNav
        BottomNavigationView bottom = findViewById(R.id.bottomNav);
        bottom.setSelectedItemId(R.id.nav_hourly);
        bottom.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            long locId = resolveOrCreateDefaultLocationId();

            if (id == R.id.nav_now) {
                startActivity(new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                finish();
                return true;

            } else if (id == R.id.nav_hourly) {
                return true; // đang ở đây

            } else if (id == R.id.nav_daily) {
                Intent it = new Intent(this, DailyActivity.class);
                // gửi cả 2 key cho chắc
                it.putExtra(EXTRA_LOCATION_ID, locId);
                it.putExtra("location_id", locId);
                startActivity(it);
                finish();
                return true;

            } else if (id == R.id.nav_fav) {
                startActivity(new Intent(this, FavoritesActivity.class));
                finish();
                return true;

            } else if (id == R.id.nav_settings) {
                startActivity(new Intent(this, SettingsActivity.class));
                finish();
                return true;
            }
            return false;
        });

        // LOCATION_ID: nhận cả 2 key; nếu không có → tạo/lấy mặc định
        long fromIntent = getIntent().getLongExtra(EXTRA_LOCATION_ID, -1L);
        if (fromIntent == -1L)
            fromIntent = getIntent().getLongExtra("location_id", -1L);

        if (fromIntent == -1L) {
            locationId = resolveOrCreateDefaultLocationId();
        } else {
            locationId = fromIntent;
        }

        // Hiển thị tên địa điểm
        WeatherRepository.LocationInfo info = repo.getLocation(locationId);
        if (info != null && info.name != null) {
            tvLocation.setText(info.name);
        }

        // Load lần đầu từ DB
        loadHourly();

        // Gọi API rồi tải lại (chạy nền để không block UI)
        refreshFromNetworkThenReload();

        // Click item demo
        adapter.setOnItemClickListener(entry ->
                Toast.makeText(this,
                        "Giờ " + new SimpleDateFormat("HH:mm", Locale.getDefault())
                                .format(new Date(entry.ts * 1000L)) +
                                " • " + (entry.text != null ? entry.text : ""),
                        Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Đảm bảo nút "Theo giờ" được highlight khi quay lại
        BottomNavigationView bottom = findViewById(R.id.bottomNav);
        if (bottom != null) {
            bottom.setSelectedItemId(R.id.nav_hourly);
        }
    }

    /** Đọc DB và hiển thị danh sách (và biểu đồ nếu tab Biểu đồ đang mở) */
    private void loadHourly() {
        List<WeatherRepository.HourlyEntry> list = repo.getHourlyForecast(locationId);
        currentHourly = list;

        if (list == null || list.isEmpty()) {
            findViewById(R.id.emptyStateLayout).setVisibility(android.view.View.VISIBLE);
        } else {
            findViewById(R.id.emptyStateLayout).setVisibility(android.view.View.GONE);
            adapter.updateData(list);
            // Nếu đang ở tab Biểu đồ thì render ngay
            if (chartContainer.getVisibility() == android.view.View.VISIBLE) {
                renderCharts();
            }
        }
    }

    /** Gọi API ở background rồi đọc lại DB */
    private void refreshFromNetworkThenReload() {
        new Thread(() -> {
            WeatherRepository.LocationInfo li = repo.getLocation(locationId);
            if (li != null) {
                new OpenMeteoClient().fetchAndStore(
                        li.lat,
                        li.lon,
                        (li.timezone != null ? li.timezone : "auto"),
                        locationId,
                        repo
                );
            }
            runOnUiThread(this::loadHourly);
        }).start();
    }

    private void switchToList() {
        recyclerView.setVisibility(android.view.View.VISIBLE);
        chartContainer.setVisibility(android.view.View.GONE);

        btnList.setBackgroundResource(R.drawable.toggle_button_selected);
        btnList.setTextColor(Color.WHITE);
        btnChart.setBackgroundResource(R.drawable.toggle_button_unselected);
        btnChart.setTextColor(Color.parseColor("#666666"));
    }

    private void switchToChart() {
        recyclerView.setVisibility(android.view.View.GONE);
        chartContainer.setVisibility(android.view.View.VISIBLE);

        btnChart.setBackgroundResource(R.drawable.toggle_button_selected);
        btnChart.setTextColor(Color.WHITE);
        btnList.setBackgroundResource(R.drawable.toggle_button_unselected);
        btnList.setTextColor(Color.parseColor("#666666"));

        renderCharts();
    }

    private void renderCharts() {
        if (currentHourly == null || currentHourly.isEmpty()) return;
        // Hiển thị 24 giờ đầu; đổi 48 nếu muốn
        ChartHelperHourly.setupTemperatureChartHourly(tempChartHourly, currentHourly, 24);
        ChartHelperHourly.setupPrecipitationChartHourly(precipChartHourly, currentHourly, 24);
    }

    /** Đảm bảo luôn có 1 locationId hợp lệ */
    private long resolveOrCreateDefaultLocationId() {
        WeatherRepository r = new WeatherRepository(this);
        long id = r.getCurrentLocationIdOrAny();
        if (id != -1) return id;
        return r.insertOrGetLocation(
                "Hồ Chí Minh", "VN", null, null,
                10.776, 106.700, "Asia/Ho_Chi_Minh", true
        );
    }
}
