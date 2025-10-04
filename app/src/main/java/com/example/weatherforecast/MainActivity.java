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

import java.util.Collections;

public class MainActivity extends AppCompatActivity {

    private WeatherRepository repo;

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

        repo = new WeatherRepository(this);
        TextView tv = findViewById(R.id.tvStatus);

        // Tạo DB + dữ liệu mẫu trên background thread
        WeatherDb.get(this).io().execute(() -> {
            long locId = repo.insertOrGetLocation(
                    "Ho Chi Minh City", "VN", "Ho Chi Minh", null,
                    10.776, 106.700, "Asia/Ho_Chi_Minh", true
            );
            repo.addFavorite(locId);

            repo.upsertCurrent(
                    locId,
                    System.currentTimeMillis() / 1000L,
                    31.2,
                    36.0,
                    70.0,
                    2.5,
                    180.0,
                    10.0,
                    1005.0,
                    8.0,
                    40.0,
                    0.0,
                    "03d", "Partly Cloudy", "03d"
            );

            WeatherRepository.HourlyEntry h = new WeatherRepository.HourlyEntry();
            h.ts = (System.currentTimeMillis() / 1000L) + 3600;
            h.tempC = 30.5;
            h.code = "03d"; h.text = "Clouds"; h.icon = "03d";
            repo.upsertHourly(locId, Collections.singletonList(h));

            var cards = repo.getFavoritesCards();
            runOnUiThread(() -> {
                if (!cards.isEmpty()) {
                    var c = cards.get(0);
                    tv.setText(c.name + " • " + (c.tempC != null ? c.tempC + "°C" : "--"));
                } else {
                    tv.setText("No data yet");
                }
            });
        });


        int cnt = 0;
        try (android.database.Cursor c =
                     com.example.weatherforecast.data.WeatherDb.get(this)
                             .readable().rawQuery("SELECT COUNT(*) FROM locations", null)) {
            if (c.moveToFirst()) cnt = c.getInt(0);
        }
        android.widget.Toast.makeText(this, "DB OK • locations=" + cnt,
                android.widget.Toast.LENGTH_SHORT).show();


    }
}
