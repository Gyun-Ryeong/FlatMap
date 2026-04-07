package com.flatmap.navigation.repository;

import com.flatmap.navigation.entity.ShadeShelter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ShadeShelterRepository extends JpaRepository<ShadeShelter, Long> {
    List<ShadeShelter> findByRegionCodeStartingWith(String regionCodePrefix);
    boolean existsByName(String name);
}
