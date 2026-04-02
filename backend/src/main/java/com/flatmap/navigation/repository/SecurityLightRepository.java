package com.flatmap.navigation.repository;

import com.flatmap.navigation.entity.SecurityLight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SecurityLightRepository extends JpaRepository<SecurityLight, Long> {
    List<SecurityLight> findByRegionCodeStartingWith(String regionCodePrefix);
}
