'use strict';

// 把 mysql2 和 mqtt 都換成假的，測試才不會真的去連資料庫或 MQTT broker。
jest.mock('mysql2/promise', () => ({
  createPool: jest.fn()
}));
jest.mock('mqtt', () => ({
  connect: jest.fn(() => ({ on: jest.fn(), subscribe: jest.fn() }))
}));

const mysql = require('mysql2/promise');
const request = require('supertest');

function buildPoolMock() {
  return { query: jest.fn(), execute: jest.fn() };
}

let pool;
let app;

beforeAll(() => {
  // 強制走「沒有設定 Firebase」的分支，測試才不會依賴機器上到底有沒有
  // serviceAccountKey.json，也不會用到裡面的真實憑證。
  process.env.FIREBASE_SERVICE_ACCOUNT_PATH = '/tmp/does-not-exist.json';
  process.env.MAX_USER_LIMIT = '5';

  // app.js 一開始就會執行 dotenv.config()，如果本機的 .env 剛好也設定了
  // 這幾個門檻值，會蓋掉程式碼裡的預設值，導致測試結果隨著本機環境不同
  // 而變動。這裡先明確指定成跟 sensorLogic.js 預設值一致，讓測試在任何
  // 機器（包含 CI）上都得到一樣的結果。dotenv 預設不會覆蓋已存在的
  // process.env 變數，所以只要在 require('../app') 之前設定好就有效。
  process.env.LPG_ALERT_THRESHOLD = '180';
  process.env.CO_ALERT_THRESHOLD = '150';
  process.env.SMOKE_ALERT_THRESHOLD = '400';

  pool = buildPoolMock();
  mysql.createPool.mockReturnValue(pool);

  // app.js 在 require 當下就會建立 dbPool / mqtt 連線，所以要先把 mock 準備好
  // 再 require，之後同一個 app 實例可以重複用在每一個測試裡。
  app = require('../app');
});

beforeEach(() => {
  pool.query.mockReset();
  pool.execute.mockReset();
});

describe('GET /health', () => {
  test('資料庫正常時回傳 200', async () => {
    pool.query.mockResolvedValueOnce([[{ 1: 1 }]]);
    const res = await request(app).get('/health');
    expect(res.status).toBe(200);
    expect(res.body).toEqual({ status: 'ok' });
  });

  test('資料庫連線失敗時回傳 503', async () => {
    pool.query.mockRejectedValueOnce(new Error('connection refused'));
    const res = await request(app).get('/health');
    expect(res.status).toBe(503);
    expect(res.body.status).toBe('error');
  });
});

describe('GET /api/sensors/latest', () => {
  test('沒有任何資料時回傳 404', async () => {
    pool.query.mockResolvedValueOnce([[]]);
    const res = await request(app).get('/api/sensors/latest');
    expect(res.status).toBe(404);
  });

  test('有資料時回傳資料與安全狀態', async () => {
    pool.query.mockResolvedValueOnce([[
      { id: 1, device_id: 'ESP8266_Kitchen', lpg: 200, co: 10, smoke: 10, fire: 0, created_at: '2026-01-01 08:00:00' }
    ]]);
    const res = await request(app).get('/api/sensors/latest');
    expect(res.status).toBe(200);
    expect(res.body.status).toBe('success');
    expect(res.body.safetyStatus).toEqual({
      isCooking: false,
      lpgWarning: true,
      coWarning: false,
      smokeWarning: false
    });
  });

  test('帶 device_id 查詢參數時，SQL 會加上對應的 WHERE 條件', async () => {
    pool.query.mockResolvedValueOnce([[
      { id: 1, device_id: 'Living_Room', lpg: 1, co: 1, smoke: 1, fire: 0, created_at: '2026-01-01 08:00:00' }
    ]]);
    await request(app).get('/api/sensors/latest?device_id=Living_Room');
    const [sql, params] = pool.query.mock.calls[0];
    expect(sql).toMatch(/WHERE device_id = \?/);
    expect(params).toEqual(['Living_Room']);
  });
});

describe('GET /api/sensors/history', () => {
  test('start 格式錯誤時回傳 400', async () => {
    const res = await request(app).get('/api/sensors/history?start=2026/01/01');
    expect(res.status).toBe(400);
    expect(pool.execute).not.toHaveBeenCalled();
  });

  test('end 格式錯誤時回傳 400', async () => {
    const res = await request(app).get('/api/sensors/history?end=not-a-date');
    expect(res.status).toBe(400);
  });

  test('limit 超過上限時會被夾到 200', async () => {
    pool.execute.mockResolvedValueOnce([[]]);
    await request(app).get('/api/sensors/history?limit=9999');
    const [, params] = pool.execute.mock.calls[0];
    expect(params[params.length - 1]).toBe(200);
  });

  test('正常查詢會回傳資料陣列', async () => {
    pool.execute.mockResolvedValueOnce([[
      { id: 1, device_id: 'ESP8266_Kitchen', created_at: '2026-01-01 08:00:00' }
    ]]);
    const res = await request(app).get('/api/sensors/history');
    expect(res.status).toBe(200);
    expect(res.body.data).toHaveLength(1);
  });
});

describe('POST /api/auth/register', () => {
  test('手機號碼格式錯誤時回傳 400', async () => {
    const res = await request(app).post('/api/auth/register').send({ phone_number: 'abc' });
    expect(res.status).toBe(400);
  });

  test('已達註冊人數上限時回傳 403', async () => {
    pool.query.mockResolvedValueOnce([[{ total: 5 }]]); // COUNT(*)
    const res = await request(app).post('/api/auth/register').send({ phone_number: '0912345678' });
    expect(res.status).toBe(403);
  });

  test('手機號碼已註冊過時回傳 400', async () => {
    pool.query
      .mockResolvedValueOnce([[{ total: 1 }]])       // COUNT(*)
      .mockResolvedValueOnce([[{ id: 1 }]]);          // 既有使用者查詢
    const res = await request(app).post('/api/auth/register').send({ phone_number: '0912345678' });
    expect(res.status).toBe(400);
  });

  test('成功註冊時回傳 201 並寫入資料庫', async () => {
    pool.query
      .mockResolvedValueOnce([[{ total: 1 }]])  // COUNT(*)
      .mockResolvedValueOnce([[]]);              // 尚未註冊
    pool.execute.mockResolvedValueOnce([{}]);
    const res = await request(app).post('/api/auth/register').send({ phone_number: '0912345678' });
    expect(res.status).toBe(201);
    expect(pool.execute).toHaveBeenCalledWith(
      expect.stringContaining('INSERT INTO users'),
      ['0912345678', null]
    );
  });
});

describe('POST /api/auth/login', () => {
  test('手機號碼格式錯誤時回傳 400', async () => {
    const res = await request(app).post('/api/auth/login').send({ phone_number: 'abc' });
    expect(res.status).toBe(400);
  });

  test('尚未註冊時回傳 401', async () => {
    pool.query.mockResolvedValueOnce([[]]);
    const res = await request(app).post('/api/auth/login').send({ phone_number: '0912345678' });
    expect(res.status).toBe(401);
  });

  test('已註冊時回傳 success，且不帶 fcm_token 時不會更新', async () => {
    pool.query.mockResolvedValueOnce([[{ id: 1 }]]);
    const res = await request(app).post('/api/auth/login').send({ phone_number: '0912345678' });
    expect(res.status).toBe(200);
    expect(res.text).toBe('success');
    expect(pool.execute).not.toHaveBeenCalled();
  });

  test('帶 fcm_token 時會更新資料庫', async () => {
    pool.query.mockResolvedValueOnce([[{ id: 1 }]]);
    pool.execute.mockResolvedValueOnce([{}]);
    await request(app).post('/api/auth/login').send({ phone_number: '0912345678', fcm_token: 'token-abc' });
    expect(pool.execute).toHaveBeenCalledWith(
      expect.stringContaining('UPDATE users'),
      ['token-abc', '0912345678']
    );
  });
});

describe('POST /api/sensors', () => {
  test('缺少必要數值欄位時回傳 400，且不會寫入資料庫', async () => {
    const res = await request(app).post('/api/sensors').send({ device_id: 'ESP8266_Kitchen' });
    expect(res.status).toBe(400);
    expect(pool.execute).not.toHaveBeenCalled();
  });

  test('合法資料時回傳 201 並寫入資料庫', async () => {
    pool.execute.mockResolvedValueOnce([{}]);
    const res = await request(app).post('/api/sensors').send({
      device_id: 'ESP8266_Kitchen',
      humidity: 55,
      temperature: 26,
      lpg: 10,
      co: 10,
      smoke: 10,
      fire: 0
    });
    expect(res.status).toBe(201);
    expect(pool.execute).toHaveBeenCalledWith(
      expect.stringContaining('INSERT INTO sensor_logs'),
      ['ESP8266_Kitchen', 55, 26, 10, 10, 10, 0]
    );
  });
});
