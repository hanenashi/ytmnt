(() => {
  window.addEventListener('ytmnt-native-ad-tap', (event) => {
    try {
      const detail = typeof event.detail === 'string'
        ? JSON.parse(event.detail)
        : event.detail;
      if (!detail || !Number.isFinite(detail.x) || !Number.isFinite(detail.y)) return;
      browser.runtime.sendMessage({ type: 'ad-tap', ...detail });
    } catch (_) {}
  });
})();
