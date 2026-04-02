package com.flatmap.navigation.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "accident_zones")
public class AccidentZone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private Double latitude;
    private Double longitude;

    /** ICY = 결빙 사고, NORMAL = 일반 사고 */
    private String type;

    @Column(name = "region_code")
    private String regionCode;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public AccidentZone() {}

    public AccidentZone(String name, Double latitude, Double longitude, String type, String regionCode) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.type = type;
        this.regionCode = regionCode;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public String getType() { return type; }
    public String getRegionCode() { return regionCode; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
