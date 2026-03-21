package com.flatmap.navigation.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "road_segments")
@Getter
@NoArgsConstructor
public class RoadSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "start_lat", nullable = false, precision = 10, scale = 7)
    private BigDecimal startLat;

    @Column(name = "start_lng", nullable = false, precision = 10, scale = 7)
    private BigDecimal startLng;

    @Column(name = "end_lat", nullable = false, precision = 10, scale = 7)
    private BigDecimal endLat;

    @Column(name = "end_lng", nullable = false, precision = 10, scale = 7)
    private BigDecimal endLng;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal grade;

    @Column(name = "length_m", nullable = false)
    private Integer lengthM;

    private String region;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
}
