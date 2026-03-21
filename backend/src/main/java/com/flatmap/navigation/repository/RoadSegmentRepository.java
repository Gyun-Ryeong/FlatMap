package com.flatmap.navigation.repository;

import com.flatmap.navigation.entity.RoadSegment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;

@Repository
public interface RoadSegmentRepository extends JpaRepository<RoadSegment, Long> {
    List<RoadSegment> findByRegion(String region);
    List<RoadSegment> findByGradeGreaterThanEqual(BigDecimal grade);
}
