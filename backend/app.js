require('dotenv').config();

const fs = require('fs');
const path = require('path');
const express = require('express');
const mqtt = require('mqtt');
const mysql = require('mysql2/promise');

const {
  THRESHOLDS,
  COOLDOWN_SECONDS,
  toTaipeiString,
  withTaipeiTime,
  normalizeSensorPayload,
  evaluateSafetyStatus,
  pickAlert,
  createAlertGate
} = require('./lib/sensorLogic');

const app = express();
const port = Number(process.env.PORT || 3000);
const historyLimit = Math.min(Math.max(Number(process.env.HISTORY_LIMIT || 20), 1), 200);

// 最大註冊人數限制
const MAX_USER_LIMIT = Number(process.env.MAX_USER_LIMIT || 5);

app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Firebase Cloud Messaging初始化
let firebaseAdmin;
const serviceAccountPath = process.env.FIREBASE_SERVICE_ACCOUNT_PATH
  || path.join(__dirname, 'serviceAccountKey.json');

if (fs.existsSync(serviceAccountPath)) {
  try {
    firebaseAdmin = require('firebase-admin');
    firebaseAdmin.initializeApp({ credential: firebaseAdmin.credential.cert(require(serviceAccountPath)) });
    console.log('Firebase Cloud Messaging enabled.');
  } catch (error) {
    console.error('Firebase disabled: %s', error.message);
  }
} else {
  console.warn('Firebase disabled: no service-account file configured.');
}

// MySQL連線池
const dbPool = mysql.createPool({
  host: process.env.DB_HOST || '127.0.0.1',
  port: Number(process.env.DB_PORT || 3306),
  user: process.env.DB_USER || 'root',
  password: process.env.DB_PASSWORD || '',
  database: process.env.DB_NAME || 'sensor_logs',
  waitForConnections: true,
  connectionLimit: Number(process.env.DB_CONNECTION_LIMIT || 10),
  timezone: 'Z'
});

// 記錄各警報類型上一次發送時間、判斷是否還在冷卻中（邏輯本體在 lib/sensorLogic.js，方便測試）
const alertGate = createAlertGate(COOLDOWN_SECONDS);

// 發送FCM推播
async function sendFcmAlert(title, body) {
  if (!firebaseAdmin) return;
  await firebaseAdmin.messaging().send({ notification: { title, body }, topic: 'sensor_alerts' });
}

// 帶有冷卻時間防洗板機制的警報發送函式
async function checkAndSendFcmAlert(type, title, body) {
  if (!alertGate.shouldSend(type)) {
    console.log(`[Alert Suppressed] ${type.toUpperCase()} 警報冷卻中。`);
    return;
  }
  await sendFcmAlert(title, body);
}

// 儲存資料並進行廚房安全風險判斷
async function storeSensorData(payload) {
  const data = normalizeSensorPayload(payload);

  // 寫入資料庫
  await dbPool.execute(
    'INSERT INTO sensor_logs(device_id, humidity, temperature, lpg, co, smoke, fire) VALUES(?, ?, ?, ?, ?, ?, ?)',
    [data.device_id, data.humidity, data.temperature, data.lpg, data.co, data.smoke, data.fire]
  );

  // 廚房氣體與煙霧多重檢測（優先順序：LPG > CO > 煙霧）
  const alert = pickAlert(data);
  if (alert) {
    await checkAndSendFcmAlert(alert.type, alert.title, alert.body);
  }
}

// MQTT相關設定與訂閱
const mqttClient = mqtt.connect(process.env.MQTT_BROKER_URL || 'mqtt://127.0.0.1:1883');
mqttClient.on('connect', () => {
  const topic = process.env.MQTT_SENSOR_TOPIC || 'sensor/data';
  mqttClient.subscribe(topic, error => {
    if (error) console.error('MQTT subscribe failed:', error.message);
    else console.log(`Subscribed to ${topic}`);
  });
});
mqttClient.on('error', error => console.error('MQTT error:', error.message));
mqttClient.on('message', async (_topic, message) => {
  try {
    await storeSensorData(JSON.parse(message.toString()));
  } catch (error) {
    console.error('Unable to process MQTT message:', error.message);
  }
});

// HTTP RESTful APIs
app.get('/health', async (_req, res) => {
  try {
    await dbPool.query('SELECT 1');
    res.json({ status: 'ok' });
  } catch (error) {
    res.status(503).json({ status: 'error', message: error.message });
  }
});

// 取得最新感測器數據
app.get('/api/sensors/latest', async (req, res) => {
  try {
    const { device_id } = req.query;
    let sql = 'SELECT * FROM sensor_logs';
    const params = [];

    if (device_id) {
      sql += ' WHERE device_id = ?';
      params.push(device_id);
    }

    sql += ' ORDER BY created_at DESC LIMIT 1';

    const [rows] = await dbPool.query(sql, params);
    if (!rows.length) return res.status(404).json({ status: 'error', message: 'No sensor data is available yet.' });

    const latest = rows[0];
    const status = evaluateSafetyStatus(latest);

    res.json({ status: 'success', data: withTaipeiTime(latest), safetyStatus: status });
  } catch (error) {
    res.status(500).json({ status: 'error', message: error.message });
  }
});

// 取得歷史紀錄
app.get('/api/sensors/history', async (req, res) => {
  try {
    const limit = Math.min(Math.max(Number(req.query.limit || historyLimit), 1), 200);
    const { start, end, device_id } = req.query;
    const dateRegex = /^\d{4}-\d{2}-\d{2}$/;

    const conditions = [];
    const params = [];

    if (device_id) {
      conditions.push('device_id = ?');
      params.push(device_id);
    }

    if (start) {
      if (!dateRegex.test(start)) {
        return res.status(400).json({ status: 'error', message: 'start must be in YYYY-MM-DD format.' });
      }
      conditions.push('created_at >= ?');
      params.push(`${start} 00:00:00`);
    }

    if (end) {
      if (!dateRegex.test(end)) {
        return res.status(400).json({ status: 'error', message: 'end must be in YYYY-MM-DD format.' });
      }
      conditions.push('created_at <= ?');
      params.push(`${end} 23:59:59`);
    }

    const whereClause = conditions.length ? `WHERE ${conditions.join(' AND ')}` : '';
    params.push(limit);

    const [rows] = await dbPool.execute(
      `SELECT * FROM sensor_logs ${whereClause} ORDER BY created_at DESC LIMIT ?`,
      params
    );
    res.json({ status: 'success', data: rows.map(withTaipeiTime) });
  } catch (error) {
    res.status(500).json({ status: 'error', message: error.message });
  }
});

// 註冊API
app.post('/api/auth/register', async (req, res) => {
  try {
    const phone = String(req.body.phone_number || req.body.phone || '').trim();
    const fcmToken = String(req.body.fcm_token || '').trim() || null;

    if (!/^\+?[0-9]{6,20}$/.test(phone)) {
      return res.status(400).json({ status: 'error', message: '請輸入有效的手機號碼' });
    }

    // 檢查註冊總人數是否已達上限
    const [countRows] = await dbPool.query('SELECT COUNT(*) as total FROM users');
    if (countRows[0].total >= MAX_USER_LIMIT) {
      return res.status(403).json({ status: 'error', message: `系統註冊人數已達上限 (${MAX_USER_LIMIT} 人)` });
    }

    // 檢查手機號碼是否已被註冊
    const [existing] = await dbPool.query('SELECT id FROM users WHERE phone_number = ?', [phone]);
    if (existing.length > 0) {
      return res.status(400).json({ status: 'error', message: '該手機號碼已經註冊過' });
    }

    // 寫入users資料表
    await dbPool.execute(
      'INSERT INTO users(phone_number, fcm_token) VALUES(?, ?)',
      [phone, fcmToken]
    );

    res.status(201).json({ status: 'success', message: '註冊成功！' });

  } catch (error) {
    res.status(500).json({ status: 'error', message: error.message });
  }
});

// 登入API（比對users資料表中的phone_number，已註冊者才可進入）
app.post('/api/auth/login', async (req, res) => {
  try {
    const phone = String(req.body.phone_number || req.body.phone || '').trim();
    const fcmToken = String(req.body.fcm_token || '').trim();

    if (!/^\+?[0-9]{6,20}$/.test(phone)) {
      return res.status(400).json({ status: 'error', message: '請輸入有效的手機號碼' });
    }

    // 查詢號碼是否在users資料表中
    const [rows] = await dbPool.query('SELECT id FROM users WHERE phone_number = ?', [phone]);
    if (rows.length === 0) {
      return res.status(401).json({ status: 'error', message: '該號碼尚未註冊，請先進行註冊' });
    }

    // 登入時若有帶入FCM Token則更新
    if (fcmToken) {
      await dbPool.execute(
        'UPDATE users SET fcm_token = ? WHERE phone_number = ?',
        [fcmToken, phone]
      );
    }

    res.type('text').send('success');

  } catch (error) {
    res.status(500).json({ status: 'error', message: error.message });
  }
});

app.post('/api/sensors', async (req, res) => {
  try {
    await storeSensorData(req.body);
    res.status(201).json({ status: 'success' });
  } catch (error) {
    res.status(400).json({ status: 'error', message: error.message });
  }
});

if (require.main === module) {
  app.listen(port, () => console.log(`API listening on port ${port}`));
}

module.exports = app;