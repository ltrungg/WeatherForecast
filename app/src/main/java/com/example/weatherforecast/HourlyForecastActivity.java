package com.example.weatherforecast;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
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
import com.example.weatherforecast.utils.Units;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import com.example.weatherforecast.AlertEvaluator;

public class HourlyForecastActivity extends AppCompatActivity {

    public static final String EXTRA_LOCATION_ID = "LOCATION_ID";

    private static final String PREFS = "settings";
    private static final String KEY_TEMP_UNIT = "temp_unit";     // "C" | "F"
    private static final String KEY_WIND_UNIT = "wind_unit";     // "kmh" | "mph"

    private TextView tvLocation, tvDate, tvTitle;
    private RecyclerView recyclerView;
    private MaterialButton btnList, btnChart;
    private View chartContainer;
    private LineChart tempChartHourly;
    private BarChart precipChartHourly;

    private WeatherRepository repo;
    private HourlyAdapter adapter;
    private long locationId = -1L;

    private List<WeatherRepository.HourlyEntry> currentHourly;

    private SharedPreferences sp;
    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener =
            (prefs, key) -> {
                if (KEY_TEMP_UNIT.equals(key) || KEY_WIND_UNIT.equals(key)) {
                    if (adapter != null && currentHourly != null) {
                        adapter.updateData(currentHourly);
                    }
                    if (chartContainer != null && chartContainer.getVisibility() == View.VISIBLE) {
                        renderCharts();
                    }
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hourly_forecast);

        repo = new WeatherRepository(this);
        sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        sp.registerOnSharedPreferenceChangeListener(prefListener);

        tvTitle = findViewById(R.id.tvTitle);
        tvLocation = findViewById(R.id.tvLocation);
        tvDate = findViewById(R.id.tvDate);

        btnList = findViewById(R.id.btnList);
        btnChart = findViewById(R.id.btnChart);
        chartContainer = findViewById(R.id.chartContainer);
        tempChartHourly = findViewById(R.id.tempChartHourly);
        precipChartHourly = findViewById(R.id.precipChartHourly);

        recyclerView = findViewById(R.id.recyclerView);

        tvTitle.setText("Dự báo theo giờ");
        String today = new SimpleDateFormat("EEEE, dd/MM/yyyy", new Locale("vi")).format(new Date());
        tvDate.setText(Character.toUpperCase(today.charAt(0)) + today.substring(1));

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HourlyAdapter(new java.util.ArrayList<>());
        recyclerView.setAdapter(adapter);

        btnList.setOnClickListener(v -> switchToList());
        btnChart.setOnClickListener(v -> switchToChart());
        switchToList();

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
                return true;

            } else if (id == R.id.nav_daily) {
                Intent it = new Intent(this, DailyActivity.class);
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

        long fromIntent = getIntent().getLongExtra(EXTRA_LOCATION_ID, -1L);
        if (fromIntent == -1L)
            fromIntent = getIntent().getLongExtra("location_id", -1L);
        locationId = (fromIntent == -1L) ? resolveOrCreateDefaultLocationId() : fromIntent;

        WeatherRepository.LocationInfo info = repo.getLocation(locationId);
        if (info != null && info.name != null) tvLocation.setText(info.name);

        loadHourly();
        refreshFromNetworkThenReload();

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
        BottomNavigationView bottom = findViewById(R.id.bottomNav);
        if (bottom != null) bottom.setSelectedItemId(R.id.nav_hourly);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sp != null) sp.unregisterOnSharedPreferenceChangeListener(prefListener);
    }

    private void loadHourly() {
        List<WeatherRepository.HourlyEntry> list = repo.getHourlyForecast(locationId);
        currentHourly = list;

        if (list == null || list.isEmpty()) {
            findViewById(R.id.emptyStateLayout).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.emptyStateLayout).setVisibility(View.GONE);
            adapter.updateData(list);
            if (chartContainer.getVisibility() == View.VISIBLE) {
                renderCharts();
            }
        }
    }

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
                AlertEvaluator.evaluateAndNotify(getApplicationContext(), locationId);
            }
            runOnUiThread(this::loadHourly);
        }).start();
    }

    private void switchToList() {
        recyclerView.setVisibility(View.VISIBLE);
        chartContainer.setVisibility(View.GONE);

        btnList.setBackgroundResource(R.drawable.toggle_button_selected);
        btnList.setTextColor(Color.WHITE);
        btnChart.setBackgroundResource(R.drawable.toggle_button_unselected);
        btnChart.setTextColor(Color.parseColor("#666666"));
    }

    private void switchToChart() {
        recyclerView.setVisibility(View.GONE);
        chartContainer.setVisibility(View.VISIBLE);

        btnChart.setBackgroundResource(R.drawable.toggle_button_selected);
        btnChart.setTextColor(Color.WHITE);
        btnList.setBackgroundResource(R.drawable.toggle_button_unselected);
        btnList.setTextColor(Color.parseColor("#666666"));

        renderCharts();
    }

    private void renderCharts() {
        if (currentHourly == null || currentHourly.isEmpty()) return;
        boolean useF = Units.useF(this);

        // Dữ liệu truyền vào ChartHelperHourly vẫn theo chữ ký cũ (3 tham số).
        // Nếu chọn °F, tạo bản sao với nhiệt độ đã quy đổi.
        List<WeatherRepository.HourlyEntry> chartData =
                useF ? cloneHourlyAsF(currentHourly) : currentHourly;

        ChartHelperHourly.setupTemperatureChartHourly(tempChartHourly, chartData, 24);
        ChartHelperHourly.setupPrecipitationChartHourly(precipChartHourly, currentHourly, 24);
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

    // ===== Helpers chuyển đơn vị cho biểu đồ giờ =====
    private static double cToF(double c) { return c * 9 / 5.0 + 32.0; }

    private List<WeatherRepository.HourlyEntry> cloneHourlyAsF(List<WeatherRepository.HourlyEntry> src) {
        List<WeatherRepository.HourlyEntry> out = new ArrayList<>(src.size());
        for (WeatherRepository.HourlyEntry h : src) {
            WeatherRepository.HourlyEntry x = new WeatherRepository.HourlyEntry();
            x.ts = h.ts;
            x.tempC = cToF(h.tempC);                // đổi sang °F
            x.humidity = h.humidity;
            x.windMps  = h.windMps;
            x.windDeg  = h.windDeg;
            x.clouds   = h.clouds;
            x.popPct   = h.popPct;
            x.precipMm = h.precipMm;
            x.uvi      = h.uvi;
            x.pressure = h.pressure;
            x.code = h.code;
            x.text = h.text;
            x.icon = h.icon;
            out.add(x);
        }
        return out;
    }
}