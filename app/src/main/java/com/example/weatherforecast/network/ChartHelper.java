package com.example.weatherforecast.network;

import android.graphics.Color;
import android.util.Log;

import com.example.weatherforecast.data.WeatherRepository;
import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import java.util.ArrayList;
import java.util.List;

public class ChartHelper {
    private static final String TAG = "ChartHelper";

    public static void setupTemperatureChart(LineChart chart, List<WeatherRepository.DailyForecastData> dailyData) {
        if (chart == null || dailyData == null || dailyData.isEmpty()) {
            Log.w(TAG, "Invalid chart or data for temperature chart");
            return;
        }

        try {
            // Chuẩn bị dữ liệu
            List<Entry> maxTempEntries = new ArrayList<>();
            List<Entry> minTempEntries = new ArrayList<>();
            List<String> labels = new ArrayList<>();

            // Labels theo ngày trong tuần
            String[] dayLabels = {"Hôm nay", "Thứ 6", "Thứ 7", "Chủ nhật", "Thứ 2", "Thứ 3", "Thứ 4"};

            for (int i = 0; i < dailyData.size() && i < dayLabels.length; i++) {
                WeatherRepository.DailyForecastData data = dailyData.get(i);
                
                // Thêm dữ liệu nhiệt độ
                if (data.tempMaxC != null) {
                    maxTempEntries.add(new Entry(i, data.tempMaxC.floatValue()));
                }
                if (data.tempMinC != null) {
                    minTempEntries.add(new Entry(i, data.tempMinC.floatValue()));
                }

                // Thêm label cho ngày
                labels.add(dayLabels[i]);
            }

            // Tạo dataset cho nhiệt độ cao nhất (chỉ điểm, không có đường nối)
            LineDataSet maxTempDataSet = new LineDataSet(maxTempEntries, "Cao nhất");
            maxTempDataSet.setColor(Color.parseColor("#000000"));
            maxTempDataSet.setLineWidth(0f); // Không có đường nối
            maxTempDataSet.setCircleColor(Color.parseColor("#000000"));
            maxTempDataSet.setCircleRadius(6f);
            maxTempDataSet.setDrawValues(false);
            maxTempDataSet.setDrawCircles(true);
            maxTempDataSet.setDrawHorizontalHighlightIndicator(false);
            maxTempDataSet.setDrawVerticalHighlightIndicator(false);

            // Tạo dataset cho nhiệt độ thấp nhất (có đường nối)
            LineDataSet minTempDataSet = new LineDataSet(minTempEntries, "Thấp nhất");
            minTempDataSet.setColor(Color.parseColor("#87CEEB")); // Light blue
            minTempDataSet.setLineWidth(3f);
            minTempDataSet.setCircleColor(Color.parseColor("#87CEEB"));
            minTempDataSet.setCircleRadius(4f);
            minTempDataSet.setDrawValues(false);
            minTempDataSet.setDrawCircles(true);
            minTempDataSet.setDrawHorizontalHighlightIndicator(false);
            minTempDataSet.setDrawVerticalHighlightIndicator(false);

            // Tạo LineData
            LineData lineData = new LineData(maxTempDataSet, minTempDataSet);
            chart.setData(lineData);

            // Cấu hình trục X
            XAxis xAxis = chart.getXAxis();
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
            xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
            xAxis.setGranularity(1f);
            xAxis.setLabelCount(labels.size());
            xAxis.setTextSize(12f);
            xAxis.setTextColor(Color.parseColor("#333333"));
            xAxis.setDrawGridLines(true);
            xAxis.setGridColor(Color.parseColor("#E0E0E0"));
            xAxis.setGridLineWidth(1f);
            xAxis.enableGridDashedLine(10f, 5f, 0f);
            // Tăng khoảng trống 2 đầu để nhãn giãn ra
            xAxis.setAxisMinimum(-0.2f);
            xAxis.setAxisMaximum(labels.size() - 0.8f);
            xAxis.setXOffset(6f);

            // Cấu hình trục Y
            YAxis leftAxis = chart.getAxisLeft();
            leftAxis.setTextSize(12f);
            leftAxis.setTextColor(Color.parseColor("#333333"));
            leftAxis.setAxisMinimum(0f);
            leftAxis.setAxisMaximum(40f);
            leftAxis.setDrawGridLines(true);
            leftAxis.setGridColor(Color.parseColor("#E0E0E0"));
            leftAxis.setGridLineWidth(1f);
            leftAxis.enableGridDashedLine(10f, 5f, 0f);
            leftAxis.setLabelCount(5, true);

            YAxis rightAxis = chart.getAxisRight();
            rightAxis.setEnabled(false);

            // Cấu hình chart
            chart.setDescription(null);
            chart.getLegend().setTextSize(12f);
            chart.getLegend().setTextColor(Color.parseColor("#333333"));
            chart.getLegend().setHorizontalAlignment(com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.CENTER);
            chart.setTouchEnabled(true);
            chart.setDragEnabled(true);
            chart.setScaleEnabled(true);
            chart.setPinchZoom(true);
            chart.setBackgroundColor(Color.TRANSPARENT);
            chart.setDrawGridBackground(false);
            chart.setDrawBorders(false);

            // Animation khi hiển thị
            chart.animateY(900, Easing.EaseInOutQuad);

            // Refresh chart
            chart.invalidate();

        } catch (Exception e) {
            Log.e(TAG, "Error setting up temperature chart", e);
        }
    }

    public static void setupPrecipitationChart(BarChart chart, List<WeatherRepository.DailyForecastData> dailyData) {
        if (chart == null || dailyData == null || dailyData.isEmpty()) {
            Log.w(TAG, "Invalid chart or data for precipitation chart");
            return;
        }

        try {
            // Chuẩn bị dữ liệu - sử dụng xác suất mưa thay vì lượng mưa
            List<BarEntry> entries = new ArrayList<>();
            List<String> labels = new ArrayList<>();

            // Labels theo ngày trong tuần
            String[] dayLabels = {"Hôm nay", "Thứ 6", "Thứ 7", "Chủ nhật", "Thứ 2", "Thứ 3", "Thứ 4"};

            for (int i = 0; i < dailyData.size() && i < dayLabels.length; i++) {
                WeatherRepository.DailyForecastData data = dailyData.get(i);
                
                // Sử dụng xác suất mưa (popPct) thay vì lượng mưa
                float precipitation = 0f;
                if (data.popPct != null) {
                    precipitation = data.popPct.floatValue();
                } else if (data.precipMm != null && data.precipMm > 0) {
                    // Nếu không có xác suất mưa, tạo dữ liệu giả dựa trên lượng mưa
                    precipitation = Math.min(data.precipMm.floatValue() * 10, 80f);
                }
                entries.add(new BarEntry(i, precipitation));

                // Thêm label cho ngày
                labels.add(dayLabels[i]);
            }

            // Tạo dataset
            BarDataSet dataSet = new BarDataSet(entries, "");
            dataSet.setColor(Color.parseColor("#000000")); // Màu đen như trong hình
            dataSet.setValueTextSize(0f); // Ẩn giá trị trên cột
            dataSet.setValueTextColor(Color.parseColor("#333333"));
            dataSet.setDrawValues(false);

            // Tạo BarData
            BarData barData = new BarData(dataSet);
            barData.setBarWidth(0.5f); // Thu nhỏ bar để tạo khoảng trống giữa các cột
            chart.setData(barData);

            // Cấu hình trục X
            XAxis xAxis = chart.getXAxis();
            xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
            xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
            xAxis.setGranularity(1f);
            xAxis.setLabelCount(labels.size());
            xAxis.setTextSize(12f);
            xAxis.setTextColor(Color.parseColor("#333333"));
            xAxis.setDrawGridLines(true);
            xAxis.setGridColor(Color.parseColor("#E0E0E0"));
            xAxis.setGridLineWidth(1f);
            xAxis.enableGridDashedLine(10f, 5f, 0f);
            // Tăng khoảng trống 2 đầu để nhãn giãn ra
            xAxis.setAxisMinimum(-0.5f);
            xAxis.setAxisMaximum(labels.size() - 0.5f);
            xAxis.setXOffset(6f);

            // Cấu hình trục Y
            YAxis leftAxis = chart.getAxisLeft();
            leftAxis.setTextSize(12f);
            leftAxis.setTextColor(Color.parseColor("#333333"));
            leftAxis.setAxisMinimum(0f);
            leftAxis.setAxisMaximum(80f);
            leftAxis.setDrawGridLines(true);
            leftAxis.setGridColor(Color.parseColor("#E0E0E0"));
            leftAxis.setGridLineWidth(1f);
            leftAxis.enableGridDashedLine(10f, 5f, 0f);
            leftAxis.setLabelCount(5, true);

            YAxis rightAxis = chart.getAxisRight();
            rightAxis.setEnabled(false);

            // Cấu hình chart
            chart.setDescription(null);
            chart.getLegend().setEnabled(false); // Ẩn legend
            chart.setTouchEnabled(true);
            chart.setDragEnabled(true);
            chart.setScaleEnabled(true);
            chart.setPinchZoom(true);
            chart.setBackgroundColor(Color.TRANSPARENT);
            chart.setDrawGridBackground(false);
            chart.setDrawBorders(false);
            chart.setFitBars(false);

            // Animation khi hiển thị
            chart.animateY(900, Easing.EaseInOutQuad);

            // Refresh chart
            chart.invalidate();

        } catch (Exception e) {
            Log.e(TAG, "Error setting up precipitation chart", e);
        }
    }
}
