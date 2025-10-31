package com.example.weatherforecast.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weatherforecast.R;
import com.example.weatherforecast.data.WeatherRepository;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DailyAdapter extends RecyclerView.Adapter<DailyAdapter.ViewHolder> {

    private List<WeatherRepository.DailyForecastData> list;

    public void setData(List<WeatherRepository.DailyForecastData> list) {
        android.util.Log.d("DailyAdapter", "setData called with " + (list != null ? list.size() : "null") + " items");
        this.list = list;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_forecast, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int pos) {
        WeatherRepository.DailyForecastData d = list.get(pos);
        Context ctx = h.itemView.getContext();

        // Format date label
        h.tvDate.setText(formatDayOfWeek(d.dateTs, pos));

        // Weather description in Vietnamese
        h.tvDesc.setText(translateWeatherCondition(d.condition));

        // Rain percentage (fallback from precipMm)
        String rainText = d.precipMm != null && d.precipMm > 0
                ? String.format(Locale.getDefault(), "%.0f%% mưa", Math.min(d.precipMm * 10, 100))
                : (d.popPct != null ? String.format(Locale.getDefault(), "%.0f%% mưa", d.popPct) : "0% mưa");
        h.tvRain.setText(rainText);

        // Units
        SharedPreferences sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE);
        boolean useF = "F".equals(sp.getString("temp_unit", "C"));

        // High / Low temperature
        if (d.tempMaxC != null) {
            double v = useF ? (d.tempMaxC * 9 / 5.0 + 32.0) : d.tempMaxC;
            h.tvTempHigh.setText(String.format(Locale.getDefault(), "%.0f°", v));
        } else {
            h.tvTempHigh.setText("--");
        }

        if (d.tempMinC != null) {
            double v = useF ? (d.tempMinC * 9 / 5.0 + 32.0) : d.tempMinC;
            h.tvTempLow.setText(String.format(Locale.getDefault(), "%.0f°", v));
        } else {
            h.tvTempLow.setText("--");
        }

        // Icon by condition
        setWeatherIcon(h.ivWeatherIcon, d.condition);
    }

    @Override
    public int getItemCount() {
        android.util.Log.d("DailyAdapter", "getItemCount: " + (list == null ? 0 : list.size()));
        return list == null ? 0 : list.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvDate, tvDesc, tvRain, tvTempHigh, tvTempLow;
        ImageView ivWeatherIcon;

        ViewHolder(View v) {
            super(v);
            tvDate = v.findViewById(R.id.tvDate);
            tvDesc = v.findViewById(R.id.tvDesc);
            tvRain = v.findViewById(R.id.tvRain);
            tvTempHigh = v.findViewById(R.id.tvTempHigh);
            tvTempLow = v.findViewById(R.id.tvTempLow);
            ivWeatherIcon = v.findViewById(R.id.ivWeatherIcon);
        }
    }

    private static String formatDayOfWeek(Long epochSec, int position) {
        if (position == 0) return "Hôm nay";
        if (epochSec == null) return "Ngày " + (position + 1);

        SimpleDateFormat sdf = new SimpleDateFormat("EEEE", Locale.getDefault());
        String dayName = sdf.format(new Date(epochSec * 1000L)).toLowerCase(Locale.getDefault());

        switch (dayName) {
            case "monday": return "Thứ 2";
            case "tuesday": return "Thứ 3";
            case "wednesday": return "Thứ 4";
            case "thursday": return "Thứ 5";
            case "friday": return "Thứ 6";
            case "saturday": return "Thứ 7";
            case "sunday": return "Chủ nhật";
            default: return dayName;
        }
    }

    private static String translateWeatherCondition(String condition) {
        if (condition == null) return "Nhiều mây";
        String lowerCondition = condition.toLowerCase(Locale.getDefault());
        if (lowerCondition.contains("unknown")) return "Không xác định";
        if (lowerCondition.contains("clear") || lowerCondition.contains("sunny")) return "Trời quang";
        if (lowerCondition.contains("partly cloudy")) return "Ít mây";
        if (lowerCondition.contains("cloudy") || lowerCondition.contains("overcast")) return "Nhiều mây";
        if (lowerCondition.contains("rain showers")) return "Mưa rào";
        if (lowerCondition.contains("rain") || lowerCondition.contains("drizzle")) return "Có mưa";
        if (lowerCondition.contains("thunderstorm") || lowerCondition.contains("storm")) return "Dông bão";
        if (lowerCondition.contains("snow")) return "Có tuyết";
        if (lowerCondition.contains("fog") || lowerCondition.contains("mist")) return "Sương mù";
        if (lowerCondition.contains("haze")) return "Mù mịt";
        return condition;
    }

    private static void setWeatherIcon(ImageView imageView, String condition) {
        if (condition == null) {
            imageView.setImageResource(R.drawable.ic_cloud_24);
            return;
        }
        String lowerCondition = condition.toLowerCase(Locale.getDefault());
        if (lowerCondition.contains("rain") || lowerCondition.contains("mưa")) {
            imageView.setImageResource(R.drawable.ic_rain_24);
        } else if (lowerCondition.contains("sun") || lowerCondition.contains("clear") || lowerCondition.contains("nắng")) {
            imageView.setImageResource(R.drawable.ic_sunny_24);
        } else if (lowerCondition.contains("storm") || lowerCondition.contains("thunder")) {
            imageView.setImageResource(R.drawable.ic_storm_24);
        } else if (lowerCondition.contains("cloud") || lowerCondition.contains("mây")) {
            imageView.setImageResource(R.drawable.ic_cloud_24);
        } else {
            imageView.setImageResource(R.drawable.ic_cloud_24);
        }
    }
}