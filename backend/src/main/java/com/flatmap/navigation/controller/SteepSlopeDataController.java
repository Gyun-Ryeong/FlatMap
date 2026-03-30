package com.flatmap.navigation.controller;

import com.flatmap.navigation.entity.SteepSlopeArea;
import com.flatmap.navigation.repository.SteepSlopeAreaRepository;
import com.flatmap.navigation.service.DisasterDataService;
import com.flatmap.navigation.service.GyeonggiDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
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

    /** DB에 저장된 급경사지 데이터 조회 (필터링 지원) */
    @GetMapping
    public ResponseEntity<?> getSteepSlopes(
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) String riskLevel) {

        List<SteepSlopeArea> result;

        if (regionCode != null && !regionCode.isEmpty() && riskLevel != null && !riskLevel.isEmpty()) {
            result = repository.findByRegionCodeStartingWithAndRiskLevel(regionCode, riskLevel.toUpperCase());
        } else if (regionCode != null && !regionCode.isEmpty()) {
            result = repository.findByRegionCodeStartingWith(regionCode);
        } else if (riskLevel != null && !riskLevel.isEmpty()) {
            result = repository.findByRiskLevel(riskLevel.toUpperCase());
        } else {
            result = repository.findAll();
        }

        return ResponseEntity.ok(result);
    }

    /** 출처별 조회 */
    @GetMapping("/source/{source}")
    public ResponseEntity<?> getBySource(@PathVariable String source) {
        return ResponseEntity.ok(repository.findBySource(source.toUpperCase()));
    }

    /** 데이터 통계 */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("total", repository.count());
        stats.put("gyeonggi", repository.countBySource("GYEONGGI"));
        stats.put("disaster", repository.countBySource("DISASTER"));
        stats.put("dummy", repository.countBySource("DUMMY"));
        return ResponseEntity.ok(stats);
    }

    /** 테스트용 더미 데이터 삽입 (성남시 수정구/중원구/분당구) */
    @PostMapping("/seed")
    public ResponseEntity<Map<String, Object>> seedDummyData() {
        log.info("테스트용 더미 데이터 삽입 시작");

        List<SteepSlopeArea> dummyData = new ArrayList<>();

        // === 수정구 (41131) — 신구대학교~산성역 경로 근처 ===
        dummyData.add(new SteepSlopeArea("수정구 산성동 급경사 구간", 37.4566, 127.0101, 12.5, "HIGH", "DUMMY", "41131"));
        dummyData.add(new SteepSlopeArea("수정구 신흥동 오르막길", 37.4516, 127.0143, 8.3, "MEDIUM", "DUMMY", "41131"));
        dummyData.add(new SteepSlopeArea("수정구 태평동 경사로", 37.4524, 127.0121, 15.7, "VERY_HIGH", "DUMMY", "41131"));
        dummyData.add(new SteepSlopeArea("수정구 복정동 급경사지", 37.4540, 127.0106, 6.2, "MEDIUM", "DUMMY", "41131"));
        dummyData.add(new SteepSlopeArea("신구대학교 북측 경사로", 37.4507, 127.0142, 18.4, "VERY_HIGH", "DUMMY", "41131"));
        dummyData.add(new SteepSlopeArea("산성역 인근 오르막", 37.4555, 127.0094, 9.8, "MEDIUM", "DUMMY", "41131"));
        dummyData.add(new SteepSlopeArea("수정구 단대동 언덕길", 37.4538, 127.0113, 7.1, "MEDIUM", "DUMMY", "41131"));

        // === 중원구 (41133) ===
        dummyData.add(new SteepSlopeArea("중원구 성남동 급경사", 37.4410, 127.0098, 11.2, "HIGH", "DUMMY", "41133"));
        dummyData.add(new SteepSlopeArea("중원구 중앙동 오르막길", 37.4395, 127.0135, 5.5, "MEDIUM", "DUMMY", "41133"));
        dummyData.add(new SteepSlopeArea("중원구 금광동 경사구간", 37.4450, 127.0050, 14.3, "HIGH", "DUMMY", "41133"));
        dummyData.add(new SteepSlopeArea("중원구 은행동 급경사지", 37.4365, 127.0180, 16.8, "VERY_HIGH", "DUMMY", "41133"));
        dummyData.add(new SteepSlopeArea("중원구 여수동 언덕", 37.4330, 127.0070, 7.9, "MEDIUM", "DUMMY", "41133"));

        // === 분당구 (41135) ===
        dummyData.add(new SteepSlopeArea("분당구 정자동 경사로", 37.3660, 127.1085, 5.8, "MEDIUM", "DUMMY", "41135"));
        dummyData.add(new SteepSlopeArea("분당구 수내동 오르막길", 37.3780, 127.1145, 10.5, "HIGH", "DUMMY", "41135"));
        dummyData.add(new SteepSlopeArea("분당구 야탑동 급경사구간", 37.4115, 127.1275, 13.1, "HIGH", "DUMMY", "41135"));
        dummyData.add(new SteepSlopeArea("분당구 판교동 언덕길", 37.3530, 127.1120, 19.2, "VERY_HIGH", "DUMMY", "41135"));
        dummyData.add(new SteepSlopeArea("분당구 운중동 경사지", 37.3480, 127.0980, 6.7, "MEDIUM", "DUMMY", "41135"));

        // 기존 DUMMY 데이터 삭제 후 삽입
        repository.deleteAll(repository.findBySource("DUMMY"));
        repository.saveAll(dummyData);

        log.info("더미 데이터 {}건 삽입 완료", dummyData.size());

        return ResponseEntity.ok(Map.of(
                "message", "더미 데이터 삽입 완료",
                "insertedCount", dummyData.size(),
                "totalInDb", repository.count()
        ));
    }
}
