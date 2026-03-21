import React, { useEffect, useRef, useState } from 'react';
import './MapPage.css';

const TABS = [
  { id: 'route', label: '경로찾기', icon: '🗺️' },
  { id: 'uphill', label: '오르막길', icon: '⛰️' },
  { id: 'safety', label: '안전정보', icon: '⚠️' },
];

function MapPage() {
  const mapRef = useRef(null);
  const mapInstanceRef = useRef(null);
  const [activeTab, setActiveTab] = useState('route');
  const [panelOpen, setPanelOpen] = useState(true);

  useEffect(() => {
    if (!window.naver || mapInstanceRef.current) return;

    mapInstanceRef.current = new window.naver.maps.Map(mapRef.current, {
      center: new window.naver.maps.LatLng(37.4138, 127.5183),
      zoom: 10,
    });

    return () => {
      if (mapInstanceRef.current) {
        mapInstanceRef.current.destroy();
        mapInstanceRef.current = null;
      }
    };
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
          {activeTab === 'route' && <RoutePanel />}
          {activeTab === 'uphill' && <UphillPanel />}
          {activeTab === 'safety' && <SafetyPanel />}
        </div>
      </div>
    </div>
  );
}

function RoutePanel() {
  return (
    <div className="content-section">
      <h3 className="section-title">경로 찾기</h3>
      <div className="route-inputs">
        <div className="route-input-row">
          <span className="route-dot start" />
          <input type="text" placeholder="출발지 입력" className="route-input" />
        </div>
        <div className="route-input-divider" />
        <div className="route-input-row">
          <span className="route-dot end" />
          <input type="text" placeholder="도착지 입력" className="route-input" />
        </div>
      </div>
      <button className="primary-btn">경로 검색</button>

      <div className="option-group">
        <p className="option-label">경로 옵션</p>
        <div className="option-chips">
          <button className="chip active">안전 우선</button>
          <button className="chip">최단 거리</button>
          <button className="chip">오르막 최소</button>
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
