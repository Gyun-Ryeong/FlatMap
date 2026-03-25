package com.flatmap.navigation.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApiKeyConfig {

    @Value("${kakao.rest-api-key}")
    private String kakaoRestApiKey;

    @Value("${tmap.app-key}")
    private String tmapAppKey;

    @Value("${weather.api-key}")
    private String weatherApiKey;

    @Value("${gyeonggi.security-light.api-key}")
    private String gyeonggiSecurityLightApiKey;

    @Value("${gyeonggi.accident-icy.api-key}")
    private String gyeonggiAccidentIcyApiKey;

    @Value("${gyeonggi.accident-normal.api-key}")
    private String gyeonggiAccidentNormalApiKey;

    @Value("${gyeonggi.uphill-lane.api-key}")
    private String gyeonggiUphillLaneApiKey;

    @Value("${gyeonggi.senior-welfare.api-key}")
    private String gyeonggiSeniorWelfareApiKey;

    @Value("${gyeonggi.senior-center.api-key}")
    private String gyeonggiSeniorCenterApiKey;

    @Value("${disaster.steep-slope.api-key}")
    private String disasterSteepSlopeApiKey;

    @Value("${disaster.traffic-downhill.api-key}")
    private String disasterTrafficDownhillApiKey;

    @Value("${disaster.steep-slope-final.api-key}")
    private String disasterSteepSlopeFinalApiKey;

    @Value("${region.seongnam}")
    private String regionSeongnam;

    @Value("${region.sujeong}")
    private String regionSujeong;

    @Value("${region.jungwon}")
    private String regionJungwon;

    @Value("${region.bundang}")
    private String regionBundang;

    public String getKakaoRestApiKey() { return kakaoRestApiKey; }
    public String getTmapAppKey() { return tmapAppKey; }
    public String getWeatherApiKey() { return weatherApiKey; }
    public String getGyeonggiSecurityLightApiKey() { return gyeonggiSecurityLightApiKey; }
    public String getGyeonggiAccidentIcyApiKey() { return gyeonggiAccidentIcyApiKey; }
    public String getGyeonggiAccidentNormalApiKey() { return gyeonggiAccidentNormalApiKey; }
    public String getGyeonggiUphillLaneApiKey() { return gyeonggiUphillLaneApiKey; }
    public String getGyeonggiSeniorWelfareApiKey() { return gyeonggiSeniorWelfareApiKey; }
    public String getGyeonggiSeniorCenterApiKey() { return gyeonggiSeniorCenterApiKey; }
    public String getDisasterSteepSlopeApiKey() { return disasterSteepSlopeApiKey; }
    public String getDisasterTrafficDownhillApiKey() { return disasterTrafficDownhillApiKey; }
    public String getDisasterSteepSlopeFinalApiKey() { return disasterSteepSlopeFinalApiKey; }
    public String getRegionSeongnam() { return regionSeongnam; }
    public String getRegionSujeong() { return regionSujeong; }
    public String getRegionJungwon() { return regionJungwon; }
    public String getRegionBundang() { return regionBundang; }
}
