package com.flatmap.navigation.controller;

import com.flatmap.navigation.entity.SteepSlopeArea;
import com.flatmap.navigation.repository.SteepSlopeAreaRepository;
import com.flatmap.navigation.service.DisasterDataService;
import com.flatmap.navigation.service.GyeonggiDataService;
import com.flatmap.navigation.service.PublicDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/steep-slope")
public class SteepSlopeDataController {

    private static final Logger log = LoggerFactory.getLogger(SteepSlopeDataController.class);

    private final GyeonggiDataService gyeonggiDataService;
    private final DisasterDataService disasterDataService;
    private final PublicDataService publicDataService;
    private final SteepSlopeAreaRepository repository;

    public SteepSlopeDataController(GyeonggiDataService gyeonggiDataService,
                                     DisasterDataService disasterDataService,
                                     PublicDataService publicDataService,
                                     SteepSlopeAreaRepository repository) {
        this.gyeonggiDataService = gyeonggiDataService;
        this.disasterDataService = disasterDataService;
        this.publicDataService = publicDataService;
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

    /** 모든 소스 수집 (경기데이터드림 + 재난안전데이터 + 공공데이터포털) */
    @PostMapping("/fetch/all")
    public ResponseEntity<Map<String, Object>> fetchAllData() {
        log.info("전체 급경사지 데이터 수집 시작");
        int gyeonggiCount = 0;
        int disasterCount = 0;
        int publicDataCount = 0;
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

        try {
            publicDataCount = publicDataService.fetchAndSaveSteepSlopes();
        } catch (Exception e) {
            log.error("공공데이터포털 수집 실패: {}", e.getMessage());
            error = (error != null ? error + " | " : "") + "PUBLIC_DATA: " + e.getMessage();
        }

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("gyeonggiSaved", gyeonggiCount);
        result.put("disasterSaved", disasterCount);
        result.put("publicDataSaved", publicDataCount);
        result.put("totalInDb", repository.count());
        if (error != null) result.put("errors", error);

        return ResponseEntity.ok(result);
    }

    /** 더미 데이터 전체 삭제 */
    @DeleteMapping("/dummy")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteDummyData() {
        long count = repository.countBySource("DUMMY");
        repository.deleteBySource("DUMMY");
        log.info("DUMMY 데이터 {}건 삭제 완료", count);
        return ResponseEntity.ok(Map.of(
                "deletedCount", count,
                "totalInDb", repository.count(),
                "message", "더미 데이터 삭제 완료"
        ));
    }

    /** 공공데이터포털 급경사지 데이터 수집 */
    @PostMapping("/fetch/public-data")
    public ResponseEntity<Map<String, Object>> fetchPublicData() {
        log.info("공공데이터포털 급경사지 데이터 수집 시작");
        try {
            int saved = publicDataService.fetchAndSaveSteepSlopes();
            return ResponseEntity.ok(Map.of(
                    "source", "PUBLIC_DATA",
                    "savedCount", saved,
                    "totalInDb", repository.count(),
                    "message", "공공데이터포털 급경사지 데이터 수집 완료"
            ));
        } catch (Exception e) {
            log.error("공공데이터포털 데이터 수집 실패: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "source", "PUBLIC_DATA",
                    "error", e.getMessage()
            ));
        }
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
        stats.put("publicData", repository.countBySource("PUBLIC_DATA"));
        stats.put("dummy", repository.countBySource("DUMMY"));
        return ResponseEntity.ok(stats);
    }

    /** 위험도별 통계 (SafetyPanel용) — 성남시 데이터만 카운트 */
    @GetMapping("/risk-stats")
    public ResponseEntity<Map<String, Object>> getRiskStats() {
        // 성남시 행정코드: 41130, 41131, 41133, 41135
        List<SteepSlopeArea> seongnam = new ArrayList<>();
        for (String prefix : List.of("41130", "41131", "41133", "41135")) {
            seongnam.addAll(repository.findByRegionCodeStartingWith(prefix));
        }
        // regionCode가 null인 데이터 중 DUMMY가 아닌 것도 포함 (내리막 사고 등)
        List<SteepSlopeArea> all = repository.findAll();
        for (SteepSlopeArea a : all) {
            if (a.getRegionCode() == null && !"DUMMY".equals(a.getSource())) {
                seongnam.add(a);
            }
        }

        long high = seongnam.stream().filter(a -> "HIGH".equals(a.getRiskLevel()) || "VERY_HIGH".equals(a.getRiskLevel())).count();
        long medium = seongnam.stream().filter(a -> "MEDIUM".equals(a.getRiskLevel())).count();
        long low = seongnam.stream().filter(a -> "LOW".equals(a.getRiskLevel())).count();
        return ResponseEntity.ok(Map.of("high", high, "medium", medium, "low", low, "total", seongnam.size()));
    }

    /** 성남시 실제 급경사/오르막 지점 시드 데이터 */
    @PostMapping("/seed/seongnam")
    @Transactional
    public ResponseEntity<Map<String, Object>> seedSeongnamData() {
        log.info("성남시 실제 급경사 시드 데이터 삽입 시작");

        // 기존 SEONGNAM_SEED 데이터 삭제
        repository.deleteBySource("SEONGNAM_SEED");

        List<SteepSlopeArea> seedData = new ArrayList<>();

        // 위험 (경사도 25도 이상)
        seedData.add(new SteepSlopeArea("남한산성 진입로", 37.4590, 127.1790, 25.0, "HIGH", "SEONGNAM_SEED", "41131"));
        seedData.add(new SteepSlopeArea("검단산 등산로 입구", 37.4520, 127.2050, 30.0, "VERY_HIGH", "SEONGNAM_SEED", "41131"));
        seedData.add(new SteepSlopeArea("은행동-검단산 진입", 37.4480, 127.2000, 28.0, "VERY_HIGH", "SEONGNAM_SEED", "41133"));
        seedData.add(new SteepSlopeArea("판교 청계산 입구", 37.3980, 127.0820, 26.0, "HIGH", "SEONGNAM_SEED", "41135"));

        // 주의 (경사도 15~24도)
        seedData.add(new SteepSlopeArea("영장산 방면 오르막", 37.4380, 127.1510, 20.0, "MEDIUM", "SEONGNAM_SEED", "41131"));
        seedData.add(new SteepSlopeArea("태평동-산성동 연결 고개", 37.4450, 127.1580, 22.0, "MEDIUM", "SEONGNAM_SEED", "41131"));
        seedData.add(new SteepSlopeArea("단대동 뒷산 오르막", 37.4510, 127.1620, 18.0, "MEDIUM", "SEONGNAM_SEED", "41131"));
        seedData.add(new SteepSlopeArea("성남시청 뒤편 오르막", 37.4200, 127.1270, 15.0, "MEDIUM", "SEONGNAM_SEED", "41133"));
        seedData.add(new SteepSlopeArea("수정구 산성대로 고개", 37.4550, 127.1700, 19.0, "MEDIUM", "SEONGNAM_SEED", "41131"));
        seedData.add(new SteepSlopeArea("중원구 여수동 오르막", 37.4330, 127.1550, 17.0, "MEDIUM", "SEONGNAM_SEED", "41133"));
        seedData.add(new SteepSlopeArea("분당 불곡산 진입로", 37.3850, 127.1200, 23.0, "MEDIUM", "SEONGNAM_SEED", "41135"));
        seedData.add(new SteepSlopeArea("분당 영장산 북쪽", 37.3920, 127.1480, 21.0, "MEDIUM", "SEONGNAM_SEED", "41135"));
        seedData.add(new SteepSlopeArea("율동공원 뒤편 오르막", 37.3780, 127.0950, 16.0, "MEDIUM", "SEONGNAM_SEED", "41135"));
        seedData.add(new SteepSlopeArea("중원구 은행동 고개", 37.4470, 127.1950, 20.0, "MEDIUM", "SEONGNAM_SEED", "41133"));

        // 안전 (경사도 15도 미만)
        seedData.add(new SteepSlopeArea("수진동-신흥동 고개", 37.4370, 127.1430, 14.0, "LOW", "SEONGNAM_SEED", "41131"));

        repository.saveAll(seedData);

        log.info("성남시 시드 데이터 {}건 삽입 완료", seedData.size());
        return ResponseEntity.ok(Map.of(
                "message", "성남시 실제 급경사 시드 데이터 삽입 완료",
                "insertedCount", seedData.size(),
                "totalInDb", repository.count()
        ));
    }

    /** 테스트용 더미 데이터 삽입 (성남시 수정구/중원구/분당구) */
    @PostMapping("/seed")
    public ResponseEntity<Map<String, Object>> seedDummyData() {
        log.info("테스트용 더미 데이터 삽입 시작");

        List<SteepSlopeArea> dummyData = new ArrayList<>();

        // === 수정구 (41131) — 신구대학교~산성역 실제 도보 경로 위 좌표 ===
        // 경로 경도 범위: 127.126 ~ 127.147, 위도 범위: 37.443 ~ 37.458
        dummyData.add(new SteepSlopeArea("신구대학교 정문 앞 급경사", 37.4438, 127.1468, 12.5, "HIGH", "DUMMY", "41131"));
        dummyData.add(new SteepSlopeArea("복정동 오르막길", 37.4462, 127.1458, 8.3, "MEDIUM", "DUMMY", "41131"));
        dummyData.add(new SteepSlopeArea("수정구 위례순환로 급경사", 37.4486, 127.1425, 15.7, "VERY_HIGH", "DUMMY", "41131"));
        dummyData.add(new SteepSlopeArea("수정구 복정동 언덕길", 37.4479, 127.1394, 6.2, "MEDIUM", "DUMMY", "41131"));
        dummyData.add(new SteepSlopeArea("단대동 경사 구간", 37.4468, 127.1356, 18.4, "VERY_HIGH", "DUMMY", "41131"));
        dummyData.add(new SteepSlopeArea("산성역 방면 오르막", 37.4494, 127.1332, 9.8, "MEDIUM", "DUMMY", "41131"));
        dummyData.add(new SteepSlopeArea("산성역 인근 급경사지", 37.4564, 127.1268, 7.1, "MEDIUM", "DUMMY", "41131"));

        // === 중원구 (41133) — 성남시 중원구 주요 경사 구간 ===
        dummyData.add(new SteepSlopeArea("중원구 성남동 급경사", 37.4440, 127.1380, 11.2, "HIGH", "DUMMY", "41133"));
        dummyData.add(new SteepSlopeArea("중원구 중앙동 오르막길", 37.4420, 127.1410, 5.5, "MEDIUM", "DUMMY", "41133"));
        dummyData.add(new SteepSlopeArea("중원구 금광동 경사구간", 37.4455, 127.1350, 14.3, "HIGH", "DUMMY", "41133"));
        dummyData.add(new SteepSlopeArea("중원구 은행동 급경사지", 37.4390, 127.1440, 16.8, "VERY_HIGH", "DUMMY", "41133"));
        dummyData.add(new SteepSlopeArea("중원구 여수동 언덕", 37.4410, 127.1320, 7.9, "MEDIUM", "DUMMY", "41133"));

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
