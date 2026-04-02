package com.flatmap.navigation.dto;

public class WeatherInfo {

    private double temperature;         // 기온 (℃)
    private int precipitationType;      // 강수형태 (0=없음,1=비,2=비/눈,3=눈,5=빗방울,6=빗방울눈날림,7=눈날림)
    private String rainfall;            // 1시간 강수량 (mm)
    private int sky;                    // 하늘상태 (1=맑음,3=구름많음,4=흐림)
    private int humidity;               // 습도 (%)
    private double windSpeed;           // 풍속 (m/s)
    private String description;         // 설명 텍스트
    private String icon;                // 날씨 아이콘
    private String warning;             // 경사구간 관련 경고 메시지 (nullable)

    public WeatherInfo() {}

    // === 기본값 팩토리 (API 실패 시 fallback) ===
    public static WeatherInfo fallback() {
        WeatherInfo w = new WeatherInfo();
        w.temperature = 20.0;
        w.precipitationType = 0;
        w.rainfall = "0";
        w.sky = 1;
        w.humidity = 50;
        w.windSpeed = 1.0;
        w.description = "맑음 (기본값)";
        w.icon = "clear";
        w.warning = null;
        return w;
    }

    // === 날씨 위험 계수 (경사도 보정에 사용) ===
    public double getRiskMultiplier() {
        // 눈/결빙 → 1.8배, 비 → 1.4배, 강풍(10m/s+) → 1.2배
        if (precipitationType == 3 || precipitationType == 7) return 1.8;  // 눈
        if (precipitationType == 2 || precipitationType == 6) return 1.6;  // 비/눈
        if (precipitationType == 1 || precipitationType == 5) return 1.4;  // 비
        if (temperature <= 0) return 1.6;  // 영하 결빙 위험
        if (windSpeed >= 10) return 1.2;   // 강풍
        return 1.0;
    }

    // === Getters / Setters ===
    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public int getPrecipitationType() { return precipitationType; }
    public void setPrecipitationType(int precipitationType) { this.precipitationType = precipitationType; }

    public String getRainfall() { return rainfall; }
    public void setRainfall(String rainfall) { this.rainfall = rainfall; }

    public int getSky() { return sky; }
    public void setSky(int sky) { this.sky = sky; }

    public int getHumidity() { return humidity; }
    public void setHumidity(int humidity) { this.humidity = humidity; }

    public double getWindSpeed() { return windSpeed; }
    public void setWindSpeed(double windSpeed) { this.windSpeed = windSpeed; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }

    public String getWarning() { return warning; }
    public void setWarning(String warning) { this.warning = warning; }
}