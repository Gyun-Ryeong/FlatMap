package com.flatmap.navigation.service;

import com.flatmap.navigation.dto.RiskSection;
import com.flatmap.navigation.dto.RouteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DetourService {

    private static final Logger log = LoggerFactory.getLogger(DetourService.class);
    private static final double DETOUR_OFFSET_METERS = 250.0;
    private static final int MAX_WAYPOINTS = 5;

    /**
     * 위험구간 목록과 원래 경로 좌표를 기반으로 우회 경유지를 생성한다.
     * 각 위험구간의 중심에서 경로 진행 방향의 법선(수직) 방향으로 250m 이동한 좌표를 경유지로 만든다.
     *
     * @return T Map passList 형식 문자열 "lng1,lat1_lng2,lat2"  (최대 5개)
     */
    public String generatePassList(List<RiskSection> riskSections, List<RouteResponse.Coord> routeCoords) {
        if (riskSections == null || riskSections.isEmpty()) return null;

        // 위험도 높은 순으로 정렬, 최대 5개
        List<RiskSection> topRisks = riskSections.stream()
                .sorted(Comparator.comparingInt(rs -> {
                    switch (rs.getRiskLevel()) {
                        case "VERY_HIGH": return 0;
                        case "HIGH": return 1;
                        case "MEDIUM": return 2;
                        default: return 3;
                    }
                }))
                .limit(MAX_WAYPOINTS)
                .sorted(Comparator.comparingInt(RiskSection::getNearestRouteIdx))
                .collect(Collectors.toList());

        StringBuilder passList = new StringBuilder();

        for (RiskSection rs : topRisks) {
            int idx = rs.getNearestRouteIdx();
            double[] waypoint = calculateWaypoint(routeCoords, idx);

            if (waypoint != null) {
                if (passList.length() > 0) passList.append("_");
                // T Map passList 형식: 경도,위도 (lng,lat)
                passList.append(String.format("%.6f,%.6f", waypoint[1], waypoint[0]));
                log.info("우회 경유지 생성: {} → ({}, {}) [위험구간: {}]",
                        rs.getName(), waypoint[0], waypoint[1], rs.getRiskLevel());
            }
        }

        String result = passList.length() > 0 ? passList.toString() : null;
        log.info("생성된 passList: {}", result);
        return result;
    }

    /**
     * 경로의 특정 인덱스 지점에서 진행 방향에 수직인 방향으로 DETOUR_OFFSET_METERS만큼 이동한 좌표를 반환한다.
     *
     * @return [lat, lng] 또는 null
     */
    private double[] calculateWaypoint(List<RouteResponse.Coord> coords, int idx) {
        if (coords == null || coords.size() < 2) return null;

        // 전후 좌표로 진행 방향 벡터 계산
        int prevIdx = Math.max(0, idx - 5);
        int nextIdx = Math.min(coords.size() - 1, idx + 5);

        // prevIdx == nextIdx인 경우 방지
        if (prevIdx == nextIdx) {
            if (nextIdx < coords.size() - 1) nextIdx++;
            else prevIdx = Math.max(0, prevIdx - 1);
        }

        RouteResponse.Coord prev = coords.get(prevIdx);
        RouteResponse.Coord next = coords.get(nextIdx);
        RouteResponse.Coord center = coords.get(idx);

        // 진행 방향 벡터 (lat, lng 단위)
        double dLat = next.getLat() - prev.getLat();
        double dLng = next.getLng() - prev.getLng();

        // 길이가 0이면 건너뜀
        double length = Math.sqrt(dLat * dLat + dLng * dLng);
        if (length < 1e-10) return null;

        // 법선 벡터 (시계 방향 90도 회전: (dLat, dLng) → (dLng, -dLat))
        double normLat = dLng / length;
        double normLng = -dLat / length;

        // 위도 1도 ≈ 111,000m, 경도 1도 ≈ 111,000m * cos(lat)
        double metersPerDegLat = 111000.0;
        double metersPerDegLng = 111000.0 * Math.cos(Math.toRadians(center.getLat()));

        // 250m를 도(degree) 단위로 변환
        double offsetLat = (DETOUR_OFFSET_METERS * normLat) / metersPerDegLat;
        double offsetLng = (DETOUR_OFFSET_METERS * normLng) / metersPerDegLng;

        double waypointLat = center.getLat() + offsetLat;
        double waypointLng = center.getLng() + offsetLng;

        log.debug("경유지 계산: center=({},{}), direction=({},{}), normal=({},{}), waypoint=({},{})",
                center.getLat(), center.getLng(), dLat, dLng, normLat, normLng, waypointLat, waypointLng);

        return new double[]{waypointLat, waypointLng};
    }
}
