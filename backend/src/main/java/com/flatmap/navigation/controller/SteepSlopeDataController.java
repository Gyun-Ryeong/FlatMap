package com.flatmap.navigation.controller;

import com.flatmap.navigation.entity.SteepSlopeArea;
import com.flatmap.navigation.repository.SteepSlopeAreaRepository;
import com.flatmap.navigation.service.DisasterDataService;
import com.flatmap.navigation.service.GyeonggiDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/steep-slope")
@CrossOrigin(origins = "http://localhost:3000")
public class SteepSlopeDataController {

    private static final Logger log = LoggerFactory.getLogger(SteepSlopeDataController.class);

    private final GyeonggiDataService gyeonggiDataService;
    private final DisasterDataService disasterDataService;
    private final SteepSlopeAreaRepository repository;

    public SteepSlopeDataController(GyeonggiDataService gyeonggiDataService,
                                     DisasterDataService disasterDataService,
                                     SteepSlopeAreaRepository repository) {
        this.gyeonggiDataService = gyeonggiDataService;
        this.disasterDataService = disasterDataService;
        this.repository = repository;
    }

    /** 경기데이터드림 오르막차로 데이터 수집 */
    @PostMapping("/fetch/gyeonggi")
    public ResponseEntity<Map<String, Object>> fetchGyeonggiData() {
        log.info("경기데이터드림 오르막차로 데이터 수집 시작");
        try {
            int saved = gyeonggiDataService.fetchAndSaveUphillLanes();
            return ResponseEntity.ok(Map.of(
                    "source", "GYEONGGI",
                    "savedCount", saved,
                    "message", "경기데이터드림 오르막차로 데이터 수집 완료"
            ));
        } catch (Exception e) {
            log.error("경기데이터드림 데이터 수집 실패: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "source", "GYEONGGI",
                    "error", e.getMessage()
            ));
        }
    }

    /** 재난안전데이터 급경사지 데이터 수집 */
    @PostMapping("/fetch/disaster")
    public ResponseEntity<Map<String, Object>> fetchDisasterData() {
        log.info("재난안전데이터 급경사지 데이터 수집 시작");
        try {
            int saved = disasterDataService.fetchAndSaveSteepSlopes();
            return ResponseEntity.ok(Map.of(
                    "source", "DISASTER",
                    "savedCount", saved,
                    "message", "재난안전데이터 급경사지 데이터 수집 완료"
            ));
        } catch (Exception e) {
            log.error("재난안전데이터 수집 실패: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "source", "DISASTER",
                    "error", e.getMessage()
            ));
        }
    }

    /** 두 소스 모두 수집 */
    @PostMapping("/fetch/all")
    public ResponseEntity<Map<String, Object>> fetchAllData() {
        log.info("전체 급경사지 데이터 수집 시작");
        int gyeonggiCount = 0;
        int disasterCount = 0;
        String error = null;

        try {
            gyeonggiCount = gyeonggiDataService.fetchAndSaveUphillLanes();
        } catch (Exception e) {
            log.error("경기데이터드림 수집 실패: {}", e.getMessage());
            error = "GYEONGGI: " + e.getMessage();
        }

        try {
            disasterCount = disasterDataService.fetchAndSaveSteepSlopes();
        } catch (Exception e) {
            log.error("재난안전데이터 수집 실패: {}", e.getMessage());
            error = (error != null ? error + " | " : "") + "DISASTER: " + e.getMessage();
        }

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("gyeonggiSaved", gyeonggiCount);
        result.put("disasterSaved", disasterCount);
        result.put("totalInDb", repository.count());
        if (error != null) result.put("errors", error);

        return ResponseEntity.ok(result);
    }

    /** DB에 저장된 급경사지 데이터 조회 */
    @GetMapping
    public ResponseEntity<?> getAllSteepSlopes() {
        return ResponseEntity.ok(repository.findAll());
    }

    /** 출처별 조회 */
    @GetMapping("/source/{source}")
    public ResponseEntity<?> getBySource(@PathVariable String source) {
        return ResponseEntity.ok(repository.findBySource(source.toUpperCase()));
    }

    /** 데이터 통계 */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(Map.of(
                "total", repository.count(),
                "gyeonggi", repository.countBySource("GYEONGGI"),
                "disaster", repository.countBySource("DISASTER")
        ));
    }
}
