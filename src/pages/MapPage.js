import React, { useEffect, useRef, useState, useCallback } from 'react';
import { loadKakaoSDK } from '../utils/kakaoLoader';
import './MapPage.css';

const API_BASE = process.env.REACT_APP_API_BASE_URL || 'http://localhost:8080/api';

const TABS = [
  { id: 'route', label: '경로찾기', icon: '🗺️' },
  { id: 'uphill', label: '오르막길', icon: '⛰️' },
  { id: 'safety', label: '안전정보', icon: '⚠️' },
];

const DEFAULT_CENTER = { lat: 37.4201, lng: 127.1265 }; // 성남시청

function MapPage() {
  const mapRef = useRef(null);
  const mapInstanceRef = useRef(null);
  const polylineRef = useRef(null);
  const markersRef = useRef([]);
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

  const drawRoute = useCallback((coords) => {
    if (!mapInstanceRef.current || !window.kakao?.maps) return;
    const kakao = window.kakao;

    // 기존 경로/마커 제거
    if (polylineRef.current) polylineRef.current.setMap(null);
    markersRef.current.forEach((m) => m.setMap(null));
    markersRef.current = [];

    const path = coords.map((c) => new kakao.maps.LatLng(c.lat, c.lng));

    // 경로 폴리라인
    polylineRef.current = new kakao.maps.Polyline({
      map: mapInstanceRef.current,
      path,
      strokeWeight: 5,
      strokeColor: '#03C75A',
      strokeOpacity: 0.8,
      strokeStyle: 'solid',
    });

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

    console.log('경로 표시 완료:', coords.length, '개 좌표');
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
          {activeTab === 'uphill' && <UphillPanel />}
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
      });

      if (data.coords && data.coords.length > 0) {
        onRouteFound(data.coords);
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
        <div className="route-result-info">
          <span>거리: {(routeInfo.distance / 1000).toFixed(1)}km</span>
          <span>예상 시간: {Math.round(routeInfo.duration / 60)}분</span>
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

function UphillPanel() {
  return (
    <div className="content-section">
      <h3 className="section-title">오르막길 구간</h3>
      <p className="section-desc">경기도 내 오르막길 안전 구간 정보입니다.</p>

      <div className="filter-row">
        <select className="filter-select">
          <option>전체 지역</option>
          <option>수원시</option>
          <option>성남시</option>
          <option>고양시</option>
          <option>용인시</option>
        </select>
        <select className="filter-select">
          <option>위험도 전체</option>
          <option>높음</option>
          <option>보통</option>
          <option>낮음</option>
        </select>
      </div>

      <div className="segment-list">
        {[
          { name: '광교산 오르막', city: '수원시', grade: '12%', risk: 'high' },
          { name: '청계산 진입로', city: '성남시', grade: '9%', risk: 'medium' },
          { name: '북한산 순환로', city: '고양시', grade: '7%', risk: 'low' },
        ].map((item, i) => (
          <div key={i} className="segment-item">
            <div className="segment-info">
              <span className="segment-name">{item.name}</span>
              <span className="segment-city">{item.city}</span>
            </div>
            <div className="segment-meta">
              <span className="grade-badge">경사 {item.grade}</span>
              <span className={`risk-badge ${item.risk}`}>
                {item.risk === 'high' ? '위험' : item.risk === 'medium' ? '주의' : '양호'}
              </span>
            </div>
          </div>
        ))}
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
