package com.example.weatherforecast;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import okhttp3.logging.HttpLoggingInterceptor;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weatherforecast.data.WeatherRepository;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AiChatActivity extends AppCompatActivity {

    private static final String MODEL = "gemini-flash-latest";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private final OkHttpClient http = new OkHttpClient.Builder()
            .addInterceptor(new HttpLoggingInterceptor()
                    .setLevel(HttpLoggingInterceptor.Level.BODY))
            .build();
    private RecyclerView rv;
    private ChatAdapter adapter;
    private EditText etInput;
    private View btnSend;
    private TextView btnUseCurrentLoc;

    // Ngữ cảnh vị trí (đọc từ DB khi bấm "Lấy vị trí hiện tại")
    private String locationContext = null;

    // Lịch sử hội thoại (role: user/model)
    private final List<Message> history = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ai_chat);

        rv = findViewById(R.id.recycler);
        etInput = findViewById(R.id.etInput);
        btnSend = findViewById(R.id.btnSend);
        btnUseCurrentLoc = findViewById(R.id.btnUseCurrentLoc);

        adapter = new ChatAdapter();
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setAdapter(adapter);

        // Lời chào
        addModel("Xin chào! Tôi là trợ lý AI về thời tiết. Hãy hỏi tôi bất cứ điều gì!");

        btnUseCurrentLoc.setOnClickListener(v -> {
            WeatherRepository repo = new WeatherRepository(this);
            long id = repo.getCurrentLocationIdOrAny();
            WeatherRepository.LocationInfo li = id != -1 ? repo.getLocation(id) : null;

            if (li == null) {
                locationContext = null;
                addModel("Chưa có vị trí hiện tại trong DB. Hãy bật vị trí tự động hoặc chọn từ Yêu thích.");
            } else {
                String tz = (li.timezone == null || li.timezone.isEmpty())
                        ? "Asia/Ho_Chi_Minh" : li.timezone;
                locationContext = String.format(Locale.getDefault(),
                        "Vị trí hiện tại của người dùng: %s (lat=%.4f, lon=%.4f, tz=%s).",
                        (li.name == null ? "Không tên" : li.name), li.lat, li.lon, tz);
                addModel("Đã lấy vị trí: " + li.name + String.format(" (%.4f, %.4f, %s)", li.lat, li.lon, tz));
            }
        });

        btnSend.setOnClickListener(v -> {
            String text = etInput.getText().toString().trim();
            if (TextUtils.isEmpty(text)) return;
            etInput.setText("");
            addUser(text);
            callGemini(text);
        });
    }

    private void addUser(String t) {
        history.add(new Message("user", t));
        adapter.add(new ChatBubble(t, true));
        rv.scrollToPosition(adapter.getItemCount() - 1);
    }

    private void addModel(String t) {
        history.add(new Message("model", t));
        adapter.add(new ChatBubble(t, false));
        rv.scrollToPosition(adapter.getItemCount() - 1);
    }

    private void callGemini(String userText) {
        // Kết hợp vị trí (nếu có) vào prompt đầu vào
        String finalPrompt = (locationContext == null)
                ? userText
                : locationContext + "\n\n" +
                "Hãy dùng thông tin vị trí trên để trả lời ngắn gọn, rõ ràng, ưu tiên tiếng Việt.\n\n" + userText;

        new Thread(() -> {
            try {
                String apiKey = BuildConfig.GOOGLE_AI_KEY;
                if (apiKey == null || apiKey.isEmpty()) {
                    // Fallback từ strings.xml (demo)
                    apiKey = getString(R.string.google_ai_key);
                }

                JSONObject root = new JSONObject();
                JSONArray contents = new JSONArray();

                // Duyệt lịch sử thành mảng contents theo format Gemini
                // (chỉ gửi vài lượt gần nhất để ngắn gọn)
                int start = Math.max(0, history.size() - 10);
                for (int i = start; i < history.size(); i++) {
                    Message m = history.get(i);
                    JSONObject c = new JSONObject();
                    c.put("role", m.role);
                    JSONArray parts = new JSONArray();
                    JSONObject part = new JSONObject();
                    part.put("text", m.text);
                    parts.put(part);
                    c.put("parts", parts);
                    contents.put(c);
                }

                // Thêm lượt user hiện tại với prompt đã gộp vị trí
                JSONObject c = new JSONObject();
                c.put("role", "user");
                JSONArray parts = new JSONArray();
                JSONObject part = new JSONObject();
                part.put("text", finalPrompt);
                parts.put(part);
                c.put("parts", parts);
                contents.put(c);

                root.put("contents", contents);

                Request req = new Request.Builder()
                        .url("https://generativelanguage.googleapis.com/v1beta/models/"
                                + MODEL + ":generateContent?key=" + apiKey)
                        .post(RequestBody.create(root.toString(), JSON))
                        .build();

                try (Response resp = http.newCall(req).execute()) {
                    String body = resp.body() != null ? resp.body().string() : "";
                    if (!resp.isSuccessful()) {
                        String serverMsg = extractErrorMessage(body);
                        final String display = "Lỗi gọi AI: HTTP " + resp.code()
                                + (serverMsg == null ? "" : (" – " + serverMsg));
                        runOnUiThread(() -> addModel(display));
                        return;
                    }
                    String text = parseGeminiText(body);
                    if (TextUtils.isEmpty(text)) text = "Xin lỗi, tôi chưa hiểu. Bạn có thể hỏi lại không?";
                    String finalText = text;
                    runOnUiThread(() -> addModel(finalText));
                }
            } catch (IOException ioe) {
                runOnUiThread(() -> addModel("Mạng lỗi: " + ioe.getMessage()));
            } catch (Exception e) {
                runOnUiThread(() -> addModel("Đã xảy ra lỗi: " + e.getMessage()));
            }
        }).start();
    }

    private String extractErrorMessage(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONObject err = root.optJSONObject("error");
            if (err != null) {
                String msg = err.optString("message", null);
                String status = err.optString("status", null);
                if (msg != null && status != null) return status + ": " + msg;
                if (msg != null) return msg;
            }
            return json != null && !json.isEmpty() ? json : null;
        } catch (Exception ignore) { return null; }
    }
    private String parseGeminiText(String json) {
        try {
            JSONObject root = new JSONObject(json);
            JSONArray candidates = root.optJSONArray("candidates");
            if (candidates == null || candidates.length() == 0) return null;
            JSONObject content = candidates.getJSONObject(0).optJSONObject("content");
            if (content == null) return null;
            JSONArray parts = content.optJSONArray("parts");
            if (parts == null || parts.length() == 0) return null;
            return parts.getJSONObject(0).optString("text", null);
        } catch (Exception e) {
            return null;
        }
    }

    // ====== models & adapter tối giản ======
    static class Message {
        final String role; // "user" | "model"
        final String text;
        Message(String r, String t) { role = r; text = t; }
    }

    static class ChatBubble {
        final String text;
        final boolean mine;
        ChatBubble(String t, boolean m) { text = t; mine = m; }
    }

    static class ChatAdapter extends RecyclerView.Adapter<ChatVH> {
        private final List<ChatBubble> data = new ArrayList<>();
        @Override public ChatVH onCreateViewHolder(android.view.ViewGroup p, int v) {
            android.view.View view = android.view.LayoutInflater.from(p.getContext())
                    .inflate(R.layout.item_chat_bubble, p, false);
            return new ChatVH(view);
        }
        @Override public void onBindViewHolder(ChatVH h, int pos) { h.bind(data.get(pos)); }
        @Override public int getItemCount() { return data.size(); }
        void add(ChatBubble b) { data.add(b); notifyItemInserted(data.size()-1); }
    }
    static class ChatVH extends RecyclerView.ViewHolder {
        TextView tv;
        View bubble;
        ChatVH(View v) { super(v); tv = v.findViewById(R.id.tvMsg); bubble = v.findViewById(R.id.bubble); }
        void bind(ChatBubble b) {
            tv.setText(b.text);
            // canh lề & màu đơn giản
            android.view.ViewGroup.MarginLayoutParams lp = (android.view.ViewGroup.MarginLayoutParams) bubble.getLayoutParams();
            if (b.mine) {
                lp.setMarginStart(64); lp.setMarginEnd(8);
                bubble.setBackgroundResource(R.drawable.bg_bubble_user);
            } else {
                lp.setMarginStart(8); lp.setMarginEnd(64);
                bubble.setBackgroundResource(R.drawable.bg_bubble_bot);
            }
            bubble.setLayoutParams(lp);
        }
    }
}
