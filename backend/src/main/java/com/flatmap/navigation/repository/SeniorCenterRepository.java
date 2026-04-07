package com.flatmap.navigation.repository;

import com.flatmap.navigation.entity.SeniorCenter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SeniorCenterRepository extends JpaRepository<SeniorCenter, Long> {
    List<SeniorCenter> findByRegionCodeStartingWith(String regionCodePrefix);
    boolean existsByName(String name);
}
