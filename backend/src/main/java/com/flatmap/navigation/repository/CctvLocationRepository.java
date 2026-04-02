package com.flatmap.navigation.repository;

import com.flatmap.navigation.entity.CctvLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CctvLocationRepository extends JpaRepository<CctvLocation, Long> {
}
