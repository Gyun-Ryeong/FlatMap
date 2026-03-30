package com.flatmap.navigation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flatmap.navigation.config.ApiKeyConfig;
import com.flatmap.navigation.entity.SteepSlopeArea;
import com.flatmap.navigation.repository.SteepSlopeAreaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
public class DisasterDataService {

    private static final Logger log = LoggerFactory.getLogger(DisasterDataService.class);

    private final RestTemplate restTemplate;
    private final ApiKeyConfig apiKeyConfig;
    private final ObjectMapper objectMapper;
    private final SteepSlopeAreaRepository steepSlopeAreaRepository;

    public DisasterDataService(RestTemplate restTemplate, ApiKeyConfig apiKeyConfig,
                                ObjectMapper objectMapper, SteepSlopeAreaRepository steepSlopeAreaRepository) {
        this.restTemplate = restTemplate;
        this.apiKeyConfig = apiKeyConfig;
        this.objectMapper = objectMapper;
        this.steepSlopeAreaRepository = steepSlopeAreaRepository;
    }

    // ============================================================
    // 급경사지 데이터를 DB에 저장하는 메서드
    // ============================================================

    public int fetchAndSaveSteepSlopes() {
        log.info("=== 재난안전데이터 급경사지 데이터 수집 시작 ===");

        List<SteepSlopeArea> allAreas = new ArrayList<>();

        // 1. 급경사지 현황 (DSSP-IF-10158) — 좌표 DMS 형식
        allAreas.addAll(fetchSteepSlopeLocations());

        // 2. 내리막 사고 (DSSP-IF-10006) — 좌표 십진수
        allAreas.addAll(fetchTrafficDownhill());

        // 기존 DISASTER 소스 데이터 삭제 후 새로 저장
        if (!allAreas.isEmpty()) {
            long deleted = steepSlopeAreaRepository.countBySource("DISASTER");
            steepSlopeAreaRepository.deleteAll(steepSlopeAreaRepository.findBySource("DISASTER"));
            log.info("기존 DISASTER 데이터 {}건 삭제", deleted);
            steepSlopeAreaRepository.saveAll(allAreas);
            log.info("DISASTER 데이터 {}건 저장 완료", allAreas.size());
        }

        return allAreas.size();
    }

    // ============================================================
    // DSSP-IF-10158: 급경사지 현황 (좌표: DMS 도분초)
    // ============================================================

    private List<SteepSlopeArea> fetchSteepSlopeLocations() {
        String apiId = "DSSP-IF-10158";
        String serviceKey = apiKeyConfig.getDisasterSteepSlopeApiKey();

        List<SteepSlopeArea> areas = new ArrayList<>();
        int page = 1;
        int pageSize = 1000;
        boolean hasMore = true;

        while (hasMore) {
            JsonNode root = callApi(apiId, serviceKey, page, pageSize);
            if (root == null) break;

            JsonNode body = root.path("body");
            if (!body.isArray() || body.isEmpty()) {
                log.info("{} page {} body가 비어있음 — 수집 종료", apiId, page);
                break;
            }

            if (page == 1) {
                log.info("{} totalCount={}, 첫 item 필드: {}", apiId,
                        root.path("totalCount").asInt(), iterableToString(body.get(0).fieldNames()));
            }

            for (JsonNode item : body) {
                try {
                    SteepSlopeArea area = parseSteepSlopeItem(item);
                    if (area != null) areas.add(area);
                } catch (Exception e) {
                    log.warn("{} item 파싱 실패: {}", apiId, e.getMessage());
                }
            }

            int totalCount = root.path("totalCount").asInt(0);
            if (page * pageSize >= totalCount || body.size() < pageSize) {
                hasMore = false;
            } else {
                page++;
            }

            log.info("{} page {} 처리 완료 — 누적 {}건", apiId, page, areas.size());
        }

        log.info("{} 에서 총 {}건 추출", apiId, areas.size());
        return areas;
    }

    private SteepSlopeArea parseSteepSlopeItem(JsonNode item) {
        String name = getFieldValue(item, "SCAR_NM");
        if (name == null || name.isEmpty()) name = "급경사지 구간";

        // DMS(도분초) → 십진수 변환
        Double latitude = dmsToDecimal(
                getFieldDouble(item, "GIS_CRTS_FNLST_LAT_PROVIN"),
                getFieldDouble(item, "GIS_CRTS_FNLST_LAT_MIN"),
                getFieldDouble(item, "GIS_CRTS_FNLST_LAT_SEC_1"));

        Double longitude = dmsToDecimal(
                getFieldDouble(item, "GIS_CRTS_FNLST_LOT_PROVIN"),
                getFieldDouble(item, "GIS_CRTS_FNLST_LOT_MIN"),
                getFieldDouble(item, "GIS_CRTS_FNLST_LOT_SEC_1"));

        if (latitude == null || longitude == null) return null;

        String regionCode = getFieldValue(item, "STDG_CD_1");

        return new SteepSlopeArea(name, latitude, longitude, 0.0, "MEDIUM", "DISASTER", regionCode);
    }

    private Double dmsToDecimal(Double degrees, Double minutes, Double seconds) {
        if (degrees == null || minutes == null || seconds == null) return null;
        return degrees + minutes / 60.0 + seconds / 3600.0;
    }

    // ============================================================
    // DSSP-IF-10006: 내리막 사고 (좌표: 십진수 LAT/LOT)
    // ============================================================

    private List<SteepSlopeArea> fetchTrafficDownhill() {
        String apiId = "DSSP-IF-10006";
        String serviceKey = apiKeyConfig.getDisasterTrafficDownhillApiKey();

        List<SteepSlopeArea> areas = new ArrayList<>();
        int page = 1;
        int pageSize = 1000;
        boolean hasMore = true;

        while (hasMore) {
            JsonNode root = callApi(apiId, serviceKey, page, pageSize);
            if (root == null) break;

            JsonNode body = root.path("body");
            if (!body.isArray() || body.isEmpty()) {
                log.info("{} page {} body가 비어있음 — 수집 종료", apiId, page);
                break;
            }

            if (page == 1) {
                log.info("{} totalCount={}, 첫 item 필드: {}", apiId,
                        root.path("totalCount").asInt(), iterableToString(body.get(0).fieldNames()));
            }

            for (JsonNode item : body) {
                try {
                    SteepSlopeArea area = parseDownhillItem(item);
                    if (area != null) areas.add(area);
                } catch (Exception e) {
                    log.warn("{} item 파싱 실패: {}", apiId, e.getMessage());
                }
            }

            int totalCount = root.path("totalCount").asInt(0);
            if (page * pageSize >= totalCount || body.size() < pageSize) {
                hasMore = false;
            } else {
                page++;
            }

            log.info("{} page {} 처리 완료 — 누적 {}건", apiId, page, areas.size());
        }

        log.info("{} 에서 총 {}건 추출", apiId, areas.size());
        return areas;
    }

    private SteepSlopeArea parseDownhillItem(JsonNode item) {
        String name = getFieldValue(item, "DTL_CN");
        if (name == null || name.isEmpty()) name = "내리막 사고 구간";

        Double latitude = getFieldDouble(item, "LAT");
        Double longitude = getFieldDouble(item, "LOT");

        if (latitude == null || longitude == null) return null;

        return new SteepSlopeArea(name, latitude, longitude, 0.0, "HIGH", "DISASTER", null);
    }

    // ============================================================
    // 공통 API 호출
    // ============================================================

    private JsonNode callApi(String apiId, String serviceKey, int page, int pageSize) {
        String url = String.format(
                "https://www.safetydata.go.kr/V2/api/%s?serviceKey=%s&pageNo=%d&numOfRows=%d&returnType=json",
                apiId, serviceKey, page, pageSize);
        log.info("API 호출 ({}) page={}", apiId, page);

        String rawResponse;
        try {
            rawResponse = restTemplate.getForObject(url, String.class);
        } catch (Exception e) {
            log.error("API 호출 실패 ({}): {}", apiId, e.getMessage());
            return null;
        }

        log.info("[RAW-RESPONSE] {} API 응답 전체 (page={}): {}", apiId, page,
                rawResponse != null ? rawResponse.substring(0, Math.min(rawResponse.length(), 2000)) : "null");

        if (rawResponse == null || rawResponse.trim().startsWith("<")) {
            log.error("{} JSON이 아닌 응답 반환 (서버 장애 가능)", apiId);
            return null;
        }

        try {
            JsonNode root = objectMapper.readTree(rawResponse);

            // header 에러 체크
            JsonNode header = root.path("header");
            if (!header.isMissingNode()) {
                String resultCode = header.path("resultCode").asText("");
                if (!"00".equals(resultCode)) {
                    log.error("{} API 에러: resultCode={}, errorMsg={}",
                            apiId, resultCode, header.path("errorMsg").asText(""));
                    return null;
                }
            }

            return root;
        } catch (Exception e) {
            log.error("{} 응답 파싱 실패: {}", apiId, e.getMessage());
            return null;
        }
    }

    // ============================================================
    // 기존 단순 호출 메서드들 (하위 호환)
    // ============================================================

    public String getSteepSlopes(int page, int size) {
        String url = String.format(
                "https://www.safetydata.go.kr/V2/api/DSSP-IF-10158?serviceKey=%s&pageNo=%d&numOfRows=%d&returnType=json",
                apiKeyConfig.getDisasterSteepSlopeApiKey(), page, size);
        return restTemplate.getForObject(url, String.class);
    }

    public String getTrafficDownhillAccidents(int page, int size) {
        String url = String.format(
                "https://www.safetydata.go.kr/V2/api/DSSP-IF-10006?serviceKey=%s&pageNo=%d&numOfRows=%d&returnType=json",
                apiKeyConfig.getDisasterTrafficDownhillApiKey(), page, size);
        return restTemplate.getForObject(url, String.class);
    }

    public String getSteepSlopesFinal(int page, int size) {
        String url = String.format(
                "https://www.safetydata.go.kr/V2/api/DSSP-IF-10159?serviceKey=%s&pageNo=%d&numOfRows=%d&returnType=json",
                apiKeyConfig.getDisasterSteepSlopeFinalApiKey(), page, size);
        return restTemplate.getForObject(url, String.class);
    }

    // ============================================================
    // 유틸
    // ============================================================

    private String getFieldValue(JsonNode item, String... fieldNames) {
        for (String name : fieldNames) {
            JsonNode node = item.path(name);
            if (!node.isMissingNode() && !node.isNull()) {
                String val = node.asText().trim();
                if (!val.isEmpty()) return val;
            }
        }
        return null;
    }

    private Double getFieldDouble(JsonNode item, String... fieldNames) {
        for (String name : fieldNames) {
            JsonNode node = item.path(name);
            if (!node.isMissingNode() && !node.isNull()) {
                try {
                    return node.asDouble();
                } catch (Exception e) {
                    log.debug("숫자 변환 실패 필드={}", name);
                }
            }
        }
        return null;
    }

    private String iterableToString(java.util.Iterator<?> iter) {
        StringBuilder sb = new StringBuilder("[");
        while (iter.hasNext()) {
            if (sb.length() > 1) sb.append(", ");
            sb.append(iter.next());
        }
        sb.append("]");
        return sb.toString();
    }
}
