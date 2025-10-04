package com.example.weatherforecast;

import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.weatherforecast.data.WeatherDb;
import com.example.weatherforecast.data.WeatherRepository;
import com.example.weatherforecast.network.OpenMeteoClient;

import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private WeatherRepository repo;

    // view refs
    private TextView tvCity, tvUpdatedAt, tvTemp, tvCondition, tvFeelsLike, tvHumidity, tvWind, tvVisibility;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // bind views
        tvCity = findViewById(R.id.tvCity);
        tvUpdatedAt = findViewById(R.id.tvUpdatedAt);
        tvTemp = findViewById(R.id.tvTemp);
        tvCondition = findViewById(R.id.tvCondition);
        tvFeelsLike = findViewById(R.id.tvFeelsLike);
        tvHumidity = findViewById(R.id.tvHumidity);
        tvWind = findViewById(R.id.tvWind);
        tvVisibility = findViewById(R.id.tvVisibility);

        repo = new WeatherRepository(this);

        // Tải & hiển thị dữ liệu
        WeatherDb.get(this).io().execute(() -> {
            try {
                long locId = repo.insertOrGetLocation(
                        "Ho Chi Minh City", "VN", "Ho Chi Minh", null,
                        10.776, 106.700, "Asia/Ho_Chi_Minh", true
                );
                repo.addFavorite(locId);

                // (Nếu đã thêm OpenMeteoClient, gọi API; nếu chưa thì comment dòng dưới)
                // new OpenMeteoClient().fetchAndStore(10.776, 106.700, "auto", locId, repo);

                var cards = repo.getFavoritesCards();
                runOnUiThread(() -> {
                    if (cards.isEmpty()) return;
                    var c = cards.get(0);
                    tvCity.setText(c.name);
                    if (c.updatedAt > 0) {
                        tvUpdatedAt.setText("Cập nhật lúc " + fmtTime(c.updatedAt));
                    }
                    tvTemp.setText(c.tempC != null ? String.format(Locale.getDefault(),"%.0f°", c.tempC) : "--");
                    tvCondition.setText(c.condition != null ? c.condition : "");
                    tvFeelsLike.setText(c.feelsLikeC != null ? String.format(Locale.getDefault(),"Cảm giác như %.0f°", c.feelsLikeC) : "");
                    tvHumidity.setText(c.humidity != null ? String.format(Locale.getDefault(),"%.0f%%", c.humidity) : "--");
                    tvWind.setText(c.windKmh != null ? String.format(Locale.getDefault(),"%.0f km/h", c.windKmh) : "--");
                    tvVisibility.setText(c.visibilityKm != null ? String.format(Locale.getDefault(),"%.1f km", c.visibilityKm) : "--");
                });
            } catch (Exception e) {
                android.util.Log.e("APP", "Init error", e);
            }
        });
    }

    private static String fmtTime(long epochSec) {
        Date d = new Date(epochSec * 1000L);
        return new SimpleDateFormat("HH:mm:ss d/M/yyyy", Locale.getDefault()).format(d);
    }
}