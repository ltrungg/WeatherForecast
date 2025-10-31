package com.example.weatherforecast;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {

    private TextView tvVersion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        // Back cứng / gesture: nếu là root -> mở Settings, ngược lại -> finish
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (isTaskRoot()) {
                    startActivity(new Intent(AboutActivity.this, SettingsActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                    finish();
                } else {
                    finish();
                }
            }
        });

        // Click nút back và cả thanh topBar: gọi finish() trực tiếp (an toàn, không phụ thuộc dispatcher)
        ImageView btnBack = findViewById(R.id.btnBack);
        View topBar = findViewById(R.id.topBar);
        View.OnClickListener backClick = v -> finish();
        if (btnBack != null) btnBack.setOnClickListener(backClick);
        if (topBar != null) topBar.setOnClickListener(backClick);

        // Hiển thị phiên bản
        tvVersion = findViewById(R.id.tvVersion);
        String versionName = "1.0.0";
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                versionName = getPackageManager()
                        .getPackageInfo(getPackageName(),
                                PackageManager.PackageInfoFlags.of(0))
                        .versionName;
            } else {
                versionName = getPackageManager()
                        .getPackageInfo(getPackageName(), 0)
                        .versionName;
            }
        } catch (Exception ignored) {}
        tvVersion.setText("Phiên bản " + versionName);
    }
}
