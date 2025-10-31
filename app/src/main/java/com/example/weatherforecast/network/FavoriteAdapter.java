package com.example.weatherforecast.network;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weatherforecast.R;
import com.example.weatherforecast.data.WeatherRepository;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class FavoriteAdapter extends RecyclerView.Adapter<FavoriteAdapter.FavoriteViewHolder> {

    private static final String PREFS = "settings";
    private static final String KEY_TEMP_UNIT = "temp_unit"; // "C" | "F"

    private final Context context;
    private List<WeatherRepository.FavoriteCard> favoriteCards;
    private final OnFavoriteClickListener listener;
    private boolean isCompareMode = false;
    private Double minTempOverall = null;
    private Double maxTempOverall = null;

    public interface OnFavoriteClickListener {
        void onFavoriteClick(long locationId, String cityName);
        void onFavoriteDelete(long locationId, int position);
    }

    public FavoriteAdapter(Context context, List<WeatherRepository.FavoriteCard> favoriteCards, OnFavoriteClickListener listener) {
        this.context = context;
        this.favoriteCards = favoriteCards;
        this.listener = listener;
    }

    public void setCompareMode(boolean isCompare, List<WeatherRepository.FavoriteCard> fullList) {
        this.isCompareMode = isCompare;
        if (isCompare && fullList != null && !fullList.isEmpty()) {
            minTempOverall = Collections.min(fullList, Comparator.comparing(c -> c.tempC != null ? c.tempC : Double.MAX_VALUE)).tempC;
            maxTempOverall = Collections.max(fullList, Comparator.comparing(c -> c.tempC != null ? c.tempC : Double.MIN_VALUE)).tempC;
        } else {
            minTempOverall = null;
            maxTempOverall = null;
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public FavoriteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_favorite_location, parent, false);
        return new FavoriteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull FavoriteViewHolder holder, int position) {
        WeatherRepository.FavoriteCard card = favoriteCards.get(position);

        boolean useF = "F".equals(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TEMP_UNIT, "C"));

        holder.tvCityName.setText(card.name);
        holder.tvCountryName.setText(card.country);
        holder.tvWeatherDescription.setText(card.condition);

        // Nhiệt độ hiển thị theo đơn vị
        if (card.tempC != null) {
            double display = useF ? cToF(card.tempC) : card.tempC;
            holder.tvTemperature.setText(String.format(Locale.getDefault(), "%.0f°", display));
        } else {
            holder.tvTemperature.setText("--°");
        }

        holder.ivWeatherIcon.setImageResource(getWeatherIconResource(card.icon));

        if (isCompareMode && minTempOverall != null && maxTempOverall != null && card.tempC != null) {
            holder.ivDelete.setVisibility(View.GONE);
            holder.ivTrendIcon.setVisibility(View.VISIBLE);

            if (card.tempC.equals(maxTempOverall)) {
                setCardBackground(holder, ContextCompat.getColor(context, R.color.bg_hot_card));
                holder.ivTrendIcon.setImageResource(R.drawable.ic_trend_up);
            } else if (card.tempC.equals(minTempOverall)) {
                setCardBackground(holder, ContextCompat.getColor(context, R.color.bg_cold_card));
                holder.ivTrendIcon.setImageResource(R.drawable.ic_trend_down);
            } else {
                setCardBackground(holder, ContextCompat.getColor(context, android.R.color.white));
                holder.ivTrendIcon.setVisibility(View.GONE);
            }
            // Thanh nhiệt độ: tỉ lệ không đổi khi đổi đơn vị (biến đổi tuyến tính)
            updateTemperatureBar(holder, card.tempC, card.minTempC, card.maxTempC);
        } else {
            holder.ivDelete.setVisibility(View.VISIBLE);
            holder.tempBarContainer.setVisibility(View.GONE);
            holder.ivTrendIcon.setVisibility(View.GONE);
            setCardBackground(holder, ContextCompat.getColor(context, android.R.color.white));
        }

        holder.itemView.setOnClickListener(v -> listener.onFavoriteClick(card.locationId, card.name));
        holder.ivDelete.setOnClickListener(v -> listener.onFavoriteDelete(card.locationId, holder.getAdapterPosition()));
    }

    private static double cToF(double c) {
        return c * 9.0 / 5.0 + 32.0;
    }

    private void setCardBackground(FavoriteViewHolder holder, int colorInt) {
        if (holder.rootCardView != null) {
            holder.rootCardView.setCardBackgroundColor(colorInt);
        } else {
            // fallback nếu root không phải CardView
            holder.itemView.setBackgroundColor(colorInt);
        }
    }

    private void updateTemperatureBar(FavoriteViewHolder holder, Double currentTemp, Double minTemp, Double maxTemp) {
        if (currentTemp == null || minTemp == null || maxTemp == null || minTemp >= maxTemp) {
            holder.tempBarContainer.setVisibility(View.GONE);
            return;
        }
        holder.tempBarContainer.setVisibility(View.VISIBLE);
        double range = maxTemp - minTemp;
        double hotRatio = ((currentTemp - minTemp) / range);
        hotRatio = Math.max(0, Math.min(1, hotRatio));
        float hotWeight = (float) (hotRatio * 100.0);
        float coldWeight = 100 - hotWeight;
        holder.tempBarHot.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, hotWeight));
        holder.tempBarCold.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, coldWeight));
    }

    @Override
    public int getItemCount() {
        return favoriteCards.size();
    }

    private int getWeatherIconResource(String iconCode) {
        if (iconCode == null) return R.drawable.ic_cloud_24;
        // Map sang bộ icon có sẵn trong dự án (an toàn)
        switch (iconCode) {
            case "01d":
            case "01n":
                return R.drawable.ic_sunny_24;
            case "02d":
            case "03d":
            case "04d":
            case "02n":
            case "03n":
            case "04n":
                return R.drawable.ic_cloud_24;
            case "09d":
            case "10d":
            case "09n":
            case "10n":
                return R.drawable.ic_rain_24;
            case "11d":
            case "11n":
                return R.drawable.ic_storm_24;
            default:
                return R.drawable.ic_cloud_24;
        }
    }

    public void updateData(List<WeatherRepository.FavoriteCard> newCards) {
        this.favoriteCards.clear();
        this.favoriteCards.addAll(newCards);
        notifyDataSetChanged();
    }

    public static class FavoriteViewHolder extends RecyclerView.ViewHolder {
        TextView tvCityName, tvCountryName, tvTemperature, tvWeatherDescription;
        ImageView ivWeatherIcon, ivDelete, ivTrendIcon;
        LinearLayout tempBarContainer;
        View tempBarCold, tempBarHot;
        CardView rootCardView;

        public FavoriteViewHolder(@NonNull View itemView) {
            super(itemView);

            // Nếu root của item là CardView thì cast trực tiếp, không phụ thuộc vào id
            if (itemView instanceof CardView) {
                rootCardView = (CardView) itemView;
            } else {
                rootCardView = null;
            }

            tvCityName = itemView.findViewById(R.id.tv_city_name);
            tvCountryName = itemView.findViewById(R.id.tv_country_name);
            tvTemperature = itemView.findViewById(R.id.tv_temperature);
            tvWeatherDescription = itemView.findViewById(R.id.tv_weather_description);
            ivWeatherIcon = itemView.findViewById(R.id.iv_weather_icon);
            ivDelete = itemView.findViewById(R.id.iv_delete);
            tempBarContainer = itemView.findViewById(R.id.tempBarContainer);
            tempBarCold = itemView.findViewById(R.id.tempBarCold);
            tempBarHot = itemView.findViewById(R.id.tempBarHot);
            ivTrendIcon = itemView.findViewById(R.id.iv_trend_icon);
        }
    }
}