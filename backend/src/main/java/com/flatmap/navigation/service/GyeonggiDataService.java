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
public class GyeonggiDataService {

    private static final Logger log = LoggerFactory.getLogger(GyeonggiDataService.class);

    private final RestTemplate restTemplate;
    private final ApiKeyConfig apiKeyConfig;
    private final ObjectMapper objectMapper;
    private final SteepSlopeAreaRepository steepSlopeAreaRepository;

    public GyeonggiDataService(RestTemplate restTemplate, ApiKeyConfig apiKeyConfig,
                                ObjectMapper objectMapper, SteepSlopeAreaRepository steepSlopeAreaRepository) {
        this.restTemplate = restTemplate;
        this.apiKeyConfig = apiKeyConfig;
        this.objectMapper = objectMapper;
        this.steepSlopeAreaRepository = steepSlopeAreaRepository;
    }

    // ============================================================
    // 오르막차로 데이터를 DB에 저장하는 메서드
    // ============================================================

    /**
     * 경기데이터드림 오르막차로 API를 호출하여 SteepSlopeArea 테이블에 저장한다.
     * 응답 구조가 불확실하므로 디버깅 로그를 상세히 남긴다.
     */
    public int fetchAndSaveUphillLanes() {
        log.info("=== 경기데이터드림 오르막차로 데이터 수집 시작 ===");

        List<SteepSlopeArea> allAreas = new ArrayList<>();
        int page = 1;
        int pageSize = 100;
        boolean hasMore = true;

        while (hasMore) {
            String rawResponse;
            try {
                rawResponse = getUphillLanes(page, pageSize);
            } catch (Exception e) {
                log.error("오르막차로 API 호출 실패: {}", e.getMessage());
                break;
            }

            // HTML 응답 감지 (서버 장애 시 HTML 에러 페이지 반환)
            if (rawResponse == null || rawResponse.trim().startsWith("<")) {
                log.error("오르막차로 API가 JSON이 아닌 응답을 반환했습니다 (서버 장애 가능). 응답 앞부분: {}",
                        rawResponse != null ? rawResponse.substring(0, Math.min(rawResponse.length(), 200)) : "null");
                break;
            }

            log.info("[DEBUG] 오르막차로 API 응답 (page {}): {}", page,
                    rawResponse.substring(0, Math.min(rawResponse.length(), 500)));

            try {
                JsonNode root = objectMapper.readTree(rawResponse);

                // 경기데이터드림 응답 구조: { "UphillLane": [ {head: [...]}, {row: [...]} ] }
                JsonNode uphillLane = root.path("UphillLane");
                if (!uphillLane.isArray() || uphillLane.isEmpty()) {
                    // RESULT 에러 체크 (인증 실패 등)
                    JsonNode result = root.path("RESULT");
                    if (!result.isMissingNode()) {
                        log.error("경기데이터드림 API 에러: CODE={}, MESSAGE={}",
                                result.path("CODE").asText(), result.path("MESSAGE").asText());
                    } else {
                        log.warn("'UphillLane' 키가 없거나 비어있음. 전체 응답 키: {}", iterableToString(root.fieldNames()));
                    }
                    break;
                }

                // head에서 총 건수 확인
                JsonNode head = uphillLane.get(0).path("head");
                if (head.isArray() && !head.isEmpty()) {
                    int totalCount = head.get(0).path("list_total_count").asInt(0);
                    log.info("[DEBUG] 전체 데이터 건수: {}", totalCount);
                    String resultCode = head.get(1).path("RESULT").path("CODE").asText("");
                    String resultMsg = head.get(1).path("RESULT").path("MESSAGE").asText("");
                    log.info("[DEBUG] 결과 코드: {}, 메시지: {}", resultCode, resultMsg);

                    if (!"INFO-000".equals(resultCode)) {
                        log.error("API 오류 응답: {} - {}", resultCode, resultMsg);
                        break;
                    }
                }

                // row 배열에서 데이터 추출
                JsonNode rows = uphillLane.size() > 1 ? uphillLane.get(1).path("row") : null;
                if (rows == null || !rows.isArray() || rows.isEmpty()) {
                    log.info("[DEBUG] page {} 에 row 데이터 없음 — 수집 종료", page);
                    hasMore = false;
                    break;
                }

                log.info("[DEBUG] page {} row 개수: {}", page, rows.size());
                // 첫 번째 row의 필드명 로깅 (구조 파악용)
                if (page == 1 && rows.size() > 0) {
                    log.info("[DEBUG] row 필드 목록: {}", rows.get(0).fieldNames());
                    log.info("[DEBUG] 첫 번째 row 전체: {}", rows.get(0).toString());
                }

                for (JsonNode row : rows) {
                    try {
                        SteepSlopeArea area = parseUphillLaneRow(row);
                        if (area != null) {
                            allAreas.add(area);
                        }
                    } catch (Exception e) {
                        log.warn("[DEBUG] row 파싱 실패: {}, 에러: {}", row, e.getMessage());
                    }
                }

                // 다음 페이지 여부 확인
                if (rows.size() < pageSize) {
                    hasMore = false;
                } else {
                    page++;
                }

            } catch (Exception e) {
                log.error("[DEBUG] 오르막차로 응답 파싱 실패: {}", e.getMessage(), e);
                break;
            }
        }

        // 기존 GYEONGGI 소스 데이터 삭제 후 새로 저장
        if (!allAreas.isEmpty()) {
            long deleted = steepSlopeAreaRepository.countBySource("GYEONGGI");
            steepSlopeAreaRepository.deleteAll(steepSlopeAreaRepository.findBySource("GYEONGGI"));
            log.info("기존 GYEONGGI 데이터 {}건 삭제", deleted);
            steepSlopeAreaRepository.saveAll(allAreas);
            log.info("GYEONGGI 오르막차로 데이터 {}건 저장 완료", allAreas.size());
        }

        return allAreas.size();
    }

    /**
     * 오르막차로 row를 파싱하여 SteepSlopeArea로 변환한다.
     * TODO: 실제 API 응답 필드명을 확인 후 수정 필요
     */
    private SteepSlopeArea parseUphillLaneRow(JsonNode row) {
        // TODO: 실제 필드명은 API 호출 후 로그에서 확인하여 수정
        // 가능한 필드명: ROAD_NM, SECT_NM, LATITUDE, LONGITUDE, GRADIENT, SIGUN_CD 등
        String name = getFieldValue(row, "ROAD_NM", "SECT_NM", "UPHILL_LANE_NM", "NM");
        Double latitude = getFieldDouble(row, "REFINE_WGS84_LAT", "LATITUDE", "LAT", "Y");
        Double longitude = getFieldDouble(row, "REFINE_WGS84_LOGT", "LONGITUDE", "LNG", "LOT", "X");
        Double grade = getFieldDouble(row, "GRADIENT", "GRADE", "SLOPE", "INCLINE_RATE");
        String regionCode = getFieldValue(row, "SIGUN_CD", "REGION_CD", "SIGNGU_CD");

        if (name == null || name.isEmpty()) {
            name = getFieldValue(row, "MANAGE_NM", "ROAD_ROUTE_NM", "SECT_NM");
            if (name == null) name = "오르막차로 구간";
        }

        // 좌표가 없으면 저장 불가
        if (latitude == null || longitude == null) {
            log.debug("[DEBUG] 좌표 없는 row 건너뜀: {}", row);
            return null;
        }

        // 경사도 없으면 기본값 0
        if (grade == null) grade = 0.0;

        String riskLevel = SteepSlopeArea.calculateRiskLevel(grade);

        return new SteepSlopeArea(name, latitude, longitude, grade, riskLevel, "GYEONGGI", regionCode);
    }

    // ============================================================
    // 기존 단순 호출 메서드들 (하위 호환)
    // ============================================================

    public String getSecurityLights(int page, int size) {
        String url = String.format(
                "https://openapi.gg.go.kr/SecurityLight?KEY=%s&Type=json&pIndex=%d&pSize=%d",
                apiKeyConfig.getGyeonggiSecurityLightApiKey(), page, size);
        return restTemplate.getForObject(url, String.class);
    }

    public String getAccidentIcyZones(int page, int size) {
        String url = String.format(
                "https://openapi.gg.go.kr/AccidentIcy?KEY=%s&Type=json&pIndex=%d&pSize=%d",
                apiKeyConfig.getGyeonggiAccidentIcyApiKey(), page, size);
        return restTemplate.getForObject(url, String.class);
    }

    public String getAccidentNormalZones(int page, int size) {
        String url = String.format(
                "https://openapi.gg.go.kr/AccidentNormal?KEY=%s&Type=json&pIndex=%d&pSize=%d",
                apiKeyConfig.getGyeonggiAccidentNormalApiKey(), page, size);
        return restTemplate.getForObject(url, String.class);
    }

    public String getUphillLanes(int page, int size) {
        String url = String.format(
                "https://openapi.gg.go.kr/UphillLane?KEY=%s&Type=json&pIndex=%d&pSize=%d",
                apiKeyConfig.getGyeonggiUphillLaneApiKey(), page, size);
        log.info("[DEBUG] 오르막차로 API 호출: {}", url.replace(apiKeyConfig.getGyeonggiUphillLaneApiKey(), "***"));
        String response = restTemplate.getForObject(url, String.class);
        log.info("[RAW-RESPONSE] 오르막차로 API 응답 전체 (page={}, size={}): {}", page, size,
                response != null ? response.substring(0, Math.min(response.length(), 2000)) : "null");
        return response;
    }

    public String getSeniorWelfareFacilities(int page, int size) {
        String url = String.format(
                "https://openapi.gg.go.kr/SeniorWelfare?KEY=%s&Type=json&pIndex=%d&pSize=%d",
                apiKeyConfig.getGyeonggiSeniorWelfareApiKey(), page, size);
        return restTemplate.getForObject(url, String.class);
    }

    public String getSeniorCenters(int page, int size) {
        String url = String.format(
                "https://openapi.gg.go.kr/SeniorCenter?KEY=%s&Type=json&pIndex=%d&pSize=%d",
                apiKeyConfig.getGyeonggiSeniorCenterApiKey(), page, size);
        return restTemplate.getForObject(url, String.class);
    }

    // ============================================================
    // 유틸: 여러 후보 필드명에서 값을 찾는 헬퍼
    // ============================================================

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
                    try {
                        return Double.parseDouble(val);
                    } catch (NumberFormatException e) {
                        log.debug("[DEBUG] 숫자 변환 실패 필드={}, 값={}", name, val);
                    }
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
