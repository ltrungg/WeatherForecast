package com.example.weatherforecast;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weatherforecast.data.GeminiAiHelper;
import com.example.weatherforecast.network.ChatAdapter;

import java.util.ArrayList;
import java.util.List;

public class ChatbotActivity extends AppCompatActivity {

    private RecyclerView recyclerViewChat;
    private EditText editTextMessage;
    private ImageButton buttonSend;
    private ProgressBar progressBar;

    private ChatAdapter chatAdapter;
    private List<ChatMessage> messageList;
    private GeminiAiHelper aiHelper;
    private long locationId;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chatbot);

        // Lấy location_id từ MainActivity
        locationId = getIntent().getLongExtra("location_id", -1);
        if (locationId == -1) {
            Toast.makeText(this, "Lỗi: Không tìm thấy vị trí.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        String myApiKey = "AiBietDau";
        aiHelper = new GeminiAiHelper(this, myApiKey);

        recyclerViewChat = findViewById(R.id.recyclerViewChat);
        editTextMessage = findViewById(R.id.editTextMessage);
        buttonSend = findViewById(R.id.buttonSend);
        progressBar = findViewById(R.id.progressBar);

        setupRecyclerView();
        setupSendButton();

        // Thêm tin nhắn chào mừng từ bot
        addMessageToList(new ChatMessage(
                "Xin chào! Bạn muốn tôi gợi ý lịch trình như thế nào? (Ví dụ: cuối tuần này đi đâu, hôm nay có nên chạy bộ không?)",
                ChatMessage.MessageType.RECEIVED
        ));
    }

    private void setupRecyclerView() {
        messageList = new ArrayList<>();
        chatAdapter = new ChatAdapter(messageList);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        recyclerViewChat.setLayoutManager(layoutManager);
        recyclerViewChat.setAdapter(chatAdapter);
    }

    private void setupSendButton() {
        buttonSend.setOnClickListener(v -> {
            String userQuery = editTextMessage.getText().toString().trim();
            if (userQuery.isEmpty()) {
                return;
            }

            // Thêm tin nhắn của người dùng vào danh sách
            addMessageToList(new ChatMessage(userQuery, ChatMessage.MessageType.SENT));
            editTextMessage.setText("");

            // Hiển thị loading và gọi AI
            progressBar.setVisibility(View.VISIBLE);
            aiHelper.generateSchedule(userQuery, locationId, new GeminiAiHelper.ResponseCallback() {
                @Override
                public void onSuccess(String response) {
                    // Cần chạy trên UI thread để cập nhật giao diện
                    new Handler(Looper.getMainLooper()).post(() -> {
                        progressBar.setVisibility(View.GONE);
                        addMessageToList(new ChatMessage(response, ChatMessage.MessageType.RECEIVED));
                    });
                }

                @Override
                public void onError(Exception e) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        progressBar.setVisibility(View.GONE);
                        addMessageToList(new ChatMessage(
                                "Xin lỗi, đã có lỗi xảy ra. Vui lòng thử lại sau.",
                                ChatMessage.MessageType.RECEIVED
                        ));
                        // Log lỗi để debug
                        e.printStackTrace();
                    });
                }
            });
        });
    }

    private void addMessageToList(ChatMessage message) {
        messageList.add(message);
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        recyclerViewChat.scrollToPosition(messageList.size() - 1);
    }
}
