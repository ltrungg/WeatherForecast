package com.example.weatherforecast;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
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
import com.example.weatherforecast.data.WeatherRepository;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AlertsActivity extends AppCompatActivity {

    private AlertsRepository repo;
    // NEW: dùng để lấy current location id cho việc evaluate
    private WeatherRepository weatherRepo;

    private LinearLayout bannerBlocked, formPanel, emptyPanel;
    private Spinner spMetric, spOp, spUnit;
    private EditText etName, etValue;
    private MaterialButton btnCreate, btnCreatePrimary, btnCancel, btnCreateTop;
    private RecyclerView rv;
    private AlertsAdapter adapter;

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
        // NEW
        weatherRepo = new WeatherRepository(this);

        notifPermLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    refreshBanner();
                    if (!granted) Toast.makeText(this, "Bạn đã từ chối quyền thông báo", Toast.LENGTH_SHORT).show();
                });

        // Bind views
        bannerBlocked = findViewById(R.id.bannerBlocked);
        formPanel     = findViewById(R.id.formPanel);
        emptyPanel    = findViewById(R.id.emptyPanel);

        rv = findViewById(R.id.recycler);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AlertsAdapter();
        rv.setAdapter(adapter);

        etName  = findViewById(R.id.etName);
        etValue = findViewById(R.id.etValue);
        etValue.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);

        spMetric = findViewById(R.id.spMetric);
        spOp     = findViewById(R.id.spOp);
        spUnit   = findViewById(R.id.spUnit);

        btnCreate        = findViewById(R.id.btnCreate);
        btnCreatePrimary = findViewById(R.id.btnCreatePrimary);
        btnCancel        = findViewById(R.id.btnCancel);
        btnCreateTop     = findViewById(R.id.btnCreateTop);

        findViewById(R.id.btnBack).setOnClickListener(v -> onBackPressed());

        // Spinners
        ArrayAdapter<String> metricAd = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[]{"Nhiệt độ", "Mưa", "Gió"});
        spMetric.setAdapter(metricAd);
        spMetric.setSelection(0);
        spMetric.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (pos == 0) {
                    spUnit.setAdapter(new ArrayAdapter<>(AlertsActivity.this,
                            android.R.layout.simple_spinner_dropdown_item,
                            new String[]{"°C", "°F"}));
                } else if (pos == 1) {
                    spUnit.setAdapter(new ArrayAdapter<>(AlertsActivity.this,
                            android.R.layout.simple_spinner_dropdown_item,
                            new String[]{"%"}));
                } else {
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
        btnCreateTop.setOnClickListener(v -> showForm(true));
        btnCreatePrimary.setOnClickListener(v -> showForm(true));
        btnCancel.setOnClickListener(v -> showForm(false));
        btnCreate.setOnClickListener(v -> doCreate());

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
                    Intent intent = new Intent();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                        intent.putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
                    } else {
                        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(android.net.Uri.fromParts("package", getPackageName(), null));
                    }
                    startActivity(intent);
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

        // chuẩn hoá về đơn vị gốc để lưu
        if ("temp".equals(metric)) {
            if ("°F".equals(unitDisplay)) valueBase = (valueDisplay - 32) * 5.0 / 9.0; // -> °C
        } else if ("wind".equals(metric)) {
            if ("km/h".equals(unitDisplay)) valueBase = valueDisplay / 3.6;            // -> m/s
            else if ("mph".equals(unitDisplay)) valueBase = valueDisplay * 0.44704;    // -> m/s
        } // rain_prob: %

        AlertsRepository.AlertRule r = new AlertsRepository.AlertRule();
        r.name = name.isEmpty() ? defaultName(metric, op, valueDisplay, unitDisplay) : name;
        r.locationId = null;
        r.metric = metric;
        r.op = op;
        r.threshold = valueBase;  // gốc
        r.unit = unitDisplay;     // hiển thị
        r.active = true;
        r.rearmMinutes = 60;

        repo.insert(r);

        // NEW: đánh giá ngay sau khi tạo rule để bắn thông báo nếu đủ điều kiện
        long locId = resolveOrCreateDefaultLocationId();
        AlertEvaluator.evaluateAndNotify(getApplicationContext(), locId);

        Toast.makeText(this, "Đã tạo cảnh báo", Toast.LENGTH_SHORT).show();
        showForm(false);
        etName.setText(""); etValue.setText("");
        loadRules();
    }

    private static String defaultName(String metric, String op, double valueDisplay, String unitDisplay) {
        String metricName = metric.equals("temp") ? "Nhiệt độ"
                : metric.equals("wind") ? "Gió" : "Mưa";
        return String.format(Locale.getDefault(),
                "%s %s %.1f%s",
                metricName, op.equals(">") ? "trên" : "dưới", valueDisplay, unitDisplay);
    }

    // === LẤY location id như các màn khác ===
    private long resolveOrCreateDefaultLocationId() {
        long id = weatherRepo.getCurrentLocationIdOrAny();
        if (id != -1) return id;
        return weatherRepo.insertOrGetLocation(
                "Hồ Chí Minh", "VN", null, null,
                10.776, 106.700, "Asia/Ho_Chi_Minh", true
        );
    }

    private static double cToF(double c) { return c * 9.0 / 5.0 + 32.0; }
    private static double msToKmh(double ms) { return ms * 3.6; }
    private static double msToMph(double ms) { return ms / 0.44704; }

    /** Hiển thị threshold đúng đơn vị người dùng đã chọn */
    private static String formatSub(AlertsRepository.AlertRule a) {
        String metricLabel;
        switch (a.metric) {
            case "temp":      metricLabel = "Nhiệt độ"; break;
            case "wind":      metricLabel = "Gió"; break;
            case "rain_prob": metricLabel = "Mưa"; break;
            case "feels_like":metricLabel = "Cảm giác"; break;
            case "uvi":       metricLabel = "UVI"; break;
            case "clouds":    metricLabel = "Mây"; break;
            default:          metricLabel = a.metric;
        }
        double displayVal = a.threshold;
        if ("temp".equals(a.metric)) {
            if ("°F".equals(a.unit)) displayVal = cToF(a.threshold);
        } else if ("wind".equals(a.metric)) {
            if ("km/h".equals(a.unit)) displayVal = msToKmh(a.threshold);
            else if ("mph".equals(a.unit)) displayVal = msToMph(a.threshold);
        }
        String unit = a.unit == null ? "" : a.unit;
        return String.format(Locale.getDefault(), "%s %s %.1f%s", metricLabel, a.op, displayVal, unit);
    }

    // ===== RecyclerView =====
    private class AlertsAdapter extends RecyclerView.Adapter<AlertsVH> {
        private final List<AlertsRepository.AlertRule> data = new ArrayList<>();

        void submit(List<AlertsRepository.AlertRule> items) {
            data.clear();
            if (items != null) data.addAll(items);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public AlertsVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View item = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_alert_rule, parent, false);
            return new AlertsVH(item);
        }

        @Override
        public void onBindViewHolder(@NonNull AlertsVH holder, int position) {
            holder.bind(data.get(position));
        }

        @Override
        public int getItemCount() { return data.size(); }
    }

    private class AlertsVH extends RecyclerView.ViewHolder {
        TextView tvTitle, tvSub;
        CompoundButton swActive;
        ImageButton btnDel;

        @SuppressLint("WrongViewCast")
        AlertsVH(View v) {
            super(v);
            tvTitle = v.findViewById(R.id.tvTitle);
            tvSub   = v.findViewById(R.id.tvSub);

            View swView = v.findViewById(R.id.swActive);
            if (swView instanceof CompoundButton) {
                swActive = (CompoundButton) swView;
            }
            btnDel  = v.findViewById(R.id.btnDel);
        }

        void bind(AlertsRepository.AlertRule a) {
            tvTitle.setText(a.name);
            tvSub.setText(formatSub(a));

            if (swActive != null) {
                swActive.setOnCheckedChangeListener(null);
                swActive.setChecked(a.active);
                swActive.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    repo.setActive(a.id, isChecked);
                    // NEW: nếu bật lại rule → evaluate ngay
                    if (isChecked) {
                        long locId = resolveOrCreateDefaultLocationId();
                        AlertEvaluator.evaluateAndNotify(getApplicationContext(), locId);
                    }
                });
            }

            btnDel.setOnClickListener(v -> {
                int pos = getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) {
                    repo.delete(a.id);
                    adapter.data.remove(pos);
                    adapter.notifyItemRemoved(pos);
                    adapter.notifyItemRangeChanged(pos, adapter.data.size());
                    emptyPanel.setVisibility(adapter.data.isEmpty() ? View.VISIBLE : View.GONE);
                }
            });
        }
    }
}
