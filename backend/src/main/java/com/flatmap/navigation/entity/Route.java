package com.flatmap.navigation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "routes")
@Getter
@NoArgsConstructor
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
}
