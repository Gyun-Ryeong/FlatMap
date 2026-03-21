import React from 'react';
import { useParams } from 'react-router-dom';

function RouteDetailPage() {
  const { id } = useParams();

  return (
    <div style={{ padding: '2rem' }}>
      <h2>경로 상세 정보</h2>
      <p>경로 ID: {id}</p>
    </div>
  );
}

export default RouteDetailPage;
