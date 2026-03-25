package com.flatmap.navigation.controller;

import com.flatmap.navigation.dto.RiskSection;
import com.flatmap.navigation.dto.RouteRequest;
import com.flatmap.navigation.dto.RouteResponse;
import com.flatmap.navigation.service.SteepSlopeService;
import com.flatmap.navigation.service.TMapRouteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/route")
@CrossOrigin(origins = "http://localhost:3000")
public class WalkRouteController {

    private static final Logger log = LoggerFactory.getLogger(WalkRouteController.class);
    private final TMapRouteService tMapRouteService;
    private final SteepSlopeService steepSlopeService;

    public WalkRouteController(TMapRouteService tMapRouteService, SteepSlopeService steepSlopeService) {
        this.tMapRouteService = tMapRouteService;
        this.steepSlopeService = steepSlopeService;
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

            // 경로 좌표와 DB의 급경사지 데이터를 비교하여 위험구간 판별
            List<RiskSection> riskSections = steepSlopeService.findRiskSections(response.getCoords());
            response.setRiskSections(riskSections);

            if (!riskSections.isEmpty()) {
                log.info("경로에서 위험구간 {}건 감지", riskSections.size());
            }

            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            log.error("도보 경로 검색 실패: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }
}
