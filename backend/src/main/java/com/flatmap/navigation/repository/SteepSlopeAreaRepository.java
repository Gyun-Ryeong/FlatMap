package com.flatmap.navigation.repository;

import com.flatmap.navigation.entity.SteepSlopeArea;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SteepSlopeAreaRepository extends JpaRepository<SteepSlopeArea, Long> {

    List<SteepSlopeArea> findBySource(String source);

    List<SteepSlopeArea> findByRegionCode(String regionCode);

    List<SteepSlopeArea> findByRiskLevel(String riskLevel);

    List<SteepSlopeArea> findByRiskLevelIn(List<String> riskLevels);

    List<SteepSlopeArea> findByRegionCodeAndRiskLevel(String regionCode, String riskLevel);

    List<SteepSlopeArea> findByRegionCodeStartingWith(String regionCodePrefix);

    List<SteepSlopeArea> findByRegionCodeStartingWithAndRiskLevel(String regionCodePrefix, String riskLevel);

    List<SteepSlopeArea> findByRegionCodeStartingWithAndRiskLevelIn(String regionCodePrefix, List<String> riskLevels);

    long countBySource(String source);

    void deleteBySource(String source);
}
