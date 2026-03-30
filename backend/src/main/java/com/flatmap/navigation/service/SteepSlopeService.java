package com.flatmap.navigation.service;

import com.flatmap.navigation.dto.RiskSection;
import com.flatmap.navigation.dto.RouteResponse;
import com.flatmap.navigation.entity.SteepSlopeArea;
import com.flatmap.navigation.repository.SteepSlopeAreaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class SteepSlopeService {

    private static final Logger log = LoggerFactory.getLogger(SteepSlopeService.class);

    // 경로 좌표에서 이 반경(미터) 이내의 급경사지를 위험구간으로 판별
    private static final double RISK_RADIUS_METERS = 200.0;

    private final SteepSlopeAreaRepository repository;

    public SteepSlopeService(SteepSlopeAreaRepository repository) {
        this.repository = repository;
    }

    /**
     * 도보 경로 좌표 목록과 DB의 급경사지 데이터를 비교하여 위험구간을 반환한다.
     * DB에 데이터가 없으면 빈 리스트를 반환한다.
     */
    public List<RiskSection> findRiskSections(List<RouteResponse.Coord> routeCoords) {
        List<SteepSlopeArea> allAreas = repository.findAll();

        if (allAreas.isEmpty()) {
            log.info("급경사지 데이터가 없음 — 위험구간 판별 건너뜀");
            return Collections.emptyList();
        }

        log.info("급경사지 데이터 {}건과 경로 좌표 {}개 비교 시작", allAreas.size(), routeCoords.size());

        // 1단계: 각 급경사지별 경로 최소거리 계산 (2-pass 방식)
        List<RiskSection> riskSections = new ArrayList<>();

        for (SteepSlopeArea area : allAreas) {
            if (area.getLatitude() == null || area.getLongitude() == null) continue;

            double minDist = Double.MAX_VALUE;
            int minIdx = -1;

            for (int i = 0; i < routeCoords.size(); i++) {
                RouteResponse.Coord coord = routeCoords.get(i);
                double distance = haversineMeters(
                        coord.getLat(), coord.getLng(),
                        area.getLatitude(), area.getLongitude()
                );
                if (distance < minDist) {
                    minDist = distance;
                    minIdx = i;
                }
            }

            // 디버깅 로그 (1000m 이내만)
            if (minDist < 1000) {
                String status = minDist <= RISK_RADIUS_METERS ? "MATCHED" : "MISS";
                log.info("[거리분석] {} : 최소 {}m (좌표[{}]) → {}",
                        area.getName(), Math.round(minDist), minIdx, status);
            }

            // 2단계: 반경 이내이면 위험구간으로 추가
            if (minDist <= RISK_RADIUS_METERS) {
                riskSections.add(new RiskSection(
                        area.getLatitude(),
                        area.getLongitude(),
                        area.getName(),
                        area.getGrade() != null ? area.getGrade() : 0.0,
                        area.getRiskLevel(),
                        minDist,
                        minIdx
                ));
                log.info("위험구간 감지: {} (경사도 {}%, 경로에서 {}m, 좌표인덱스 {})",
                        area.getName(), area.getGrade(), Math.round(minDist), minIdx);
            }
        }

        // nearestRouteIdx 기준으로 경로 순서대로 정렬
        riskSections.sort(Comparator.comparingInt(RiskSection::getNearestRouteIdx));

        log.info("위험구간 판별 완료: {}건 감지 (반경 {}m)", riskSections.size(), RISK_RADIUS_METERS);
        return riskSections;
    }

    /**
     * Haversine 공식으로 두 좌표 간 거리(미터)를 계산한다.
     */
    private double haversineMeters(double lat1, double lng1, double lat2, double lng2) {
        double R = 6371000; // 지구 반지름 (미터)
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
