package com.flatmap.navigation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flatmap.navigation.config.ApiKeyConfig;
import com.flatmap.navigation.dto.RouteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

@Service
public class KakaoRouteService {

    private static final Logger log = LoggerFactory.getLogger(KakaoRouteService.class);
    private static final String KAKAO_DIRECTIONS_URL = "https://apis-navi.kakaomobility.com/v1/directions";

    private final RestTemplate restTemplate;
    private final ApiKeyConfig apiKeyConfig;
    private final ObjectMapper objectMapper;

    public KakaoRouteService(RestTemplate restTemplate, ApiKeyConfig apiKeyConfig, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.apiKeyConfig = apiKeyConfig;
        this.objectMapper = objectMapper;
    }

    public RouteResponse getWalkingRoute(double originLng, double originLat,
                                          double destLng, double destLat,
                                          String option) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "KakaoAK " + apiKeyConfig.getKakaoRestApiKey());

        // TODO: option에 따라 priority 매핑 (2단계에서 안전/오르막 로직 구현)
        String priority = "RECOMMEND";
        if ("SHORT".equals(option)) {
            priority = "DISTANCE";
        }

        String url = String.format("%s?origin=%f,%f&destination=%f,%f&priority=%s",
                KAKAO_DIRECTIONS_URL, originLng, originLat, destLng, destLat, priority);

        log.info("카카오 경로 API 요청: origin=({},{}), dest=({},{}), option={}",
                originLat, originLng, destLat, destLng, option);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return parseDirectionsResponse(response.getBody());
        } catch (RestClientException e) {
            log.error("카카오 경로 API 호출 실패: {}", e.getMessage());
            throw new RuntimeException("경로 검색에 실패했습니다.", e);
        }
    }

    private RouteResponse parseDirectionsResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode routes = root.path("routes");

            if (!routes.isArray() || routes.isEmpty()) {
                log.warn("카카오 경로 응답에 routes가 없음");
                throw new RuntimeException("경로를 찾을 수 없습니다.");
            }

            JsonNode firstRoute = routes.get(0);
            int resultCode = firstRoute.path("result_code").asInt();
            if (resultCode != 0) {
                log.warn("카카오 경로 결과 코드: {}", resultCode);
                throw new RuntimeException("경로를 찾을 수 없습니다.");
            }

            JsonNode summary = firstRoute.path("summary");
            int distance = summary.path("distance").asInt();
            int duration = summary.path("duration").asInt();

            List<RouteResponse.Coord> coords = new ArrayList<>();
            JsonNode sections = firstRoute.path("sections");
            for (JsonNode section : sections) {
                JsonNode roads = section.path("roads");
                for (JsonNode road : roads) {
                    JsonNode vertexes = road.path("vertexes");
                    for (int i = 0; i < vertexes.size(); i += 2) {
                        double lng = vertexes.get(i).asDouble();
                        double lat = vertexes.get(i + 1).asDouble();
                        coords.add(new RouteResponse.Coord(lat, lng));
                    }
                }
            }

            log.info("카카오 경로 파싱 완료: 거리={}m, 시간={}초, 좌표={}개", distance, duration, coords.size());

            return new RouteResponse(coords, distance, duration);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("카카오 경로 응답 파싱 실패: {}", e.getMessage());
            throw new RuntimeException("경로 응답 처리에 실패했습니다.", e);
        }
    }
}
