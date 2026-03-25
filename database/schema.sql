-- 경기도 오르막길 안전 네비게이션 DB 스키마

-- 도로 구간 테이블
CREATE TABLE road_segments (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    start_lat   DECIMAL(10, 7) NOT NULL,
    start_lng   DECIMAL(10, 7) NOT NULL,
    end_lat     DECIMAL(10, 7) NOT NULL,
    end_lng     DECIMAL(10, 7) NOT NULL,
    grade       DECIMAL(5, 2) NOT NULL,   -- 경사도 (%)
    length_m    INTEGER NOT NULL,          -- 구간 길이 (미터)
    region      VARCHAR(100),              -- 지역명 (예: 수원시, 성남시)
    created_at  TIMESTAMP DEFAULT NOW()
);

-- 안전 위험 등급 (경사도 기반)
-- LOW: 0~5%, MEDIUM: 5~10%, HIGH: 10~15%, VERY_HIGH: 15%+

-- 사고 이력 테이블
CREATE TABLE accident_records (
    id               BIGSERIAL PRIMARY KEY,
    segment_id       BIGINT REFERENCES road_segments(id),
    occurred_at      TIMESTAMP NOT NULL,
    accident_type    VARCHAR(100),          -- 사고 유형
    weather          VARCHAR(50),           -- 날씨 상태
    description      TEXT,
    created_at       TIMESTAMP DEFAULT NOW()
);

-- 경로 테이블
CREATE TABLE routes (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(255),
    origin_lat      DECIMAL(10, 7) NOT NULL,
    origin_lng      DECIMAL(10, 7) NOT NULL,
    dest_lat        DECIMAL(10, 7) NOT NULL,
    dest_lng        DECIMAL(10, 7) NOT NULL,
    total_length_m  INTEGER,
    max_grade       DECIMAL(5, 2),          -- 최대 경사도
    avg_grade       DECIMAL(5, 2),          -- 평균 경사도
    safety_score    INTEGER,                -- 0~100 안전 점수
    created_at      TIMESTAMP DEFAULT NOW()
);

-- 경로-구간 매핑
CREATE TABLE route_segments (
    route_id    BIGINT REFERENCES routes(id),
    segment_id  BIGINT REFERENCES road_segments(id),
    seq_order   INTEGER NOT NULL,
    PRIMARY KEY (route_id, segment_id)
);

-- 급경사지/오르막 데이터 (외부 API 수집)
CREATE TABLE steep_slope_areas (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255),
    latitude    DOUBLE PRECISION,
    longitude   DOUBLE PRECISION,
    grade       DOUBLE PRECISION,              -- 경사도 (%)
    risk_level  VARCHAR(20),                   -- VERY_HIGH, HIGH, MEDIUM, LOW
    source      VARCHAR(50),                   -- GYEONGGI, DISASTER
    region_code VARCHAR(20),                   -- 행정코드
    created_at  TIMESTAMP DEFAULT NOW()
);

-- 인덱스
CREATE INDEX idx_road_segments_region ON road_segments(region);
CREATE INDEX idx_road_segments_grade ON road_segments(grade);
CREATE INDEX idx_accident_records_segment ON accident_records(segment_id);
CREATE INDEX idx_steep_slope_areas_source ON steep_slope_areas(source);
CREATE INDEX idx_steep_slope_areas_region ON steep_slope_areas(region_code);
CREATE INDEX idx_steep_slope_areas_coords ON steep_slope_areas(latitude, longitude);
