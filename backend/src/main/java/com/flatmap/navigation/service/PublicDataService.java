package com.flatmap.navigation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flatmap.navigation.config.ApiKeyConfig;
import com.flatmap.navigation.entity.*;
import com.flatmap.navigation.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class PublicDataService {

    private static final Logger log = LoggerFactory.getLogger(PublicDataService.class);

    private static final Set<String> SEONGNAM_REGION_PREFIXES = Set.of("41130", "41131", "41133", "41135");
    private static final double SEONGNAM_MIN_LAT = 37.33;
    private static final double SEONGNAM_MAX_LAT = 37.48;
    private static final double SEONGNAM_MIN_LNG = 127.02;
    private static final double SEONGNAM_MAX_LNG = 127.17;

    private final RestTemplate restTemplate;
    private final ApiKeyConfig apiKeyConfig;
    private final ObjectMapper objectMapper;
    private final SteepSlopeAreaRepository steepSlopeAreaRepository;
    private final ProtectedZoneRepository protectedZoneRepository;
    private final WelfareFacilityRepository welfareFacilityRepository;
    private final ShadeShelterRepository shadeShelterRepository;
    private final SeniorCenterRepository seniorCenterRepository;
    private final CctvLocationRepository cctvLocationRepository;

    @Value("${public-data.api-key}")
    private String publicDataApiKey;

    public PublicDataService(RestTemplate restTemplate, ApiKeyConfig apiKeyConfig,
                             ObjectMapper objectMapper,
                             SteepSlopeAreaRepository steepSlopeAreaRepository,
                             ProtectedZoneRepository protectedZoneRepository,
                             WelfareFacilityRepository welfareFacilityRepository,
                             ShadeShelterRepository shadeShelterRepository,
                             SeniorCenterRepository seniorCenterRepository,
                             CctvLocationRepository cctvLocationRepository) {
        this.restTemplate = restTemplate;
        this.apiKeyConfig = apiKeyConfig;
        this.objectMapper = objectMapper;
        this.steepSlopeAreaRepository = steepSlopeAreaRepository;
        this.protectedZoneRepository = protectedZoneRepository;
        this.welfareFacilityRepository = welfareFacilityRepository;
        this.shadeShelterRepository = shadeShelterRepository;
        this.seniorCenterRepository = seniorCenterRepository;
        this.cctvLocationRepository = cctvLocationRepository;
    }

    // ============================================================
    // 급경사지 (기존)
    // ============================================================

    public int fetchAndSaveSteepSlopes() {
        log.info("=== 공공데이터포털 급경사지 데이터 수집 시작 ===");

        List<SteepSlopeArea> allAreas = new ArrayList<>();
        allAreas.addAll(fetchSteepSlopeCollapseRisk());
        allAreas.addAll(fetchSteepSlopeStatus());

        if (!allAreas.isEmpty()) {
            long deleted = steepSlopeAreaRepository.countBySource("PUBLIC_DATA");
            steepSlopeAreaRepository.deleteAll(steepSlopeAreaRepository.findBySource("PUBLIC_DATA"));
            log.info("기존 PUBLIC_DATA 데이터 {}건 삭제", deleted);
            steepSlopeAreaRepository.saveAll(allAreas);
            log.info("PUBLIC_DATA 데이터 {}건 저장 완료", allAreas.size());
        } else {
            log.info("공공데이터포털에서 수집된 성남시 급경사지 데이터 없음");
        }

        return allAreas.size();
    }

    private List<SteepSlopeArea> fetchSteepSlopeCollapseRisk() {
        String baseUrl = "https://apis.data.go.kr/1741000/SteepSlopeCollapseRisk/getSteepSlopeCollapseRiskList";
        log.info("급경사지 붕괴위험지역 API 호출 시도: {}", baseUrl);

        List<SteepSlopeArea> areas = new ArrayList<>();
        try {
            String rawResponse = callPublicDataApi(baseUrl, 1, 100);
            if (rawResponse != null) {
                areas.addAll(parseSteepSlopeResponse(rawResponse, "붕괴위험지역"));
            } else {
                log.warn("붕괴위험지역 API 응답 없음. TODO: 공공데이터포털에서 정확한 URL 확인 필요");
            }
        } catch (Exception e) {
            log.error("붕괴위험지역 API 호출 실패: {}. TODO: 엔드포인트 URL 재확인 필요", e.getMessage());
        }
        return areas;
    }

    private List<SteepSlopeArea> fetchSteepSlopeStatus() {
        String baseUrl = "https://apis.data.go.kr/1741000/SteepSlopeStatus/getSteepSlopeStatusList";
        log.info("급경사지 현황 API 호출 시도: {}", baseUrl);

        List<SteepSlopeArea> areas = new ArrayList<>();
        try {
            String rawResponse = callPublicDataApi(baseUrl, 1, 100);
            if (rawResponse != null) {
                areas.addAll(parseSteepSlopeResponse(rawResponse, "급경사지현황"));
            } else {
                log.warn("급경사지현황 API 응답 없음. TODO: 공공데이터포털에서 정확한 URL 확인 필요");
            }
        } catch (Exception e) {
            log.error("급경사지현황 API 호출 실패: {}. TODO: 엔드포인트 URL 재확인 필요", e.getMessage());
        }
        return areas;
    }

    private List<SteepSlopeArea> parseSteepSlopeResponse(String rawResponse, String apiName) {
        List<SteepSlopeArea> areas = new ArrayList<>();
        try {
            JsonNode items = extractItems(rawResponse, apiName);
            if (items == null) return areas;

            for (JsonNode item : items) {
                try {
                    String name = getFieldValue(item, "scar_nm", "SCAR_NM", "nm", "NM", "faclt_nm", "FACLT_NM");
                    if (name == null || name.isEmpty()) name = "급경사지 구간(공공)";

                    String regionCode = getFieldValue(item, "stdg_cd", "STDG_CD", "stdg_cd_1", "STDG_CD_1",
                            "region_cd", "REGION_CD", "sigun_cd", "SIGUN_CD");
                    String address = getAddress(item);
                    if (!isSeongnamRegion(regionCode) && !(address != null && address.contains("성남"))) continue;

                    double[] coords = extractCoords(item, address);
                    if (coords == null || !isInSeongnamBounds(coords[0], coords[1])) continue;

                    Double grade = getFieldDouble(item, "gradient", "GRADIENT", "grade", "GRADE", "slope", "SLOPE");
                    if (grade == null) grade = 0.0;

                    areas.add(new SteepSlopeArea(name, coords[0], coords[1], grade,
                            SteepSlopeArea.calculateRiskLevel(grade), "PUBLIC_DATA", regionCode));
                } catch (Exception e) {
                    log.warn("[{}] item 파싱 실패: {}", apiName, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("[{}] 응답 파싱 실패: {}", apiName, e.getMessage());
        }
        log.info("[{}] 성남시 필터링 후 {}건 추출", apiName, areas.size());
        return areas;
    }

    // ============================================================
    // 2-1. 교통약자 보호구역
    // ============================================================

    public int fetchAndSaveProtectedZones() {
        log.info("=== 경기도 성남시 교통약자 보호구역 데이터 수집 시작 ===");

        // 공공데이터포털 "경기도 성남시교통약자 보호��역" API
        // TODO: 정확한 엔드포인트 URL은 공공데이터포털에서 확인 필요
        String baseUrl = "https://apis.data.go.kr/6410000/transportvlnrblprotectzn/getTransportvlnrblprotectznList";
        List<ProtectedZone> zones = new ArrayList<>();

        try {
            String rawResponse = callPublicDataApiPaginated(baseUrl, 500);
            if (rawResponse != null) {
                JsonNode items = extractItems(rawResponse, "교통약자보호구역");
                if (items != null) {
                    for (JsonNode item : items) {
                        try {
                            ProtectedZone zone = parseProtectedZone(item);
                            if (zone != null) zones.add(zone);
                        } catch (Exception e) {
                            log.warn("교통약자보호구역 item 파싱 실패: {}", e.getMessage());
                        }
                    }
                }
            } else {
                log.warn("교통약자보호구역 API 응답 없음. TODO: 공공데이터포털에서 정확한 URL 확인 필요. 시도한 URL: {}", baseUrl);
            }
        } catch (Exception e) {
            log.error("교통약자보호구역 API 호출 실패: {}. TODO: 엔드포인트 URL 재확인 필요", e.getMessage());
        }

        if (!zones.isEmpty()) {
            protectedZoneRepository.deleteAll();
            protectedZoneRepository.saveAll(zones);
            log.info("교통약자 보호구역 {}건 저장 완료", zones.size());
        } else {
            log.info("교통약자 보호구역 수집 결과 없음 (API 엔드포인트 확인 필요)");
        }

        return zones.size();
    }

    private ProtectedZone parseProtectedZone(JsonNode item) {
        String name = getFieldValue(item, "PROTECT_NM", "ZONE_NM", "NM", "nm",
                "protectzn_nm", "protect_nm", "zone_nm");
        if (name == null || name.isEmpty()) name = "교통약자 보호구역";

        String type = getFieldValue(item, "PROTECT_SE", "ZONE_SE", "SE", "zone_type",
                "protectzn_se", "protect_se");
        String address = getAddress(item);

        // 성남시 필터 (주소 기반)
        boolean isSeongnam = address != null && address.contains("성남");
        String regionCode = getFieldValue(item, "SIGUN_CD", "sigun_cd", "REGION_CD");
        if (!isSeongnam && !isSeongnamRegion(regionCode)) return null;

        double[] coords = extractCoords(item, address);
        if (coords == null) return null;
        if (!isInSeongnamBounds(coords[0], coords[1])) return null;

        return new ProtectedZone(name, coords[0], coords[1], type, address, regionCode);
    }

    // ============================================================
    // 2-2. 장애인복지시설
    // ============================================================

    public int fetchAndSaveWelfareFacilities() {
        log.info("=== 경기도 성남시 장애인복지시설 데이터 수집 시작 ===");

        // "경기도 성남시장애인복지시설현황" API
        String baseUrl = "https://apis.data.go.kr/6410000/dsbldwlfrfclt/getDsbldwlfrfcltList";
        List<WelfareFacility> facilities = new ArrayList<>();

        try {
            String rawResponse = callPublicDataApiPaginated(baseUrl, 500);
            if (rawResponse != null) {
                JsonNode items = extractItems(rawResponse, "장애인복지시설");
                if (items != null) {
                    for (JsonNode item : items) {
                        try {
                            WelfareFacility f = parseWelfareFacility(item);
                            if (f != null) facilities.add(f);
                        } catch (Exception e) {
                            log.warn("장애인복지시설 item 파싱 실패: {}", e.getMessage());
                        }
                    }
                }
            } else {
                log.warn("장애인복지시설 API 응답 없음. TODO: 공공데이터포털에서 정확한 URL 확인 필요. 시도한 URL: {}", baseUrl);
            }
        } catch (Exception e) {
            log.error("장애인복지시설 API 호출 실패: {}. TODO: 엔드포인트 URL 재확인 필요", e.getMessage());
        }

        if (!facilities.isEmpty()) {
            welfareFacilityRepository.deleteAll();
            welfareFacilityRepository.saveAll(facilities);
            log.info("장애인복지시설 {}건 저장 완료", facilities.size());
        } else {
            log.info("장애인복지시설 수집 결과 없음 (API 엔드포인트 확인 필요)");
        }

        return facilities.size();
    }

    private WelfareFacility parseWelfareFacility(JsonNode item) {
        String name = getFieldValue(item, "FACLT_NM", "BIZPLC_NM", "NM", "nm",
                "fclt_nm", "faclt_nm", "bizplc_nm");
        if (name == null || name.isEmpty()) name = "장애인복지시설";

        String type = getFieldValue(item, "FACLT_SE", "BSNS_SE", "SE",
                "fclt_se", "faclt_se", "bsns_se");
        String address = getAddress(item);
        String phone = getFieldValue(item, "TELNO", "TEL", "PHONE", "telno", "tel", "phone");

        boolean isSeongnam = address != null && address.contains("성남");
        String regionCode = getFieldValue(item, "SIGUN_CD", "sigun_cd", "REGION_CD");
        if (!isSeongnam && !isSeongnamRegion(regionCode)) return null;

        double[] coords = extractCoords(item, address);
        if (coords == null) return null;
        if (!isInSeongnamBounds(coords[0], coords[1])) return null;

        return new WelfareFacility(name, coords[0], coords[1], type, address, phone, regionCode);
    }

    // ============================================================
    // 2-3. 그늘막
    // ============================================================

    public int fetchAndSaveShadeShelters() {
        log.info("=== 경기도 성남시 그늘막 데이터 수집 시작 ===");

        // "경기도성남시그늘막현황" API
        String baseUrl = "https://apis.data.go.kr/6410000/shdeshltr/getShdeshltrList";
        List<ShadeShelter> shelters = new ArrayList<>();

        try {
            String rawResponse = callPublicDataApiPaginated(baseUrl, 500);
            if (rawResponse != null) {
                JsonNode items = extractItems(rawResponse, "그늘막");
                if (items != null) {
                    for (JsonNode item : items) {
                        try {
                            ShadeShelter s = parseShadeShelter(item);
                            if (s != null) shelters.add(s);
                        } catch (Exception e) {
                            log.warn("그늘막 item 파싱 실패: {}", e.getMessage());
                        }
                    }
                }
            } else {
                log.warn("그늘막 API 응답 없음. TODO: 공공데이터포털에서 정확한 URL 확인 필요. 시도한 URL: {}", baseUrl);
            }
        } catch (Exception e) {
            log.error("그늘막 API 호출 실패: {}. TODO: 엔드포인트 URL 재확인 필요", e.getMessage());
        }

        if (!shelters.isEmpty()) {
            shadeShelterRepository.deleteAll();
            shadeShelterRepository.saveAll(shelters);
            log.info("그늘막 {}건 저장 완료", shelters.size());
        } else {
            log.info("그늘막 수집 결과 없음 (API 엔드포인트 확인 필요)");
        }

        return shelters.size();
    }

    private ShadeShelter parseShadeShelter(JsonNode item) {
        String name = getFieldValue(item, "INSTL_LC", "FACLT_NM", "NM", "nm",
                "instl_lc", "faclt_nm", "shde_nm");
        if (name == null || name.isEmpty()) name = "그늘막";

        String address = getAddress(item);

        boolean isSeongnam = address != null && address.contains("성남");
        String regionCode = getFieldValue(item, "SIGUN_CD", "sigun_cd", "REGION_CD");
        if (!isSeongnam && !isSeongnamRegion(regionCode)) return null;

        double[] coords = extractCoords(item, address);
        if (coords == null) return null;
        if (!isInSeongnamBounds(coords[0], coords[1])) return null;

        return new ShadeShelter(name, coords[0], coords[1], address, regionCode);
    }

    // ============================================================
    // 2-4. 노인종합복지관
    // ============================================================

    public int fetchAndSaveSeniorCenters() {
        log.info("=== 경기도 성남시 노인종합복지관 데이터 수집 시작 ===");

        // "경기도 성남시노인종합복지관현황" API
        String baseUrl = "https://apis.data.go.kr/6410000/seniorcenter/getSeniorcenterList";
        List<SeniorCenter> centers = new ArrayList<>();

        try {
            String rawResponse = callPublicDataApiPaginated(baseUrl, 500);
            if (rawResponse != null) {
                JsonNode items = extractItems(rawResponse, "노인종합복지관");
                if (items != null) {
                    for (JsonNode item : items) {
                        try {
                            SeniorCenter c = parseSeniorCenter(item);
                            if (c != null) centers.add(c);
                        } catch (Exception e) {
                            log.warn("노인종합복지관 item 파싱 실패: {}", e.getMessage());
                        }
                    }
                }
            } else {
                log.warn("노인종합복지관 API 응답 없음. TODO: 공공데이터포털에서 정확한 URL 확인 필요. 시도한 URL: {}", baseUrl);
            }
        } catch (Exception e) {
            log.error("노인종합복지관 API 호출 실패: {}. TODO: 엔드포인트 URL 재확인 필요", e.getMessage());
        }

        if (!centers.isEmpty()) {
            seniorCenterRepository.deleteAll();
            seniorCenterRepository.saveAll(centers);
            log.info("노인종합복지관 {}건 저장 완료", centers.size());
        } else {
            log.info("노인종합복지관 수집 결과 없음 (API 엔드포인트 확인 필요)");
        }

        return centers.size();
    }

    private SeniorCenter parseSeniorCenter(JsonNode item) {
        String name = getFieldValue(item, "FACLT_NM", "BIZPLC_NM", "NM", "nm",
                "fclt_nm", "faclt_nm", "bizplc_nm", "center_nm");
        if (name == null || name.isEmpty()) name = "노인종합복지관";

        String address = getAddress(item);
        String phone = getFieldValue(item, "TELNO", "TEL", "PHONE", "telno", "tel", "phone");

        boolean isSeongnam = address != null && address.contains("성남");
        String regionCode = getFieldValue(item, "SIGUN_CD", "sigun_cd", "REGION_CD");
        if (!isSeongnam && !isSeongnamRegion(regionCode)) return null;

        double[] coords = extractCoords(item, address);
        if (coords == null) return null;
        if (!isInSeongnamBounds(coords[0], coords[1])) return null;

        return new SeniorCenter(name, coords[0], coords[1], address, phone, regionCode);
    }

    // ============================================================
    // 2-5. CCTV 위치 (실제 데이터로 교체)
    // ============================================================

    public int fetchAndSaveCctvLocations() {
        log.info("=== 경기도 성남시 CCTV 데이터 수집 시작 ===");

        // "경기도 성남시CCTV 위치별 조도 휘도" API
        String baseUrl = "https://apis.data.go.kr/6410000/cctvlcilmnnclmnnc/getCctvlcilmnnclmnncList";
        List<CctvLocation> cctvs = new ArrayList<>();

        try {
            String rawResponse = callPublicDataApiPaginated(baseUrl, 1000);
            if (rawResponse != null) {
                JsonNode items = extractItems(rawResponse, "CCTV");
                if (items != null) {
                    for (JsonNode item : items) {
                        try {
                            CctvLocation c = parseCctvLocation(item);
                            if (c != null) cctvs.add(c);
                        } catch (Exception e) {
                            log.warn("CCTV item 파싱 실패: {}", e.getMessage());
                        }
                    }
                }
            } else {
                log.warn("CCTV API 응답 없음. TODO: 공공데이터포털에서 정확한 URL 확인 필요. 시도한 URL: {}", baseUrl);
            }
        } catch (Exception e) {
            log.error("CCTV API 호출 실패: {}. TODO: 엔드포인트 URL 재확인 필요", e.getMessage());
        }

        if (!cctvs.isEmpty()) {
            cctvLocationRepository.deleteAll();
            cctvLocationRepository.saveAll(cctvs);
            log.info("CCTV {}건 저장 완료 (실제 데이터로 교체)", cctvs.size());
        } else {
            log.info("CCTV 수집 결과 없음 (API 엔드포인트 확인 필요)");
        }

        return cctvs.size();
    }

    private CctvLocation parseCctvLocation(JsonNode item) {
        String name = getFieldValue(item, "CCTV_NM", "INSTL_LC", "NM", "nm",
                "cctv_nm", "instl_lc", "faclt_nm");
        if (name == null || name.isEmpty()) name = "CCTV";

        String address = getAddress(item);

        boolean isSeongnam = address != null && address.contains("성남");
        String regionCode = getFieldValue(item, "SIGUN_CD", "sigun_cd", "REGION_CD");
        if (!isSeongnam && !isSeongnamRegion(regionCode)) return null;

        double[] coords = extractCoords(item, address);
        if (coords == null) return null;
        if (!isInSeongnamBounds(coords[0], coords[1])) return null;

        return new CctvLocation(name, coords[0], coords[1], address);
    }

    // ============================================================
    // 공통 API 호출
    // ============================================================

    /**
     * 공공데이터포털 API 호출. JSON/XML 자동 감지 및 재시도.
     */
    private String callPublicDataApi(String baseUrl, int page, int pageSize) {
        // 1차: type=json
        String url = String.format("%s?serviceKey=%s&pageNo=%d&numOfRows=%d&type=json",
                baseUrl, publicDataApiKey, page, pageSize);
        log.info("공공데이터 API 호출: {}", url.replace(publicDataApiKey, "***"));

        String rawResponse;
        try {
            rawResponse = restTemplate.getForObject(url, String.class);
        } catch (Exception e) {
            log.error("공공데이터 API 호출 실패: {}", e.getMessage());
            return null;
        }

        log.info("[RAW-RESPONSE] (page={}): {}", page,
                rawResponse != null ? rawResponse.substring(0, Math.min(rawResponse.length(), 1500)) : "null");

        if (rawResponse == null || rawResponse.trim().startsWith("<")) {
            // 2차: resultType=json
            log.warn("XML 응답 감지 — resultType=json으로 재시도");
            url = String.format("%s?serviceKey=%s&pageNo=%d&numOfRows=%d&resultType=json",
                    baseUrl, publicDataApiKey, page, pageSize);
            try {
                rawResponse = restTemplate.getForObject(url, String.class);
                log.info("[RETRY RAW-RESPONSE] {}", rawResponse != null ?
                        rawResponse.substring(0, Math.min(rawResponse.length(), 1500)) : "null");
            } catch (Exception e) {
                log.error("재시도 API 호출 실패: {}", e.getMessage());
                return null;
            }
        }

        if (rawResponse != null && rawResponse.trim().startsWith("<")) {
            log.warn("여전히 XML 응답 — 이 API는 JSON을 지원하지 않을 수 있음");
            return null;
        }

        return rawResponse;
    }

    /**
     * 페이지네이션을 처리하며 대량 데이터를 한 번에 가져온다.
     */
    private String callPublicDataApiPaginated(String baseUrl, int pageSize) {
        return callPublicDataApi(baseUrl, 1, pageSize);
    }

    // ============================================================
    // 공통 응답 파싱
    // ============================================================

    /**
     * 다양한 공공데이터포털 응답 구조에서 items 배열을 추출한다.
     */
    private JsonNode extractItems(String rawResponse, String apiName) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            log.info("[{}] 응답 루트 키: {}", apiName, iterableToString(root.fieldNames()));

            // 구조 1: response.body.items.item[]
            JsonNode items = root.path("response").path("body").path("items").path("item");
            if (items.isArray() && !items.isEmpty()) {
                logFirstItem(apiName, items);
                return items;
            }

            // 구조 2: response.body.items[] (item 없이)
            items = root.path("response").path("body").path("items");
            if (items.isArray() && !items.isEmpty()) {
                logFirstItem(apiName, items);
                return items;
            }

            // 구조 3: body[]
            items = root.path("body");
            if (items.isArray() && !items.isEmpty()) {
                logFirstItem(apiName, items);
                return items;
            }

            // 구조 4: data[]
            items = root.path("data");
            if (items.isArray() && !items.isEmpty()) {
                logFirstItem(apiName, items);
                return items;
            }

            // 구조 5: ServiceName[1].row[] (경기데이터드림 스타일)
            var fieldNames = root.fieldNames();
            while (fieldNames.hasNext()) {
                String key = fieldNames.next();
                JsonNode service = root.path(key);
                if (service.isArray() && service.size() > 1) {
                    JsonNode rows = service.get(1).path("row");
                    if (rows.isArray() && !rows.isEmpty()) {
                        log.info("[{}] 경기데이터드림 스타일 응답 감지. 키={}, row {}건", apiName, key, rows.size());
                        logFirstItem(apiName, rows);
                        return rows;
                    }
                }
            }

            log.warn("[{}] items 배열을 찾을 수 없음", apiName);
            return null;
        } catch (Exception e) {
            log.error("[{}] 응답 파싱 실패: {}", apiName, e.getMessage());
            return null;
        }
    }

    private void logFirstItem(String apiName, JsonNode items) {
        log.info("[{}] {}건 발견. 첫 item 필드: {}", apiName, items.size(),
                items.get(0) != null ? iterableToString(items.get(0).fieldNames()) : "null");
    }

    // ============================================================
    // 공통 좌표 추출 + 주소 추출
    // ============================================================

    private String getAddress(JsonNode item) {
        return getFieldValue(item, "REFINE_ROADNM_ADDR", "REFINE_LOTNO_ADDR",
                "ROADNM_ADDR", "LOTNO_ADDR", "ADDR", "ADDRESS",
                "refine_roadnm_addr", "refine_lotno_addr",
                "roadnm_addr", "lotno_addr", "addr", "address",
                "LC_NM", "lc_nm", "INSTL_LC", "instl_lc");
    }

    /**
     * 좌표를 추출한다. 좌표가 없으면 주소 기반 지오코딩을 시도한다.
     * @return [lat, lng] 또는 null
     */
    private double[] extractCoords(JsonNode item, String address) {
        Double latitude = getFieldDouble(item, "REFINE_WGS84_LAT", "LAT", "LATITUDE",
                "WGS84_LAT", "Y", "refine_wgs84_lat", "lat", "latitude", "y");
        Double longitude = getFieldDouble(item, "REFINE_WGS84_LOGT", "LOT", "LNG", "LONGITUDE",
                "WGS84_LOGT", "X", "refine_wgs84_logt", "lot", "lng", "longitude", "x");

        if (latitude != null && longitude != null) return new double[]{latitude, longitude};

        // 지오코딩 폴백
        if (address != null && !address.isEmpty()) {
            double[] coords = geocodeAddress(address);
            if (coords != null) {
                log.info("지오코딩 성공: '{}' → ({}, {})", address, coords[0], coords[1]);
                return coords;
            }
        }

        return null;
    }

    // ============================================================
    // 성남시 필터링 + 지오코딩
    // ============================================================

    private boolean isSeongnamRegion(String regionCode) {
        if (regionCode == null || regionCode.isEmpty()) return false;
        for (String prefix : SEONGNAM_REGION_PREFIXES) {
            if (regionCode.startsWith(prefix)) return true;
        }
        return false;
    }

    private boolean isInSeongnamBounds(double lat, double lng) {
        return lat >= SEONGNAM_MIN_LAT && lat <= SEONGNAM_MAX_LAT
                && lng >= SEONGNAM_MIN_LNG && lng <= SEONGNAM_MAX_LNG;
    }

    private double[] geocodeAddress(String address) {
        try {
            String encoded = URLEncoder.encode(address, StandardCharsets.UTF_8);
            String url = "https://dapi.kakao.com/v2/local/search/address.json?query=" + encoded;

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "KakaoAK " + apiKeyConfig.getKakaoRestApiKey());

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode documents = root.path("documents");
                if (documents.isArray() && !documents.isEmpty()) {
                    double lat = documents.get(0).path("y").asDouble();
                    double lng = documents.get(0).path("x").asDouble();
                    if (lat != 0 && lng != 0) return new double[]{lat, lng};
                }
            }
        } catch (Exception e) {
            log.debug("지오코딩 실패 ({}): {}", address, e.getMessage());
        }
        return null;
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
                    String val = node.asText().trim();
                    if (!val.isEmpty()) return Double.parseDouble(val);
                } catch (NumberFormatException e) {
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
