package com.flatmap.navigation.repository;

import com.flatmap.navigation.entity.WelfareFacility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WelfareFacilityRepository extends JpaRepository<WelfareFacility, Long> {
    List<WelfareFacility> findByRegionCodeStartingWith(String regionCodePrefix);
    boolean existsByName(String name);
}
