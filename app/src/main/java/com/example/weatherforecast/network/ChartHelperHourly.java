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

public class ChartHelperHourly {

    private static final String TAG = "ChartHelperHourly";

    public static void setupTemperatureChartHourly(LineChart chart,
                                                   List<WeatherRepository.HourlyEntry> data,
                                                   int maxHours) {
        if (chart == null || data == null || data.isEmpty()) return;
        try {
            int n = Math.min(maxHours, data.size());
            List<Entry> tempEntries = new ArrayList<>();
            List<String> labels = new ArrayList<>();

            SimpleDateFormat hh = new SimpleDateFormat("HH:mm", Locale.getDefault());
            for (int i = 0; i < n; i++) {
                WeatherRepository.HourlyEntry e = data.get(i);
                tempEntries.add(new Entry(i, (float) e.tempC));
                labels.add(hh.format(new Date(e.ts * 1000L)));
            }

            LineDataSet tempSet = new LineDataSet(tempEntries, "Nhiệt độ (°C)");
            tempSet.setColor(Color.parseColor("#000000"));
            tempSet.setLineWidth(3f);
            tempSet.setCircleColor(Color.parseColor("#000000"));
            tempSet.setCircleRadius(3.5f);
            tempSet.setDrawValues(false);
            tempSet.setDrawCircles(true);
            tempSet.setDrawHorizontalHighlightIndicator(false);
            tempSet.setDrawVerticalHighlightIndicator(false);

            LineData lineData = new LineData(tempSet);
            chart.setData(lineData);

            XAxis x = chart.getXAxis();
            x.setPosition(XAxis.XAxisPosition.BOTTOM);
            x.setValueFormatter(new IndexAxisValueFormatter(labels));
            x.setGranularity(1f);
            x.setLabelCount(Math.min(8, labels.size())); // tránh quá dày
            x.setTextSize(11f);
            x.setTextColor(Color.parseColor("#333333"));
            x.setDrawGridLines(true);
            x.setGridColor(Color.parseColor("#E0E0E0"));
            x.enableGridDashedLine(10f, 5f, 0f);
            x.setAxisMinimum(-0.2f);
            x.setAxisMaximum(labels.size() - 0.8f);
            x.setXOffset(6f);

            YAxis left = chart.getAxisLeft();
            left.setTextSize(12f);
            left.setTextColor(Color.parseColor("#333333"));
            left.setDrawGridLines(true);
            left.setGridColor(Color.parseColor("#E0E0E0"));
            left.enableGridDashedLine(10f, 5f, 0f);
            left.setLabelCount(6, false);

            chart.getAxisRight().setEnabled(false);
            chart.setDescription(null);
            chart.getLegend().setEnabled(false);
            chart.setTouchEnabled(true);
            chart.setDragEnabled(true);
            chart.setScaleEnabled(true);
            chart.setPinchZoom(true);
            chart.setDrawGridBackground(false);
            chart.setDrawBorders(false);
            chart.animateY(800, Easing.EaseInOutQuad);
            chart.invalidate();
        } catch (Exception e) {
            Log.e(TAG, "setupTemperatureChartHourly error", e);
        }
    }

    public static void setupPrecipitationChartHourly(BarChart chart,
                                                     List<WeatherRepository.HourlyEntry> data,
                                                     int maxHours) {
        if (chart == null || data == null || data.isEmpty()) return;
        try {
            int n = Math.min(maxHours, data.size());
            List<BarEntry> entries = new ArrayList<>();
            List<String> labels = new ArrayList<>();
            SimpleDateFormat hh = new SimpleDateFormat("HH:mm", Locale.getDefault());

            for (int i = 0; i < n; i++) {
                WeatherRepository.HourlyEntry e = data.get(i);
                float v;
                if (e.popPct != null) {
                    v = e.popPct.floatValue(); // %
                } else if (e.precipMm != null) {
                    v = Math.min(e.precipMm.floatValue() * 30f, 100f); // quy đổi gần đúng
                } else {
                    v = 0f;
                }
                entries.add(new BarEntry(i, v));
                labels.add(hh.format(new Date(e.ts * 1000L)));
            }

            BarDataSet set = new BarDataSet(entries, "");
            set.setColor(Color.parseColor("#000000"));
            set.setDrawValues(false);

            BarData barData = new BarData(set);
            barData.setBarWidth(0.6f);
            chart.setData(barData);

            XAxis x = chart.getXAxis();
            x.setPosition(XAxis.XAxisPosition.BOTTOM);
            x.setValueFormatter(new IndexAxisValueFormatter(labels));
            x.setGranularity(1f);
            x.setLabelCount(Math.min(8, labels.size()));
            x.setTextSize(11f);
            x.setTextColor(Color.parseColor("#333333"));
            x.setDrawGridLines(true);
            x.setGridColor(Color.parseColor("#E0E0E0"));
            x.enableGridDashedLine(10f, 5f, 0f);
            x.setAxisMinimum(-0.5f);
            x.setAxisMaximum(labels.size() - 0.5f);
            x.setXOffset(6f);

            YAxis left = chart.getAxisLeft();
            left.setTextSize(12f);
            left.setTextColor(Color.parseColor("#333333"));
            left.setAxisMinimum(0f);
            left.setAxisMaximum(100f); // %
            left.setLabelCount(6, true);
            left.setDrawGridLines(true);
            left.setGridColor(Color.parseColor("#E0E0E0"));
            left.enableGridDashedLine(10f, 5f, 0f);

            chart.getAxisRight().setEnabled(false);
            chart.setDescription(null);
            chart.getLegend().setEnabled(false);
            chart.setTouchEnabled(true);
            chart.setDragEnabled(true);
            chart.setScaleEnabled(true);
            chart.setPinchZoom(true);
            chart.setDrawGridBackground(false);
            chart.setDrawBorders(false);
            chart.animateY(800, Easing.EaseInOutQuad);
            chart.invalidate();
        } catch (Exception e) {
            Log.e(TAG, "setupPrecipitationChartHourly error", e);
        }
    }
}
