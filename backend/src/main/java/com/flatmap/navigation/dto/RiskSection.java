package com.flatmap.navigation.dto;

public class RiskSection {
    private double lat;
    private double lng;
    private String name;
    private double grade;
    private String riskLevel;
    private double distanceFromRoute;
    private int nearestRouteIdx;

    public RiskSection(double lat, double lng, String name, double grade,
                       String riskLevel, double distanceFromRoute, int nearestRouteIdx) {
        this.lat = lat;
        this.lng = lng;
        this.name = name;
        this.grade = grade;
        this.riskLevel = riskLevel;
        this.distanceFromRoute = Math.round(distanceFromRoute * 10.0) / 10.0;
        this.nearestRouteIdx = nearestRouteIdx;
    }

    public double getLat() { return lat; }
    public double getLng() { return lng; }
    public String getName() { return name; }
    public double getGrade() { return grade; }
    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public double getDistanceFromRoute() { return distanceFromRoute; }
    public int getNearestRouteIdx() { return nearestRouteIdx; }
}
