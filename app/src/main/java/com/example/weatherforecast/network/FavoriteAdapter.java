package com.example.weatherforecast.network;

import android.content.Context;
import android.graphics.Color;
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

        holder.tvCityName.setText(card.name);
        holder.tvCountryName.setText(card.country);
        holder.tvWeatherDescription.setText(card.condition);
        holder.tvTemperature.setText(card.tempC != null ? String.format(Locale.getDefault(), "%.0f°", card.tempC) : "--°");
        holder.ivWeatherIcon.setImageResource(getWeatherIconResource(card.icon));

        if (isCompareMode && minTempOverall != null && maxTempOverall != null && card.tempC != null) {
            holder.ivDelete.setVisibility(View.GONE);
            holder.ivTrendIcon.setVisibility(View.VISIBLE);

            // So sánh nhiệt độ hiện tại (tempC)
            if (card.tempC.equals(maxTempOverall)) {
                holder.rootCardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.bg_hot_card));
                holder.ivTrendIcon.setImageResource(R.drawable.ic_trend_up);
            } else if (card.tempC.equals(minTempOverall)) {
                holder.rootCardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.bg_cold_card));
                holder.ivTrendIcon.setImageResource(R.drawable.ic_trend_down);
            } else {
                holder.rootCardView.setCardBackgroundColor(Color.WHITE);
                holder.ivTrendIcon.setVisibility(View.GONE);
            }
            updateTemperatureBar(holder, card.tempC, card.minTempC, card.maxTempC);
        } else {
            holder.ivDelete.setVisibility(View.VISIBLE);
            holder.tempBarContainer.setVisibility(View.GONE);
            holder.ivTrendIcon.setVisibility(View.GONE);
            holder.rootCardView.setCardBackgroundColor(Color.WHITE);
        }

        holder.itemView.setOnClickListener(v -> listener.onFavoriteClick(card.locationId, card.name));
        holder.ivDelete.setOnClickListener(v -> listener.onFavoriteDelete(card.locationId, holder.getAdapterPosition()));
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
        switch (iconCode) {
            case "01d": return R.drawable.ic_sunny_24;
            case "01n": return R.drawable.ic_moon;
            case "02d": case "03d": case "04d": return R.drawable.ic_cloud_24;
            case "09d": case "10d": return R.drawable.ic_rain;
            default: return R.drawable.ic_cloud_24;
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
            rootCardView = (CardView) itemView;

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