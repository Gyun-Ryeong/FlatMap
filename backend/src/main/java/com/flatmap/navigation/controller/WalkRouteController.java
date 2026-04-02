package com.flatmap.navigation.controller;

import com.flatmap.navigation.dto.*;
import com.flatmap.navigation.service.DetourService;
import com.flatmap.navigation.service.SteepSlopeService;
import com.flatmap.navigation.service.TMapRouteService;
import com.flatmap.navigation.service.WeatherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/route")
public class WalkRouteController {

    private static final Logger log = LoggerFactory.getLogger(WalkRouteController.class);
    private final TMapRouteService tMapRouteService;
    private final SteepSlopeService steepSlopeService;
    private final WeatherService weatherService;
    private final DetourService detourService;

    public WalkRouteController(TMapRouteService tMapRouteService,
                               SteepSlopeService steepSlopeService,
                               WeatherService weatherService,
                               DetourService detourService) {
        this.tMapRouteService = tMapRouteService;
        this.steepSlopeService = steepSlopeService;
        this.weatherService = weatherService;
        this.detourService = detourService;
    }

    @PostMapping("/walk")
    public ResponseEntity<RouteResponse> getWalkingRoute(@RequestBody RouteRequest request) {
        log.info("도보 경로 요청: origin=({},{}), dest=({},{}), option={}",
                request.getOriginLat(), request.getOriginLng(),
                request.getDestLat(), request.getDestLng(),
                request.getOption());

        try {
            RouteResponse response = tMapRouteService.getWalkingRoute(
                    request.getOriginLng(), request.getOriginLat(),
                    request.getDestLng(), request.getDestLat(),
                    request.getOption()
            );

            // 1. 경로 좌표와 DB의 급경사지 데이터를 비교하여 위험구간 판별
            List<RiskSection> riskSections = steepSlopeService.findRiskSections(response.getCoords());

            // 2. 날씨 조회 (경로 중간 지점 기준)
            List<RouteResponse.Coord> coords = response.getCoords();
            RouteResponse.Coord midPoint = coords.get(coords.size() / 2);
            WeatherInfo weather = weatherService.getWeather(midPoint.getLat(), midPoint.getLng());
            response.setWeather(weather);

            // 3. 날씨 기반 위험도 보정
            steepSlopeService.adjustRiskByWeather(riskSections, weather);
            response.setRiskSections(riskSections);

            if (!riskSections.isEmpty()) {
                log.info("경로에서 위험구간 {}건 감지 (날씨 보정 적용)", riskSections.size());
            }

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("도보 경로 검색 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/walk/detour")
    public ResponseEntity<RouteResponse> getDetourRoute(@RequestBody DetourRequest request) {
        log.info("우회 경로 요청: origin=({},{}), dest=({},{}), 회피지점 {}개",
                request.getOriginLat(), request.getOriginLng(),
                request.getDestLat(), request.getDestLng(),
                request.getAvoidPoints() != null ? request.getAvoidPoints().size() : 0);

        try {
            // 1. 원래 경로를 먼저 가져와서 경로 좌표 확보 (경유지 계산용)
            RouteResponse originalRoute = tMapRouteService.getWalkingRoute(
                    request.getOriginLng(), request.getOriginLat(),
                    request.getDestLng(), request.getDestLat(),
                    request.getOption()
            );

            // 2. avoidPoints → RiskSection 변환 후 passList 생성
            List<RiskSection> fakeRisks = request.getAvoidPoints().stream()
                    .map(ap -> new RiskSection(ap.getLat(), ap.getLng(), "avoid",
                            0, ap.getRiskLevel(), 0, ap.getNearestRouteIdx()))
                    .collect(Collectors.toList());

            String passList = detourService.generatePassList(fakeRisks, originalRoute.getCoords());

            if (passList == null || passList.isEmpty()) {
                log.warn("우회 경유지를 생성할 수 없음");
                return ResponseEntity.badRequest().build();
            }

            // 3. 경유지 포함 우회 경로 검색
            RouteResponse detourResponse = tMapRouteService.getWalkingRoute(
                    request.getOriginLng(), request.getOriginLat(),
                    request.getDestLng(), request.getDestLat(),
                    request.getOption(), passList
            );

            // 4. 우회 경로에 대해서도 위험구간 분석
            List<RiskSection> detourRisks = steepSlopeService.findRiskSections(detourResponse.getCoords());

            // 5. 날씨 조회 + 보정
            List<RouteResponse.Coord> coords = detourResponse.getCoords();
            RouteResponse.Coord midPoint = coords.get(coords.size() / 2);
            WeatherInfo weather = weatherService.getWeather(midPoint.getLat(), midPoint.getLng());
            detourResponse.setWeather(weather);
            steepSlopeService.adjustRiskByWeather(detourRisks, weather);
            detourResponse.setRiskSections(detourRisks);

            log.info("우회 경로 검색 완료: 거리={}m, 시간={}초, 위험구간={}건",
                    detourResponse.getTotalDistance(), detourResponse.getTotalDuration(), detourRisks.size());

            return ResponseEntity.ok(detourResponse);
        } catch (RuntimeException e) {
            log.error("우회 경로 검색 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}
