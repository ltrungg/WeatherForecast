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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ChartHelper {
    private static final String TAG = "ChartHelper";

    public static void setupTemperatureChart(LineChart chart, List<WeatherRepository.DailyForecastData> dailyData) {
        if (chart == null || dailyData == null || dailyData.isEmpty()) {
            Log.w(TAG, "Invalid chart or data for temperature chart");
            return;
        }

        try {
            List<Entry> maxTempEntries = new ArrayList<>();
            List<Entry> minTempEntries = new ArrayList<>();
            List<String> labels = new ArrayList<>();

            float minVal = Float.POSITIVE_INFINITY;
            float maxVal = Float.NEGATIVE_INFINITY;

            for (int i = 0; i < dailyData.size(); i++) {
                WeatherRepository.DailyForecastData data = dailyData.get(i);

                // X labels: dùng dateTs nếu có
                labels.add(dayLabelVi(data.dateTs, i));

                if (data.tempMaxC != null) {
                    float v = data.tempMaxC.floatValue();
                    maxTempEntries.add(new Entry(i, v));
                    if (v > maxVal) maxVal = v;
                    if (v < minVal) minVal = v;
                }
                if (data.tempMinC != null) {
                    float v = data.tempMinC.floatValue();
                    minTempEntries.add(new Entry(i, v));
                    if (v > maxVal) maxVal = v;
                    if (v < minVal) minVal = v;
                }
            }

            LineDataSet maxTempDataSet = new LineDataSet(maxTempEntries, "Cao nhất");
            maxTempDataSet.setColor(Color.parseColor("#000000"));
            maxTempDataSet.setLineWidth(0f); // chấm rời
            maxTempDataSet.setCircleColor(Color.parseColor("#000000"));
            maxTempDataSet.setCircleRadius(6f);
            maxTempDataSet.setDrawValues(false);
            maxTempDataSet.setDrawCircles(true);
            maxTempDataSet.setDrawHorizontalHighlightIndicator(false);
            maxTempDataSet.setDrawVerticalHighlightIndicator(false);

            LineDataSet minTempDataSet = new LineDataSet(minTempEntries, "Thấp nhất");
            minTempDataSet.setColor(Color.parseColor("#87CEEB"));
            minTempDataSet.setLineWidth(3f);
            minTempDataSet.setCircleColor(Color.parseColor("#87CEEB"));
            minTempDataSet.setCircleRadius(4f);
            minTempDataSet.setDrawValues(false);
            minTempDataSet.setDrawCircles(true);
            minTempDataSet.setDrawHorizontalHighlightIndicator(false);
            minTempDataSet.setDrawVerticalHighlightIndicator(false);

            LineData lineData = new LineData(maxTempDataSet, minTempDataSet);
            chart.setData(lineData);

            // X axis
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
            xAxis.setAxisMinimum(-0.2f);
            xAxis.setAxisMaximum(labels.size() - 0.8f);
            xAxis.setXOffset(6f);

            // Y axis: dải động theo dữ liệu (phù hợp cả °C/°F)
            YAxis leftAxis = chart.getAxisLeft();
            leftAxis.setTextSize(12f);
            leftAxis.setTextColor(Color.parseColor("#333333"));
            if (minVal == Float.POSITIVE_INFINITY || maxVal == Float.NEGATIVE_INFINITY) {
                // fallback
                leftAxis.setAxisMinimum(0f);
                leftAxis.setAxisMaximum(40f);
            } else {
                float pad = Math.max(1f, (maxVal - minVal) * 0.15f);
                leftAxis.setAxisMinimum((float) Math.floor(minVal - pad));
                leftAxis.setAxisMaximum((float) Math.ceil(maxVal + pad));
            }
            leftAxis.setDrawGridLines(true);
            leftAxis.setGridColor(Color.parseColor("#E0E0E0"));
            leftAxis.setGridLineWidth(1f);
            leftAxis.enableGridDashedLine(10f, 5f, 0f);
            leftAxis.setLabelCount(5, true);

            chart.getAxisRight().setEnabled(false);

            chart.setDescription(null);
            chart.getLegend().setTextSize(12f);
            chart.getLegend().setTextColor(Color.parseColor("#333333"));
            chart.getLegend().setHorizontalAlignment(
                    com.github.mikephil.charting.components.Legend.LegendHorizontalAlignment.CENTER);
            chart.setTouchEnabled(true);
            chart.setDragEnabled(true);
            chart.setScaleEnabled(true);
            chart.setPinchZoom(true);
            chart.setBackgroundColor(Color.TRANSPARENT);
            chart.setDrawGridBackground(false);
            chart.setDrawBorders(false);

            chart.animateY(900, Easing.EaseInOutQuad);
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
            List<BarEntry> entries = new ArrayList<>();
            List<String> labels = new ArrayList<>();

            for (int i = 0; i < dailyData.size(); i++) {
                WeatherRepository.DailyForecastData data = dailyData.get(i);

                float precipitation = 0f;
                if (data.popPct != null) {
                    precipitation = data.popPct.floatValue();
                } else if (data.precipMm != null && data.precipMm > 0) {
                    precipitation = Math.min(data.precipMm.floatValue() * 10f, 80f);
                }
                entries.add(new BarEntry(i, precipitation));
                labels.add(dayLabelVi(data.dateTs, i));
            }

            BarDataSet dataSet = new BarDataSet(entries, "");
            dataSet.setColor(Color.parseColor("#000000"));
            dataSet.setValueTextSize(0f);
            dataSet.setDrawValues(false);

            BarData barData = new BarData(dataSet);
            barData.setBarWidth(0.5f);
            chart.setData(barData);

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
            xAxis.setAxisMinimum(-0.5f);
            xAxis.setAxisMaximum(labels.size() - 0.5f);
            xAxis.setXOffset(6f);

            YAxis leftAxis = chart.getAxisLeft();
            leftAxis.setTextSize(12f);
            leftAxis.setTextColor(Color.parseColor("#333333"));
            leftAxis.setAxisMinimum(0f);
            leftAxis.setAxisMaximum(100f);
            leftAxis.setDrawGridLines(true);
            leftAxis.setGridColor(Color.parseColor("#E0E0E0"));
            leftAxis.setGridLineWidth(1f);
            leftAxis.enableGridDashedLine(10f, 5f, 0f);
            leftAxis.setLabelCount(6, true);

            chart.getAxisRight().setEnabled(false);
            chart.setDescription(null);
            chart.getLegend().setEnabled(false);
            chart.setTouchEnabled(true);
            chart.setDragEnabled(true);
            chart.setScaleEnabled(true);
            chart.setPinchZoom(true);
            chart.setBackgroundColor(Color.TRANSPARENT);
            chart.setDrawGridBackground(false);
            chart.setDrawBorders(false);
            chart.setFitBars(false);

            chart.animateY(900, Easing.EaseInOutQuad);
            chart.invalidate();

        } catch (Exception e) {
            Log.e(TAG, "Error setting up precipitation chart", e);
        }
    }

    // ==== Helpers ====
    private static String dayLabelVi(Long epochSec, int index) {
        if (index == 0) return "Hôm nay";
        if (epochSec == null) return "Ngày " + (index + 1);

        String day = new SimpleDateFormat("EEEE", Locale.getDefault())
                .format(new Date(epochSec * 1000L)).toLowerCase(Locale.getDefault());
        switch (day) {
            case "monday": return "Thứ 2";
            case "tuesday": return "Thứ 3";
            case "wednesday": return "Thứ 4";
            case "thursday": return "Thứ 5";
            case "friday": return "Thứ 6";
            case "saturday": return "Thứ 7";
            case "sunday": return "Chủ nhật";
            default: return day;
        }
    }
}
