package com.flatmap.navigation.controller;

import com.flatmap.navigation.dto.WeatherInfo;
import com.flatmap.navigation.service.WeatherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/weather")
public class WeatherController {

    private static final Logger log = LoggerFactory.getLogger(WeatherController.class);
    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping
    public ResponseEntity<WeatherInfo> getWeather(
            @RequestParam double lat,
            @RequestParam double lng) {
        log.info("날씨 조회 요청: lat={}, lng={}", lat, lng);
        WeatherInfo weather = weatherService.getWeather(lat, lng);
        return ResponseEntity.ok(weather);
    }
}
