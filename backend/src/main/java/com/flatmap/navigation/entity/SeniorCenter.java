package com.flatmap.navigation.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "senior_centers")
public class SeniorCenter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private Double latitude;
    private Double longitude;
    private String address;
    private String phone;

    @Column(name = "region_code")
    private String regionCode;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public SeniorCenter() {}

    public SeniorCenter(String name, Double latitude, Double longitude, String address, String phone, String regionCode) {
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
        this.address = address;
        this.phone = phone;
        this.regionCode = regionCode;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public String getAddress() { return address; }
    public String getPhone() { return phone; }
    public String getRegionCode() { return regionCode; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
