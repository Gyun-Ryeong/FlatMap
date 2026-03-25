package com.flatmap.navigation.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "routes")
public class Route {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(name = "origin_lat", nullable = false, precision = 10, scale = 7)
    private BigDecimal originLat;

    @Column(name = "origin_lng", nullable = false, precision = 10, scale = 7)
    private BigDecimal originLng;

    @Column(name = "dest_lat", nullable = false, precision = 10, scale = 7)
    private BigDecimal destLat;

    @Column(name = "dest_lng", nullable = false, precision = 10, scale = 7)
    private BigDecimal destLng;

    @Column(name = "total_length_m")
    private Integer totalLengthM;

    @Column(name = "max_grade", precision = 5, scale = 2)
    private BigDecimal maxGrade;

    @Column(name = "avg_grade", precision = 5, scale = 2)
    private BigDecimal avgGrade;

    @Column(name = "safety_score")
    private Integer safetyScore;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public Route() {}

    public Long getId() { return id; }
    public String getName() { return name; }
    public BigDecimal getOriginLat() { return originLat; }
    public BigDecimal getOriginLng() { return originLng; }
    public BigDecimal getDestLat() { return destLat; }
    public BigDecimal getDestLng() { return destLng; }
    public Integer getTotalLengthM() { return totalLengthM; }
    public BigDecimal getMaxGrade() { return maxGrade; }
    public BigDecimal getAvgGrade() { return avgGrade; }
    public Integer getSafetyScore() { return safetyScore; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
