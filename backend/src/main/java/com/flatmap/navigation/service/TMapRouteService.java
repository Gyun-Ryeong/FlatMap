package com.flatmap.navigation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flatmap.navigation.config.ApiKeyConfig;
import com.flatmap.navigation.dto.RouteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TMapRouteService {

    private static final Logger log = LoggerFactory.getLogger(TMapRouteService.class);
    private static final String TMAP_PEDESTRIAN_URL = "https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1";

    private final RestTemplate restTemplate;
    private final ApiKeyConfig apiKeyConfig;
    private final ObjectMapper objectMapper;

    public TMapRouteService(RestTemplate restTemplate, ApiKeyConfig apiKeyConfig, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.apiKeyConfig = apiKeyConfig;
        this.objectMapper = objectMapper;
    }

    public RouteResponse getWalkingRoute(double originLng, double originLat,
                                          double destLng, double destLat,
                                          String option) {
        return getWalkingRoute(originLng, originLat, destLng, destLat, option, null);
    }

    public RouteResponse getWalkingRoute(double originLng, double originLat,
                                          double destLng, double destLat,
                                          String option, String passList) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("appKey", apiKeyConfig.getTmapAppKey());

        // 옵션 매핑: SAFE→0(추천), SHORT→10(최단거리), FLAT→30(계단제외)
        int searchOption = 0;
        if ("SHORT".equals(option)) {
            searchOption = 10;
        } else if ("FLAT".equals(option)) {
            searchOption = 30;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("startX", String.valueOf(originLng));
        body.put("startY", String.valueOf(originLat));
        body.put("endX", String.valueOf(destLng));
        body.put("endY", String.valueOf(destLat));
        body.put("startName", "출발지");
        body.put("endName", "도착지");
        body.put("searchOption", String.valueOf(searchOption));

        if (passList != null && !passList.isEmpty()) {
            body.put("passList", passList);
            log.info("T Map 경유지(passList): {}", passList);
        }

        log.info("T Map 보행자 경로 요청: origin=({},{}), dest=({},{}), option={}, searchOption={}",
                originLat, originLng, destLat, destLng, option, searchOption);
        log.info("[DEBUG] 요청 URL: {}", TMAP_PEDESTRIAN_URL);
        log.info("[DEBUG] 요청 body: {}", body);
        log.info("[DEBUG] 요청 headers: appKey={}", apiKeyConfig.getTmapAppKey().substring(0, 8) + "...");

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            org.springframework.http.ResponseEntity<String> responseEntity =
                    restTemplate.exchange(TMAP_PEDESTRIAN_URL, org.springframework.http.HttpMethod.POST, entity, String.class);
            log.info("[DEBUG] 응답 status: {}", responseEntity.getStatusCode());
            log.info("[DEBUG] 응답 body: {}", responseEntity.getBody());
            return parsePedestrianResponse(responseEntity.getBody());
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("[DEBUG] HTTP 에러 status: {}", e.getStatusCode());
            log.error("[DEBUG] HTTP 에러 body: {}", e.getResponseBodyAsString());
            throw new RuntimeException("경로 검색에 실패했습니다.", e);
        } catch (RestClientException e) {
            log.error("T Map 보행자 경로 API 호출 실패: {}", e.getMessage());
            throw new RuntimeException("경로 검색에 실패했습니다.", e);
        }
    }

    private RouteResponse parsePedestrianResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode features = root.path("features");

            if (!features.isArray() || features.isEmpty()) {
                log.warn("T Map 경로 응답에 features가 없음");
                throw new RuntimeException("경로를 찾을 수 없습니다.");
            }

            // 첫 번째 Feature의 properties에서 총 거리/시간 추출
            JsonNode firstProps = features.get(0).path("properties");
            int totalDistance = firstProps.path("totalDistance").asInt();
            int totalTime = firstProps.path("totalTime").asInt();

            // LineString geometry에서 좌표 추출 (Point는 건너뜀)
            // T Map GeoJSON: [경도, 위도] → RouteResponse.Coord(위도, 경도)
            List<RouteResponse.Coord> coords = new ArrayList<>();
            for (JsonNode feature : features) {
                JsonNode geometry = feature.path("geometry");
                String type = geometry.path("type").asText();

                if ("LineString".equals(type)) {
                    JsonNode coordinates = geometry.path("coordinates");
                    for (JsonNode coord : coordinates) {
                        double lng = coord.get(0).asDouble();
                        double lat = coord.get(1).asDouble();
                        coords.add(new RouteResponse.Coord(lat, lng));
                    }
                }
            }

            log.info("T Map 경로 파싱 완료: 거리={}m, 시간={}초, 좌표={}개", totalDistance, totalTime, coords.size());

            return new RouteResponse(coords, totalDistance, totalTime);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("T Map 경로 응답 파싱 실패: {}", e.getMessage());
            throw new RuntimeException("경로 응답 처리에 실패했습니다.", e);
        }
    }
}
