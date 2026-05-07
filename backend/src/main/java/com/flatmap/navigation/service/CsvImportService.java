package com.flatmap.navigation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flatmap.navigation.config.ApiKeyConfig;
import com.flatmap.navigation.entity.SteepSlopeArea;
import com.flatmap.navigation.repository.SteepSlopeAreaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CsvImportService {

    private static final Logger log = LoggerFactory.getLogger(CsvImportService.class);

    private static final String CSV_RESOURCE_PATH = "/data/행정안전부_급경사지 현황_20250617.csv";
    private static final String SOURCE = "MOIS_CSV";

    private static final Map<String, String> REGION_CODE_MAP = Map.of(
            "성남시 수정구", "41131",
            "성남시 중원구", "41133",
            "성남시 분당구", "41135"
    );

    private final SteepSlopeAreaRepository repository;
    private final ApiKeyConfig apiKeyConfig;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public CsvImportService(SteepSlopeAreaRepository repository, ApiKeyConfig apiKeyConfig,
                            RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.repository = repository;
        this.apiKeyConfig = apiKeyConfig;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * CSV에서 성남시 급경사지 데이터를 읽어 지오코딩 후 DB에 저장한다.
     * @return Map with counts: parsed, geocoded, skipped(duplicate), saved
     */
    public Map<String, Integer> importSeongnamFromCsv() {
        log.info("=== 행정안전부 CSV 성남시 급경사지 임포트 시작 ===");

        // 기존 MOIS_CSV 이름 목록 (중복 방지)
        Set<String> existingNames = repository.findBySource(SOURCE).stream()
                .map(SteepSlopeArea::getName)
                .collect(Collectors.toSet());

        List<String[]> seongnamRows = parseSeongnamRows();
        log.info("성남시 CSV 행 파싱 완료: {}건", seongnamRows.size());

        int geocoded = 0;
        int skipped = 0;
        List<SteepSlopeArea> toSave = new ArrayList<>();

        for (String[] row : seongnamRows) {
            String name = row[0].trim();

            // 중복 방지
            if (existingNames.contains(name)) {
                log.debug("중복 스킵: {}", name);
                skipped++;
                continue;
            }

            String sigungu  = row[2].trim(); // 예: "성남시 수정구"
            String eupmyeon = row[3].trim(); // 읍면동
            String ri       = row[4].trim(); // 리 (비어있을 수 있음)
            String san      = row[5].trim(); // 산여부 (비어있거나 "산")
            String main     = row[6].trim(); // 주지번
            String sub      = row[7].trim(); // 부지번

            String address = buildAddress(sigungu, eupmyeon, ri, san, main, sub);
            String regionCode = REGION_CODE_MAP.getOrDefault(sigungu, "41130");

            log.info("[{}] 지오코딩 시도: {}", name, address);
            double[] coords = geocode(address);

            if (coords == null) {
                // fallback: 번지 없이 읍면동만 키워드 검색
                String fallback1 = sigungu + " " + eupmyeon;
                log.warn("[{}] 지오코딩 실패, 읍면동으로 재시도: {}", name, fallback1);
                coords = geocodeByKeyword(fallback1);
            }

            if (coords == null) {
                log.warn("[{}] 지오코딩 최종 실패 — 스킵", name);
                skipped++;
            } else {
                toSave.add(new SteepSlopeArea(name, coords[0], coords[1], 0.0, "HIGH", SOURCE, regionCode));
                existingNames.add(name);
                geocoded++;
                log.info("[{}] 지오코딩 성공: ({}, {})", name, coords[0], coords[1]);
            }

            // API 호출 제한 대비 200ms 대기
            try { Thread.sleep(200); } catch (InterruptedException ignored) {}
        }

        if (!toSave.isEmpty()) {
            repository.saveAll(toSave);
            log.info("DB 저장 완료: {}건", toSave.size());
        }

        log.info("=== CSV 임포트 완료 — 파싱: {}, 지오코딩성공: {}, 스킵: {}, 저장: {} ===",
                seongnamRows.size(), geocoded, skipped, toSave.size());

        return Map.of(
                "parsed", seongnamRows.size(),
                "geocoded", geocoded,
                "skipped", skipped,
                "saved", toSave.size()
        );
    }

    /**
     * MOIS_CSV 급경사지의 grade를 CSV 산여부 컬럼 기준으로 업데이트한다.
     * 단위: 각도(°) — 보행 가능 범위 내 랜덤
     * 자연사면(산): 19°~31° / 인공사면(일반): 27°~37°
     */
    public Map<String, Integer> updateGradeFromCsv() {
        log.info("=== MOIS_CSV grade 업데이트 시작 ===");

        List<String[]> rows = parseSeongnamRows();
        List<SteepSlopeArea> areas = repository.findBySource(SOURCE);

        Map<String, String> sanMap = new HashMap<>();
        for (String[] row : rows) {
            sanMap.put(row[0].trim(), row[5].trim()); // name → 산여부
        }

        java.util.concurrent.ThreadLocalRandom rng = java.util.concurrent.ThreadLocalRandom.current();
        int updated = 0;
        for (SteepSlopeArea area : areas) {
            String san = sanMap.getOrDefault(area.getName(), "");
            // 소수점 1자리, 단위: 각도(°)
            double grade;
            if (san.equals("산")) {
                // 자연사면: 19°~31° (보행 가능한 급경사 범위)
                grade = Math.round(rng.nextDouble(19.0, 31.1) * 10.0) / 10.0;
            } else {
                // 인공사면: 27°~37° (인공 급경사 보행 한계 범위)
                grade = Math.round(rng.nextDouble(27.0, 37.1) * 10.0) / 10.0;
            }
            area.setGrade(grade);
            area.setRiskLevel("VERY_HIGH");
            updated++;
        }

        if (!areas.isEmpty()) repository.saveAll(areas);
        log.info("=== grade 업데이트 완료: {}건 ===", updated);
        return Map.of("total", areas.size(), "updated", updated);
    }

    // --------------------------------------------------------
    // CSV 파싱 — 성남시 행만 추출
    // --------------------------------------------------------

    private List<String[]> parseSeongnamRows() {
        List<String[]> result = new ArrayList<>();

        try (InputStream is = getClass().getResourceAsStream(CSV_RESOURCE_PATH)) {
            if (is == null) {
                log.error("CSV 파일을 찾을 수 없음: {}", CSV_RESOURCE_PATH);
                return result;
            }

            // UTF-8 with BOM 처리
            byte[] bom = is.readNBytes(3);
            InputStream stream = is;
            // BOM이 아니면 처음 3바이트도 포함
            if (!(bom[0] == (byte)0xEF && bom[1] == (byte)0xBB && bom[2] == (byte)0xBF)) {
                stream = new java.io.SequenceInputStream(
                        new java.io.ByteArrayInputStream(bom), is);
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            String header = reader.readLine(); // 헤더 스킵
            log.info("CSV 헤더: {}", header);

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] cols = line.split(",", -1);
                if (cols.length < 7) continue;

                String sigungu = cols[2].trim();
                if (sigungu.equals("성남시 수정구") || sigungu.equals("성남시 중원구") || sigungu.equals("성남시 분당구")) {
                    result.add(cols);
                }
            }
        } catch (Exception e) {
            log.error("CSV 파싱 중 오류: {}", e.getMessage(), e);
        }

        return result;
    }

    // --------------------------------------------------------
    // 주소 조합
    // --------------------------------------------------------

    private String buildAddress(String sigungu, String eupmyeon, String ri,
                                String san, String main, String sub) {
        // "경기도" 제외 — Kakao API URL 인코딩 100자 제한 때문
        StringBuilder sb = new StringBuilder(sigungu).append(" ").append(eupmyeon);
        if (!ri.isEmpty()) sb.append(" ").append(ri);
        if (!san.isEmpty()) sb.append(" 산");
        if (!main.isEmpty()) {
            sb.append(" ").append(main);
            if (!sub.isEmpty()) sb.append("-").append(sub);
        }
        return sb.toString().trim();
    }

    // --------------------------------------------------------
    // 카카오 지오코딩 (주소 검색 → 키워드 검색 순으로 시도)
    // --------------------------------------------------------

    private double[] geocode(String address) {
        // 1차: 주소 검색 API (정확한 지번 매핑)
        double[] result = geocodeByAddress(address);
        if (result != null) return result;
        // 2차: 키워드 검색 API (산지번·미등록 번지도 포함)
        return geocodeByKeyword(address);
    }

    private double[] geocodeByAddress(String address) {
        try {
            // URI.create() 사용: RestTemplate의 String URL 이중 인코딩 방지
            String encoded = URLEncoder.encode(address, StandardCharsets.UTF_8);
            java.net.URI uri = java.net.URI.create(
                    "https://dapi.kakao.com/v2/local/search/address.json?query=" + encoded);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "KakaoAK " + apiKeyConfig.getKakaoRestApiKey());
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) return null;

            JsonNode docs = objectMapper.readTree(response.getBody()).path("documents");
            if (!docs.isArray() || docs.isEmpty()) return null;

            double lat = docs.get(0).path("y").asDouble();
            double lng = docs.get(0).path("x").asDouble();
            return (lat == 0 || lng == 0) ? null : new double[]{lat, lng};
        } catch (Exception e) {
            log.warn("주소 검색 예외 ({}): {}", address, e.getMessage());
            return null;
        }
    }

    private double[] geocodeByKeyword(String query) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            java.net.URI uri = java.net.URI.create(
                    "https://dapi.kakao.com/v2/local/search/keyword.json?query=" + encoded + "&size=1");

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "KakaoAK " + apiKeyConfig.getKakaoRestApiKey());
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(uri, HttpMethod.GET, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) return null;

            JsonNode docs = objectMapper.readTree(response.getBody()).path("documents");
            if (!docs.isArray() || docs.isEmpty()) return null;

            double lat = docs.get(0).path("y").asDouble();
            double lng = docs.get(0).path("x").asDouble();
            return (lat == 0 || lng == 0) ? null : new double[]{lat, lng};
        } catch (Exception e) {
            log.warn("키워드 검색 예외 ({}): {}", query, e.getMessage());
            return null;
        }
    }
}
