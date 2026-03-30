import React, { useEffect, useRef, useState, useCallback } from 'react';
import { loadKakaoSDK } from '../utils/kakaoLoader';
import './MapPage.css';

const API_BASE = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080/api';

// 위험구간 색상 상수
const RISK_COLORS = {
  SAFE: '#03C75A',
  LOW: '#FF9800',
  MEDIUM: '#FF5722',
  HIGH: '#F44336',
  VERY_HIGH: '#F44336',
};

const RISK_STROKE_WEIGHT = { SAFE: 5, LOW: 7, MEDIUM: 7, HIGH: 8, VERY_HIGH: 8 };

const TABS = [
  { id: 'route', label: '경로찾기', icon: '🗺️' },
  { id: 'uphill', label: '오르막길', icon: '⛰️' },
  { id: 'safety', label: '안전정보', icon: '⚠️' },
];

const DEFAULT_CENTER = { lat: 37.4201, lng: 127.1265 }; // 성남시청

function MapPage() {
  const mapRef = useRef(null);
  const mapInstanceRef = useRef(null);
  const polylinesRef = useRef([]);
  const markersRef = useRef([]);
  const overlaysRef = useRef([]);
  const myLocationOverlayRef = useRef(null);
  const myLocationRef = useRef(null);
  const [activeTab, setActiveTab] = useState('route');
  const [panelOpen, setPanelOpen] = useState(true);
  const [kakaoReady, setKakaoReady] = useState(false);
  const [locationMsg, setLocationMsg] = useState('');
  const [defaultOrigin, setDefaultOrigin] = useState(null);

  useEffect(() => {
    let cancelled = false;

    loadKakaoSDK().then((kakao) => {
      if (cancelled || mapInstanceRef.current) return;
      mapInstanceRef.current = new kakao.maps.Map(mapRef.current, {
        center: new kakao.maps.LatLng(DEFAULT_CENTER.lat, DEFAULT_CENTER.lng),
        level: 5,
      });
      setKakaoReady(true);
      console.log('카카오맵 초기화 완료');

      // 현재 위치 가져오기
      requestCurrentLocation(kakao);
    });

    return () => { cancelled = true; };
  }, []);

  const showMyLocationMarker = useCallback((kakao, lat, lng) => {
    if (myLocationOverlayRef.current) {
      myLocationOverlayRef.current.setMap(null);
    }

    const content = '<div class="my-location-marker"><div class="my-location-dot"></div><div class="my-location-pulse"></div></div>';
    myLocationOverlayRef.current = new kakao.maps.CustomOverlay({
      position: new kakao.maps.LatLng(lat, lng),
      content,
      map: mapInstanceRef.current,
      zIndex: 150,
    });
  }, []);

  const reverseGeocode = useCallback((kakao, lat, lng) => {
    const geocoder = new kakao.maps.services.Geocoder();
    geocoder.coord2Address(lng, lat, (result, status) => {
      if (status === kakao.maps.services.Status.OK && result[0]) {
        const addr = result[0].road_address
          ? result[0].road_address.address_name
          : result[0].address.address_name;
        console.log('현재 위치 주소:', addr);
        setDefaultOrigin({ name: addr, lat, lng });
      } else {
        console.log('역지오코딩 실패, 좌표만 사용');
        setDefaultOrigin({ name: '현재 위치', lat, lng });
      }
    });
  }, []);

  const requestCurrentLocation = useCallback((kakao) => {
    if (!navigator.geolocation) {
      console.log('Geolocation 미지원');
      setLocationMsg('브라우저가 위치 서비스를 지원하지 않습니다.');
      setTimeout(() => setLocationMsg(''), 3000);
      return;
    }

    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const { latitude: lat, longitude: lng } = pos.coords;
        console.log('현재 위치:', lat, lng);
        myLocationRef.current = { lat, lng };

        mapInstanceRef.current.setCenter(new kakao.maps.LatLng(lat, lng));
        mapInstanceRef.current.setLevel(4);
        showMyLocationMarker(kakao, lat, lng);
        reverseGeocode(kakao, lat, lng);
      },
      (err) => {
        console.warn('위치 권한 거부:', err.message);
        myLocationRef.current = DEFAULT_CENTER;
        setLocationMsg('위치 권한이 거부되어 기본 위치로 설정됩니다.');
        setTimeout(() => setLocationMsg(''), 3000);
        reverseGeocode(kakao, DEFAULT_CENTER.lat, DEFAULT_CENTER.lng);
      },
      { enableHighAccuracy: true, timeout: 5000 }
    );
  }, [showMyLocationMarker, reverseGeocode]);

  const handleMyLocation = useCallback(() => {
    if (!mapInstanceRef.current || !window.kakao?.maps) return;
    const kakao = window.kakao;

    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const { latitude: lat, longitude: lng } = pos.coords;
        myLocationRef.current = { lat, lng };
        mapInstanceRef.current.setCenter(new kakao.maps.LatLng(lat, lng));
        mapInstanceRef.current.setLevel(4);
        showMyLocationMarker(kakao, lat, lng);
        console.log('내 위치로 이동:', lat, lng);
      },
      () => {
        if (myLocationRef.current) {
          const { lat, lng } = myLocationRef.current;
          mapInstanceRef.current.setCenter(new kakao.maps.LatLng(lat, lng));
          mapInstanceRef.current.setLevel(4);
        }
        setLocationMsg('위치를 가져올 수 없습니다.');
        setTimeout(() => setLocationMsg(''), 3000);
      },
      { enableHighAccuracy: true, timeout: 5000 }
    );
  }, [showMyLocationMarker]);

  const clearRoute = useCallback(() => {
    polylinesRef.current.forEach((p) => p.setMap(null));
    polylinesRef.current = [];
    markersRef.current.forEach((m) => m.setMap(null));
    markersRef.current = [];
    overlaysRef.current.forEach((o) => o.setMap(null));
    overlaysRef.current = [];
  }, []);

  const drawRoute = useCallback((coords, riskSections = []) => {
    if (!mapInstanceRef.current || !window.kakao?.maps) return;
    const kakao = window.kakao;

    clearRoute();

    const path = coords.map((c) => new kakao.maps.LatLng(c.lat, c.lng));
    if (path.length === 0) return;

    // 위험구간이 없으면 기존처럼 초록색 한 줄
    if (!riskSections || riskSections.length === 0) {
      const line = new kakao.maps.Polyline({
        map: mapInstanceRef.current,
        path,
        strokeWeight: 5,
        strokeColor: RISK_COLORS.SAFE,
        strokeOpacity: 0.8,
        strokeStyle: 'solid',
      });
      polylinesRef.current.push(line);
    } else {
      // 위험구간 인덱스 범위 계산 (nearestRouteIdx 기준 앞뒤 SPREAD 좌표)
      const SPREAD = 8;
      const riskRanges = riskSections
        .map((rs) => ({
          start: Math.max(0, rs.nearestRouteIdx - SPREAD),
          end: Math.min(coords.length - 1, rs.nearestRouteIdx + SPREAD),
          riskLevel: rs.riskLevel,
        }))
        .sort((a, b) => a.start - b.start);

      // 겹치는 구간 병합
      const merged = [];
      for (const r of riskRanges) {
        if (merged.length > 0 && r.start <= merged[merged.length - 1].end + 1) {
          const last = merged[merged.length - 1];
          last.end = Math.max(last.end, r.end);
          // 더 높은 위험도 유지
          const priority = ['LOW', 'MEDIUM', 'HIGH', 'VERY_HIGH'];
          if (priority.indexOf(r.riskLevel) > priority.indexOf(last.riskLevel)) {
            last.riskLevel = r.riskLevel;
          }
        } else {
          merged.push({ ...r });
        }
      }

      // 구간별 폴리라인 그리기
      let cursor = 0;
      for (const range of merged) {
        // 안전 구간 (cursor ~ range.start)
        if (cursor < range.start) {
          const safePath = path.slice(cursor, range.start + 1);
          if (safePath.length >= 2) {
            const safeLine = new kakao.maps.Polyline({
              map: mapInstanceRef.current,
              path: safePath,
              strokeWeight: 5,
              strokeColor: RISK_COLORS.SAFE,
              strokeOpacity: 0.8,
              strokeStyle: 'solid',
            });
            polylinesRef.current.push(safeLine);
          }
        }
        // 위험 구간
        const riskPath = path.slice(range.start, range.end + 1);
        if (riskPath.length >= 2) {
          const riskLine = new kakao.maps.Polyline({
            map: mapInstanceRef.current,
            path: riskPath,
            strokeWeight: RISK_STROKE_WEIGHT[range.riskLevel] || 7,
            strokeColor: RISK_COLORS[range.riskLevel] || RISK_COLORS.MEDIUM,
            strokeOpacity: 0.9,
            strokeStyle: 'solid',
          });
          polylinesRef.current.push(riskLine);
        }
        cursor = range.end;
      }
      // 마지막 안전 구간
      if (cursor < path.length - 1) {
        const safePath = path.slice(cursor, path.length);
        if (safePath.length >= 2) {
          const safeLine = new kakao.maps.Polyline({
            map: mapInstanceRef.current,
            path: safePath,
            strokeWeight: 5,
            strokeColor: RISK_COLORS.SAFE,
            strokeOpacity: 0.8,
            strokeStyle: 'solid',
          });
          polylinesRef.current.push(safeLine);
        }
      }

      // 위험구간 경고 오버레이
      riskSections.forEach((rs) => {
        const pos = new kakao.maps.LatLng(rs.lat, rs.lng);
        const levelLabel = rs.riskLevel === 'VERY_HIGH' || rs.riskLevel === 'HIGH' ? '위험'
          : rs.riskLevel === 'MEDIUM' ? '주의' : '양호';
        const levelClass = rs.riskLevel === 'VERY_HIGH' || rs.riskLevel === 'HIGH' ? 'high'
          : rs.riskLevel === 'MEDIUM' ? 'medium' : 'low';

        const content = document.createElement('div');
        content.className = 'risk-overlay-marker';
        content.innerHTML = `<div class="risk-overlay-icon ${levelClass}">!</div>`;

        const overlay = new kakao.maps.CustomOverlay({
          position: pos,
          content,
          map: mapInstanceRef.current,
          yAnchor: 1.3,
          zIndex: 200,
        });
        overlaysRef.current.push(overlay);

        // 클릭 시 InfoWindow
        content.addEventListener('click', () => {
          // 기존 열린 InfoWindow 닫기
          overlaysRef.current
            .filter((o) => o._infoOverlay)
            .forEach((o) => { o._infoOverlay.setMap(null); delete o._infoOverlay; });

          const infoContent = document.createElement('div');
          infoContent.className = 'risk-info-window';
          infoContent.innerHTML = `
            <div class="risk-info-header">
              <span class="risk-info-name">${rs.name || '위험구간'}</span>
              <button class="risk-info-close">&times;</button>
            </div>
            <div class="risk-info-body">
              <div class="risk-info-row"><span>경사도</span><strong>${rs.grade ? rs.grade.toFixed(1) + '%' : '정보없음'}</strong></div>
              <div class="risk-info-row"><span>위험등급</span><span class="risk-badge ${levelClass}">${levelLabel}</span></div>
              <div class="risk-info-row"><span>경로 이격</span><strong>${rs.distanceFromRoute ? rs.distanceFromRoute.toFixed(1) + 'm' : '-'}</strong></div>
            </div>`;

          const infoOverlay = new kakao.maps.CustomOverlay({
            position: pos,
            content: infoContent,
            map: mapInstanceRef.current,
            yAnchor: 1.8,
            zIndex: 300,
          });
          overlay._infoOverlay = infoOverlay;
          overlaysRef.current.push(infoOverlay);

          infoContent.querySelector('.risk-info-close').addEventListener('click', (e) => {
            e.stopPropagation();
            infoOverlay.setMap(null);
          });
        });
      });
    }

    // 출발/도착 마커
    if (path.length >= 2) {
      const startMarker = new kakao.maps.Marker({
        map: mapInstanceRef.current,
        position: path[0],
        title: '출발',
      });
      const endMarker = new kakao.maps.Marker({
        map: mapInstanceRef.current,
        position: path[path.length - 1],
        title: '도착',
      });
      markersRef.current.push(startMarker, endMarker);
    }

    // 경로에 맞게 지도 범위 조정
    const bounds = new kakao.maps.LatLngBounds();
    path.forEach((p) => bounds.extend(p));
    mapInstanceRef.current.setBounds(bounds);

    console.log('경로 표시 완료:', coords.length, '개 좌표,', riskSections.length, '개 위험구간');
  }, [clearRoute]);

  const moveToLocation = useCallback((lat, lng, name) => {
    if (!mapInstanceRef.current || !window.kakao?.maps) return;
    const kakao = window.kakao;
    const pos = new kakao.maps.LatLng(lat, lng);
    mapInstanceRef.current.setCenter(pos);
    mapInstanceRef.current.setLevel(3);

    // 기존 UphillPanel 마커 제거 (태그로 구분)
    markersRef.current = markersRef.current.filter((m) => {
      if (m._uphillMarker) { m.setMap(null); return false; }
      return true;
    });

    const marker = new kakao.maps.Marker({ map: mapInstanceRef.current, position: pos, title: name || '' });
    marker._uphillMarker = true;
    markersRef.current.push(marker);

    if (name) {
      const infoWindow = new kakao.maps.InfoWindow({ content: `<div style="padding:5px;font-size:12px;">${name}</div>` });
      infoWindow.open(mapInstanceRef.current, marker);
    }
  }, []);

  const handleTabClick = (tabId) => {
    if (activeTab === tabId && panelOpen) {
      setPanelOpen(false);
    } else {
      setActiveTab(tabId);
      setPanelOpen(true);
    }
  };

  return (
    <div className="map-layout">
      {/* 지도 */}
      <div ref={mapRef} className="map-container" />

      {/* 내 위치 버튼 */}
      <button className="my-location-btn" onClick={handleMyLocation} title="내 위치">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round">
          <circle cx="12" cy="12" r="4" />
          <line x1="12" y1="2" x2="12" y2="6" />
          <line x1="12" y1="18" x2="12" y2="22" />
          <line x1="2" y1="12" x2="6" y2="12" />
          <line x1="18" y1="12" x2="22" y2="12" />
        </svg>
      </button>

      {/* 위치 안내 메시지 */}
      {locationMsg && <div className="location-toast">{locationMsg}</div>}

      {/* 왼쪽 패널 */}
      <div className={`side-panel ${panelOpen ? 'open' : 'closed'}`}>
        {/* 헤더 */}
        <div className="panel-header">
          <div className="panel-logo">
            <span className="logo-icon">⛰️</span>
            <span className="logo-text">경기 안전 내비</span>
          </div>
          <button className="panel-toggle" onClick={() => setPanelOpen(!panelOpen)}>
            {panelOpen ? '◀' : '▶'}
          </button>
        </div>

        {/* 검색바 */}
        <div className="search-box">
          <span className="search-icon">🔍</span>
          <input
            type="text"
            placeholder="장소, 주소 검색"
            className="search-input"
          />
        </div>

        {/* 탭 메뉴 */}
        <div className="tab-menu">
          {TABS.map((tab) => (
            <button
              key={tab.id}
              className={`tab-btn ${activeTab === tab.id && panelOpen ? 'active' : ''}`}
              onClick={() => handleTabClick(tab.id)}
            >
              <span className="tab-icon">{tab.icon}</span>
              <span className="tab-label">{tab.label}</span>
            </button>
          ))}
        </div>

        {/* 탭 콘텐츠 */}
        <div className="tab-content">
          {activeTab === 'route' && <RoutePanel kakaoReady={kakaoReady} onRouteFound={drawRoute} defaultOrigin={defaultOrigin} />}
          {activeTab === 'uphill' && <UphillPanel onItemClick={moveToLocation} />}
          {activeTab === 'safety' && <SafetyPanel />}
        </div>
      </div>
    </div>
  );
}

function PlaceSearchInput({ placeholder, value, onChange, onClear, onSelect, dotClass, kakaoReady }) {
  const [results, setResults] = useState([]);
  const [showResults, setShowResults] = useState(false);
  const debounceRef = useRef(null);
  const justSelectedRef = useRef(false);

  const searchPlace = useCallback((keyword) => {
    if (!keyword.trim() || !kakaoReady || !window.kakao?.maps?.services) {
      setResults([]);
      setShowResults(false);
      return;
    }

    const ps = new window.kakao.maps.services.Places();
    ps.keywordSearch(keyword, (data, status) => {
      if (status === window.kakao.maps.services.Status.OK) {
        setResults(data.slice(0, 5));
        setShowResults(true);
        console.log('장소 검색 결과:', keyword, data.length, '건');
      } else if (status === window.kakao.maps.services.Status.ZERO_RESULT) {
        setResults([]);
        setShowResults(true);
        console.log('장소 검색 결과 없음:', keyword);
      } else {
        console.error('장소 검색 실패:', status);
        setResults([]);
        setShowResults(false);
      }
    });
  }, [kakaoReady]);

  const handleChange = (e) => {
    const val = e.target.value;
    if (justSelectedRef.current) {
      justSelectedRef.current = false;
      return;
    }
    onChange(val);
    onClear();
    clearTimeout(debounceRef.current);
    debounceRef.current = setTimeout(() => searchPlace(val), 300);
  };

  const handleSelect = (place) => {
    justSelectedRef.current = true;
    onSelect({
      name: place.place_name,
      lng: parseFloat(place.x),
      lat: parseFloat(place.y),
      address: place.address_name,
    });
    onChange(place.place_name);
    setShowResults(false);
    console.log('장소 선택:', place.place_name, place.x, place.y);
  };

  return (
    <div className="place-search-wrapper">
      <div className="route-input-row">
        <span className={`route-dot ${dotClass}`} />
        <input
          type="text"
          placeholder={placeholder}
          className="route-input"
          value={value}
          onChange={handleChange}
          onFocus={() => results.length > 0 && setShowResults(true)}
          onBlur={() => setTimeout(() => setShowResults(false), 200)}
        />
      </div>
      {showResults && (
        <ul className="place-results">
          {results.length > 0 ? (
            results.map((place) => (
              <li key={place.id} className="place-result-item" onMouseDown={() => handleSelect(place)}>
                <span className="place-name">{place.place_name}</span>
                <span className="place-address">{place.address_name}</span>
              </li>
            ))
          ) : (
            <li className="place-result-empty">검색 결과가 없습니다</li>
          )}
        </ul>
      )}
    </div>
  );
}

function RoutePanel({ kakaoReady, onRouteFound, defaultOrigin }) {
  const [originText, setOriginText] = useState('');
  const [destText, setDestText] = useState('');
  const [originPlace, setOriginPlace] = useState(null);
  const [destPlace, setDestPlace] = useState(null);
  const [routeOption, setRouteOption] = useState('SAFE');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [routeInfo, setRouteInfo] = useState(null);
  const defaultAppliedRef = useRef(false);

  useEffect(() => {
    if (defaultOrigin && !defaultAppliedRef.current && !originPlace) {
      defaultAppliedRef.current = true;
      setOriginText(defaultOrigin.name);
      setOriginPlace({ name: defaultOrigin.name, lat: defaultOrigin.lat, lng: defaultOrigin.lng });
      console.log('출발지 기본값 설정:', defaultOrigin.name);
    }
  }, [defaultOrigin, originPlace]);

  const ROUTE_OPTIONS = [
    { id: 'SAFE', label: '안전 우선' },
    { id: 'SHORT', label: '최단 거리' },
    { id: 'FLAT', label: '오르막 최소' },
  ];

  const handleSearch = async () => {
    if (!originPlace) {
      setError('출발지를 검색하여 선택해주세요.');
      return;
    }
    if (!destPlace) {
      setError('도착지를 검색하여 선택해주세요.');
      return;
    }

    setError('');
    setLoading(true);
    setRouteInfo(null);
    console.log('경로 검색 요청:', { origin: originPlace, dest: destPlace, option: routeOption });

    try {
      const res = await fetch(`${API_BASE}/route/walk`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          originLng: originPlace.lng,
          originLat: originPlace.lat,
          destLng: destPlace.lng,
          destLat: destPlace.lat,
          option: routeOption,
        }),
      });

      if (!res.ok) {
        const errBody = await res.text();
        console.error('경로 검색 실패:', res.status, errBody);
        throw new Error('경로를 찾을 수 없습니다.');
      }

      const data = await res.json();
      console.log('경로 검색 응답:', data);

      setRouteInfo({
        distance: data.totalDistance,
        duration: data.totalDuration,
        riskSections: data.riskSections || [],
        overallRisk: data.overallRisk || 'SAFE',
      });

      if (data.coords && data.coords.length > 0) {
        onRouteFound(data.coords, data.riskSections || []);
      }
    } catch (err) {
      console.error('경로 검색 에러:', err);
      setError(err.message || '경로 검색 중 오류가 발생했습니다.');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="content-section">
      <h3 className="section-title">경로 찾기</h3>
      <div className="route-inputs">
        <PlaceSearchInput
          placeholder="출발지 입력"
          value={originText}
          onChange={(v) => { setOriginText(v); }}
          onClear={() => setOriginPlace(null)}
          onSelect={setOriginPlace}
          dotClass="start"
          kakaoReady={kakaoReady}
        />
        <div className="route-input-divider" />
        <PlaceSearchInput
          placeholder="도착지 입력"
          value={destText}
          onChange={(v) => { setDestText(v); }}
          onClear={() => setDestPlace(null)}
          onSelect={setDestPlace}
          dotClass="end"
          kakaoReady={kakaoReady}
        />
      </div>

      {error && <p className="route-error">{error}</p>}

      <button className="primary-btn" onClick={handleSearch} disabled={loading}>
        {loading ? '검색 중...' : '경로 검색'}
      </button>

      {routeInfo && (
        <div className="route-result-section">
          <div className="route-result-info">
            <span>거리: {(routeInfo.distance / 1000).toFixed(1)}km</span>
            <span>예상 시간: {Math.round(routeInfo.duration / 60)}분</span>
          </div>

          {/* 위험구간 요약 */}
          <div className="risk-summary">
            <div className="risk-summary-header">
              <span className={`overall-risk-badge ${routeInfo.overallRisk === 'DANGER' ? 'danger' : routeInfo.overallRisk === 'CAUTION' ? 'caution' : 'safe'}`}>
                {routeInfo.overallRisk === 'DANGER' ? '위험' : routeInfo.overallRisk === 'CAUTION' ? '주의' : '안전'}
              </span>
              {routeInfo.riskSections.length > 0
                ? <span className="risk-summary-text">위험구간 {routeInfo.riskSections.length}개 발견</span>
                : <span className="risk-summary-text safe-text">안전한 경로입니다</span>
              }
            </div>

            {routeInfo.riskSections.length > 0 && (
              <div className="risk-section-list">
                {routeInfo.riskSections.map((rs, i) => {
                  const levelClass = rs.riskLevel === 'VERY_HIGH' || rs.riskLevel === 'HIGH' ? 'high'
                    : rs.riskLevel === 'MEDIUM' ? 'medium' : 'low';
                  const levelLabel = rs.riskLevel === 'VERY_HIGH' || rs.riskLevel === 'HIGH' ? '위험'
                    : rs.riskLevel === 'MEDIUM' ? '주의' : '양호';
                  return (
                    <div key={i} className="risk-section-item">
                      <div className="risk-section-info">
                        <span className="risk-section-name">{rs.name || `위험구간 ${i + 1}`}</span>
                        <span className="risk-section-detail">경사 {rs.grade ? rs.grade.toFixed(1) + '%' : '-'} · 경로에서 {rs.distanceFromRoute ? rs.distanceFromRoute.toFixed(0) + 'm' : '-'}</span>
                      </div>
                      <span className={`risk-badge ${levelClass}`}>{levelLabel}</span>
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        </div>
      )}

      <div className="option-group">
        <p className="option-label">경로 옵션</p>
        <div className="option-chips">
          {ROUTE_OPTIONS.map((opt) => (
            <button
              key={opt.id}
              className={`chip ${routeOption === opt.id ? 'active' : ''}`}
              onClick={() => setRouteOption(opt.id)}
            >
              {opt.label}
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

const REGION_OPTIONS = [
  { label: '전체 지역', value: '' },
  { label: '성남시', value: '41130' },
  { label: '수정구', value: '41131' },
  { label: '중원구', value: '41133' },
  { label: '분당구', value: '41135' },
];

const RISK_FILTER_OPTIONS = [
  { label: '위험도 전체', value: '' },
  { label: '위험 (HIGH)', value: 'HIGH' },
  { label: '주의 (MEDIUM)', value: 'MEDIUM' },
  { label: '양호 (LOW)', value: 'LOW' },
];

const FALLBACK_DATA = [
  { name: '광교산 오르막', regionCode: '', grade: 12, riskLevel: 'HIGH', latitude: 37.28, longitude: 127.05 },
  { name: '청계산 진입로', regionCode: '', grade: 9, riskLevel: 'MEDIUM', latitude: 37.41, longitude: 127.05 },
  { name: '북한산 순환로', regionCode: '', grade: 7, riskLevel: 'LOW', latitude: 37.66, longitude: 126.98 },
];

function UphillPanel({ onItemClick }) {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [regionCode, setRegionCode] = useState('');
  const [riskLevel, setRiskLevel] = useState('');

  const fetchData = useCallback(async (region, risk) => {
    setLoading(true);
    setError('');
    try {
      const params = new URLSearchParams();
      if (region) params.append('regionCode', region);
      if (risk) params.append('riskLevel', risk);
      const url = `${API_BASE}/steep-slope${params.toString() ? '?' + params.toString() : ''}`;
      const res = await fetch(url);
      if (!res.ok) throw new Error('데이터를 불러올 수 없습니다.');
      const data = await res.json();
      setItems(data);
    } catch (err) {
      console.error('급경사지 데이터 로드 실패:', err);
      setError(err.message);
      setItems(FALLBACK_DATA);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchData(regionCode, riskLevel);
  }, [fetchData, regionCode, riskLevel]);

  const getRiskClass = (level) => {
    if (level === 'VERY_HIGH' || level === 'HIGH') return 'high';
    if (level === 'MEDIUM') return 'medium';
    return 'low';
  };

  const getRiskLabel = (level) => {
    if (level === 'VERY_HIGH') return '매우위험';
    if (level === 'HIGH') return '위험';
    if (level === 'MEDIUM') return '주의';
    return '양호';
  };

  return (
    <div className="content-section">
      <h3 className="section-title">오르막길 구간</h3>
      <p className="section-desc">경기도 내 급경사지 안전 정보입니다. ({items.length}건)</p>

      <div className="filter-row">
        <select className="filter-select" value={regionCode} onChange={(e) => setRegionCode(e.target.value)}>
          {REGION_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
        <select className="filter-select" value={riskLevel} onChange={(e) => setRiskLevel(e.target.value)}>
          {RISK_FILTER_OPTIONS.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
      </div>

      {loading && <p className="loading-text">데이터 로딩 중...</p>}
      {error && <p className="route-error">{error} (기본 데이터 표시)</p>}

      <div className="segment-list">
        {items.map((item, i) => (
          <div
            key={item.id || i}
            className="segment-item"
            onClick={() => item.latitude && item.longitude && onItemClick?.(item.latitude, item.longitude, item.name)}
          >
            <div className="segment-info">
              <span className="segment-name">{item.name || '이름 없음'}</span>
              <span className="segment-city">{item.source || ''} {item.regionCode ? `(${item.regionCode})` : ''}</span>
            </div>
            <div className="segment-meta">
              <span className="grade-badge">경사 {item.grade != null ? item.grade.toFixed(1) + '%' : '-'}</span>
              <span className={`risk-badge ${getRiskClass(item.riskLevel)}`}>
                {getRiskLabel(item.riskLevel)}
              </span>
            </div>
          </div>
        ))}
        {!loading && items.length === 0 && (
          <p className="empty-text">조건에 맞는 데이터가 없습니다.</p>
        )}
      </div>
    </div>
  );
}

function SafetyPanel() {
  return (
    <div className="content-section">
      <h3 className="section-title">안전 정보</h3>
      <p className="section-desc">실시간 도로 안전 현황입니다.</p>

      <div className="safety-stats">
        <div className="stat-card red">
          <span className="stat-number">3</span>
          <span className="stat-label">위험 구간</span>
        </div>
        <div className="stat-card yellow">
          <span className="stat-number">12</span>
          <span className="stat-label">주의 구간</span>
        </div>
        <div className="stat-card green">
          <span className="stat-number">47</span>
          <span className="stat-label">안전 구간</span>
        </div>
      </div>

      <div className="notice-list">
        <p className="option-label">최근 알림</p>
        {[
          { msg: '광교산로 결빙 위험', time: '10분 전', level: 'high' },
          { msg: '청계산 진입로 공사 중', time: '1시간 전', level: 'medium' },
          { msg: '북한산 순환로 정상 운행', time: '2시간 전', level: 'low' },
        ].map((item, i) => (
          <div key={i} className="notice-item">
            <span className={`notice-dot ${item.level}`} />
            <div>
              <p className="notice-msg">{item.msg}</p>
              <p className="notice-time">{item.time}</p>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

export default MapPage;
