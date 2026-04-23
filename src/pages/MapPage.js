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
  const riskSectionsRef = useRef([]);         // 원래 경로 riskSections 데이터
  const detourRiskSectionsRef = useRef([]);   // 우회 경로 riskSections 데이터
  const [visibleRiskMarkers, setVisibleRiskMarkers] = useState([]); // "!" 마커 React state
  const [mapTick, setMapTick] = useState(0);   // 지도 이동/줌 시 re-render 트리거
  const [riskInfoWindow, setRiskInfoWindow] = useState(null); // 클릭한 "!" 마커 InfoWindow
  const myLocationOverlayRef = useRef(null);
  const myLocationRef = useRef(null);
  const [activeTab, setActiveTab] = useState('route');
  const [panelOpen, setPanelOpen] = useState(true);
  const [kakaoReady, setKakaoReady] = useState(false);
  const [locationMsg, setLocationMsg] = useState('');
  const [defaultOrigin, setDefaultOrigin] = useState(null);
  const [mapWeather, setMapWeather] = useState(null);
  const detourPolylinesRef = useRef([]);
  const detourMarkersRef = useRef([]);
  const layerMarkersRef = useRef({ accidents: [], cctv: [], protectedZones: [], welfare: [], shelters: [], senior: [] });
  const activeLayersRef = useRef({ accidents: false, cctv: false, protectedZones: false, welfare: false, shelters: false, senior: false });
  const layerInfoOverlayRef = useRef(null);

  const LAYER_COLORS = {
    accidents: '#F44336', cctv: '#757575',
    protectedZones: '#4CAF50', welfare: '#2196F3', shelters: '#FF9800', senior: '#9C27B0',
  };

  const cctvClustererRef = useRef(null);

  const getMaxMarkersByZoom = useCallback(() => {
    if (!mapInstanceRef.current) return 200;
    const level = mapInstanceRef.current.getLevel();
    // 카카오맵 레벨: 1(가장 확대) ~ 14(가장 축소)
    if (level <= 3) return Infinity;
    if (level <= 5) return 500;
    if (level <= 6) return 300;
    if (level <= 7) return 100;
    return 30;
  }, []);

  const updateLayerMarkersForZoom = useCallback(() => {
    if (!mapInstanceRef.current || !window.kakao?.maps) return;
    const maxMarkers = getMaxMarkersByZoom();

    Object.keys(activeLayersRef.current).forEach((type) => {
      if (!activeLayersRef.current[type]) return;
      if (type === 'cctv') return; // clusterer가 줌 변경 자동 처리
      const markers = layerMarkersRef.current[type] || [];
      markers.forEach((m, i) => {
        if (i < maxMarkers) {
          m.setMap(mapInstanceRef.current);
        } else {
          m.setMap(null);
        }
      });
    });
  }, [getMaxMarkersByZoom]);

  const clearLayerMarkers = useCallback((type) => {
    if (type === 'cctv' && cctvClustererRef.current) {
      cctvClustererRef.current.clear();
      cctvClustererRef.current = null;
    }
    if (!layerMarkersRef.current[type]) return;
    layerMarkersRef.current[type].forEach((m) => m.setMap(null));
    layerMarkersRef.current[type] = [];
    activeLayersRef.current[type] = false;
  }, []);

  const showLayerMarkers = useCallback((type, items, emoji) => {
    if (!mapInstanceRef.current || !window.kakao?.maps) return;
    const kakao = window.kakao;
    clearLayerMarkers(type);

    console.log(`[${type}] API 응답 건수:`, items.length);
    activeLayersRef.current[type] = true;

    // CCTV — MarkerClusterer 적용
    if (type === 'cctv') {
      const kakaoMarkers = items
        .filter((item) => item.latitude && item.longitude)
        .map((item) => {
          const marker = new kakao.maps.Marker({
            position: new kakao.maps.LatLng(item.latitude, item.longitude),
          });
          kakao.maps.event.addListener(marker, 'click', () => {
            if (layerInfoOverlayRef.current) {
              layerInfoOverlayRef.current.setMap(null);
              layerInfoOverlayRef.current = null;
            }
            const infoEl = document.createElement('div');
            infoEl.className = 'layer-info-window';
            infoEl.innerHTML = `
              <div class="layer-info-header">
                <span>📹 ${item.name || 'CCTV'}</span>
                <button class="layer-info-close">&times;</button>
              </div>
              <div class="layer-info-body">
                ${item.address ? `<p>${item.address}</p>` : ''}
              </div>`;
            const infoOverlay = new kakao.maps.CustomOverlay({
              position: new kakao.maps.LatLng(item.latitude, item.longitude),
              content: infoEl,
              map: mapInstanceRef.current,
              yAnchor: 1.4,
              zIndex: 300,
            });
            layerInfoOverlayRef.current = infoOverlay;
            infoEl.querySelector('.layer-info-close').addEventListener('click', (ev) => {
              ev.stopPropagation();
              infoOverlay.setMap(null);
              layerInfoOverlayRef.current = null;
            });
          });
          return marker;
        });

      const clusterer = new kakao.maps.MarkerClusterer({
        map: mapInstanceRef.current,
        averageCenter: true,
        minLevel: 5,
        disableClickZoom: false,
        styles: [{
          width: '40px', height: '40px',
          background: 'rgba(117,117,117,0.85)',
          borderRadius: '50%',
          color: '#fff',
          textAlign: 'center',
          lineHeight: '40px',
          fontSize: '13px',
          fontWeight: 'bold',
        }],
      });
      clusterer.addMarkers(kakaoMarkers);
      cctvClustererRef.current = clusterer;
      layerMarkersRef.current[type] = kakaoMarkers;
      console.log(`[cctv] MarkerClusterer 적용: ${kakaoMarkers.length}건`);
      return;
    }

    // 일반 레이어 — CustomOverlay
    const bgColor = LAYER_COLORS[type] || '#666';
    const maxMarkers = getMaxMarkersByZoom();

    const newMarkers = items.map((item) => {
      if (!item.latitude || !item.longitude) return null;

      const wrap = document.createElement('div');
      wrap.className = 'layer-marker-wrap';

      const circle = document.createElement('div');
      circle.className = 'layer-marker-circle';
      circle.style.background = bgColor;
      circle.textContent = emoji;
      wrap.appendChild(circle);

      wrap.addEventListener('click', (e) => {
        e.stopPropagation();
        if (layerInfoOverlayRef.current) {
          layerInfoOverlayRef.current.setMap(null);
          layerInfoOverlayRef.current = null;
        }
        const infoEl = document.createElement('div');
        infoEl.className = 'layer-info-window';
        infoEl.innerHTML = `
          <div class="layer-info-header">
            <span>${emoji} ${item.name || '정보 없음'}</span>
            <button class="layer-info-close">&times;</button>
          </div>
          <div class="layer-info-body">
            ${item.address ? `<p>${item.address}</p>` : ''}
            ${item.phone ? `<p>📞 ${item.phone}</p>` : ''}
            ${item.type ? `<p>유형: ${item.type}</p>` : ''}
          </div>`;
        const infoOverlay = new kakao.maps.CustomOverlay({
          position: new kakao.maps.LatLng(item.latitude, item.longitude),
          content: infoEl,
          map: mapInstanceRef.current,
          yAnchor: 1.4,
          zIndex: 300,
        });
        layerInfoOverlayRef.current = infoOverlay;
        infoEl.querySelector('.layer-info-close').addEventListener('click', (ev) => {
          ev.stopPropagation();
          infoOverlay.setMap(null);
          layerInfoOverlayRef.current = null;
        });
      });

      const overlay = new kakao.maps.CustomOverlay({
        position: new kakao.maps.LatLng(item.latitude, item.longitude),
        content: wrap,
        yAnchor: 0.5,
        zIndex: 80,
      });
      return overlay;
    }).filter(Boolean);

    console.log(`[${type}] 마커 생성: ${newMarkers.length}건, 줌 제한: ${maxMarkers === Infinity ? '없음' : maxMarkers}`);

    newMarkers.forEach((m, i) => {
      if (i < maxMarkers) m.setMap(mapInstanceRef.current);
    });

    layerMarkersRef.current[type] = newMarkers;
  }, [clearLayerMarkers, getMaxMarkersByZoom]);

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

      // 줌 변경 / 드래그 시 레이어 마커 + React "!" 마커 위치 갱신
      kakao.maps.event.addListener(mapInstanceRef.current, 'zoom_changed', () => {
        updateLayerMarkersForZoom();
        setMapTick((t) => t + 1);
      });
      kakao.maps.event.addListener(mapInstanceRef.current, 'dragend', () => {
        setMapTick((t) => t + 1);
      });

      // 현재 위치 가져오기
      requestCurrentLocation(kakao);
    });

    return () => { cancelled = true; };
  }, [updateLayerMarkersForZoom]);

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

  const fetchMapWeather = useCallback((lat, lng) => {
    fetch(`${API_BASE}/weather?lat=${lat}&lng=${lng}`)
      .then((res) => res.ok ? res.json() : null)
      .then((data) => { if (data) setMapWeather(data); })
      .catch(() => {});
  }, []);

  const requestCurrentLocation = useCallback((kakao) => {
    if (!navigator.geolocation) {
      console.log('Geolocation 미지원');
      setLocationMsg('브라우저가 위치 서비스를 지원하지 않습니다.');
      setTimeout(() => setLocationMsg(''), 3000);
      fetchMapWeather(DEFAULT_CENTER.lat, DEFAULT_CENTER.lng);
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
        fetchMapWeather(lat, lng);
      },
      (err) => {
        console.warn('위치 권한 거부:', err.message);
        myLocationRef.current = DEFAULT_CENTER;
        setLocationMsg('위치 권한이 거부되어 기본 위치로 설정됩니다.');
        setTimeout(() => setLocationMsg(''), 3000);
        reverseGeocode(kakao, DEFAULT_CENTER.lat, DEFAULT_CENTER.lng);
        fetchMapWeather(DEFAULT_CENTER.lat, DEFAULT_CENTER.lng);
      },
      { enableHighAccuracy: true, timeout: 5000 }
    );
  }, [showMyLocationMarker, reverseGeocode, fetchMapWeather]);

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

  const clearDetour = useCallback(() => {
    detourPolylinesRef.current.forEach((p) => p.setMap(null));
    detourPolylinesRef.current = [];
    detourMarkersRef.current.forEach((m) => m.setMap(null));
    detourMarkersRef.current = [];
    detourRiskSectionsRef.current = [];
  }, []);

  const clearRoute = useCallback(() => {
    polylinesRef.current.forEach((p) => p.setMap(null));
    polylinesRef.current = [];
    markersRef.current.forEach((m) => m.setMap(null));
    markersRef.current = [];
    riskSectionsRef.current = [];
    setVisibleRiskMarkers([]);
    setRiskInfoWindow(null);
    clearDetour();
  }, [clearDetour]);

  // "!" 마커를 React state로 설정 (CustomOverlay 불사용)
  const createRiskMarkers = useCallback((sections, prefix) => {
    console.log('[createRiskMarkers] sections:', sections?.length, 'prefix:', prefix);
    setVisibleRiskMarkers(sections.map((rs) => ({
      lat: rs.lat,
      lng: rs.lng,
      riskLevel: rs.riskLevel,
      name: rs.name,
      grade: rs.grade,
      distanceFromRoute: rs.distanceFromRoute,
    })));
    setRiskInfoWindow(null);
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

    // drawRoute 완료 후 "!" 마커 즉시 생성
    riskSectionsRef.current = riskSections;
    createRiskMarkers(riskSections, 'risk-overlay');

    console.log('경로 표시 완료:', coords.length, '개 좌표,', riskSections.length, '개 위험구간');
  }, [clearRoute, createRiskMarkers]);

  const setRouteOpacity = useCallback((opacity) => {
    polylinesRef.current.forEach((p) => p.setOptions({ strokeOpacity: opacity }));
  }, []);

  const drawDetourRoute = useCallback((coords, riskSections = []) => {
    if (!mapInstanceRef.current || !window.kakao?.maps) return;
    const kakao = window.kakao;
    clearDetour();

    const path = coords.map((c) => new kakao.maps.LatLng(c.lat, c.lng));
    if (path.length === 0) return;

    // 원래 경로 투명도 낮춤
    setRouteOpacity(0.4);

    if (!riskSections || riskSections.length === 0) {
      // 안전한 우회 경로: 파란 점선
      const line = new kakao.maps.Polyline({
        map: mapInstanceRef.current,
        path,
        strokeWeight: 6,
        strokeColor: '#2196F3',
        strokeOpacity: 0.9,
        strokeStyle: 'shortdash',
      });
      detourPolylinesRef.current.push(line);
    } else {
      // 우회 경로에도 위험구간이 있는 경우
      const SPREAD = 8;
      const riskRanges = riskSections
        .map((rs) => ({
          start: Math.max(0, rs.nearestRouteIdx - SPREAD),
          end: Math.min(coords.length - 1, rs.nearestRouteIdx + SPREAD),
          riskLevel: rs.riskLevel,
        }))
        .sort((a, b) => a.start - b.start);

      const merged = [];
      for (const r of riskRanges) {
        if (merged.length > 0 && r.start <= merged[merged.length - 1].end + 1) {
          const last = merged[merged.length - 1];
          last.end = Math.max(last.end, r.end);
        } else {
          merged.push({ ...r });
        }
      }

      let cursor = 0;
      for (const range of merged) {
        if (cursor < range.start) {
          const safePath = path.slice(cursor, range.start + 1);
          if (safePath.length >= 2) {
            detourPolylinesRef.current.push(new kakao.maps.Polyline({
              map: mapInstanceRef.current, path: safePath,
              strokeWeight: 6, strokeColor: '#2196F3', strokeOpacity: 0.9, strokeStyle: 'shortdash',
            }));
          }
        }
        const riskPath = path.slice(range.start, range.end + 1);
        if (riskPath.length >= 2) {
          detourPolylinesRef.current.push(new kakao.maps.Polyline({
            map: mapInstanceRef.current, path: riskPath,
            strokeWeight: 7, strokeColor: '#FF5722', strokeOpacity: 0.9, strokeStyle: 'shortdash',
          }));
        }
        cursor = range.end;
      }
      if (cursor < path.length - 1) {
        const safePath = path.slice(cursor);
        if (safePath.length >= 2) {
          detourPolylinesRef.current.push(new kakao.maps.Polyline({
            map: mapInstanceRef.current, path: safePath,
            strokeWeight: 6, strokeColor: '#2196F3', strokeOpacity: 0.9, strokeStyle: 'shortdash',
          }));
        }
      }
    }

    // 우회 경로 출발/도착 마커
    if (path.length >= 2) {
      const bounds = new kakao.maps.LatLngBounds();
      path.forEach((p) => bounds.extend(p));
      polylinesRef.current.forEach((pl) => {
        const plPath = pl.getPath();
        plPath.forEach((p) => bounds.extend(p));
      });
      mapInstanceRef.current.setBounds(bounds);
    }

    // 우회 경로 riskSections 저장, drawRoute에서 표시한 "!" 마커 초기화
    detourRiskSectionsRef.current = riskSections;
    setVisibleRiskMarkers([]);  // 원래 경로 마커 제거 (selectRoute에서 재생성)
    setRiskInfoWindow(null);

    console.log('우회 경로 표시 완료:', coords.length, '개 좌표,', riskSections.length, '개 위험구간');
  }, [clearDetour, setRouteOpacity]);

  const selectRoute = useCallback((type) => {
    const map = mapInstanceRef.current;
    if (!map) return;

    // InfoWindow 닫기
    setRiskInfoWindow(null);

    if (type === 'original') {
      console.log('[selectRoute] original - riskSectionsRef:', riskSectionsRef.current?.length);
      // 원래 경로 폴리라인 + 출발/도착 마커 표시
      polylinesRef.current.forEach((p) => { p.setMap(map); p.setOptions({ strokeOpacity: 0.8 }); });
      markersRef.current.forEach((m) => m.setMap(map));
      // 우회 경로 숨김
      detourPolylinesRef.current.forEach((p) => p.setMap(null));
      detourMarkersRef.current.forEach((m) => m.setMap(null));
      // 원래 경로 "!" 마커 새로 생성
      console.log('[selectRoute] original - setVisibleRiskMarkers 호출');
      createRiskMarkers(riskSectionsRef.current, 'risk-overlay');
    } else {
      console.log('[selectRoute] detour - detourRiskSectionsRef:', detourRiskSectionsRef.current?.length);
      // 원래 경로 폴리라인 숨김
      polylinesRef.current.forEach((p) => p.setMap(null));
      // 우회 경로 폴리라인 + 출발/도착 마커 표시
      detourPolylinesRef.current.forEach((p) => { p.setMap(map); p.setOptions({ strokeOpacity: 0.9 }); });
      detourMarkersRef.current.forEach((m) => m.setMap(map));
      markersRef.current.forEach((m) => m.setMap(map));
      // 우회 경로 "!" 마커 새로 생성
      console.log('[selectRoute] detour - setVisibleRiskMarkers 호출');
      createRiskMarkers(detourRiskSectionsRef.current, 'detour-overlay');
    }
  }, [createRiskMarkers]);

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
      <div ref={mapRef} className="map-container" />

      {/* React state로 관리하는 "!" 위험 마커 — map-layout 기준 absolute */}
      {mapInstanceRef.current && window.kakao?.maps && visibleRiskMarkers.map((marker) => {
        try {
          const proj = mapInstanceRef.current.getProjection();
          const pt = proj.containerPointFromCoords(
            new window.kakao.maps.LatLng(marker.lat, marker.lng)
          );
          const key = `${marker.lat}-${marker.lng}`;
          const levelClass = marker.riskLevel === 'VERY_HIGH' || marker.riskLevel === 'HIGH' ? 'high'
            : marker.riskLevel === 'MEDIUM' ? 'medium' : 'low';
          console.log('DOM risk markers:', document.querySelectorAll('.risk-overlay-marker').length, 'state:', visibleRiskMarkers.length);
          return (
            <div
              key={key}
              className="risk-overlay-marker"
              style={{ position: 'absolute', left: pt.x, top: pt.y, transform: 'translate(-50%, -130%)', zIndex: 200, cursor: 'pointer', pointerEvents: 'auto' }}
              onClick={(e) => { e.stopPropagation(); setRiskInfoWindow(riskInfoWindow?.key === key ? null : { ...marker, key }); }}
            >
              <div className={`risk-overlay-icon ${levelClass}`}>!</div>
            </div>
          );
        } catch (_) { return null; }
      })}

      {/* 클릭한 "!" 마커 InfoWindow */}
      {riskInfoWindow && mapInstanceRef.current && window.kakao?.maps && (() => {
        try {
          const proj = mapInstanceRef.current.getProjection();
          const pt = proj.containerPointFromCoords(
            new window.kakao.maps.LatLng(riskInfoWindow.lat, riskInfoWindow.lng)
          );
          const levelClass = riskInfoWindow.riskLevel === 'VERY_HIGH' || riskInfoWindow.riskLevel === 'HIGH' ? 'high'
            : riskInfoWindow.riskLevel === 'MEDIUM' ? 'medium' : 'low';
          const levelLabel = riskInfoWindow.riskLevel === 'VERY_HIGH' || riskInfoWindow.riskLevel === 'HIGH' ? '위험'
            : riskInfoWindow.riskLevel === 'MEDIUM' ? '주의' : '양호';
          return (
            <div
              className="risk-info-window"
              style={{ position: 'absolute', left: pt.x, top: pt.y, transform: 'translate(-50%, -200%)', zIndex: 300, pointerEvents: 'auto' }}
            >
              <div className="risk-info-header">
                <span className="risk-info-name">{riskInfoWindow.name || '위험구간'}</span>
                <button className="risk-info-close" onClick={() => setRiskInfoWindow(null)}>&times;</button>
              </div>
              <div className="risk-info-body">
                <div className="risk-info-row"><span>경사도</span><strong>{riskInfoWindow.grade ? riskInfoWindow.grade.toFixed(1) + '%' : '정보없음'}</strong></div>
                <div className="risk-info-row"><span>위험등급</span><span className={`risk-badge ${levelClass}`}>{levelLabel}</span></div>
                <div className="risk-info-row"><span>경로 이격</span><strong>{riskInfoWindow.distanceFromRoute ? riskInfoWindow.distanceFromRoute.toFixed(1) + 'm' : '-'}</strong></div>
              </div>
            </div>
          );
        } catch (_) { return null; }
      })()}

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

      {/* 날씨 미니 오버레이 */}
      {mapWeather && <WeatherMiniOverlay weather={mapWeather} />}

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

        {/* 탭 콘텐츠 — display로 전환 (unmount 방지, state 유지) */}
        <div className="tab-content">
          <div style={{ display: activeTab === 'route' ? '' : 'none' }}>
            <RoutePanel kakaoReady={kakaoReady} onRouteFound={drawRoute} onDetourFound={drawDetourRoute} onSelectRoute={selectRoute} defaultOrigin={defaultOrigin} />
          </div>
          <div style={{ display: activeTab === 'uphill' ? '' : 'none' }}>
            <UphillPanel onItemClick={moveToLocation} />
          </div>
          <div style={{ display: activeTab === 'safety' ? '' : 'none' }}>
            <SafetyPanel onToggleLayer={showLayerMarkers} onClearLayer={clearLayerMarkers} />
          </div>
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

const toKorError = (err) => {
  if (!err?.message || err.message === 'Failed to fetch' || err instanceof TypeError) {
    return '서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.';
  }
  return err.message;
};

function RoutePanel({ kakaoReady, onRouteFound, onDetourFound, onSelectRoute, defaultOrigin }) {
  const [originText, setOriginText] = useState('');
  const [destText, setDestText] = useState('');
  const [originPlace, setOriginPlace] = useState(null);
  const [destPlace, setDestPlace] = useState(null);
  const [routeOption, setRouteOption] = useState('SAFE');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [routeInfo, setRouteInfo] = useState(null);
  const [detourInfo, setDetourInfo] = useState(null);
  const [detourLoading, setDetourLoading] = useState(false);
  const [detourError, setDetourError] = useState('');
  const [selectedRoute, setSelectedRoute] = useState(null); // 'original' | 'detour' | null
  const routeDataRef = useRef(null); // 원래 경로 전체 데이터 저장
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
    if (!originPlace) { setError('출발지를 검색하여 선택해주세요.'); return; }
    if (!destPlace) { setError('도착지를 검색하여 선택해주세요.'); return; }

    setError('');
    setLoading(true);
    setRouteInfo(null);
    setDetourInfo(null);
    setDetourError('');
    setSelectedRoute(null);

    try {
      const res = await fetch(`${API_BASE}/route/walk`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          originLng: originPlace.lng, originLat: originPlace.lat,
          destLng: destPlace.lng, destLat: destPlace.lat,
          option: routeOption,
        }),
      });

      if (!res.ok) throw new Error('경로를 찾을 수 없습니다.');
      const data = await res.json();

      routeDataRef.current = data;
      setRouteInfo({
        distance: data.totalDistance,
        duration: data.totalDuration,
        riskSections: data.riskSections || [],
        overallRisk: data.overallRisk || 'SAFE',
        weather: data.weather || null,
      });

      console.log('위험구간 데이터:', data.riskSections);

      if (data.coords && data.coords.length > 0) {
        onRouteFound(data.coords, data.riskSections || []);
      }

      // 안전 우선 모드 + 위험구간 있으면 자동으로 우회 경로도 검색
      if (routeOption === 'SAFE' && data.riskSections && data.riskSections.length > 0) {
        searchDetour(data);
      }
    } catch (err) {
      setError(toKorError(err));
    } finally {
      setLoading(false);
    }
  };

  const searchDetour = async (originalData) => {
    const data = originalData || routeDataRef.current;
    if (!data || !data.riskSections || data.riskSections.length === 0) return;

    setDetourLoading(true);
    setDetourError('');
    setDetourInfo(null);

    try {
      const res = await fetch(`${API_BASE}/route/walk/detour`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          originLng: originPlace.lng, originLat: originPlace.lat,
          destLng: destPlace.lng, destLat: destPlace.lat,
          option: routeOption,
          avoidPoints: data.riskSections.map((rs) => ({
            lat: rs.lat, lng: rs.lng,
            riskLevel: rs.riskLevel,
            nearestRouteIdx: rs.nearestRouteIdx,
          })),
        }),
      });

      if (!res.ok) throw new Error('우회 경로를 찾을 수 없습니다.');
      const detour = await res.json();

      setDetourInfo({
        distance: detour.totalDistance,
        duration: detour.totalDuration,
        riskSections: detour.riskSections || [],
        overallRisk: detour.overallRisk || 'SAFE',
        coords: detour.coords,
      });

      if (detour.coords && detour.coords.length > 0) {
        onDetourFound(detour.coords, detour.riskSections || []);
      }
    } catch (err) {
      console.error('우회 경로 검색 실패:', err);
      setDetourError(toKorError(err));
    } finally {
      setDetourLoading(false);
    }
  };

  const handleSelectRoute = (type) => {
    setSelectedRoute(type);
    onSelectRoute(type);
  };

  const riskBadge = (risk) => {
    const cls = risk === 'DANGER' ? 'danger' : risk === 'CAUTION' ? 'caution' : 'safe';
    const lbl = risk === 'DANGER' ? '위험' : risk === 'CAUTION' ? '주의' : '안전';
    return <span className={`overall-risk-badge ${cls}`}>{lbl}</span>;
  };

  return (
    <div className="content-section">
      <h3 className="section-title">경로 찾기</h3>
      <div className="route-inputs">
        <PlaceSearchInput
          placeholder="출발지 입력" value={originText}
          onChange={(v) => setOriginText(v)} onClear={() => setOriginPlace(null)}
          onSelect={setOriginPlace} dotClass="start" kakaoReady={kakaoReady}
        />
        <div className="route-input-divider" />
        <PlaceSearchInput
          placeholder="도착지 입력" value={destText}
          onChange={(v) => setDestText(v)} onClear={() => setDestPlace(null)}
          onSelect={setDestPlace} dotClass="end" kakaoReady={kakaoReady}
        />
      </div>

      {error && <p className="route-error">{error}</p>}

      <button className="primary-btn" onClick={handleSearch} disabled={loading}>
        {loading ? <span className="btn-loading"><span className="btn-spinner" />검색 중...</span> : '경로 검색'}
      </button>

      {routeInfo && (
        <div className="route-result-section">
          {/* 날씨 정보 */}
          {routeInfo.weather && <WeatherCard weather={routeInfo.weather} />}

          {/* 경로 비교 패널 (우회 경로가 있을 때) */}
          {detourInfo ? (
            <div className="route-compare">
              <div className={`route-compare-card original${selectedRoute === 'original' ? ' selected' : ''}`}>
                <div className="route-compare-header">
                  <span className="route-compare-label">원래 경로</span>
                  {riskBadge(routeInfo.overallRisk)}
                </div>
                <div className="route-compare-stats">
                  <span>{(routeInfo.distance / 1000).toFixed(1)}km</span>
                  <span>{Math.round(routeInfo.duration / 60)}분</span>
                </div>
                <div className="route-compare-risk">
                  위험구간 {routeInfo.riskSections.length}개
                </div>
                <button className="route-select-btn" onClick={() => handleSelectRoute('original')}>
                  {selectedRoute === 'original' ? '선택됨' : '이 경로 선택'}
                </button>
              </div>
              <div className={`route-compare-card detour${selectedRoute === 'detour' ? ' selected' : ''}`}>
                <div className="route-compare-header">
                  <span className="route-compare-label">우회 경로</span>
                  {riskBadge(detourInfo.overallRisk)}
                </div>
                <div className="route-compare-stats">
                  <span>{(detourInfo.distance / 1000).toFixed(1)}km</span>
                  <span>{Math.round(detourInfo.duration / 60)}분</span>
                </div>
                <div className="route-compare-risk">
                  {detourInfo.riskSections.length > 0
                    ? `위험구간 ${detourInfo.riskSections.length}개 (완전한 우회 어려움)`
                    : '위험구간 없음'
                  }
                </div>
                <button className="route-select-btn detour" onClick={() => handleSelectRoute('detour')}>
                  {selectedRoute === 'detour' ? '선택됨' : '이 경로 선택'}
                </button>
              </div>
            </div>
          ) : (
            <>
              {/* 단일 경로 정보 */}
              <div className="route-result-info">
                <span>거리: {(routeInfo.distance / 1000).toFixed(1)}km</span>
                <span>예상 시간: {Math.round(routeInfo.duration / 60)}분</span>
              </div>
              <div className="risk-summary">
                <div className="risk-summary-header">
                  {riskBadge(routeInfo.overallRisk)}
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
            </>
          )}

          {/* 우회 경로 버튼 */}
          {routeInfo.riskSections.length > 0 && !detourInfo && routeOption !== 'SHORT' && (
            <button
              className="detour-btn"
              onClick={() => searchDetour(null)}
              disabled={detourLoading}
            >
              {detourLoading ? '우회 경로 검색 중...' : '\uD83D\uDD04 우회 경로 추천'}
            </button>
          )}
          {detourError && <p className="route-error">{detourError}</p>}
          {detourInfo && detourInfo.riskSections.length > 0 && (
            <p className="detour-warning">완전한 우회가 어렵습니다. 주의하여 이동하세요.</p>
          )}
        </div>
      )}

    </div>
  );
}

const REGION_OPTIONS = [
  { label: '전체 지역', value: '' },
  { label: '성남시', value: '411' },
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
  const [regionCode, setRegionCode] = useState('411');
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
      setError(toKorError(err));
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

const WEATHER_ICONS = {
  clear: '\u2600\uFE0F',
  cloudy: '\u26C5',
  overcast: '\u2601\uFE0F',
  rain: '\uD83C\uDF27\uFE0F',
  snow: '\u2744\uFE0F',
  sleet: '\uD83C\uDF28\uFE0F',
};

function WeatherMiniOverlay({ weather }) {
  if (!weather) return null;
  const icon = WEATHER_ICONS[weather.icon] || '\u2600\uFE0F';
  const hasWarning = !!weather.warning;

  return (
    <div className={`weather-mini-overlay${hasWarning ? ' warning' : ''}`}>
      <span className="weather-mini-icon">{icon}</span>
      <span className="weather-mini-temp">{weather.temperature != null ? Math.round(weather.temperature) : '--'}°C</span>
      {hasWarning && <span className="weather-mini-warn">{weather.warning}</span>}
    </div>
  );
}

function WeatherCard({ weather }) {
  if (!weather) return null;
  const icon = WEATHER_ICONS[weather.icon] || '\u2600\uFE0F';

  return (
    <div className="weather-card">
      <div className="weather-main">
        <span className="weather-icon-large">{icon}</span>
        <div className="weather-info">
          <span className="weather-temp">{weather.temperature != null ? Math.round(weather.temperature) : '--'}°C</span>
          <span className="weather-desc">{weather.description || '--'}</span>
        </div>
        <div className="weather-detail">
          <span>습도 {weather.humidity ?? '--'}%</span>
          <span>풍속 {weather.windSpeed != null ? weather.windSpeed.toFixed(1) : '--'}m/s</span>
        </div>
      </div>
      {weather.warning && (
        <div className="weather-warning">
          <span className="warning-icon">⚠️</span>
          <span>{weather.warning}</span>
        </div>
      )}
    </div>
  );
}

function SafetyPanel({ onToggleLayer, onClearLayer }) {
  const [weather, setWeather] = useState(null);
  const [loading, setLoading] = useState(true);
  const [riskStats, setRiskStats] = useState({ high: 0, medium: 0, low: 0 });
  const [layers, setLayers] = useState({
    accidents: false, cctv: false,
    protectedZones: false, welfare: false, shelters: false, senior: false,
  });
  const [layerData, setLayerData] = useState({
    accidents: [], cctv: [],
    protectedZones: [], welfare: [], shelters: [], senior: [],
  });

  // 날씨 + 통계 로드
  useEffect(() => {
    const fetchWeather = async (lat, lng) => {
      try {
        const res = await fetch(`${API_BASE}/weather?lat=${lat}&lng=${lng}`);
        if (res.ok) setWeather(await res.json());
      } catch (err) {
        console.error('날씨 조회 실패:', err);
      } finally {
        setLoading(false);
      }
    };

    if (navigator.geolocation) {
      navigator.geolocation.getCurrentPosition(
        (pos) => fetchWeather(pos.coords.latitude, pos.coords.longitude),
        () => fetchWeather(DEFAULT_CENTER.lat, DEFAULT_CENTER.lng),
        { timeout: 3000 }
      );
    } else {
      fetchWeather(DEFAULT_CENTER.lat, DEFAULT_CENTER.lng);
    }

    // 위험도별 통계
    fetch(`${API_BASE}/steep-slope/risk-stats`)
      .then((r) => r.ok ? r.json() : null)
      .then((d) => { if (d) setRiskStats(d); })
      .catch(() => {});
  }, []);

  // 레이어 설정
  const LAYER_CONFIG = [
    { key: 'accidents', label: '사고다발지', emoji: '\u26A0\uFE0F', endpoint: '/safety/accident-zones' },
    { key: 'cctv', label: 'CCTV', emoji: '\uD83D\uDCF9', endpoint: '/safety/cctv' },
    { key: 'protectedZones', label: '교통약자 보호구역', emoji: '\uD83D\uDEB8', endpoint: '/safety/protected-zones' },
    { key: 'welfare', label: '장애인복지시설', emoji: '\u267F', endpoint: '/safety/welfare-facilities' },
    { key: 'shelters', label: '그늘막', emoji: '\u26F1\uFE0F', endpoint: '/safety/shade-shelters' },
    { key: 'senior', label: '노인복지관', emoji: '\uD83C\uDFE0', endpoint: '/safety/senior-centers' },
  ];

  // 레이어 토글 핸들러
  const handleToggle = async (type) => {
    const newState = !layers[type];
    setLayers((prev) => ({ ...prev, [type]: newState }));

    if (!newState) {
      onClearLayer(type);
      return;
    }

    // 데이터가 이미 있으면 재사용
    if (layerData[type] && layerData[type].length > 0) {
      const config = LAYER_CONFIG.find((c) => c.key === type);
      onToggleLayer(type, layerData[type], config ? config.emoji : '');
      return;
    }

    // 데이터 fetch
    const config = LAYER_CONFIG.find((c) => c.key === type);
    if (!config) return;

    try {
      console.log(`[${type}] API 호출: ${API_BASE}${config.endpoint}`);
      const res = await fetch(`${API_BASE}${config.endpoint}`);
      if (res.ok) {
        const data = await res.json();
        console.log(`[${type}] API 응답:`, data.length, '건', data.slice(0, 2));
        setLayerData((prev) => ({ ...prev, [type]: data }));
        onToggleLayer(type, data, config.emoji);
      } else {
        console.error(`[${type}] API 오류: HTTP ${res.status}`);
      }
    } catch (err) {
      console.error(`[${type}] 데이터 로드 실패:`, err);
    }
  };

  const handleAllOn = () => {
    LAYER_CONFIG.forEach(({ key }) => {
      if (!layers[key]) handleToggle(key);
    });
  };

  const handleAllOff = () => {
    LAYER_CONFIG.forEach(({ key }) => {
      if (layers[key]) onClearLayer(key);
    });
    setLayers({ accidents: false, cctv: false, protectedZones: false, welfare: false, shelters: false, senior: false });
  };

  const getWeatherAlerts = () => {
    if (!weather) return [];
    const alerts = [];

    if (weather.warning) {
      alerts.push({ msg: weather.warning, time: '방금 전', level: 'high' });
    }
    if (weather.temperature != null && weather.temperature <= 3 && weather.temperature > 0) {
      alerts.push({ msg: '낮은 기온, 결빙 가능성 있음', time: '방금 전', level: 'medium' });
    }
    if (weather.humidity != null && weather.humidity >= 90) {
      alerts.push({ msg: '높은 습도, 노면 습기 주의', time: '방금 전', level: 'medium' });
    }
    if (weather.windSpeed != null && weather.windSpeed >= 8 && !(weather.windSpeed >= 10)) {
      alerts.push({ msg: '다소 강한 바람, 노출 구간 주의', time: '방금 전', level: 'medium' });
    }
    if (alerts.length === 0) {
      alerts.push({ msg: '현재 특이 기상 없음, 안전한 보행 가능', time: '방금 전', level: 'low' });
    }
    return alerts;
  };

  return (
    <div className="content-section">
      <h3 className="section-title">안전 정보</h3>
      <p className="section-desc">실시간 도로 안전 현황입니다.</p>

      {/* 날씨 카드 */}
      {loading ? (
        <p className="loading-text">날씨 정보 로딩 중...</p>
      ) : (
        <WeatherCard weather={weather} />
      )}

      {/* 위험도 통계 (실제 DB 데이터) */}
      <div className="safety-stats">
        <div className="stat-card red">
          <span className="stat-number">{riskStats.high}</span>
          <span className="stat-label">위험 구간</span>
        </div>
        <div className="stat-card yellow">
          <span className="stat-number">{riskStats.medium}</span>
          <span className="stat-label">주의 구간</span>
        </div>
        <div className="stat-card green">
          <span className="stat-number">{riskStats.low}</span>
          <span className="stat-label">안전 구간</span>
        </div>
      </div>

      {/* 기상 알림 (실제 날씨 기반) */}
      <div className="notice-list">
        <p className="option-label">기상 알림</p>
        {getWeatherAlerts().map((item, i) => (
          <div key={i} className="notice-item">
            <span className={`notice-dot ${item.level}`} />
            <div>
              <p className="notice-msg">{item.msg}</p>
              <p className="notice-time">{item.time}</p>
            </div>
          </div>
        ))}
      </div>

      {/* 지도 레이어 토글 */}
      <div className="layer-toggles">
        <div className="layer-toggles-header">
          <p className="option-label">지도 레이어</p>
          <div className="layer-all-btns">
            <button className="layer-all-btn" onClick={handleAllOn}>전체 켜기</button>
            <button className="layer-all-btn off" onClick={handleAllOff}>전체 끄기</button>
          </div>
        </div>
        <div className="layer-toggle-list">
          {LAYER_CONFIG.map(({ key, label, emoji }) => (
            <button
              key={key}
              className={`layer-toggle-btn${layers[key] ? ' active' : ''}`}
              onClick={() => handleToggle(key)}
            >
              <span className="layer-toggle-icon">{emoji}</span>
              <span>{label}</span>
              <span className={`layer-toggle-badge${layers[key] ? ' on' : ''}`}>{layers[key] ? 'ON' : 'OFF'}</span>
            </button>
          ))}
        </div>
      </div>
    </div>
  );
}

export default MapPage;
