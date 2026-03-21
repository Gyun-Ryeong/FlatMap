package com.flatmap.navigation.controller;

import com.flatmap.navigation.entity.RoadSegment;
import com.flatmap.navigation.repository.RoadSegmentRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/segments")
@CrossOrigin(origins = "http://localhost:3000")
public class RoadSegmentController {

    private final RoadSegmentRepository repository;

    public RoadSegmentController(RoadSegmentRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<RoadSegment> getAll() {
        return repository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<RoadSegment> getById(@PathVariable Long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/region/{region}")
    public List<RoadSegment> getByRegion(@PathVariable String region) {
        return repository.findByRegion(region);
    }

    @GetMapping("/dangerous")
    public List<RoadSegment> getDangerous(@RequestParam(defaultValue = "10") double minGrade) {
        return repository.findByGradeGreaterThanEqual(BigDecimal.valueOf(minGrade));
    }
}
