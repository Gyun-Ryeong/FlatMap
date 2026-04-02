package com.flatmap.navigation.dto;

import java.util.ArrayList;
import java.util.List;

public class RouteResponse {
    private List<Coord> coords;
    private int totalDistance;
    private int totalDuration;
    private List<RiskSection> riskSections;
    private String overallRisk;
    private WeatherInfo weather;

    public RouteResponse(List<Coord> coords, int totalDistance, int totalDuration) {
        this.coords = coords;
        this.totalDistance = totalDistance;
        this.totalDuration = totalDuration;
        this.riskSections = new ArrayList<>();
        this.overallRisk = "SAFE";
    }

    public List<Coord> getCoords() { return coords; }
    public int getTotalDistance() { return totalDistance; }
    public int getTotalDuration() { return totalDuration; }
    public List<RiskSection> getRiskSections() { return riskSections; }
    public void setRiskSections(List<RiskSection> riskSections) {
        this.riskSections = riskSections;
        this.overallRisk = calculateOverallRisk(riskSections);
    }
    public String getOverallRisk() { return overallRisk; }
    public WeatherInfo getWeather() { return weather; }
    public void setWeather(WeatherInfo weather) { this.weather = weather; }

    private static String calculateOverallRisk(List<RiskSection> sections) {
        if (sections == null || sections.isEmpty()) return "SAFE";
        boolean hasHigh = false;
        boolean hasMedium = false;
        for (RiskSection s : sections) {
            String level = s.getRiskLevel();
            if ("VERY_HIGH".equals(level) || "HIGH".equals(level)) hasHigh = true;
            if ("MEDIUM".equals(level)) hasMedium = true;
        }
        if (hasHigh) return "DANGER";
        if (hasMedium) return "CAUTION";
        return "SAFE";
    }

    public static class Coord {
        private double lat;
        private double lng;

        public Coord(double lat, double lng) {
            this.lat = lat;
            this.lng = lng;
        }

        public double getLat() { return lat; }
        public double getLng() { return lng; }
    }
}
