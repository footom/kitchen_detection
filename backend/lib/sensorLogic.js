'use strict';

// 廚房氣體與煙霧安全門檻值 (PPM)
const THRESHOLDS = {
  lpg: Number(process.env.LPG_ALERT_THRESHOLD || 180),   // 液化石油氣 / 瓦斯
  co: Number(process.env.CO_ALERT_THRESHOLD || 150),       // 一氧化碳
  smoke: Number(process.env.SMOKE_ALERT_THRESHOLD || 400) // 煙霧
};

// 警報冷卻時間設定（單位：秒）
const COOLDOWN_SECONDS = {
  lpg: Number(process.env.LPG_COOLDOWN_SECONDS || 60),     // 瓦斯外洩屬高危險，1分鐘冷卻
  co: Number(process.env.CO_COOLDOWN_SECONDS || 180),      // 一氧化碳3分鐘冷卻
  smoke: Number(process.env.SMOKE_COOLDOWN_SECONDS || 300) // 廚房油煙較常發生，5分鐘冷卻
};

function numberOrNull(value) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

function toTaipeiString(value) {
  if (!value) return value;
  // 如果資料庫傳出來的已經是字串，直接回傳，不重新用Date物件轉換
  if (typeof value === 'string') return value;

  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return value;

  // 原生格式化，不疊加時區
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

function withTaipeiTime(row) {
  if (!row) return row;
  return { ...row, created_at: toTaipeiString(row.created_at) };
}

function normalizeSensorPayload(payload) {
  const values = {
    device_id: String(payload.device_id || 'ESP8266_Kitchen').trim(),
    humidity: numberOrNull(payload.humidity),
    temperature: numberOrNull(payload.temperature),
    lpg: numberOrNull(payload.lpg),
    co: numberOrNull(payload.co),
    smoke: numberOrNull(payload.smoke),
    fire: Number(payload.fire) === 1 ? 1 : 0
  };
  if (Object.entries(values).some(([key, value]) => key !== 'fire' && key !== 'device_id' && value === null)) {
    throw new Error('Sensor payload must contain numeric humidity, temperature, lpg, co, and smoke values.');
  }
  return values;
}

// 依門檻值計算目前的安全狀態（給/api/sensors/latest使用）
function evaluateSafetyStatus(data, thresholds = THRESHOLDS) {
  return {
    isCooking: data.fire === 1,
    lpgWarning: data.lpg > thresholds.lpg,
    coWarning: data.co > thresholds.co,
    smokeWarning: data.smoke > thresholds.smoke
  };
}

// 依優先順序（LPG > CO > 煙霧）決定這筆資料該不該觸發警報，以及警報內容。
// 與 storeSensorData 原本的判斷順序保持一致。
function pickAlert(data, thresholds = THRESHOLDS) {
  if (data.lpg > thresholds.lpg) {
    const statusText = data.fire === 1 ? '（爐火使用中）' : '（爐火未開啟，疑瓦斯外洩！）';
    return {
      type: 'lpg',
      title: '⚠️ 瓦斯外洩警報 (LPG High)',
      body: `LPG濃度: ${data.lpg} ppm ${statusText}`
    };
  }
  if (data.co > thresholds.co) {
    return {
      type: 'co',
      title: '⚠️ 一氧化碳濃度過高 (CO Warning)',
      body: `CO 濃度: ${data.co} ppm，請注意廚房通風！`
    };
  }
  if (data.smoke > thresholds.smoke) {
    const statusText = data.fire === 1 ? '（煙霧過大）' : '（疑似設備殘留煙霧）';
    return {
      type: 'smoke',
      title: '⚠️ 煙霧異常 (Smoke Alert)',
      body: `煙霧濃度: ${data.smoke} ppm ${statusText}`
    };
  }
  return null;
}

// 帶冷卻時間的警報「是否該發送」判斷，跟實際發送 FCM 的動作分開，
// 這樣測試時不需要真的接 Firebase，只要驗證冷卻邏輯本身對不對即可。
function createAlertGate(cooldownSeconds = COOLDOWN_SECONDS) {
  const lastAlertTimes = { lpg: 0, co: 0, smoke: 0 };

  return {
    // now 預設用 Date.now()，但測試時可以直接傳入固定時間戳，
    // 不需要依賴 jest 的假時鐘。
    shouldSend(type, now = Date.now()) {
      const cooldownMs = (cooldownSeconds[type] || 300) * 1000;
      const lastTime = lastAlertTimes[type] || 0;
      if (now - lastTime < cooldownMs) {
        return false;
      }
      lastAlertTimes[type] = now;
      return true;
    },
    reset() {
      lastAlertTimes.lpg = 0;
      lastAlertTimes.co = 0;
      lastAlertTimes.smoke = 0;
    }
  };
}

module.exports = {
  THRESHOLDS,
  COOLDOWN_SECONDS,
  numberOrNull,
  toTaipeiString,
  withTaipeiTime,
  normalizeSensorPayload,
  evaluateSafetyStatus,
  pickAlert,
  createAlertGate
};
