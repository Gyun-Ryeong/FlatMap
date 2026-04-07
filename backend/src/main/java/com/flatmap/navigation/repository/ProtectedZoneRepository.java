package com.flatmap.navigation.repository;

import com.flatmap.navigation.entity.ProtectedZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProtectedZoneRepository extends JpaRepository<ProtectedZone, Long> {
    List<ProtectedZone> findByRegionCodeStartingWith(String regionCodePrefix);
    List<ProtectedZone> findByType(String type);
    boolean existsByName(String name);
}
