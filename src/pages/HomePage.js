import React from 'react';
import { useNavigate } from 'react-router-dom';

function HomePage() {
  const navigate = useNavigate();

  return (
    <div style={{ padding: '2rem', textAlign: 'center' }}>
      <h1>경기도 오르막길 안전 네비게이션</h1>
      <p>오르막길 구간의 안전 정보를 확인하세요.</p>
      <button onClick={() => navigate('/map')} style={{ marginTop: '1rem', padding: '0.5rem 1.5rem' }}>
        지도 보기
      </button>
    </div>
  );
}

export default HomePage;
