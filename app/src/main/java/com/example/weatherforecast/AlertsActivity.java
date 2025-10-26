package com.example.weatherforecast;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weatherforecast.data.AlertsRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AlertsActivity extends AppCompatActivity {
    private AlertsRepository repo;

    private LinearLayout bannerBlocked, formPanel, emptyPanel;
    private Spinner spMetric, spOp, spUnit;
    private EditText etName, etValue;
    private Button btnCreate, btnCreatePrimary, btnCancel;
    private RecyclerView rv;
    private AlertsAdapter adapter;

    // notification permission
    private ActivityResultLauncher<String> notifPermLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_alerts);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.alerts_root), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        repo = new AlertsRepository(this);

        // permission launcher for notifications
        notifPermLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    refreshBanner();
                    if (!granted) Toast.makeText(this, "Bạn đã từ chối quyền thông báo", Toast.LENGTH_SHORT).show();
                });

        bannerBlocked = findViewById(R.id.bannerBlocked);
        formPanel = findViewById(R.id.formPanel);
        emptyPanel = findViewById(R.id.emptyPanel);
        rv = findViewById(R.id.recycler);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AlertsAdapter();
        rv.setAdapter(adapter);

        // Form controls
        etName = findViewById(R.id.etName);
        etValue = findViewById(R.id.etValue);
        etValue.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        spMetric = findViewById(R.id.spMetric);
        spOp = findViewById(R.id.spOp);
        spUnit = findViewById(R.id.spUnit);
        btnCreate = findViewById(R.id.btnCreate);
        btnCreatePrimary = findViewById(R.id.btnCreatePrimary);
        btnCancel = findViewById(R.id.btnCancel);

        // metric spinner
        ArrayAdapter<String> metricAd = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Nhiệt độ", "Mưa", "Gió"});
        spMetric.setAdapter(metricAd);
        spMetric.setSelection(0);
        spMetric.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (pos == 0) {                 // temp
                    spUnit.setAdapter(new ArrayAdapter<>(AlertsActivity.this,
                            android.R.layout.simple_spinner_dropdown_item,
                            new String[]{"°C", "°F"}));
                } else if (pos == 1) {          // rain prob
                    spUnit.setAdapter(new ArrayAdapter<>(AlertsActivity.this,
                            android.R.layout.simple_spinner_dropdown_item,
                            new String[]{"%"}));
                } else {                         // wind
                    spUnit.setAdapter(new ArrayAdapter<>(AlertsActivity.this,
                            android.R.layout.simple_spinner_dropdown_item,
                            new String[]{"km/h", "mph"}));
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        spOp.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Trên", "Dưới"}));

        // Buttons
        btnCreatePrimary.setOnClickListener(v -> showForm(true));
        findViewById(R.id.btnCreateTop).setOnClickListener(v -> showForm(true));
        btnCancel.setOnClickListener(v -> showForm(false));
        btnCreate.setOnClickListener(v -> doCreate());

        // back
        findViewById(R.id.btnBack).setOnClickListener(v -> onBackPressed());

        refreshBanner();
        loadRules();
    }

    private void refreshBanner() {
        boolean blocked = !NotificationManagerCompat.from(this).areNotificationsEnabled() ||
                (Build.VERSION.SDK_INT >= 33 &&
                        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                                != PackageManager.PERMISSION_GRANTED);

        bannerBlocked.setVisibility(blocked ? View.VISIBLE : View.GONE);
        if (blocked) {
            bannerBlocked.setOnClickListener(v -> {
                if (Build.VERSION.SDK_INT >= 33) {
                    notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                } else {
                    startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
                }
            });
        }
    }

    private void showForm(boolean show) {
        formPanel.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void loadRules() {
        List<AlertsRepository.AlertRule> items = repo.listAll();
        adapter.submit(items);
        emptyPanel.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void doCreate() {
        String name = etName.getText().toString().trim();
        String metric;
        switch (spMetric.getSelectedItemPosition()) {
            case 0: metric = "temp"; break;
            case 1: metric = "rain_prob"; break;
            default: metric = "wind"; break;
        }
        String op = spOp.getSelectedItemPosition() == 0 ? ">" : "<";
        String unitDisplay = String.valueOf(spUnit.getSelectedItem());

        String valStr = etValue.getText().toString().trim();
        if (valStr.isEmpty()) { etValue.setError("Nhập giá trị"); return; }

        double valueDisplay = Double.parseDouble(valStr);
        double valueBase = valueDisplay;

        // ---- chuẩn hoá về đơn vị gốc để lưu vào threshold ----
        if (metric.equals("temp")) {
            if ("°F".equals(unitDisplay)) valueBase = (valueDisplay - 32) * 5.0 / 9.0; // -> °C
        } else if (metric.equals("wind")) {
            if ("km/h".equals(unitDisplay)) valueBase = valueDisplay / 3.6;            // -> m/s
            else if ("mph".equals(unitDisplay)) valueBase = valueDisplay * 0.44704;    // -> m/s
        } // rain_prob: %, không đổi

        AlertsRepository.AlertRule r = new AlertsRepository.AlertRule();
        r.name = name.isEmpty() ? defaultName(metric, op, valueDisplay, unitDisplay) : name;
        r.locationId = null; // vị trí hiện tại
        r.metric = metric;
        r.op = op;
        r.threshold = valueBase;          // lưu theo đơn vị gốc
        r.unit = unitDisplay;             // lưu đơn vị hiển thị người dùng chọn
        r.active = true;
        r.rearmMinutes = 60;
        repo.insert(r);

        Toast.makeText(this, "Đã tạo cảnh báo", Toast.LENGTH_SHORT).show();
        showForm(false);
        etName.setText(""); etValue.setText("");
        loadRules();
    }

    private static String defaultName(String metric, String op, double valueDisplay, String unitDisplay) {
        String metricName = metric.equals("temp") ? "Nhiệt độ" :
                metric.equals("wind") ? "Gió" : "Mưa";
        return String.format(Locale.getDefault(),
                "%s %s %.1f%s",
                metricName, op.equals(">") ? "trên" : "dưới", valueDisplay, unitDisplay);
    }

    // ===== RecyclerView =====
    private class AlertsAdapter extends RecyclerView.Adapter<AlertsVH> {
        private final List<AlertsRepository.AlertRule> data = new ArrayList<>();
        void submit(List<AlertsRepository.AlertRule> items) { data.clear(); if (items != null) data.addAll(items); notifyDataSetChanged(); }
        @Override public AlertsVH onCreateViewHolder(ViewGroup p, int vt) {
            return new AlertsVH(getLayoutInflater().inflate(R.layout.item_alert_rule, p, false));
        }
        @Override public void onBindViewHolder(AlertsVH h, int i) { h.bind(data.get(i)); }
        @Override public int getItemCount() { return data.size(); }
    }
    private class AlertsVH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvSub; Switch swActive; ImageButton btnDel;
        AlertsVH(View v) {
            super(v);
            tvTitle = v.findViewById(R.id.tvTitle);
            tvSub   = v.findViewById(R.id.tvSub);
            swActive= v.findViewById(R.id.swActive);
            btnDel  = v.findViewById(R.id.btnDel);
        }
        void bind(AlertsRepository.AlertRule a) {
            tvTitle.setText(a.name);
            // hiển thị theo đơn vị người dùng đã chọn khi tạo rule
            tvSub.setText(a.metric + " " + a.op + " " + a.threshold + (a.unit == null ? "" : a.unit));
            swActive.setChecked(a.active);
            swActive.setOnCheckedChangeListener((b, is) -> repo.setActive(a.id, is));
            btnDel.setOnClickListener(v -> { repo.delete(a.id); loadRules(); });
        }
    }
}
