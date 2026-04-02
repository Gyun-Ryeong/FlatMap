package com.flatmap.navigation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flatmap.navigation.config.ApiKeyConfig;
import com.flatmap.navigation.dto.WeatherInfo;
import com.flatmap.navigation.util.GridConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);
    private static final String API_URL =
            "http://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getUltraSrtFcst";
    private static final long CACHE_TTL_MS = 30 * 60 * 1000; // 30분

    private final RestTemplate restTemplate;
    private final ApiKeyConfig apiKeyConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // 캐시: "nx,ny" → {weatherInfo, timestamp}
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public WeatherService(RestTemplate restTemplate, ApiKeyConfig apiKeyConfig) {
        this.restTemplate = restTemplate;
        this.apiKeyConfig = apiKeyConfig;
    }

    /**
     * 위경도 기반 현재 날씨 조회 (30분 캐싱, 실패 시 fallback).
     */
    public WeatherInfo getWeather(double lat, double lng) {
        int[] grid = GridConverter.toGrid(lat, lng);
        int nx = grid[0];
        int ny = grid[1];
        String cacheKey = nx + "," + ny;

        CacheEntry entry = cache.get(cacheKey);
        if (entry != null && System.currentTimeMillis() - entry.timestamp < CACHE_TTL_MS) {
            log.debug("날씨 캐시 히트: grid({},{})", nx, ny);
            return entry.weather;
        }

        try {
            WeatherInfo weather = fetchFromApi(nx, ny);
            cache.put(cacheKey, new CacheEntry(weather, System.currentTimeMillis()));
            log.info("날씨 조회 성공: grid({},{}) → {}℃, PTY={}, SKY={}",
                    nx, ny, weather.getTemperature(), weather.getPrecipitationType(), weather.getSky());
            return weather;
        } catch (Exception e) {
            log.warn("날씨 API 호출 실패 (fallback 사용): {}", e.getMessage());
            return WeatherInfo.fallback();
        }
    }

    private WeatherInfo fetchFromApi(int nx, int ny) throws Exception {
        LocalDateTime now = LocalDateTime.now();
        String baseDate = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String baseTime = resolveBaseTime(now);

        // 발표 시간이 전 시간대이고 자정 넘어간 경우
        if (baseTime.equals("2330") && now.getHour() == 0 && now.getMinute() < 45) {
            baseDate = now.minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        }

        String url = UriComponentsBuilder.fromHttpUrl(API_URL)
                .queryParam("serviceKey", apiKeyConfig.getWeatherApiKey())
                .queryParam("numOfRows", 60)
                .queryParam("pageNo", 1)
                .queryParam("dataType", "JSON")
                .queryParam("base_date", baseDate)
                .queryParam("base_time", baseTime)
                .queryParam("nx", nx)
                .queryParam("ny", ny)
                .build(false)
                .toUriString();

        log.debug("기상청 API 호출: base_date={}, base_time={}, nx={}, ny={}", baseDate, baseTime, nx, ny);

        String response = restTemplate.getForObject(url, String.class);
        return parseResponse(response);
    }

    private WeatherInfo parseResponse(String json) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode header = root.path("response").path("header");

        String resultCode = header.path("resultCode").asText();
        if (!"00".equals(resultCode)) {
            throw new RuntimeException("기상청 API 오류: " + header.path("resultMsg").asText());
        }

        JsonNode items = root.path("response").path("body").path("items").path("item");
        if (!items.isArray() || items.isEmpty()) {
            throw new RuntimeException("기상청 API 응답 데이터 없음");
        }

        WeatherInfo weather = new WeatherInfo();
        weather.setTemperature(20);
        weather.setPrecipitationType(0);
        weather.setRainfall("0");
        weather.setSky(1);
        weather.setHumidity(50);
        weather.setWindSpeed(1.0);

        for (JsonNode item : items) {
            String category = item.path("category").asText();
            String value = item.path("fcstValue").asText();

            switch (category) {
                case "T1H": weather.setTemperature(parseDouble(value, 20)); break;
                case "PTY": weather.setPrecipitationType(parseInt(value, 0)); break;
                case "RN1": weather.setRainfall(value); break;
                case "SKY": weather.setSky(parseInt(value, 1)); break;
                case "REH": weather.setHumidity(parseInt(value, 50)); break;
                case "WSD": weather.setWindSpeed(parseDouble(value, 1.0)); break;
            }
        }

        applyDescriptionAndIcon(weather);
        applyWarning(weather);
        return weather;
    }

    private void applyDescriptionAndIcon(WeatherInfo w) {
        int pty = w.getPrecipitationType();
        if (pty == 1 || pty == 5) {
            w.setDescription("비"); w.setIcon("rain");
        } else if (pty == 3 || pty == 7) {
            w.setDescription("눈"); w.setIcon("snow");
        } else if (pty == 2 || pty == 6) {
            w.setDescription("비/눈"); w.setIcon("sleet");
        } else {
            switch (w.getSky()) {
                case 1: w.setDescription("맑음"); w.setIcon("clear"); break;
                case 3: w.setDescription("구름많음"); w.setIcon("cloudy"); break;
                case 4: w.setDescription("흐림"); w.setIcon("overcast"); break;
                default: w.setDescription("맑음"); w.setIcon("clear");
            }
        }
        w.setDescription(w.getDescription() + ", " + String.format("%.0f", w.getTemperature()) + "\u2103");
    }

    private void applyWarning(WeatherInfo w) {
        int pty = w.getPrecipitationType();
        if (pty == 3 || pty == 7) {
            w.setWarning("눈 예보, 경사구간 결빙 주의");
        } else if (pty == 2 || pty == 6) {
            w.setWarning("비/눈 예보, 경사구간 미끄럼 주의");
        } else if (pty == 1 || pty == 5) {
            w.setWarning("비 예보, 경사구간 미끄럼 주의");
        } else if (w.getTemperature() <= 0) {
            w.setWarning("영하 기온, 경사구간 결빙 위험");
        } else if (w.getWindSpeed() >= 10) {
            w.setWarning("강풍 주의, 노출 구간 보행 주의");
        } else {
            w.setWarning(null);
        }
    }

    /**
     * 현재 시간 기준 가장 최근 초단기예보 발표 시간.
     * 초단기예보는 매시 30분에 발표, ~15분 뒤 조회 가능.
     */
    String resolveBaseTime(LocalDateTime now) {
        int hour = now.getHour();
        int minute = now.getMinute();

        if (minute >= 45) {
            return String.format("%02d30", hour);
        } else {
            int prevHour = (hour == 0) ? 23 : hour - 1;
            return String.format("%02d30", prevHour);
        }
    }

    private double parseDouble(String value, double fallback) {
        try { return Double.parseDouble(value); }
        catch (Exception e) { return fallback; }
    }

    private int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value); }
        catch (Exception e) { return fallback; }
    }

    private static class CacheEntry {
        final WeatherInfo weather;
        final long timestamp;
        CacheEntry(WeatherInfo weather, long timestamp) {
            this.weather = weather;
            this.timestamp = timestamp;
        }
    }
}