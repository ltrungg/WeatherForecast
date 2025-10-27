package com.example.weatherforecast.utils;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.weatherforecast.FavoritesActivity;
import com.example.weatherforecast.R;
import com.example.weatherforecast.SettingsActivity;
import com.example.weatherforecast.network.DailyActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import com.example.weatherforecast.adapter.HourlyAdapter;
import com.example.weatherforecast.data.WeatherDb;
import com.example.weatherforecast.data.WeatherRepository;
import com.example.weatherforecast.network.OpenMeteoClient;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
public class HourlyForecastActivity extends AppCompatActivity {

    private static final String TAG = "HourlyForecast";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    // Views
    private TextView tvTitle, tvLocation, tvDate;
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private BottomNavigationView bottomNav;

    // Data
    private WeatherRepository repo;
    private HourlyAdapter adapter;
    private long currentLocationId = -1;
    private List<WeatherRepository.HourlyEntry> hourlyData = new ArrayList<>();

    // Location
    private FusedLocationProviderClient fusedLocationClient;
    private double currentLatitude = 10.776; // Default to Ho Chi Minh
    private double currentLongitude = 106.700;
    private String currentLocationName = "Hồ Chí Minh";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_hourly_forecast);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initializeViews();
        initializeLocation();
        initializeData();
        setupRecyclerView();
        setupSwipeRefresh();
        setupBottomNavigation();
        getCurrentLocationAndLoadData();
    }

    private void initializeViews() {
        tvTitle = findViewById(R.id.tvTitle);
        tvLocation = findViewById(R.id.tvLocation);
        tvDate = findViewById(R.id.tvDate);
        recyclerView = findViewById(R.id.recyclerView);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        bottomNav = findViewById(R.id.bottomNav);

        tvTitle.setText("Dự báo theo giờ");
    }

    private void initializeLocation() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
    }

    private void initializeData() {
        repo = new WeatherRepository(this);
        tvDate.setText(getCurrentDateString());
    }

    private void setupRecyclerView() {
        adapter = new HourlyAdapter(hourlyData);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // Set click listener cho cards
        adapter.setOnItemClickListener(this::showHourlyDetail);
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setOnRefreshListener(() -> {
            refreshHourlyData();
        });

        swipeRefreshLayout.setColorSchemeResources(
                R.color.colorPrimary,
                R.color.colorAccent
        );
    }

//    private void setupBottomNavigation() {
//        bottomNav.setOnItemSelectedListener(item -> {
//            int itemId = item.getItemId();
//
//            if (itemId == R.id.nav_hourly) {
//                // Already on hourly screen
//                return true;
//            } else if (itemId == R.id.nav_now) {
//                // Go back to main activity
//                finish();
//                return true;
//            } else if (itemId == R.id.nav_daily) {
//                // TODO: Implement Daily Forecast Activity
//                Toast.makeText(this, "Tính năng dự báo 7 ngày đang phát triển", Toast.LENGTH_SHORT).show();
//                return true;
//            } else if (itemId == R.id.nav_fav) {
//                // TODO: Implement Favorites Activity
//                Toast.makeText(this, "Tính năng yêu thích đang phát triển", Toast.LENGTH_SHORT).show();
//                return true;
//            } else if (itemId == R.id.nav_settings) {
//                // TODO: Implement Settings Activity
//                Toast.makeText(this, "Tính năng cài đặt đang phát triển", Toast.LENGTH_SHORT).show();
//                return true;
//            }
//
//            return false;
//        });

//        // Set current selected item
//        bottomNav.setSelectedItemId(R.id.nav_hourly);
//    }

    // XÓA HÀM CŨ VÀ DÁN TOÀN BỘ HÀM NÀY VÀO HourlyForecastActivity.java

    private void setupBottomNavigation() {
        // Đánh dấu tab "Theo giờ" là đang được chọn
        bottomNav.setSelectedItemId(R.id.nav_hourly);

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            // 1. Nếu đang ở trang hiện tại thì không làm gì
            if (itemId == R.id.nav_hourly) {
                return true;
            }

            // 2. Nếu nhấn "Bây giờ", quay về trang chủ (MainActivity)
            // Cách tốt nhất là dùng finish() để đóng trang hiện tại và quay về trang trước đó.
            if (itemId == R.id.nav_now) {
                finish(); // Đóng Activity này để quay về MainActivity
                return true;
            }

            // 3. Khởi tạo Intent để mở các trang khác
            Intent intent = null;

            if (itemId == R.id.nav_daily) {
                // Chuyển sang trang 7 ngày
                // Sửa dòng này trong hàm setupBottomNavigation() của HourlyForecastActivity
                intent = new Intent(this, com.example.weatherforecast.network.DailyActivity.class);
                // Gửi ID vị trí hiện tại sang cho trang 7 ngày
                if (currentLocationId != -1) {
                    intent.putExtra("LOCATION_ID", currentLocationId);
                }
            } else if (itemId == R.id.nav_fav) {
                // Chuyển sang trang Yêu thích
                intent = new Intent(this, com.example.weatherforecast.FavoritesActivity.class);
            } else if (itemId == R.id.nav_settings) {
                // Chuyển sang trang Cài đặt
                intent = new Intent(this, com.example.weatherforecast.SettingsActivity.class);
            }

            // 4. Khởi động Activity nếu intent đã được tạo
            if (intent != null) {
                startActivity(intent);
                // Thêm finish() nếu bạn muốn đóng trang hiện tại sau khi chuyển
                // finish();
            }

            return true;
        });
    }

    private void loadHourlyData() {
        if (currentLocationId == -1) {
            Toast.makeText(this, "Không tìm thấy vị trí", Toast.LENGTH_SHORT).show();
            return;
        }

        swipeRefreshLayout.setRefreshing(true);

        WeatherDb.get(this).io().execute(() -> {
            try {
                // Load data từ database
                List<WeatherRepository.HourlyEntry> data = getHourlyForecastFromDb(currentLocationId);

                runOnUiThread(() -> {
                    hourlyData.clear();
                    hourlyData.addAll(data);
                    adapter.notifyDataSetChanged();
                    swipeRefreshLayout.setRefreshing(false);

                    if (data.isEmpty()) {
                        Toast.makeText(this, "Không có dữ liệu dự báo", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error loading hourly data", e);
                runOnUiThread(() -> {
                    swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(this, "Lỗi tải dữ liệu", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void refreshHourlyData() {
        if (currentLocationId == -1) {
            swipeRefreshLayout.setRefreshing(false);
            return;
        }

        WeatherDb.get(this).io().execute(() -> {
            try {
                // Gọi API để refresh data với vị trí hiện tại
                new OpenMeteoClient().fetchAndStore(currentLatitude, currentLongitude, "auto", currentLocationId, repo);

                // Load lại data từ database
                List<WeatherRepository.HourlyEntry> data = getHourlyForecastFromDb(currentLocationId);

                runOnUiThread(() -> {
                    hourlyData.clear();
                    hourlyData.addAll(data);
                    adapter.notifyDataSetChanged();
                    swipeRefreshLayout.setRefreshing(false);

                    Toast.makeText(this, "Dữ liệu đã được cập nhật", Toast.LENGTH_SHORT).show();
                });

            } catch (Exception e) {
                Log.e(TAG, "Error refreshing hourly data", e);
                runOnUiThread(() -> {
                    swipeRefreshLayout.setRefreshing(false);
                    Toast.makeText(this, "Lỗi cập nhật dữ liệu", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private List<WeatherRepository.HourlyEntry> getHourlyForecastFromDb(long locationId) {
        return repo.getHourlyForecast(locationId);
    }

    private void showHourlyDetail(WeatherRepository.HourlyEntry hourlyEntry) {
        // TODO: Implement show detail dialog/bottom sheet
        Toast.makeText(this, "Chi tiết giờ " + formatTime(hourlyEntry.ts), Toast.LENGTH_SHORT).show();
    }

    private String getCurrentDateString() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEEE, dd MMMM", Locale.getDefault());
        return sdf.format(new Date());
    }

    private String formatTime(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        return sdf.format(new Date(timestamp * 1000L));
    }

    private void getCurrentLocationAndLoadData() {
        // Check location permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // Request permission
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }

        // Get current location
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                            currentLatitude = location.getLatitude();
                            currentLongitude = location.getLongitude();
                            Log.d(TAG, "Current location: " + currentLatitude + ", " + currentLongitude);

                            // Get location name and load weather data
                            getLocationNameAndLoadData();
                        } else {
                            Log.w(TAG, "Location is null, using default location");
                            getLocationNameAndLoadData();
                        }
                    }
                });
    }

    private void getLocationNameAndLoadData() {
        WeatherDb.get(this).io().execute(() -> {
            try {
                // Get province name from GPS coordinates
                String provinceName = VietnamLocationUtils.getProvinceName(currentLatitude, currentLongitude);

                // Insert or get location in database
                long locationId = repo.insertOrGetLocation(
                        provinceName, "VN", provinceName, null,
                        currentLatitude, currentLongitude, "Asia/Ho_Chi_Minh", true
                );
                currentLocationId = locationId;

                // Fetch weather data from Open-Meteo API
                new OpenMeteoClient().fetchAndStore(currentLatitude, currentLongitude, "auto", locationId, repo);

                // Load hourly data
                runOnUiThread(() -> {
                    tvLocation.setText(provinceName + " • VN");
                    loadHourlyData();
                });

            } catch (Exception e) {
                Log.e(TAG, "Error getting location and loading data", e);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Lỗi lấy vị trí", Toast.LENGTH_SHORT).show();
                    // Fallback to default location
                    currentLatitude = 10.776;
                    currentLongitude = 106.700;
                    tvLocation.setText("Hồ Chí Minh • VN");
                    loadHourlyData();
                });
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, get location
                getCurrentLocationAndLoadData();
            } else {
                // Permission denied, use default location
                Toast.makeText(this, "Không có quyền truy cập vị trí, sử dụng vị trí mặc định", Toast.LENGTH_SHORT).show();
                tvLocation.setText("Hồ Chí Minh • VN");
                loadHourlyData();
            }
        }
    }
}

