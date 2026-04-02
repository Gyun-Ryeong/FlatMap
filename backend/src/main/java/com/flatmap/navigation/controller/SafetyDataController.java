package com.flatmap.navigation.controller;

import com.flatmap.navigation.entity.AccidentZone;
import com.flatmap.navigation.entity.CctvLocation;
import com.flatmap.navigation.entity.SecurityLight;
import com.flatmap.navigation.repository.AccidentZoneRepository;
import com.flatmap.navigation.repository.CctvLocationRepository;
import com.flatmap.navigation.repository.SecurityLightRepository;
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

    public SafetyDataController(SecurityLightRepository securityLightRepository,
                                AccidentZoneRepository accidentZoneRepository,
                                CctvLocationRepository cctvLocationRepository) {
        this.securityLightRepository = securityLightRepository;
        this.accidentZoneRepository = accidentZoneRepository;
        this.cctvLocationRepository = cctvLocationRepository;
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
        // 최대 100개 제한
        if (result.size() > 100) result = result.subList(0, 100);
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
        if (result.size() > 100) result = result.subList(0, 100);
        return ResponseEntity.ok(result);
    }

    // ========== CCTV ==========

    @GetMapping("/cctv")
    public ResponseEntity<List<CctvLocation>> getCctvLocations() {
        List<CctvLocation> result = cctvLocationRepository.findAll();
        if (result.size() > 100) result = result.subList(0, 100);
        return ResponseEntity.ok(result);
    }

    // ========== 더미 데이터 시드 ==========

    @PostMapping("/seed")
    public ResponseEntity<Map<String, Object>> seedSafetyData() {
        log.info("안전 시설 더미 데이터 삽입 시작");

        // --- 보안등 (신구대~산성역 경로 주변) ---
        securityLightRepository.deleteAll();
        List<SecurityLight> lights = List.of(
            new SecurityLight("복정동 보안등 1", 37.4440, 127.1470, "성남시 수정구 복정동 123", "41131"),
            new SecurityLight("복정동 보안등 2", 37.4455, 127.1452, "성남시 수정구 복정동 156", "41131"),
            new SecurityLight("단대동 보안등 1", 37.4475, 127.1420, "성남시 수정구 단대동 45", "41131"),
            new SecurityLight("단대동 보안등 2", 37.4480, 127.1395, "성남시 수정구 단대동 78", "41131"),
            new SecurityLight("단대동 보안등 3", 37.4470, 127.1360, "성남시 수정구 단대동 112", "41131"),
            new SecurityLight("산성동 보안등 1", 37.4495, 127.1335, "성남시 수정구 산성동 23", "41131"),
            new SecurityLight("산성동 보안등 2", 37.4520, 127.1300, "성남시 수정구 산성동 56", "41131"),
            new SecurityLight("산성동 보안등 3", 37.4550, 127.1275, "성남시 수정구 산성동 89", "41131"),
            new SecurityLight("성남동 보안등 1", 37.4410, 127.1380, "성남시 중원구 성남동 34", "41133"),
            new SecurityLight("성남동 보안등 2", 37.4425, 127.1410, "성남시 중원구 성남동 67", "41133"),
            new SecurityLight("중앙동 보안등 1", 37.4390, 127.1430, "성남시 중원구 중앙동 12", "41133"),
            new SecurityLight("야탑동 보안등 1", 37.4120, 127.1280, "성남시 분당구 야탑동 45", "41135")
        );
        securityLightRepository.saveAll(lights);

        // --- 사고다발지 (결빙 + 일반) ---
        accidentZoneRepository.deleteAll();
        List<AccidentZone> accidents = List.of(
            new AccidentZone("복정동 결빙사고 다발지", 37.4445, 127.1465, "ICY", "41131"),
            new AccidentZone("단대동 결빙사고 구간", 37.4472, 127.1410, "ICY", "41131"),
            new AccidentZone("산성동 결빙 위험구간", 37.4560, 127.1270, "ICY", "41131"),
            new AccidentZone("복정동 교차로 사고다발", 37.4450, 127.1448, "NORMAL", "41131"),
            new AccidentZone("단대동 횡단보도 사고", 37.4468, 127.1355, "NORMAL", "41131"),
            new AccidentZone("산성역 앞 사고다발지", 37.4575, 127.1260, "NORMAL", "41131"),
            new AccidentZone("성남동 사고다발 구간", 37.4415, 127.1390, "NORMAL", "41133"),
            new AccidentZone("중앙동 결빙사고 지점", 37.4400, 127.1420, "ICY", "41133"),
            new AccidentZone("야탑역 사고다발지", 37.4110, 127.1270, "NORMAL", "41135")
        );
        accidentZoneRepository.saveAll(accidents);

        // --- CCTV ---
        cctvLocationRepository.deleteAll();
        List<CctvLocation> cctvs = List.of(
            new CctvLocation("복정동 CCTV", 37.4442, 127.1472, "성남시 수정구 복정동"),
            new CctvLocation("단대동 CCTV 1", 37.4478, 127.1415, "성남시 수정구 단대동"),
            new CctvLocation("단대동 CCTV 2", 37.4465, 127.1350, "성남시 수정구 단대동"),
            new CctvLocation("산성동 CCTV", 37.4500, 127.1330, "성남시 수정구 산성동"),
            new CctvLocation("산성역 CCTV", 37.4565, 127.1265, "성남시 수정구 산성동"),
            new CctvLocation("성남동 CCTV", 37.4420, 127.1400, "성남시 중원구 성남동"),
            new CctvLocation("중앙동 CCTV", 37.4395, 127.1435, "성남시 중원구 중앙동"),
            new CctvLocation("야탑동 CCTV", 37.4115, 127.1278, "성남시 분당구 야탑동")
        );
        cctvLocationRepository.saveAll(cctvs);

        log.info("안전 시설 더미 데이터 삽입 완료: 보안등 {}건, 사고다발지 {}건, CCTV {}건",
                lights.size(), accidents.size(), cctvs.size());

        return ResponseEntity.ok(Map.of(
            "securityLights", lights.size(),
            "accidentZones", accidents.size(),
            "cctv", cctvs.size()
        ));
    }
}
