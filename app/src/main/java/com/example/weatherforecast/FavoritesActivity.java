package com.example.weatherforecast;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ProgressBar;
import android.widget.LinearLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weatherforecast.data.WeatherRepository;
import com.example.weatherforecast.network.DailyActivity;
import com.example.weatherforecast.network.FavoriteAdapter;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class FavoritesActivity extends AppCompatActivity implements FavoriteAdapter.OnFavoriteClickListener {

    private RecyclerView recyclerView;
    private FavoriteAdapter adapter;
    private List<WeatherRepository.FavoriteCard> favoriteCards;
    private WeatherRepository repository;
    private TextView tvSubtitle;
    private BottomNavigationView bottomNavigationView;
    private ActivityResultLauncher<Intent> addLocationLauncher;

    private MaterialButtonToggleGroup toggleGroup;
    private LinearLayout compareContainer;
    private androidx.cardview.widget.CardView cardHighest, cardLowest, cardAverage;
    private boolean isCompareMode = false;
    private ProgressBar progressBar;
    private NestedScrollView nestedScrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_favorites);

        repository = new WeatherRepository(this);

        tvSubtitle = findViewById(R.id.tv_subtitle);
        recyclerView = findViewById(R.id.recycler_view_favorites);
        FloatingActionButton fabAdd = findViewById(R.id.fab_add);
        bottomNavigationView = findViewById(R.id.bottomNav);
        toggleGroup = findViewById(R.id.toggleGroup);
        compareContainer = findViewById(R.id.compareContainer);
        cardHighest = findViewById(R.id.cardHighest);
        cardLowest = findViewById(R.id.cardLowest);
        cardAverage = findViewById(R.id.cardAverage);
        progressBar = findViewById(R.id.progress_bar);
        nestedScrollView = findViewById(R.id.nested_scroll_view);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        addLocationLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Log.d("FavoritesActivity", "New location added. Reloading list...");
                        loadAndDisplayFavorites();
                    }
                });

        fabAdd.setOnClickListener(v -> {
            Intent intent = new Intent(FavoritesActivity.this, SearchActivity.class);
            addLocationLauncher.launch(intent);
        });

        setupBottomNavigation();
        setupToggleGroup();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadAndDisplayFavorites();
    }

    private void setupToggleGroup() {
        if (toggleGroup == null) return;

        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked && adapter != null) {
                if (checkedId == R.id.btnList) {
                    isCompareMode = false;
                    compareContainer.setVisibility(android.view.View.GONE);
                    adapter.setCompareMode(false, favoriteCards);
                } else if (checkedId == R.id.btnCompare) {
                    isCompareMode = true;
                    compareContainer.setVisibility(android.view.View.VISIBLE);
                    updateCompareSummary();
                    adapter.setCompareMode(true, favoriteCards);
                }
            }
        });
    }

    private void setupBottomNavigation() {
        bottomNavigationView.setSelectedItemId(R.id.nav_fav);
        bottomNavigationView.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_now) {
                startActivity(new Intent(FavoritesActivity.this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                finish();
                return true;

            } else if (itemId == R.id.nav_hourly) {
                // ✅ Điều hướng sang trang Theo giờ
                startActivity(new Intent(FavoritesActivity.this, HourlyForecastActivity.class));
                finish();
                return true;

            } else if (itemId == R.id.nav_daily) {
                startActivity(new Intent(FavoritesActivity.this, DailyActivity.class));
                finish();
                return true;

            } else if (itemId == R.id.nav_fav) {
                return true;

            } else if (itemId == R.id.nav_settings) {
                startActivity(new Intent(FavoritesActivity.this, SettingsActivity.class));
                finish();
                return true;
            }
            return false;
        });
    }

    private void loadAndDisplayFavorites() {
        progressBar.setVisibility(android.view.View.VISIBLE);
        recyclerView.setVisibility(android.view.View.GONE);
        compareContainer.setVisibility(android.view.View.GONE);

        new Thread(() -> {
            List<WeatherRepository.FavoriteCard> updatedCards = repository.getFavoritesCards();
            this.favoriteCards = updatedCards;
            runOnUiThread(() -> {
                if (adapter == null) {
                    adapter = new FavoriteAdapter(this, this.favoriteCards, this);
                    recyclerView.setAdapter(adapter);
                } else {
                    adapter.updateData(this.favoriteCards);
                }
                if (isCompareMode) {
                    toggleGroup.check(R.id.btnCompare);
                    compareContainer.setVisibility(android.view.View.VISIBLE);
                    adapter.setCompareMode(true, this.favoriteCards);
                    updateCompareSummary();
                } else {
                    toggleGroup.check(R.id.btnList);
                    compareContainer.setVisibility(android.view.View.GONE);
                    adapter.setCompareMode(false, this.favoriteCards);
                }
                updateSubtitle();
                progressBar.setVisibility(android.view.View.GONE);
                recyclerView.setVisibility(android.view.View.VISIBLE);
                nestedScrollView.setVisibility(android.view.View.VISIBLE);
            });
        }).start();
    }

    private void updateCompareSummary() {
        if (favoriteCards == null || favoriteCards.isEmpty() || compareContainer == null) {
            compareContainer.setVisibility(android.view.View.GONE);
            return;
        }

        WeatherRepository.FavoriteCard highest = Collections.max(
                favoriteCards,
                Comparator.comparing(c -> c.maxTempC != null ? c.maxTempC : Double.MIN_VALUE)
        );
        WeatherRepository.FavoriteCard lowest = Collections.min(
                favoriteCards,
                Comparator.comparing(c -> c.minTempC != null ? c.minTempC : Double.MAX_VALUE)
        );

        double totalTemp = 0;
        int count = 0;
        for (WeatherRepository.FavoriteCard card : favoriteCards) {
            if (card.tempC != null) {
                totalTemp += card.tempC;
                count++;
            }
        }

        TextView tvHighestTitle = cardHighest.findViewById(R.id.tvSummaryTitle);
        TextView tvHighestTemp = cardHighest.findViewById(R.id.tvSummaryTemp);
        TextView tvHighestCity = cardHighest.findViewById(R.id.tvSummaryCity);
        ImageView ivHighestIcon = cardHighest.findViewById(R.id.ivSummaryIcon);
        if (tvHighestTitle != null && tvHighestTemp != null && tvHighestCity != null) {
            tvHighestTitle.setText("Cao nhất");
            tvHighestTemp.setText(String.format(Locale.getDefault(), "%.0f°", highest.maxTempC));
            tvHighestCity.setText(highest.name);
            ivHighestIcon.setVisibility(android.view.View.VISIBLE);
            ivHighestIcon.setImageResource(R.drawable.ic_trend_up);
            ivHighestIcon.setColorFilter(ContextCompat.getColor(this, R.color.hot_trend_color));
            cardHighest.setCardBackgroundColor(ContextCompat.getColor(this, R.color.bg_hot_card));
        }

        TextView tvLowestTitle = cardLowest.findViewById(R.id.tvSummaryTitle);
        TextView tvLowestTemp = cardLowest.findViewById(R.id.tvSummaryTemp);
        TextView tvLowestCity = cardLowest.findViewById(R.id.tvSummaryCity);
        ImageView ivLowestIcon = cardLowest.findViewById(R.id.ivSummaryIcon);
        if (tvLowestTitle != null && tvLowestTemp != null && tvLowestCity != null) {
            tvLowestTitle.setText("Thấp nhất");
            tvLowestTemp.setText(String.format(Locale.getDefault(), "%.0f°", lowest.minTempC));
            tvLowestCity.setText(lowest.name);
            ivLowestIcon.setVisibility(android.view.View.VISIBLE);
            ivLowestIcon.setImageResource(R.drawable.ic_trend_down);
            ivLowestIcon.setColorFilter(ContextCompat.getColor(this, R.color.cold_trend_color));
            cardLowest.setCardBackgroundColor(ContextCompat.getColor(this, R.color.bg_cold_card));
        }

        TextView tvAverageTitle = cardAverage.findViewById(R.id.tvSummaryTitle);
        TextView tvAverageTemp = cardAverage.findViewById(R.id.tvSummaryTemp);
        TextView tvAverageCity = cardAverage.findViewById(R.id.tvSummaryCity);
        ImageView ivAverageIcon = cardAverage.findViewById(R.id.ivSummaryIcon);
        if (tvAverageTitle != null && tvAverageTemp != null && tvAverageCity != null) {
            tvAverageTitle.setText("Trung bình");
            if (count > 0) {
                tvAverageTemp.setText(String.format(Locale.getDefault(), "%.0f°", totalTemp / count));
            } else {
                tvAverageTemp.setText("--°");
            }
            tvAverageCity.setText(count + " vị trí");
            ivAverageIcon.setImageResource(R.drawable.ic_average_temperature);
            ivAverageIcon.setVisibility(android.view.View.VISIBLE);
        }
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
        if (isCompareMode) {
            updateCompareSummary();
        }
        loadAndDisplayFavorites();
    }
}
