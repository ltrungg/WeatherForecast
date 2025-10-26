package com.example.weatherforecast.network;

public class DailyForecast {
    private String date;
    private int weatherCode;
    private int maxTemp;
    private int minTemp;
    private int rainProb;

    public DailyForecast(String date, int weatherCode, int maxTemp, int minTemp, int rainProb) {
        this.date = date;
        this.weatherCode = weatherCode;
        this.maxTemp = maxTemp;
        this.minTemp = minTemp;
        this.rainProb = rainProb;
    }

    public String getDate() { return date; }
    public int getWeatherCode() { return weatherCode; }
    public int getMaxTemp() { return maxTemp; }
    public int getMinTemp() { return minTemp; }
    public int getRainProb() { return rainProb; }
}
