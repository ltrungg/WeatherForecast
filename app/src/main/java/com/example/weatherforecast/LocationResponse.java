package com.example.weatherforecast;

import com.google.gson.annotations.SerializedName;

public class LocationResponse {

    @SerializedName("id")
    private long id;

    @SerializedName("name")
    private String name;

    @SerializedName("region")
    private String region;

    @SerializedName("country")
    private String country;

    @SerializedName("lat")
    private double lat;

    @SerializedName("lon")
    private double lon;

    // Getters
    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getRegion() {
        return region;
    }

    public String getCountry() {
        return country;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }
}
