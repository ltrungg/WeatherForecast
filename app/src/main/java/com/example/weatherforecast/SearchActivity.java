package com.example.weatherforecast;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weatherforecast.data.WeatherRepository;
import com.example.weatherforecast.network.OpenMeteoApi;
import com.example.weatherforecast.network.OpenMeteoClient;
import com.example.weatherforecast.network.SearchAdapter;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SearchActivity extends AppCompatActivity {

    private SearchView searchView;
    private RecyclerView recyclerView;
    private SearchAdapter adapter;
    private WeatherRepository repository;
    private List<LocationResponse> searchResults = new ArrayList<>();
    private OpenMeteoApi apiService;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        searchView = findViewById(R.id.search_view);
        recyclerView = findViewById(R.id.recycler_view_search_results);
        apiService = OpenMeteoClient.getApiService();
        repository = new WeatherRepository(this);

        setupRecyclerView();
        setupSearchView();
    }

    private void setupRecyclerView() {
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SearchAdapter(searchResults, location -> {
            Toast.makeText(this, "Đang thêm " + location.getName() + "...", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                long locationId = repository.insertOrGetLocation(
                        location.getName(),
                        location.getCountry(),
                        location.getRegion(),
                        "", // admin2
                        location.getLat(),
                        location.getLon(),
                        "",
                        false
                );

                OpenMeteoClient client = new OpenMeteoClient();
                client.fetchAndStore(
                        location.getLat(),
                        location.getLon(),
                        "auto",
                        locationId,
                        repository
                );
                repository.addFavorite(locationId, location.getName(), location.getCountry());
                runOnUiThread(() -> {
                    Intent resultIntent = new Intent();
                    setResult(Activity.RESULT_OK, resultIntent);
                    finish();
                });
            }).start();
        });
        recyclerView.setAdapter(adapter);
    }

    private void setupSearchView() {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                performSearch(query);
                return true; // Đã xử lý
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                return false;
            }
        });
    }

    private void performSearch(String query) {
        String apiKey = OpenMeteoClient.API_KEY;

        if (apiKey == null || apiKey.equals("YOUR_API_KEY_HERE") || apiKey.isEmpty()) {
            Toast.makeText(this, "API Key không hợp lệ. Vui lòng kiểm tra file OpenMeteoClient.java", Toast.LENGTH_LONG).show();
            Log.e("SearchActivity", "API_KEY chưa được thiết lập trong OpenMeteoClient.java");
            return;
        }

        apiService.searchLocations(apiKey, query).enqueue(new Callback<List<LocationResponse>>() {
            @Override
            public void onResponse(Call<List<LocationResponse>> call, Response<List<LocationResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    searchResults.clear();
                    searchResults.addAll(response.body());
                    adapter.notifyDataSetChanged();
                    if (searchResults.isEmpty()) {
                        Toast.makeText(SearchActivity.this, "Không tìm thấy địa điểm nào", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(SearchActivity.this, "Lỗi khi tìm kiếm: " + response.code(), Toast.LENGTH_SHORT).show();
                    Log.e("SearchActivity", "Lỗi API: " + response.message());
                }
            }

            @Override
            public void onFailure(Call<List<LocationResponse>> call, Throwable t) {
                Toast.makeText(SearchActivity.this, "Lỗi mạng: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                Log.e("SearchActivity", "Lỗi mạng", t);
            }
        });
    }
}
