package com.flatmap.navigation.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "steep_slope_areas")
public class SteepSlopeArea {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private Double latitude;

    private Double longitude;

    private Double grade;

    @Column(name = "risk_level")
    private String riskLevel;

    private String source;

    @Column(name = "region_code")
    private String regionCode;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public SteepSlopeArea() {}

    public SteepSlopeArea(String name, Double latitude, Double longitude, Double grade,
                          String riskLevel, String source, String regionCode) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.grade = grade;
        this.riskLevel = riskLevel;
        this.source = source;
        this.regionCode = regionCode;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public Double getGrade() { return grade; }
    public String getRiskLevel() { return riskLevel; }
    public String getSource() { return source; }
    public String getRegionCode() { return regionCode; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setName(String name) { this.name = name; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }
    public void setGrade(Double grade) { this.grade = grade; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public void setSource(String source) { this.source = source; }
    public void setRegionCode(String regionCode) { this.regionCode = regionCode; }

    public static String calculateRiskLevel(double grade) {
        if (grade >= 15.0) return "VERY_HIGH";
        if (grade >= 10.0) return "HIGH";
        if (grade >= 5.0) return "MEDIUM";
        return "LOW";
    }
}