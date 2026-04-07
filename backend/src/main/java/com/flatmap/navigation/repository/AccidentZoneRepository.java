package com.flatmap.navigation.repository;

import com.flatmap.navigation.entity.AccidentZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccidentZoneRepository extends JpaRepository<AccidentZone, Long> {
    List<AccidentZone> findByType(String type);
    List<AccidentZone> findByRegionCodeStartingWith(String regionCodePrefix);
    boolean existsByName(String name);
}
