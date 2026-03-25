package com.flatmap.navigation.dto;

import java.util.ArrayList;
import java.util.List;

public class RouteResponse {
    private List<Coord> coords;
    private int totalDistance;
    private int totalDuration;
    private List<RiskSection> riskSections;

    public RouteResponse(List<Coord> coords, int totalDistance, int totalDuration) {
        this.coords = coords;
        this.totalDistance = totalDistance;
        this.totalDuration = totalDuration;
        this.riskSections = new ArrayList<>();
    }

    public List<Coord> getCoords() { return coords; }
    public int getTotalDistance() { return totalDistance; }
    public int getTotalDuration() { return totalDuration; }
    public List<RiskSection> getRiskSections() { return riskSections; }
    public void setRiskSections(List<RiskSection> riskSections) { this.riskSections = riskSections; }

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
