package com.flatmap.navigation.service;

import com.flatmap.navigation.config.ApiKeyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class PublicDataService {

    private static final Logger log = LoggerFactory.getLogger(PublicDataService.class);

    private final RestTemplate restTemplate;
    private final ApiKeyConfig apiKeyConfig;

    public PublicDataService(RestTemplate restTemplate, ApiKeyConfig apiKeyConfig) {
        this.restTemplate = restTemplate;
        this.apiKeyConfig = apiKeyConfig;
    }

    // TODO: 공공데이터포털 API 엔드포인트들

    public String getProtectedZones(int page, int size) {
        // TODO: 실제 엔드포인트 URL 및 서비스키 설정 필요
        String url = String.format(
                "https://apis.data.go.kr/TODO_ENDPOINT?serviceKey=%s&pageNo=%d&numOfRows=%d&type=json",
                apiKeyConfig.getWeatherApiKey(), page, size);
        return restTemplate.getForObject(url, String.class);
    }

    public String getDisabledWelfareFacilities(int page, int size) {
        // TODO: 실제 엔드포인트 URL 및 서비스키 설정 필요
        String url = String.format(
                "https://apis.data.go.kr/TODO_ENDPOINT?serviceKey=%s&pageNo=%d&numOfRows=%d&type=json",
                apiKeyConfig.getWeatherApiKey(), page, size);
        return restTemplate.getForObject(url, String.class);
    }

    public String getCctvLocations(int page, int size) {
        // TODO: 실제 엔드포인트 URL 및 서비스키 설정 필요
        String url = String.format(
                "https://apis.data.go.kr/TODO_ENDPOINT?serviceKey=%s&pageNo=%d&numOfRows=%d&type=json",
                apiKeyConfig.getWeatherApiKey(), page, size);
        return restTemplate.getForObject(url, String.class);
    }
}
