'use strict';

const {
  numberOrNull,
  toTaipeiString,
  withTaipeiTime,
  normalizeSensorPayload,
  evaluateSafetyStatus,
  pickAlert,
  createAlertGate
} = require('./sensorLogic');

describe('numberOrNull', () => {
  test('回傳數字型別的數值', () => {
    expect(numberOrNull(42)).toBe(42);
    expect(numberOrNull('12.5')).toBe(12.5);
  });

  test('無法轉成有效數字的字串或 undefined 回傳 null', () => {
    expect(numberOrNull('abc')).toBeNull();
    expect(numberOrNull(undefined)).toBeNull();
  });

  // 注意：Number(null) === 0，屬於JS本身的行為，不是這支函式的bug，
  // 但這代表 normalizeSensorPayload 沒辦法用這個函式分辨欄位是0
  // 還是欄位根本沒帶，寫這條測試就是為了讓這個邊界情況被記錄下來。
  test('null會被視為數字0，而不是無效值（JS行為，非本函式判斷）', () => {
    expect(numberOrNull(null)).toBe(0);
  });
});

describe('toTaipeiString', () => {
  test('字串直接原樣回傳，不重新解析', () => {
    expect(toTaipeiString('2026-01-01 08:00:00')).toBe('2026-01-01 08:00:00');
  });

  test('falsy值直接回傳', () => {
    expect(toTaipeiString(null)).toBeNull();
    expect(toTaipeiString(undefined)).toBeUndefined();
    expect(toTaipeiString('')).toBe('');
  });

  test('Date物件會被格式化成 YYYY-MM-DD HH:mm:ss', () => {
    const d = new Date(2026, 0, 5, 9, 3, 7); // 本地時間 2026-01-05 09:03:07
    expect(toTaipeiString(d)).toBe('2026-01-05 09:03:07');
  });

  test('無效日期時原樣回傳', () => {
    expect(toTaipeiString(new Date('not-a-date'))).toBeInstanceOf(Date);
  });
});

describe('withTaipeiTime', () => {
  test('null/undefined 直接回傳', () => {
    expect(withTaipeiTime(null)).toBeNull();
  });

  test('只轉換created_at欄位，其餘欄位不動', () => {
    const row = { id: 1, lpg: 50, created_at: '2026-01-01 08:00:00' };
    expect(withTaipeiTime(row)).toEqual(row);
  });
});

describe('normalizeSensorPayload', () => {
  test('合法資料會回傳正規化後的物件', () => {
    const result = normalizeSensorPayload({
      device_id: ' ESP8266_Kitchen ',
      humidity: '55.5',
      temperature: '26',
      lpg: '10',
      co: '5',
      smoke: '3',
      fire: '1'
    });
    expect(result).toEqual({
      device_id: 'ESP8266_Kitchen',
      humidity: 55.5,
      temperature: 26,
      lpg: 10,
      co: 5,
      smoke: 3,
      fire: 1
    });
  });

  test('缺少device_id時使用預設值', () => {
    const result = normalizeSensorPayload({ humidity: 1, temperature: 1, lpg: 1, co: 1, smoke: 1 });
    expect(result.device_id).toBe('ESP8266_Kitchen');
  });

  test('fire非1時一律視為 0', () => {
    const base = { humidity: 1, temperature: 1, lpg: 1, co: 1, smoke: 1 };
    expect(normalizeSensorPayload({ ...base, fire: 0 }).fire).toBe(0);
    expect(normalizeSensorPayload({ ...base, fire: undefined }).fire).toBe(0);
    expect(normalizeSensorPayload({ ...base, fire: 'yes' }).fire).toBe(0);
  });

  test('缺少必要的數值欄位時拋出錯誤', () => {
    expect(() => normalizeSensorPayload({ humidity: 1, temperature: 1, lpg: 1, co: 1 }))
      .toThrow('Sensor payload must contain numeric humidity, temperature, lpg, co, and smoke values.');
  });

  test('數值欄位是無法轉換的字串時拋出錯誤', () => {
    expect(() => normalizeSensorPayload({ humidity: 'n/a', temperature: 1, lpg: 1, co: 1, smoke: 1 }))
      .toThrow();
  });
});

describe('evaluateSafetyStatus', () => {
  const thresholds = { lpg: 180, co: 150, smoke: 400 };

  test('所有數值都在門檻內時沒有警告', () => {
    const status = evaluateSafetyStatus({ fire: 0, lpg: 10, co: 10, smoke: 10 }, thresholds);
    expect(status).toEqual({ isCooking: false, lpgWarning: false, coWarning: false, smokeWarning: false });
  });

  test('個別數值超過門檻時各自標記警告', () => {
    expect(evaluateSafetyStatus({ fire: 0, lpg: 200, co: 10, smoke: 10 }, thresholds).lpgWarning).toBe(true);
    expect(evaluateSafetyStatus({ fire: 0, lpg: 10, co: 200, smoke: 10 }, thresholds).coWarning).toBe(true);
    expect(evaluateSafetyStatus({ fire: 0, lpg: 10, co: 10, smoke: 500 }, thresholds).smokeWarning).toBe(true);
  });

  test('fire為1時isCooking為true', () => {
    expect(evaluateSafetyStatus({ fire: 1, lpg: 0, co: 0, smoke: 0 }, thresholds).isCooking).toBe(true);
  });

  test('剛好等於門檻值不算超標（use > 而非 >=）', () => {
    const status = evaluateSafetyStatus({ fire: 0, lpg: 180, co: 150, smoke: 400 }, thresholds);
    expect(status).toEqual({ isCooking: false, lpgWarning: false, coWarning: false, smokeWarning: false });
  });
});

describe('pickAlert', () => {
  const thresholds = { lpg: 180, co: 150, smoke: 400 };

  test('都沒超標時回傳null', () => {
    expect(pickAlert({ fire: 0, lpg: 10, co: 10, smoke: 10 }, thresholds)).toBeNull();
  });

  test('LPG優先於CO與煙霧', () => {
    const alert = pickAlert({ fire: 0, lpg: 200, co: 200, smoke: 500 }, thresholds);
    expect(alert.type).toBe('lpg');
  });

  test('沒有LPG警報時才輪到 CO', () => {
    const alert = pickAlert({ fire: 0, lpg: 10, co: 200, smoke: 500 }, thresholds);
    expect(alert.type).toBe('co');
  });

  test('只有煙霧超標時回傳煙霧警報', () => {
    const alert = pickAlert({ fire: 0, lpg: 10, co: 10, smoke: 500 }, thresholds);
    expect(alert.type).toBe('smoke');
  });

  test('LPG 警報文字會依fire狀態不同而不同', () => {
    const cooking = pickAlert({ fire: 1, lpg: 200, co: 0, smoke: 0 }, thresholds);
    const notCooking = pickAlert({ fire: 0, lpg: 200, co: 0, smoke: 0 }, thresholds);
    expect(cooking.body).toContain('爐火使用中');
    expect(notCooking.body).toContain('疑瓦斯外洩');
  });
});

describe('createAlertGate', () => {
  const cooldownSeconds = { lpg: 60, co: 180, smoke: 300 };

  test('第一次觸發一定允許發送', () => {
    const gate = createAlertGate(cooldownSeconds);
    expect(gate.shouldSend('lpg', 1_000_000)).toBe(true);
  });

  test('冷卻時間內的第二次觸發會被抑制', () => {
    const gate = createAlertGate(cooldownSeconds);
    const t0 = 1_000_000;
    expect(gate.shouldSend('lpg', t0)).toBe(true);
    // 30 秒後，還在 60 秒冷卻內
    expect(gate.shouldSend('lpg', t0 + 30_000)).toBe(false);
  });

  test('超過冷卻時間後恢復可發送，並更新上次發送時間', () => {
    const gate = createAlertGate(cooldownSeconds);
    const t0 = 1_000_000;
    expect(gate.shouldSend('lpg', t0)).toBe(true);
    expect(gate.shouldSend('lpg', t0 + 61_000)).toBe(true);
    // 剛更新過時間，馬上再打一次應該又被抑制
    expect(gate.shouldSend('lpg', t0 + 61_500)).toBe(false);
  });

  test('不同警報類型的冷卻互不影響', () => {
    const gate = createAlertGate(cooldownSeconds);
    const t0 = 1_000_000;
    expect(gate.shouldSend('lpg', t0)).toBe(true);
    expect(gate.shouldSend('co', t0)).toBe(true);
    expect(gate.shouldSend('smoke', t0)).toBe(true);
  });

  test('reset() 會清空所有類型的冷卻紀錄', () => {
    const gate = createAlertGate(cooldownSeconds);
    const t0 = 1_000_000;
    gate.shouldSend('lpg', t0);
    gate.reset();
    expect(gate.shouldSend('lpg', t0 + 1_000)).toBe(true);
  });
});
