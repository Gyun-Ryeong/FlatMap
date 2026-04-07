package com.flatmap.navigation.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "protected_zones")
public class ProtectedZone {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private Double latitude;
    private Double longitude;
    private String type;
    private String address;

    @Column(name = "region_code")
    private String regionCode;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public ProtectedZone() {}

    public ProtectedZone(String name, Double latitude, Double longitude, String type, String address, String regionCode) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.type = type;
        this.address = address;
        this.regionCode = regionCode;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public String getType() { return type; }
    public String getAddress() { return address; }
    public String getRegionCode() { return regionCode; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
