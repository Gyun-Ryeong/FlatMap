package com.flatmap.navigation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flatmap.navigation.config.ApiKeyConfig;
import com.flatmap.navigation.entity.AccidentZone;
import com.flatmap.navigation.entity.SecurityLight;
import com.flatmap.navigation.entity.SeniorCenter;
import com.flatmap.navigation.entity.SteepSlopeArea;
import com.flatmap.navigation.repository.AccidentZoneRepository;
import com.flatmap.navigation.repository.SecurityLightRepository;
import com.flatmap.navigation.repository.SeniorCenterRepository;
import com.flatmap.navigation.repository.SteepSlopeAreaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
public class GyeonggiDataService {

    private static final Logger log = LoggerFactory.getLogger(GyeonggiDataService.class);
    private static final String GG_BASE = "https://openapi.gg.go.kr";
    private static final int TIMEOUT_MS = 5000; // 5s — openapi.gg.go.kr is occasionally slow

    private final RestTemplate restTemplate;
    private final ApiKeyConfig apiKeyConfig;
    private final ObjectMapper objectMapper;
    private final SteepSlopeAreaRepository steepSlopeAreaRepository;
    private final SecurityLightRepository securityLightRepository;
    private final AccidentZoneRepository accidentZoneRepository;
    private final SeniorCenterRepository seniorCenterRepository;

    public GyeonggiDataService(RestTemplate restTemplate, ApiKeyConfig apiKeyConfig,
                                ObjectMapper objectMapper,
                                SteepSlopeAreaRepository steepSlopeAreaRepository,
                                SecurityLightRepository securityLightRepository,
                                AccidentZoneRepository accidentZoneRepository,
                                SeniorCenterRepository seniorCenterRepository) {
        this.restTemplate = restTemplate;
        this.apiKeyConfig = apiKeyConfig;
        this.objectMapper = objectMapper;
        this.steepSlopeAreaRepository = steepSlopeAreaRepository;
        this.securityLightRepository = securityLightRepository;
        this.accidentZoneRepository = accidentZoneRepository;
        this.seniorCenterRepository = seniorCenterRepository;
    }

    // ============================================================
    // Common Gyeonggi API caller with 5-second timeout
    // ============================================================

    private String callGyeonggiApi(String serviceName, String apiKey, int page, int size) {
        String url = String.format("%s/%s?KEY=%s&Type=json&pIndex=%d&pSize=%d",
                GG_BASE, serviceName, apiKey, page, size);
        log.info("[경기] API 호출: {}", url.replace(apiKey, "***"));

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT_MS);
        factory.setReadTimeout(TIMEOUT_MS);
        RestTemplate ggRestTemplate = new RestTemplate(factory);

        try {
            String raw = ggRestTemplate.getForObject(url, String.class);
            if (raw != null) {
                log.info("[경기][{}] 응답 앞 300자: {}", serviceName,
                        raw.substring(0, Math.min(raw.length(), 300)));
            }
            return raw;
        } catch (Exception e) {
            log.error("[경기][{}] API 호출 실패 (timeout={}ms): {}", serviceName, TIMEOUT_MS, e.getMessage());
            return null;
        }
    }

    /** Extract the row array from Gyeonggi-style JSON: { "ServiceName": [{head}, {row:[...]}] } */
    private JsonNode findRows(JsonNode root) {
        var fieldNames = root.fieldNames();
        while (fieldNames.hasNext()) {
            String key = fieldNames.next();
            JsonNode service = root.path(key);
            if (service.isArray() && service.size() > 1) {
                JsonNode rows = service.get(1).path("row");
                if (rows.isArray() && !rows.isEmpty()) return rows;
            }
        }
        return null;
    }

    // ============================================================
    // 오르막차로 (SteepSlopeArea)
    // ============================================================

    public int fetchAndSaveUphillLanes() {
        log.info("=== 경기데이터드림 오르막차로 데이터 수집 시작 ===");
        List<SteepSlopeArea> allAreas = new ArrayList<>();
        int page = 1;

        while (true) {
            String raw = callGyeonggiApi("UphillLane", apiKeyConfig.getGyeonggiUphillLaneApiKey(), page, 100);
            if (raw == null || raw.trim().startsWith("<")) break;

            try {
                JsonNode root = objectMapper.readTree(raw);
                JsonNode rows = findRows(root);
                if (rows == null || rows.isEmpty()) break;

                if (page == 1) log.info("[오르막차로] 첫 row: {}", rows.get(0));

                for (JsonNode row : rows) {
                    SteepSlopeArea area = parseUphillLaneRow(row);
                    if (area != null) allAreas.add(area);
                }

                if (rows.size() < 100) break;
                page++;
            } catch (Exception e) {
                log.error("[오르막차로] 파싱 실패: {}", e.getMessage());
                break;
            }
        }

        if (!allAreas.isEmpty()) {
            steepSlopeAreaRepository.deleteAll(steepSlopeAreaRepository.findBySource("GYEONGGI"));
            steepSlopeAreaRepository.saveAll(allAreas);
            log.info("GYEONGGI 오르막차로 {}건 저장", allAreas.size());
        }
        return allAreas.size();
    }

    private SteepSlopeArea parseUphillLaneRow(JsonNode row) {
        String name = getFieldValue(row, "ROAD_NM", "SECT_NM", "UPHILL_LANE_NM", "NM", "MANAGE_NM");
        if (name == null) name = "오르막차로 구간";
        Double lat = getFieldDouble(row, "REFINE_WGS84_LAT", "LAT", "Y");
        Double lng = getFieldDouble(row, "REFINE_WGS84_LOGT", "LNG", "LOT", "X");
        if (lat == null || lng == null) return null;
        Double grade = getFieldDouble(row, "GRADIENT", "GRADE", "SLOPE", "INCLINE_RATE");
        if (grade == null) grade = 0.0;
        String regionCode = getFieldValue(row, "SIGUN_CD", "REGION_CD", "SIGNGU_CD");
        return new SteepSlopeArea(name, lat, lng, grade, SteepSlopeArea.calculateRiskLevel(grade), "GYEONGGI", regionCode);
    }

    // ============================================================
    // 보안등 — SECRTLGT
    // ============================================================

    public int fetchAndSaveSecurityLights() {
        log.info("=== 경기데이터드림 보안등(SECRTLGT) 데이터 수집 시작 ===");
        List<SecurityLight> all = new ArrayList<>();

        try {
            String raw = callGyeonggiApi("SECRTLGT", apiKeyConfig.getGyeonggiApiKey(), 1, 100);
            if (raw == null || raw.trim().startsWith("<")) {
                log.warn("[SECRTLGT] 유효한 응답 없음");
                return 0;
            }
            JsonNode root = objectMapper.readTree(raw);
            JsonNode rows = findRows(root);
            if (rows != null) {
                if (!rows.isEmpty()) log.info("[SECRTLGT] 첫 row: {}", rows.get(0));
                for (JsonNode row : rows) {
                    // TODO: 실제 응답 확인 후 필드명 조정 (INSTL_PL_CD, INST_NM, SECRTLGT_NM 등)
                    String name = getFieldValue(row, "INST_NM", "INSTL_PL_CD", "SECRTLGT_NM", "NM", "MANAGE_NM");
                    Double lat = getFieldDouble(row, "REFINE_WGS84_LAT", "LAT");
                    Double lng = getFieldDouble(row, "REFINE_WGS84_LOGT", "LNG", "LOT");
                    String addr = getFieldValue(row, "REFINE_ROADNM_ADDR", "REFINE_LOTNO_ADDR", "ADDR");
                    String regionCode = getFieldValue(row, "SIGUN_CD", "SIGUN_NM");
                    if (lat != null && lng != null) {
                        if (name == null) name = "보안등";
                        all.add(new SecurityLight(name, lat, lng, addr, regionCode));
                    }
                }
            }
        } catch (Exception e) {
            log.error("[SECRTLGT] 데이터 수집 실패: {}", e.getMessage());
        }

        if (!all.isEmpty()) {
            securityLightRepository.deleteAll();
            securityLightRepository.saveAll(all);
            log.info("[SECRTLGT] 보안등 {}건 저장 완료", all.size());
        }
        return all.size();
    }

    // ============================================================
    // 사고다발지 — IceTfcacdar (결빙) + TfcacdarM (일반)
    // ============================================================

    public int fetchAndSaveAccidentZones() {
        log.info("=== 경기데이터드림 사고다발지 데이터 수집 시작 ===");
        List<AccidentZone> all = new ArrayList<>();

        all.addAll(fetchAccidentService("IceTfcacdar", "ICY"));
        all.addAll(fetchAccidentService("TfcacdarM", "NORMAL"));

        if (!all.isEmpty()) {
            accidentZoneRepository.deleteAll();
            accidentZoneRepository.saveAll(all);
            log.info("사고다발지 총 {}건 저장 완료 (성남시 필터링 후)", all.size());
        }
        return all.size();
    }

    private List<AccidentZone> fetchAccidentService(String serviceName, String type) {
        List<AccidentZone> result = new ArrayList<>();
        int page = 1;
        while (true) {
            try {
                String raw = callGyeonggiApi(serviceName, apiKeyConfig.getGyeonggiApiKey(), page, 1000);
                if (raw == null || raw.trim().startsWith("<")) break;
                JsonNode rows = findRows(objectMapper.readTree(raw));
                if (rows == null || rows.isEmpty()) break;
                if (page == 1) log.info("[{}] 첫 row: {}", serviceName, rows.get(0));
                for (JsonNode row : rows) {
                    AccidentZone z = parseAccidentRow(row, type, serviceName);
                    if (z != null) result.add(z);
                }
                log.info("[{}] page={} {}건 조회, 성남시 {}건 누적", serviceName, page, rows.size(), result.size());
                if (rows.size() < 1000) break;
                page++;
            } catch (Exception e) {
                log.error("[{}] 수집 실패: {}", serviceName, e.getMessage());
                break;
            }
        }
        return result;
    }

    private AccidentZone parseAccidentRow(JsonNode row, String type, String serviceName) {
        String regionCode = getFieldValue(row, "SIGUN_CD", "SIGUN_NM");
        // 성남시 SIGUN_CD: 41130(성남시), 41131(수정구), 41133(중원구), 41135(분당구)
        if (!isSeongnam(regionCode)) return null;

        String name = getFieldValue(row, "LOC_INFO", "ACDNT_DIV_NM", "SPOT_NM", "ACCDNT_SPOT_NM",
                "ACCDNT_NM", "ROAD_NM", "SECT_NM", "NM", "MANAGE_NM");
        Double lat = getFieldDouble(row, "REFINE_WGS84_LAT", "LAT", "Y_COORD", "GPS_Y",
                "CRDNT_Y", "POINT_Y", "WGS84_LAT", "Y");
        Double lng = getFieldDouble(row, "REFINE_WGS84_LOGT", "LOGT", "LNG", "LOT", "X_COORD",
                "GPS_X", "CRDNT_X", "POINT_X", "WGS84_LOGT", "X");
        if (lat == null || lng == null) return null;
        if (name == null) name = "ICY".equals(type) ? "결빙사고 구간" : "사고다발 구간";
        return new AccidentZone(name, lat, lng, type, regionCode);
    }

    // ============================================================
    // 노인복지관 + 경로당 — OldpsnLsrWelfaclt + SenircentFaclt
    // ============================================================

    public int fetchAndSaveSeniorCenters() {
        log.info("=== 경기데이터드림 노인복지관(OldpsnLsrWelfaclt) + 경로당(SenircentFaclt) 수집 시작 ===");
        List<SeniorCenter> all = new ArrayList<>();

        all.addAll(fetchSeniorService("OldpsnLsrWelfaclt", "노인복지관"));
        all.addAll(fetchSeniorService("SenircentFaclt", "경로당"));

        if (!all.isEmpty()) {
            seniorCenterRepository.deleteAll();
            seniorCenterRepository.saveAll(all);
            log.info("노인복지관+경로당 총 {}건 저장 완료 (성남시 필터링 후)", all.size());
        }
        return all.size();
    }

    private List<SeniorCenter> fetchSeniorService(String serviceName, String defaultName) {
        List<SeniorCenter> result = new ArrayList<>();
        int page = 1;
        while (true) {
            try {
                String raw = callGyeonggiApi(serviceName, apiKeyConfig.getGyeonggiApiKey(), page, 1000);
                if (raw == null || raw.trim().startsWith("<")) break;
                JsonNode rows = findRows(objectMapper.readTree(raw));
                if (rows == null || rows.isEmpty()) break;
                if (page == 1) log.info("[{}] 첫 row: {}", serviceName, rows.get(0));
                for (JsonNode row : rows) {
                    SeniorCenter c = parseSeniorCenterRow(row, defaultName);
                    if (c != null) result.add(c);
                }
                log.info("[{}] page={} {}건 조회, 성남시 {}건 누적", serviceName, page, rows.size(), result.size());
                if (rows.size() < 1000) break;
                page++;
            } catch (Exception e) {
                log.error("[{}] 수집 실패: {}", serviceName, e.getMessage());
                break;
            }
        }
        return result;
    }

    private SeniorCenter parseSeniorCenterRow(JsonNode row, String defaultName) {
        String regionCode = getFieldValue(row, "SIGUN_CD", "SIGUN_NM");
        if (!isSeongnam(regionCode)) return null;
        String name = getFieldValue(row, "FACLT_NM", "INSTIT_NM", "WLFRE_FACLT_NM", "INST_NM", "NM");
        if (name == null) name = defaultName;
        Double lat = getFieldDouble(row, "REFINE_WGS84_LAT", "LAT");
        Double lng = getFieldDouble(row, "REFINE_WGS84_LOGT", "LOGT", "LNG", "LOT");
        if (lat == null || lng == null) return null;
        String addr = getFieldValue(row, "REFINE_ROADNM_ADDR", "REFINE_LOTNO_ADDR", "ADDR");
        String phone = getFieldValue(row, "TELNO", "TEL", "PHONE");
        return new SeniorCenter(name, lat, lng, addr, phone, regionCode);
    }

    // ============================================================
    // Legacy raw-response getters (kept for backward compat)
    // ============================================================

    public String getUphillLanes(int page, int size) {
        return callGyeonggiApi("UphillLane", apiKeyConfig.getGyeonggiUphillLaneApiKey(), page, size);
    }

    // ============================================================
    // Utilities
    // ============================================================

    private boolean isSeongnam(String regionCode) {
        if (regionCode == null) return false;
        // 성남시: 41130, 수정구: 41131, 중원구: 41133, 분당구: 41135
        return regionCode.startsWith("4113");
    }

    private String getFieldValue(JsonNode row, String... fieldNames) {
        for (String name : fieldNames) {
            JsonNode node = row.path(name);
            if (!node.isMissingNode() && !node.isNull()) {
                String val = node.asText().trim();
                if (!val.isEmpty()) return val;
            }
        }
        return null;
    }

    private Double getFieldDouble(JsonNode row, String... fieldNames) {
        for (String name : fieldNames) {
            JsonNode node = row.path(name);
            if (!node.isMissingNode() && !node.isNull()) {
                String val = node.asText().trim();
                if (!val.isEmpty()) {
                    try { return Double.parseDouble(val); }
                    catch (NumberFormatException ignored) {}
                }
            }
        }
        return null;
    }
}
