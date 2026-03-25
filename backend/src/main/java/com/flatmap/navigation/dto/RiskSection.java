package com.flatmap.navigation.dto;

public class RiskSection {
    private double lat;
    private double lng;
    private String name;
    private double grade;
    private String riskLevel;
    private double distanceFromRoute;

    public RiskSection(double lat, double lng, String name, double grade,
                       String riskLevel, double distanceFromRoute) {
        this.lat = lat;
        this.lng = lng;
        this.name = name;
        this.grade = grade;
        this.riskLevel = riskLevel;
        this.distanceFromRoute = Math.round(distanceFromRoute * 10.0) / 10.0;
    }

    public double getLat() { return lat; }
    public double getLng() { return lng; }
    public String getName() { return name; }
    public double getGrade() { return grade; }
    public String getRiskLevel() { return riskLevel; }
    public double getDistanceFromRoute() { return distanceFromRoute; }
}
