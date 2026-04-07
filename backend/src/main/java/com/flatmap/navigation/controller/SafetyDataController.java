package com.flatmap.navigation.controller;

import com.flatmap.navigation.entity.*;
import com.flatmap.navigation.repository.*;
import com.flatmap.navigation.service.GyeonggiDataService;
import com.flatmap.navigation.service.PublicDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/safety")
public class SafetyDataController {

    private static final Logger log = LoggerFactory.getLogger(SafetyDataController.class);

    private final SecurityLightRepository securityLightRepository;
    private final AccidentZoneRepository accidentZoneRepository;
    private final CctvLocationRepository cctvLocationRepository;
    private final ProtectedZoneRepository protectedZoneRepository;
    private final WelfareFacilityRepository welfareFacilityRepository;
    private final ShadeShelterRepository shadeShelterRepository;
    private final SeniorCenterRepository seniorCenterRepository;
    private final GyeonggiDataService gyeonggiDataService;
    private final PublicDataService publicDataService;

    public SafetyDataController(SecurityLightRepository securityLightRepository,
                                AccidentZoneRepository accidentZoneRepository,
                                CctvLocationRepository cctvLocationRepository,
                                ProtectedZoneRepository protectedZoneRepository,
                                WelfareFacilityRepository welfareFacilityRepository,
                                ShadeShelterRepository shadeShelterRepository,
                                SeniorCenterRepository seniorCenterRepository,
                                GyeonggiDataService gyeonggiDataService,
                                PublicDataService publicDataService) {
        this.securityLightRepository = securityLightRepository;
        this.accidentZoneRepository = accidentZoneRepository;
        this.cctvLocationRepository = cctvLocationRepository;
        this.protectedZoneRepository = protectedZoneRepository;
        this.welfareFacilityRepository = welfareFacilityRepository;
        this.shadeShelterRepository = shadeShelterRepository;
        this.seniorCenterRepository = seniorCenterRepository;
        this.gyeonggiDataService = gyeonggiDataService;
        this.publicDataService = publicDataService;
    }

    // ========== 보안등 ==========

    @GetMapping("/security-lights")
    public ResponseEntity<List<SecurityLight>> getSecurityLights(
            @RequestParam(required = false) String regionCode) {
        List<SecurityLight> result;
        if (regionCode != null && !regionCode.isEmpty()) {
            result = securityLightRepository.findByRegionCodeStartingWith(regionCode);
        } else {
            result = securityLightRepository.findAll();
        }
        if (result.size() > 200) result = result.subList(0, 200);
        return ResponseEntity.ok(result);
    }

    // ========== 사고다발지 ==========

    @GetMapping("/accident-zones")
    public ResponseEntity<List<AccidentZone>> getAccidentZones(
            @RequestParam(required = false) String type) {
        List<AccidentZone> result;
        if (type != null && !type.isEmpty()) {
            result = accidentZoneRepository.findByType(type.toUpperCase());
        } else {
            result = accidentZoneRepository.findAll();
        }
        if (result.size() > 200) result = result.subList(0, 200);
        return ResponseEntity.ok(result);
    }

    // ========== CCTV ==========

    @GetMapping("/cctv")
    public ResponseEntity<List<CctvLocation>> getCctvLocations() {
        List<CctvLocation> result = cctvLocationRepository.findAll();
        if (result.size() > 200) result = result.subList(0, 200);
        return ResponseEntity.ok(result);
    }

    // ========== 교통약자 보호구역 ==========

    @GetMapping("/protected-zones")
    public ResponseEntity<List<ProtectedZone>> getProtectedZones(
            @RequestParam(required = false) String regionCode) {
        List<ProtectedZone> result;
        if (regionCode != null && !regionCode.isEmpty()) {
            result = protectedZoneRepository.findByRegionCodeStartingWith(regionCode);
        } else {
            result = protectedZoneRepository.findAll();
        }
        if (result.size() > 200) result = result.subList(0, 200);
        return ResponseEntity.ok(result);
    }

    // ========== 장애인복지시설 ==========

    @GetMapping("/welfare-facilities")
    public ResponseEntity<List<WelfareFacility>> getWelfareFacilities(
            @RequestParam(required = false) String regionCode) {
        List<WelfareFacility> result;
        if (regionCode != null && !regionCode.isEmpty()) {
            result = welfareFacilityRepository.findByRegionCodeStartingWith(regionCode);
        } else {
            result = welfareFacilityRepository.findAll();
        }
        if (result.size() > 200) result = result.subList(0, 200);
        return ResponseEntity.ok(result);
    }

    // ========== 그늘막 ==========

    @GetMapping("/shade-shelters")
    public ResponseEntity<List<ShadeShelter>> getShadeShelters(
            @RequestParam(required = false) String regionCode) {
        List<ShadeShelter> result;
        if (regionCode != null && !regionCode.isEmpty()) {
            result = shadeShelterRepository.findByRegionCodeStartingWith(regionCode);
        } else {
            result = shadeShelterRepository.findAll();
        }
        if (result.size() > 200) result = result.subList(0, 200);
        return ResponseEntity.ok(result);
    }

    // ========== 노인복지관 ==========

    @GetMapping("/senior-centers")
    public ResponseEntity<List<SeniorCenter>> getSeniorCenters(
            @RequestParam(required = false) String regionCode) {
        List<SeniorCenter> result;
        if (regionCode != null && !regionCode.isEmpty()) {
            result = seniorCenterRepository.findByRegionCodeStartingWith(regionCode);
        } else {
            result = seniorCenterRepository.findAll();
        }
        if (result.size() > 200) result = result.subList(0, 200);
        return ResponseEntity.ok(result);
    }

    // ========== 통계 ==========

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new java.util.HashMap<>();
        stats.put("securityLights", securityLightRepository.count());
        stats.put("accidentZones", accidentZoneRepository.count());
        stats.put("cctv", cctvLocationRepository.count());
        stats.put("protectedZones", protectedZoneRepository.count());
        stats.put("welfareFacilities", welfareFacilityRepository.count());
        stats.put("shadeShelters", shadeShelterRepository.count());
        stats.put("seniorCenters", seniorCenterRepository.count());
        return ResponseEntity.ok(stats);
    }

    // ========== 실제 API 데이터 수집 ==========

    @PostMapping("/fetch/security-lights")
    public ResponseEntity<Map<String, Object>> fetchSecurityLights() {
        log.info("보안등 실제 데이터 수집 시작");
        try {
            int saved = gyeonggiDataService.fetchAndSaveSecurityLights();
            return ResponseEntity.ok(Map.of("savedCount", saved, "message", "보안등 데이터 수집 완료"));
        } catch (Exception e) {
            log.error("보안등 데이터 수집 실패: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/fetch/accident-zones")
    public ResponseEntity<Map<String, Object>> fetchAccidentZones() {
        log.info("사고다발지 실제 데이터 수집 시작");
        try {
            int saved = gyeonggiDataService.fetchAndSaveAccidentZones();
            return ResponseEntity.ok(Map.of("savedCount", saved, "message", "사고다발지 데이터 수집 완료"));
        } catch (Exception e) {
            log.error("사고다발지 데이터 수집 실패: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/fetch/protected-zones")
    public ResponseEntity<Map<String, Object>> fetchProtectedZones() {
        log.info("교통약자 보호구역 데이터 수집 시작");
        try {
            int saved = publicDataService.fetchAndSaveProtectedZones();
            return ResponseEntity.ok(Map.of("savedCount", saved, "message", "교통약자 보호구역 데이터 수집 완료"));
        } catch (Exception e) {
            log.error("교통약자 보호구역 수집 실패: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/fetch/welfare-facilities")
    public ResponseEntity<Map<String, Object>> fetchWelfareFacilities() {
        log.info("장애인복지시설 데이터 수집 시작");
        try {
            int saved = publicDataService.fetchAndSaveWelfareFacilities();
            return ResponseEntity.ok(Map.of("savedCount", saved, "message", "장애인복지시설 데이터 수집 완료"));
        } catch (Exception e) {
            log.error("장애인복지시설 수집 실패: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/fetch/shade-shelters")
    public ResponseEntity<Map<String, Object>> fetchShadeShelters() {
        log.info("그늘막 데이터 수집 시작");
        try {
            int saved = publicDataService.fetchAndSaveShadeShelters();
            return ResponseEntity.ok(Map.of("savedCount", saved, "message", "그늘막 데이터 수집 완료"));
        } catch (Exception e) {
            log.error("그늘막 수집 실패: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/fetch/senior-centers")
    public ResponseEntity<Map<String, Object>> fetchSeniorCenters() {
        log.info("노인복지관 데이터 수집 시작");
        try {
            int saved = publicDataService.fetchAndSaveSeniorCenters();
            return ResponseEntity.ok(Map.of("savedCount", saved, "message", "노인복지관 데이터 수집 완료"));
        } catch (Exception e) {
            log.error("노인복지관 수집 실패: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/fetch/cctv")
    public ResponseEntity<Map<String, Object>> fetchCctv() {
        log.info("CCTV 실제 데이터 수집 시작");
        try {
            int saved = publicDataService.fetchAndSaveCctvLocations();
            return ResponseEntity.ok(Map.of("savedCount", saved, "message", "CCTV 데이터 수집 완료"));
        } catch (Exception e) {
            log.error("CCTV 수집 실패: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /** 전체 안전 데이터 수집 */
    @PostMapping("/fetch/all")
    public ResponseEntity<Map<String, Object>> fetchAllSafetyData() {
        log.info("전체 안전 데이터 수집 시작");
        Map<String, Object> result = new java.util.HashMap<>();
        StringBuilder errors = new StringBuilder();

        result.put("securityLights", safeFetch("보안등", () -> gyeonggiDataService.fetchAndSaveSecurityLights(), errors));
        result.put("accidentZones", safeFetch("사고다발지", () -> gyeonggiDataService.fetchAndSaveAccidentZones(), errors));
        result.put("protectedZones", safeFetch("교통약자보호구역", () -> publicDataService.fetchAndSaveProtectedZones(), errors));
        result.put("welfareFacilities", safeFetch("장애인복지시설", () -> publicDataService.fetchAndSaveWelfareFacilities(), errors));
        result.put("shadeShelters", safeFetch("그늘막", () -> publicDataService.fetchAndSaveShadeShelters(), errors));
        result.put("seniorCenters", safeFetch("노인복지관", () -> publicDataService.fetchAndSaveSeniorCenters(), errors));
        result.put("cctv", safeFetch("CCTV", () -> publicDataService.fetchAndSaveCctvLocations(), errors));

        if (errors.length() > 0) result.put("errors", errors.toString());
        return ResponseEntity.ok(result);
    }

    private int safeFetch(String name, java.util.concurrent.Callable<Integer> task, StringBuilder errors) {
        try {
            return task.call();
        } catch (Exception e) {
            log.error("{} 수집 실패: {}", name, e.getMessage());
            if (errors.length() > 0) errors.append(" | ");
            errors.append(name).append(": ").append(e.getMessage());
            return 0;
        }
    }

    // ========== 시드 데이터 ==========

    /** 성남시 전체 안전시설 시드 데이터 삽입 (7개 카테고리) */
    @PostMapping("/seed/all")
    public ResponseEntity<Map<String, Object>> seedAllSafetyData() {
        log.info("성남시 전체 안전시설 시드 데이터 삽입 시작");

        // --- 보안등 ---
        securityLightRepository.deleteAll();
        List<SecurityLight> lights = List.of(
            new SecurityLight("성남시청 앞 보안등", 37.4200, 127.1265, "성남시 수정구 수정로 283", "41131"),
            new SecurityLight("수정구청 앞 보안등", 37.4530, 127.1560, "성남시 수정구 수정로 398", "41131"),
            new SecurityLight("중원구청 앞 보안등", 37.4310, 127.1380, "성남시 중원구 중앙동", "41133"),
            new SecurityLight("분당구청 앞 보안등", 37.3830, 127.1220, "성남시 분당구 정자동", "41135"),
            new SecurityLight("야탑역 인근 보안등", 37.4110, 127.1280, "성남시 분당구 야탑동", "41135"),
            new SecurityLight("모란시장 인근 보안등", 37.4320, 127.1290, "성남시 중원구 성남동", "41133"),
            new SecurityLight("서현역 인근 보안등", 37.3850, 127.1250, "성남시 분당구 서현동", "41135"),
            new SecurityLight("신흥역 인근 보안등", 37.4400, 127.1510, "성남시 수정구 신흥동", "41131"),
            new SecurityLight("단대오거리역 인근 보안등", 37.4440, 127.1570, "성남시 수정구 단대동", "41131"),
            new SecurityLight("태평역 인근 보안등", 37.4470, 127.1470, "성남시 수정구 태평동", "41131")
        );
        securityLightRepository.saveAll(lights);

        // --- 사고다발지 ---
        accidentZoneRepository.deleteAll();
        List<AccidentZone> accidents = List.of(
            new AccidentZone("성남대로-중앙대로 교차로", 37.4380, 127.1370, "NORMAL", "41133"),
            new AccidentZone("산성대로-단대로 교차로", 37.4510, 127.1590, "ICY", "41131"),
            new AccidentZone("분당수서로-돌마로 교차로", 37.3790, 127.1190, "NORMAL", "41135"),
            new AccidentZone("야탑로-성남대로 교차로", 37.4080, 127.1260, "NORMAL", "41135"),
            new AccidentZone("수정로-남한산성로 교차로", 37.4530, 127.1690, "ICY", "41131"),
            new AccidentZone("모란로-성남대로 교차로", 37.4340, 127.1300, "NORMAL", "41133"),
            new AccidentZone("돌마로-서현로 교차로", 37.3820, 127.1280, "ICY", "41135"),
            new AccidentZone("여수대로-금광로 교차로", 37.4290, 127.1460, "NORMAL", "41133")
        );
        accidentZoneRepository.saveAll(accidents);

        // --- CCTV ---
        cctvLocationRepository.deleteAll();
        List<CctvLocation> cctvs = List.of(
            new CctvLocation("성남시청 CCTV", 37.4195, 127.1270, "성남시 수정구 수정로 283"),
            new CctvLocation("수정구청 CCTV", 37.4535, 127.1565, "성남시 수정구 수정로 398"),
            new CctvLocation("모란시장 CCTV", 37.4325, 127.1285, "성남시 중원구 성남동"),
            new CctvLocation("야탑역 CCTV", 37.4105, 127.1275, "성남시 분당구 야탑동"),
            new CctvLocation("서현역 CCTV", 37.3845, 127.1255, "성남시 분당구 서현동"),
            new CctvLocation("분당구청 CCTV", 37.3835, 127.1225, "성남시 분당구 정자동"),
            new CctvLocation("태평역 CCTV", 37.4475, 127.1475, "성남시 수정구 태평동"),
            new CctvLocation("신흥역 CCTV", 37.4405, 127.1505, "성남시 수정구 신흥동"),
            new CctvLocation("남한산성입구 CCTV", 37.4560, 127.1750, "성남시 수정구 산성동"),
            new CctvLocation("단대오거리역 CCTV", 37.4445, 127.1575, "성남시 수정구 단대동")
        );
        cctvLocationRepository.saveAll(cctvs);

        // --- 교통약자 보호구역 ---
        protectedZoneRepository.deleteAll();
        List<ProtectedZone> zones = List.of(
            new ProtectedZone("성남초등학교 앞", 37.4370, 127.1360, "어린이보호구역", "성남시 중원구 성남동", "41133"),
            new ProtectedZone("단대초등학교 앞", 37.4490, 127.1600, "어린이보호구역", "성남시 수정구 단대동", "41131"),
            new ProtectedZone("양지초등학교 앞", 37.4550, 127.1650, "어린이보호구역", "성남시 수정구 양지동", "41131"),
            new ProtectedZone("은행복지관 앞", 37.4460, 127.1920, "노인보호구역", "성남시 중원구 은행동", "41133"),
            new ProtectedZone("수진동 경로당 앞", 37.4380, 127.1420, "노인보호구역", "성남시 수정구 수진동", "41131"),
            new ProtectedZone("중원구 장애인복지관 앞", 37.4310, 127.1400, "장애인보호구역", "성남시 중원구 중앙동", "41133")
        );
        protectedZoneRepository.saveAll(zones);

        // --- 장애인복지시설 ---
        welfareFacilityRepository.deleteAll();
        List<WelfareFacility> facilities = List.of(
            new WelfareFacility("성남시장애인종합복지관", 37.4250, 127.1350, "종합복지관", "성남시 중원구 둔촌대로 101", "031-729-1400", "41133"),
            new WelfareFacility("중원구장애인복지관", 37.4310, 127.1400, "지역복지관", "성남시 중원구 중앙동", "031-731-8500", "41133"),
            new WelfareFacility("분당장애인복지관", 37.3860, 127.1230, "지역복지관", "성남시 분당구 정자동", "031-712-7200", "41135"),
            new WelfareFacility("성남시시각장애인복지관", 37.4280, 127.1320, "시각장애인", "성남시 중원구 성남동", "031-735-3311", "41133"),
            new WelfareFacility("성남시청각장애인복지관", 37.4230, 127.1280, "청각장애인", "성남시 중원구 금광동", "031-737-6280", "41133")
        );
        welfareFacilityRepository.saveAll(facilities);

        // --- 그늘막 ---
        shadeShelterRepository.deleteAll();
        List<ShadeShelter> shelters = List.of(
            new ShadeShelter("탄천 산책로 그늘막1", 37.4100, 127.1290, "성남시 분당구 야탑동 탄천변", "41135"),
            new ShadeShelter("탄천 산책로 그늘막2", 37.4200, 127.1310, "성남시 중원구 성남동 탄천변", "41133"),
            new ShadeShelter("탄천 산책로 그늘막3", 37.4300, 127.1330, "성남시 중원구 중앙동 탄천변", "41133"),
            new ShadeShelter("중앙공원 그늘막", 37.4350, 127.1370, "성남시 중원구 중앙동", "41133"),
            new ShadeShelter("분당중앙공원 그늘막", 37.3840, 127.1240, "성남시 분당구 서현동", "41135"),
            new ShadeShelter("율동공원 그늘막", 37.3770, 127.0960, "성남시 분당구 율동", "41135")
        );
        shadeShelterRepository.saveAll(shelters);

        // --- 노인복지관 ---
        seniorCenterRepository.deleteAll();
        List<SeniorCenter> seniors = List.of(
            new SeniorCenter("성남시노인종합복지관", 37.4260, 127.1340, "성남시 중원구 둔촌대로 85", "031-726-0505", "41133"),
            new SeniorCenter("중원노인복지관", 37.4300, 127.1390, "성남시 중원구 중앙동", "031-748-0091", "41133"),
            new SeniorCenter("분당노인종합복지관", 37.3870, 127.1210, "성남시 분당구 정자동", "031-783-8870", "41135"),
            new SeniorCenter("수정노인복지관", 37.4520, 127.1540, "성남시 수정구 수정로", "031-732-1230", "41131")
        );
        seniorCenterRepository.saveAll(seniors);

        Map<String, Object> result = new java.util.HashMap<>();
        result.put("securityLights", lights.size());
        result.put("accidentZones", accidents.size());
        result.put("cctv", cctvs.size());
        result.put("protectedZones", zones.size());
        result.put("welfareFacilities", facilities.size());
        result.put("shadeShelters", shelters.size());
        result.put("seniorCenters", seniors.size());

        log.info("성남시 전체 안전시설 시드 데이터 삽입 완료");
        return ResponseEntity.ok(result);
    }
}
