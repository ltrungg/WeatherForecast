package com.example.weatherforecast.network;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weatherforecast.R;
import com.example.weatherforecast.data.WeatherRepository;

import java.util.List;
import java.util.Locale;

public class FavoriteAdapter extends RecyclerView.Adapter<FavoriteAdapter.FavoriteViewHolder> {

    private final Context context;
    private List<WeatherRepository.FavoriteCard> favoriteCards;
    private final WeatherRepository repository;
    private final OnFavoriteClickListener listener;

    public interface OnFavoriteClickListener {
        void onFavoriteClick(long locationId, String cityName);
        void onFavoriteDelete(long locationId, int position);
    }

    public FavoriteAdapter(Context context, List<WeatherRepository.FavoriteCard> favoriteCards, OnFavoriteClickListener listener) {
        this.context = context;
        this.favoriteCards = favoriteCards;
        this.repository = new WeatherRepository(context);
        this.listener = listener;
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

        // Gán dữ liệu vào các view
        holder.tvCityName.setText(card.name);
        holder.tvCountryName.setText(card.country);
        holder.tvWeatherDescription.setText(card.condition);

        // Xử lý nhiệt độ (tránh lỗi NullPointerException)
        if (card.tempC != null) {
            holder.tvTemperature.setText(String.format(Locale.getDefault(), "%.0f°", card.tempC));
        } else {
            holder.tvTemperature.setText("--°");
        }

        holder.ivWeatherIcon.setImageResource(getWeatherIconResource(card.icon));

        // Bắt sự kiện click vào cả item
        holder.itemView.setOnClickListener(v -> {
            listener.onFavoriteClick(card.locationId, card.name);
        });

        // Bắt sự kiện click vào nút xóa
        holder.ivDelete.setOnClickListener(v -> {
            listener.onFavoriteDelete(card.locationId, holder.getAdapterPosition());
        });
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
            case "02d":
            case "03d":
            case "04d":
                return R.drawable.ic_cloud_24;
            case "09d":
            case "10d":
                return R.drawable.ic_rain;
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
        ImageView ivWeatherIcon, ivDelete;

        public FavoriteViewHolder(@NonNull View itemView) {
            super(itemView);
            tvCityName = itemView.findViewById(R.id.tv_city_name);
            tvCountryName = itemView.findViewById(R.id.tv_country_name);
            tvTemperature = itemView.findViewById(R.id.tv_temperature);
            tvWeatherDescription = itemView.findViewById(R.id.tv_weather_description);
            ivWeatherIcon = itemView.findViewById(R.id.iv_weather_icon);
            ivDelete = itemView.findViewById(R.id.iv_delete);
        }
    }

}
