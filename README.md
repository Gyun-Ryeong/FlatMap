# 경기도 오르막길 안전 네비게이션 서비스

경기도 내 오르막길 구간의 안전 정보를 제공하는 네비게이션 서비스입니다.

## 기술 스택

- **Frontend**: React 18, React Router v6, Axios
- **Backend**: Spring Boot 3, JPA
- **Database**: PostgreSQL
- **Maps API**: Naver Maps API v3

## 시작하기

### 환경 변수 설정

```bash
cp .env.example .env
```

`.env` 파일에 Naver Maps API 키를 입력합니다.

### Frontend 실행

```bash
npm install
npm start
```

### Backend 실행

```bash
cd backend
./mvnw spring-boot:run
```

## 프로젝트 구조

```
flatmap/
├── src/                  # React 프론트엔드
│   ├── components/       # 재사용 가능한 컴포넌트
│   ├── pages/            # 페이지 컴포넌트
│   ├── App.js
│   └── index.js
├── public/               # 정적 파일
├── backend/              # Spring Boot 백엔드
│   └── src/main/java/com/flatmap/navigation/
└── database/             # PostgreSQL 스키마
```

## 팀

- 팀원 2.5명

## 일정

- 목표 완료: 2025년 4월 중순
- 발표: 2025년 5월 학술대회
