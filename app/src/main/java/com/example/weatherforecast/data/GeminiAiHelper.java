package com.example.weatherforecast.data;

import android.content.Context;
import com.example.weatherforecast.data.WeatherRepository;
import com.google.ai.client.generativeai.GenerativeModel;
import com.google.ai.client.generativeai.java.GenerativeModelFutures;
import com.google.ai.client.generativeai.type.Content;
import com.google.ai.client.generativeai.type.GenerateContentResponse;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class GeminiAiHelper {

    private final WeatherRepository weatherRepository;
    private final GenerativeModelFutures generativeModel;
    private final Executor executor = Executors.newSingleThreadExecutor();

    // Callback để trả kết quả về cho Activity
    public interface ResponseCallback {
        void onSuccess(String response);
        void onError(Exception e);
    }

    public GeminiAiHelper(Context context, String apiKey) {
        this.weatherRepository = new WeatherRepository(context);
        // Khởi tạo model Gemini
        GenerativeModel gm = new GenerativeModel(
                "gemini-2.5-flash",
                apiKey,
                null, null
        );
        this.generativeModel = GenerativeModelFutures.from(gm);
    }

    public void generateSchedule(String userQuery, long locationId, ResponseCallback callback) {
        // 1. Lấy dữ liệu thời tiết từ Repository của bạn
        String weatherDataString = getFormattedWeatherData(locationId);
        if (weatherDataString.isEmpty()) {
            callback.onError(new Exception("Không có dữ liệu thời tiết cho vị trí này."));
            return;
        }

        // 2. Tạo câu lệnh (prompt) chi tiết cho AI
        String prompt = "Bạn là một trợ lý du lịch AI thông minh và hữu ích."
                + " Dựa vào dữ liệu thời tiết sau đây cho một địa điểm ở Việt Nam:\n"
                + weatherDataString + "\n\n"
                + "Và yêu cầu của người dùng là: \"" + userQuery + "\"\n\n"
                + "Hãy tạo một lịch trình hoặc đưa ra gợi ý chi tiết, hợp lý. Trả lời bằng tiếng Việt."
                + " Giọng văn thân thiện, tự nhiên. Chia các gợi ý theo từng ngày nếu hợp lý."
                + " Nhấn mạnh các cảnh báo thời tiết quan trọng (ví dụ: mưa to, nắng gắt).";

        Content content = new Content.Builder().addText(prompt).build();

        // 3. Gọi API Gemini một cách bất đồng bộ
        ListenableFuture<GenerateContentResponse> future = generativeModel.generateContent(content);
        Futures.addCallback(future, new FutureCallback<GenerateContentResponse>() {
            @Override
            public void onSuccess(GenerateContentResponse result) {
                String responseText = result.getText();
                callback.onSuccess(responseText);
            }

            @Override
            public void onFailure(Throwable t) {
                callback.onError(new Exception(t));
            }
        }, executor);
    }

    // Phương thức này chuyển đổi dữ liệu từ repository thành text cho AI đọc
    private String getFormattedWeatherData(long locationId) {
        List<WeatherRepository.DailyForecastData> forecastList = weatherRepository.getDailyForecast(locationId);
        if (forecastList == null || forecastList.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy (EEE)", new Locale("vi"));

        sb.append("Dữ liệu dự báo thời tiết:\n");
        for (WeatherRepository.DailyForecastData day : forecastList) {
            Date date = new Date(day.dateTs * 1000);
            sb.append(String.format("- Ngày %s: %s. Nhiệt độ cao nhất %.0f°C, thấp nhất %.0f°C. Khả năng mưa %s%%.\n",
                    sdf.format(date),
                    day.condition,
                    day.tempMaxC,
                    day.tempMinC,
                    day.popPct != null ? String.format("%.0f", day.popPct) : "không rõ"
            ));
        }
        return sb.toString();
    }
}
