# FlatMap — 성남시 오르막길 안전 보행 네비게이션

경기도 성남시(수정구·중원구·분당구) 내 급경사지 및 오르막길 구간의 안전 정보를 제공하는 보행 네비게이션 서비스입니다.

## 주요 기능

- **보행 경로 탐색** — T Map 보행자 경로 API 기반 출발지→목적지 경로 안내
- **급경사지 위험구간 표시** — 경로 상 급경사지 반경 50m 내 위험구간 감지 및 색상 표시
- **우회 경로 제안** — 안전 우선 모드 선택 시 위험구간 회피 대안 경로 제공
- **안전 정보 레이어** — 사고다발지(234건)·CCTV(2,954건)·교통약자보호구역(98건)·장애인복지시설(39건)·그늘막(100건)·노인복지관(406건)
- **기상 정보 연동** — 기상청 초단기예보 API 기반 현재 위치 기온·습도·풍속·강수형태 제공, 위험 기상 경고 배너 표시
- **급경사지 데이터** — 행정안전부 급경사지 현황 CSV 기반 82건 (수정구 10·중원구 34·분당구 34 + 기타 4)

## 기술 스택

| 분류 | 기술 |
|------|------|
| Frontend | React 18, Kakao Maps SDK, Kakao Local API |
| Backend | Spring Boot 3.2, Spring Data JPA |
| Database | PostgreSQL 18 |
| 경로 탐색 | T Map 보행자 경로 API |
| 지도 | Kakao Maps JavaScript SDK |
| 날씨 | 기상청 초단기예보 API (공공데이터포털) |
| 안전정보 | 경기데이터드림 API, 재난안전데이터공유플랫폼 API |

## 시작하기

### 사전 준비

- Node.js 18+
- Java 17+
- PostgreSQL 18
- Maven (또는 프로젝트 내 `mvnw.cmd` 사용)

### 1. 저장소 클론

```bash
git clone https://github.com/Gyun-Ryeong/FlatMap.git
cd FlatMap
```

### 2. 환경 변수 설정

`.env` 파일을 생성하고 Kakao Maps API 키를 입력합니다.

```env
REACT_APP_KAKAO_API_KEY=your_kakao_api_key
```

`backend/src/main/resources/application.properties` 파일을 생성하고 API 키와 DB 정보를 입력합니다. (팀원에게 별도 전달)

### 3. DB 복원

```bash
# DB가 이미 있는 경우
dropdb -U postgres flatmap_db
createdb -U postgres flatmap_db
psql -U postgres -d flatmap_db -f flatmap_db_dump.sql
```

### 4. 백엔드 실행

```bash
cd backend
.\mvnw.cmd spring-boot:run   # Windows
./mvnw spring-boot:run        # Mac/Linux
```

### 5. 프론트엔드 실행

```bash
# 프로젝트 루트에서
npm install
npm start
```

브라우저에서 `http://localhost:3000` 접속

## 프로젝트 구조

```
flatmap/
├── src/                        # React 프론트엔드
│   ├── pages/
│   │   └── MapPage.js          # 메인 지도 페이지 (전체 기능)
│   ├── utils/
│   │   └── kakaoLoader.js      # Kakao Maps SDK 로더
│   ├── App.js
│   └── index.js
├── public/
│   └── data/                   # 행안부 급경사지 CSV
├── backend/
│   └── src/main/java/com/flatmap/navigation/
│       ├── controller/         # REST API 컨트롤러
│       ├── service/            # 비즈니스 로직
│       ├── entity/             # JPA 엔티티
│       ├── repository/         # Spring Data JPA
│       ├── dto/                # 데이터 전송 객체
│       ├── config/             # API 키 설정
│       └── util/               # 기상청 격자 변환 등
├── flatmap_db_dump.sql         # DB 덤프 (팀원 공유용)
└── .env                        # 환경 변수 (git 제외)
```

## 주요 API 엔드포인트

| Method | URL | 설명 |
|--------|-----|------|
| POST | `/api/route/walk` | 보행 경로 탐색 |
| GET | `/api/steep-slope` | 급경사지 목록 조회 |
| GET | `/api/steep-slope/risk-stats` | 위험도 통계 |
| GET | `/api/safety/cctv` | CCTV 목록 |
| GET | `/api/safety/accident-zones` | 사고다발지 목록 |
| GET | `/api/safety/protected-zones` | 교통약자보호구역 |
| GET | `/api/safety/welfare-facilities` | 장애인복지시설 |
| GET | `/api/safety/shade-shelters` | 그늘막 |
| GET | `/api/safety/senior-centers` | 노인복지관 |
| GET | `/api/weather` | 현재 위치 날씨 조회 |

## 급경사지 데이터

행정안전부 「급경사지 재해예방에 관한 법률」 법적 지정 기준 적용

- **자연사면**: 경사도 34° 이상 → 34°~40° 범위 적용
- **인공사면**: 경사도 50° 이상 → 50°~60° 범위 적용
- 좌표 변환: 카카오 로컬 API 지오코딩 (주소검색 → 키워드검색 2단계 폴백)

## 팀

- kimrg
