package com.example.weatherforecast.adapter;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weatherforecast.R;
import com.example.weatherforecast.data.WeatherRepository;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HourlyAdapter extends RecyclerView.Adapter<HourlyAdapter.HourlyViewHolder> {

    private List<WeatherRepository.HourlyEntry> hourlyData;
    private OnItemClickListener onItemClickListener;

    public interface OnItemClickListener {
        void onItemClick(WeatherRepository.HourlyEntry hourlyEntry);
    }

    public HourlyAdapter(List<WeatherRepository.HourlyEntry> hourlyData) {
        this.hourlyData = hourlyData;
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.onItemClickListener = listener;
    }

    public void updateData(List<WeatherRepository.HourlyEntry> newData) {
        this.hourlyData.clear();
        this.hourlyData.addAll(newData);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public HourlyViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_hourly_card, parent, false);
        return new HourlyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HourlyViewHolder holder, int position) {
        WeatherRepository.HourlyEntry hourlyEntry = hourlyData.get(position);
        holder.bind(hourlyEntry);
    }

    @Override
    public int getItemCount() {
        return hourlyData.size();
    }

    class HourlyViewHolder extends RecyclerView.ViewHolder {

        private TextView tvTime, tvTemperature, tvPrecipitation, tvCondition, tvHumidity, tvWind;
        private TextView ivWeatherIcon;

        public HourlyViewHolder(@NonNull View itemView) {
            super(itemView);

            tvTime = itemView.findViewById(R.id.tvTime);
            tvTemperature = itemView.findViewById(R.id.tvTemperature);
            tvPrecipitation = itemView.findViewById(R.id.tvPrecipitation);
            tvCondition = itemView.findViewById(R.id.tvCondition);
            tvHumidity = itemView.findViewById(R.id.tvHumidity);
            tvWind = itemView.findViewById(R.id.tvWind);
            ivWeatherIcon = itemView.findViewById(R.id.ivWeatherIcon);

            itemView.setOnClickListener(v -> {
                if (onItemClickListener != null) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        onItemClickListener.onItemClick(hourlyData.get(position));
                    }
                }
            });
        }

        public void bind(WeatherRepository.HourlyEntry hourlyEntry) {
            Context ctx = itemView.getContext();
            SharedPreferences sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE);
            boolean useF = "F".equals(sp.getString("temp_unit", "C"));
            boolean useMph = "mph".equals(sp.getString("wind_unit", "kmh"));

            // Time
            String timeText = formatTime(hourlyEntry.ts);
            tvTime.setText(timeText);

            // Temperature (C or F)
            double tempDisplay = hourlyEntry.tempC;
            if (useF) tempDisplay = tempDisplay * 9 / 5.0 + 32.0;
            tvTemperature.setText(String.format(Locale.getDefault(), "%.0f°", tempDisplay));

            // Precipitation probability
            if (hourlyEntry.popPct != null && hourlyEntry.popPct > 0) {
                tvPrecipitation.setText(String.format(Locale.getDefault(), "%.0f%%", hourlyEntry.popPct));
                tvPrecipitation.setVisibility(View.VISIBLE);
            } else {
                tvPrecipitation.setVisibility(View.GONE);
            }

            // Condition text
            if (hourlyEntry.text != null && !hourlyEntry.text.isEmpty()) {
                tvCondition.setText(hourlyEntry.text);
                tvCondition.setVisibility(View.VISIBLE);
            } else {
                tvCondition.setVisibility(View.GONE);
            }

            // Humidity
            if (hourlyEntry.humidity != null) {
                tvHumidity.setText(String.format(Locale.getDefault(), "%.0f%%", hourlyEntry.humidity));
            } else {
                tvHumidity.setText("--%");
            }

            // Wind speed (km/h or mph)
            if (hourlyEntry.windMps != null) {
                double windKmh = hourlyEntry.windMps * 3.6;
                if (useMph) {
                    tvWind.setText(String.format(Locale.getDefault(), "%.0f mph", windKmh * 0.621371));
                } else {
                    tvWind.setText(String.format(Locale.getDefault(), "%.0f km/h", windKmh));
                }
            } else {
                tvWind.setText(useMph ? "-- mph" : "-- km/h");
            }

            // Weather icon
            setWeatherIcon(hourlyEntry.code);
        }

        private String formatTime(long timestamp) {
            long currentTime = System.currentTimeMillis() / 1000;
            long diffHours = (timestamp - currentTime) / 3600;
            if (diffHours == 0) return "Bây giờ";
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            return sdf.format(new Date(timestamp * 1000L));
        }

        private void setWeatherIcon(String weatherCode) {
            if (weatherCode == null || weatherCode.isEmpty()) {
                ivWeatherIcon.setText("☁️");
                return;
            }
            try {
                int code = Integer.parseInt(weatherCode);
                String emojiIcon = getWeatherEmojiIcon(code);
                ivWeatherIcon.setText(emojiIcon);
            } catch (NumberFormatException e) {
                ivWeatherIcon.setText("☁️");
            }
        }

        private String getWeatherEmojiIcon(int weatherCode) {
            switch (weatherCode) {
                case 0: return "☀️";
                case 1:
                case 2:
                case 3: return "⛅";
                case 45:
                case 48: return "🌫️";
                case 51:
                case 53:
                case 55: return "🌦️";
                case 61:
                case 63:
                case 65: return "🌧️";
                case 71:
                case 73:
                case 75: return "❄️";
                case 77: return "🌨️";
                case 80:
                case 81:
                case 82: return "🌦️";
                case 85:
                case 86: return "🌨️";
                case 95:
                case 96:
                case 99: return "⛈️";
                default: return "☁️";
            }
        }
    }
}
