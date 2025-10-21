package com.example.weatherforecast.network;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weatherforecast.LocationResponse;

import java.util.List;

public class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.SearchViewHolder> {

    private final List<LocationResponse> locations;
    private final OnLocationClickListener listener;

    public interface OnLocationClickListener {
        void onLocationClick(LocationResponse location);
    }

    public SearchAdapter(List<LocationResponse> locations, OnLocationClickListener listener) {
        this.locations = locations;
        this.listener = listener;
    }

    @NonNull
    @Override
    public SearchViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
        return new SearchViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SearchViewHolder holder, int position) {
        LocationResponse location = locations.get(position);
        holder.text1.setText(location.getName());
        holder.text2.setText(String.format("%s, %s", location.getRegion(), location.getCountry()));
        holder.itemView.setOnClickListener(v -> listener.onLocationClick(location));
    }

    @Override
    public int getItemCount() {
        return locations.size();
    }

    static class SearchViewHolder extends RecyclerView.ViewHolder {
        TextView text1, text2;
        SearchViewHolder(@NonNull View itemView) {
            super(itemView);
            text1 = itemView.findViewById(android.R.id.text1);
            text2 = itemView.findViewById(android.R.id.text2);
        }
    }
}
    