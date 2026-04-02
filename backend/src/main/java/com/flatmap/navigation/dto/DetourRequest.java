package com.flatmap.navigation.dto;

import java.util.List;

public class DetourRequest {
    private double originLng;
    private double originLat;
    private double destLng;
    private double destLat;
    private String option;
    private List<RiskCoord> avoidPoints;

    public double getOriginLng() { return originLng; }
    public void setOriginLng(double originLng) { this.originLng = originLng; }
    public double getOriginLat() { return originLat; }
    public void setOriginLat(double originLat) { this.originLat = originLat; }
    public double getDestLng() { return destLng; }
    public void setDestLng(double destLng) { this.destLng = destLng; }
    public double getDestLat() { return destLat; }
    public void setDestLat(double destLat) { this.destLat = destLat; }
    public String getOption() { return option; }
    public void setOption(String option) { this.option = option; }
    public List<RiskCoord> getAvoidPoints() { return avoidPoints; }
    public void setAvoidPoints(List<RiskCoord> avoidPoints) { this.avoidPoints = avoidPoints; }

    public static class RiskCoord {
        private double lat;
        private double lng;
        private String riskLevel;
        private int nearestRouteIdx;

        public double getLat() { return lat; }
        public void setLat(double lat) { this.lat = lat; }
        public double getLng() { return lng; }
        public void setLng(double lng) { this.lng = lng; }
        public String getRiskLevel() { return riskLevel; }
        public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
        public int getNearestRouteIdx() { return nearestRouteIdx; }
        public void setNearestRouteIdx(int nearestRouteIdx) { this.nearestRouteIdx = nearestRouteIdx; }
    }
}
