package com.example.weatherforecast.network;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
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
import com.example.weatherforecast.utils.Units;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

public class DailyActivity extends AppCompatActivity {

    // === Pref keys (khớp Settings/Main) ===
    private static final String PREFS = "settings";
    private static final String KEY_TEMP_UNIT = "temp_unit";     // "C" | "F"
    private static final String KEY_WIND_UNIT = "wind_unit";     // "kmh" | "mph"

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
    private TextView tvLocation;

    // State
    private List<WeatherRepository.DailyForecastData> currentData;
    private long locationId = -1L;

    // Prefs
    private SharedPreferences sp;
    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            (prefs, key) -> {
                if (KEY_TEMP_UNIT.equals(key) || KEY_WIND_UNIT.equals(key)) {
                    if (adapter != null) {
                        adapter.setData(currentData);
                    }
                    if (chartContainer != null && chartContainer.getVisibility() == View.VISIBLE) {
                        renderCharts();
                    }
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("DailyActivity", "DailyActivity onCreate started");
        setContentView(R.layout.daily_weather);

        sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        sp.registerOnSharedPreferenceChangeListener(prefListener);

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
        tvLocation = findViewById(R.id.tvLocation);

        setupToggleButtons();

        bottomNav = findViewById(R.id.bottomNav);
        setupBottomNavigation();

        repo = new WeatherRepository(this);

        // Nhận cả 2 key; nếu không có → tạo/lấy mặc định
        locationId = getIntent().getLongExtra("location_id", -1L);
        if (locationId == -1L)
            locationId = getIntent().getLongExtra(HourlyForecastActivity.EXTRA_LOCATION_ID, -1L);
        if (locationId == -1L)
            locationId = resolveOrCreateDefaultLocationId();

        // Load + render
        loadAndRenderDaily(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (bottomNav != null) bottomNav.setSelectedItemId(R.id.nav_daily);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sp != null) sp.unregisterOnSharedPreferenceChangeListener(prefListener);
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
                Intent it = new Intent(this, HourlyForecastActivity.class);
                it.putExtra(HourlyForecastActivity.EXTRA_LOCATION_ID, locId);
                it.putExtra("location_id", locId);
                startActivity(it);
                finish();
                return true;

            } else if (itemId == R.id.nav_daily) {
                return true;

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
        recyclerDaily.setVisibility(View.VISIBLE);
        chartContainer.setVisibility(View.GONE);
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
        recyclerDaily.setVisibility(View.GONE);
        chartContainer.setVisibility(View.VISIBLE);
        tvTitle.setText("Dự báo thời tiết trong 7 ngày");

        btnChart.setBackgroundResource(R.drawable.toggle_button_selected);
        btnChart.setTextColor(Color.parseColor("#FFFFFF"));
        btnList.setBackgroundResource(R.drawable.toggle_button_unselected);
        btnList.setTextColor(Color.parseColor("#666666"));

        renderCharts();
    }

    private void renderCharts() {
        if (currentData == null || currentData.isEmpty()) return;
        boolean useF = Units.useF(this);

        // Tạo bản sao dữ liệu theo đơn vị mong muốn (giữ nguyên chữ ký ChartHelper cũ: 2 tham số)
        List<WeatherRepository.DailyForecastData> chartData =
                useF ? cloneDailyAsF(currentData) : currentData;

        ChartHelper.setupTemperatureChart(temperatureChart, chartData);
        ChartHelper.setupPrecipitationChart(precipitationChart, currentData);
    }

    /** Load API + DB rồi render lần đầu (hoặc refresh đầy đủ) */
    private void loadAndRenderDaily(View rootBg) {
        new Thread(() -> {
            try {
                Log.d("DailyActivity", "Loading forecast for locationId: " + locationId);

                WeatherRepository.LocationInfo info = repo.getLocation(locationId);
                double lat = info != null ? info.lat : 10.776;
                double lon = info != null ? info.lon : 106.700;
                String timezone = (info != null && info.timezone != null && !info.timezone.isEmpty())
                        ? info.timezone : "Asia/Ho_Chi_Minh";

                if (info != null && info.name != null) {
                    String name = info.name;
                    runOnUiThread(() -> tvLocation.setText(name));
                }

                new OpenMeteoClient().fetchAndStore(lat, lon, timezone, locationId, repo);

                List<WeatherRepository.DailyForecastData> list = repo.getDailyForecast(locationId);

                runOnUiThread(() -> {
                    currentData = list;
                    adapter.setData(list);
                    rootBg.setBackgroundResource(R.drawable.bg_sky);
                    if (chartContainer.getVisibility() == View.VISIBLE) {
                        renderCharts();
                    }
                });
            } catch (Exception e) {
                Log.e("DailyActivity", "Error loading forecast", e);
            }
        }).start();
    }

    // ===== Helpers chuyển đơn vị cho biểu đồ ngày =====
    private static double cToF(double c) { return c * 9 / 5.0 + 32.0; }

    private List<WeatherRepository.DailyForecastData> cloneDailyAsF(List<WeatherRepository.DailyForecastData> src) {
        List<WeatherRepository.DailyForecastData> out = new ArrayList<>(src.size());
        for (WeatherRepository.DailyForecastData d : src) {
            WeatherRepository.DailyForecastData x = new WeatherRepository.DailyForecastData();
            x.dateTs   = d.dateTs;
            x.tempMinC = d.tempMinC == null ? null : cToF(d.tempMinC);
            x.tempMaxC = d.tempMaxC == null ? null : cToF(d.tempMaxC);
            x.popPct   = d.popPct;
            x.precipMm = d.precipMm;
            x.windMps  = d.windMps;
            x.windDeg  = d.windDeg;
            x.windKmh  = d.windKmh;
            x.sunriseTs = d.sunriseTs;
            x.sunsetTs  = d.sunsetTs;
            x.conditionCode = d.conditionCode;
            x.condition     = d.condition;
            x.icon          = d.icon;
            out.add(x);
        }
        return out;
    }
}
