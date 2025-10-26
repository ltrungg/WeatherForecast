package com.example.weatherforecast;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weatherforecast.data.WeatherRepository;
import com.example.weatherforecast.network.DailyActivity;
import com.example.weatherforecast.network.FavoriteAdapter;
import com.example.weatherforecast.network.OpenMeteoClient;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationBarView;

import java.util.List;

public class FavoritesActivity extends AppCompatActivity implements FavoriteAdapter.OnFavoriteClickListener {

    private RecyclerView recyclerView;
    private FavoriteAdapter adapter;
    private List<WeatherRepository.FavoriteCard> favoriteCards;
    private WeatherRepository repository;
    private TextView tvSubtitle;
    private BottomNavigationView bottomNavigationView;
    private ActivityResultLauncher<Intent> addLocationLauncher;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorites);

        repository = new WeatherRepository(this);

        tvSubtitle = findViewById(R.id.tv_subtitle);
        recyclerView = findViewById(R.id.recycler_view_favorites);
        FloatingActionButton fabAdd = findViewById(R.id.fab_add);
        bottomNavigationView = findViewById(R.id.bottomNav);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        //loadAndDisplayFavorites();
        addLocationLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // Đây là nơi xử lý kết quả trả về từ SearchActivity
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Log.d("FavoritesActivity", "Nhận được tín hiệu có địa điểm mới. Đang tải lại danh sách...");
                        loadAndDisplayFavorites();
                    }
                });

        fabAdd.setOnClickListener(v -> {
            Intent intent = new Intent(FavoritesActivity.this, SearchActivity.class);
            addLocationLauncher.launch(intent);
        });

        setupBottomNavigation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAndDisplayFavorites();
    }

    private void setupBottomNavigation() {
        bottomNavigationView.setSelectedItemId(R.id.nav_fav);

        bottomNavigationView.setOnItemSelectedListener(new NavigationBarView.OnItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                int itemId = item.getItemId();
                if (itemId == R.id.nav_now) {
                    finish();
                    return true;
                } else if (itemId == R.id.nav_hourly) {
                    return false;
                } else if (itemId == R.id.nav_daily) {
                    Intent dailyIntent = new Intent(FavoritesActivity.this, DailyActivity.class);
                    startActivity(dailyIntent);
                    finish();
                    return true;
                } else if (itemId == R.id.nav_fav) {
                    return true;
                } else if (itemId == R.id.nav_settings) {
                    Intent settingsIntent = new Intent(FavoritesActivity.this, SettingsActivity.class);
                    startActivity(settingsIntent);
                    finish();
                    return true;
                }
                return false;
            }
        });
    }

    private void loadAndDisplayFavorites() {
        favoriteCards = repository.getFavoritesCards();

        if (adapter == null) {
            adapter = new FavoriteAdapter(this, favoriteCards, this);
            recyclerView.setAdapter(adapter);
        } else {
            adapter.updateData(favoriteCards);
        }

        updateSubtitle();

        new Thread(() -> {
            OpenMeteoClient openMeteoClient = new OpenMeteoClient();
            for (WeatherRepository.FavoriteCard card : favoriteCards) {
                // Lấy thông tin chi tiết (lat, lon) của địa điểm
                WeatherRepository.LocationInfo locationInfo = repository.getLocation(card.locationId);
                if (locationInfo != null) {
                    Log.d("FavoritesActivity", "Đang cập nhật thời tiết cho: " + locationInfo.name);
                    // Gọi API để lấy dữ liệu mới nhất và lưu vào CSDL
                    openMeteoClient.fetchAndStore(locationInfo.lat, locationInfo.lon, "auto", card.locationId, repository);
                }
            }

            // Sau khi gọi API xong, tải lại dữ liệu từ CSDL và cập nhật giao diện trên luồng chính
            runOnUiThread(() -> {
                List<WeatherRepository.FavoriteCard> updatedCards = repository.getFavoritesCards();
                if (adapter != null) {
                    adapter.updateData(updatedCards);
                }
                Log.d("FavoritesActivity", "Đã cập nhật xong giao diện sau khi gọi API.");
            });
        }).start();
    }

    private void updateSubtitle() {
        if (favoriteCards != null) {
            String subtitleText = favoriteCards.size() + " vị trí đã lưu";
            tvSubtitle.setText(subtitleText);
        }
    }

    @Override
    public void onFavoriteClick(long locationId, String cityName) {
        Intent resultIntent = new Intent();
        resultIntent.putExtra("SELECTED_LOCATION_ID", locationId);
        setResult(Activity.RESULT_OK, resultIntent);
        finish();
    }

    @Override
    public void onFavoriteDelete(long locationId, int position) {
        repository.removeFavorite(locationId);

        favoriteCards.remove(position);

        adapter.notifyItemRemoved(position);

        adapter.notifyItemRangeChanged(position, favoriteCards.size());

        updateSubtitle();

    }
}
