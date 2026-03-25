package com.flatmap.navigation.service;

import com.flatmap.navigation.config.ApiKeyConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);
    private static final String WEATHER_FORECAST_URL =
            "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst";

    private final RestTemplate restTemplate;
    private final ApiKeyConfig apiKeyConfig;

    public WeatherService(RestTemplate restTemplate, ApiKeyConfig apiKeyConfig) {
        this.restTemplate = restTemplate;
        this.apiKeyConfig = apiKeyConfig;
    }

    public String getShortTermForecast(String baseDate, String baseTime, int nx, int ny) {
        String url = String.format(
                "%s?serviceKey=%s&numOfRows=10&pageNo=1&dataType=JSON&base_date=%s&base_time=%s&nx=%d&ny=%d",
                WEATHER_FORECAST_URL, apiKeyConfig.getWeatherApiKey(), baseDate, baseTime, nx, ny);

        // TODO: 응답 DTO로 매핑
        return restTemplate.getForObject(url, String.class);
    }
}
