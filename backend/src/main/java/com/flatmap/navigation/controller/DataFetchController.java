package com.flatmap.navigation.controller;

import com.flatmap.navigation.service.DataSeedService;
import com.flatmap.navigation.service.DisasterDataService;
import com.flatmap.navigation.service.GyeonggiDataService;
import com.flatmap.navigation.service.PublicDataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * 모든 외부 API 데이터를 한 번에 수집���는 통합 엔드포인트.
 */
@RestController
@RequestMapping("/api/data")
public class DataFetchController {

    private static final Logger log = LoggerFactory.getLogger(DataFetchController.class);

    private final GyeonggiDataService gyeonggiDataService;
    private final DisasterDataService disasterDataService;
    private final PublicDataService publicDataService;
    private final DataSeedService dataSeedService;

    public DataFetchController(GyeonggiDataService gyeonggiDataService,
                                DisasterDataService disasterDataService,
                                PublicDataService publicDataService,
                                DataSeedService dataSeedService) {
        this.gyeonggiDataService = gyeonggiDataService;
        this.disasterDataService = disasterDataService;
        this.publicDataService = publicDataService;
        this.dataSeedService = dataSeedService;
    }

    /**
     * 모든 외부 API를 한 번에 호출하여 DB에 저장한다.
     * 각 데이터별 저장 건수를 반환한다.
     * 실제 데이터 수집 성공 시 더미 데이터는 자동으로 덮어씌워진다.
     */
    @PostMapping("/fetch/all")
    public ResponseEntity<Map<String, Object>> fetchAllData() {
        log.info("========== 전체 데이터 통합 수집 시작 ==========");
        long startTime = System.currentTimeMillis();

        Map<String, Object> result = new HashMap<>();
        StringBuilder errors = new StringBuilder();

        // === 급경사지 데이터 ===
        result.put("steepSlope_gyeonggi", safeFetch("경기데이터드림 오르막차로",
                gyeonggiDataService::fetchAndSaveUphillLanes, errors));
        result.put("steepSlope_disaster", safeFetch("재난안전데이터 급경사지",
                disasterDataService::fetchAndSaveSteepSlopes, errors));
        result.put("steepSlope_publicData", safeFetch("공공데이터포털 급경사지",
                publicDataService::fetchAndSaveSteepSlopes, errors));

        // === 안전시설 데이터 ===
        result.put("securityLights", safeFetch("보안등",
                gyeonggiDataService::fetchAndSaveSecurityLights, errors));
        result.put("accidentZones", safeFetch("사고다발지",
                gyeonggiDataService::fetchAndSaveAccidentZones, errors));

        // === 공공데이터포털 추가 데이터 ===
        result.put("protectedZones", safeFetch("교통약자 보호구역",
                publicDataService::fetchAndSaveProtectedZones, errors));
        result.put("welfareFacilities", safeFetch("장애인복지시설",
                publicDataService::fetchAndSaveWelfareFacilities, errors));
        result.put("shadeShelters", safeFetch("그늘막",
                publicDataService::fetchAndSaveShadeShelters, errors));
        result.put("seniorCenters", safeFetch("노인복지관",
                publicDataService::fetchAndSaveSeniorCenters, errors));
        result.put("cctv", safeFetch("CCTV",
                publicDataService::fetchAndSaveCctvLocations, errors));

        long elapsed = System.currentTimeMillis() - startTime;
        result.put("elapsedMs", elapsed);
        if (errors.length() > 0) result.put("errors", errors.toString());

        log.info("========== 전체 데이터 통합 수집 완료 ({}ms) ==========", elapsed);
        return ResponseEntity.ok(result);
    }

    private int safeFetch(String name, Callable<Integer> task, StringBuilder errors) {
        try {
            int count = task.call();
            log.info("[통합수집] {} → {}건 저장", name, count);
            return count;
        } catch (Exception e) {
            log.error("[통합수집] {} 실패: {}", name, e.getMessage());
            if (errors.length() > 0) errors.append(" | ");
            errors.append(name).append(": ").append(e.getMessage());
            return 0;
        }
    }

    /**
     * 성남시 시드 데이터를 삽입한다.
     * 이미 동일 name이 존재하면 건너뛰어 중복 삽입을 방지한다.
     */
    @PostMapping("/seed/all")
    public ResponseEntity<Map<String, Object>> seedAllData() {
        log.info("========== 시드 데이터 삽입 시작 ==========");
        Map<String, Object> result = dataSeedService.seedAll();
        return ResponseEntity.ok(result);
    }
}
