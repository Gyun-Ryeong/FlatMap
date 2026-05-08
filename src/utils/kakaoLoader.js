let kakaoPromise = null;

export function loadKakaoSDK() {
  if (kakaoPromise) return kakaoPromise;

  kakaoPromise = new Promise((resolve, reject) => {
    if (window.kakao?.maps) {
      resolve(window.kakao);
      return;
    }

    const script = document.createElement('script');
    script.src = `https://dapi.kakao.com/v2/maps/sdk.js?appkey=${process.env.REACT_APP_KAKAO_API_KEY}&autoload=false&libraries=services,clusterer`;
    script.async = true;

    script.onload = () => {
      window.kakao.maps.load(() => {
        resolve(window.kakao);
      });
    };

    script.onerror = () => {
      kakaoPromise = null;
      reject(new Error('Kakao Maps SDK 로드 실패'));
    };

    document.head.appendChild(script);
  });

  return kakaoPromise;
}
