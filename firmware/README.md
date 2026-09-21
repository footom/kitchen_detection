# ESP8266 多功能環境與氣體監測發送端說明文件

本專案基於ESP8266 (NodeMCU) 開發，整合MQ氣體感測器（可計算LPG、CO及Smoke濃度）、DHT11溫濕度感測器與火焰感測器，透過MQTT通訊協定定期發送JSON格式之環境監測數據。

---

## 硬體接線說明

| 感測器 / 模組 | 腳位 | ESP8266 腳位 | 說明 |
|---|---|---|---|
| **MQ 氣體感測器** | VCC | 5V (VIN) | 建議提供 5V 獨立或穩定電源 |
| | GND | GND | 共地 |
| | AO | A0 | 類比訊號輸入 |
| **DHT11 溫濕度感測器** | VCC | 3.3V / 5V | 電源 |
| | GND | GND | 共地 |
| | DATA | D2 | 數位訊號讀取 |
| **火焰感測器** | VCC | 3.3V / 5V | 電源 |
| | GND | GND | 共地 |
| | DO | D1 | 數位訊號讀取（偵測到火源為 LOW） |

---

## 依賴函式庫

請確保Arduino IDE已透過庫管理器（Library Manager）安裝以下開發庫：

* **DHT sensor library** (by Adafruit)
* **PubSubClient** (by Nick O'Leary)
* **ArduinoJson** (建議版本v6.x)
* **ESP8266WiFi** (ESP8266開發板核心內建)

---

## 快速設定與燒錄步驟

1. 開啟專案程式碼檔案（`.ino`）。
2. 修改程式碼中的網路與MQTT服務器配置變數：
   ```cpp
   const char* ssid        = "YOUR_WIFI_NAME";      // 2.4GHz Wi-Fi名稱
   const char* password    = "YOUR_WIFI_PASSWORD";  // Wi-Fi密碼
   const char* mqtt_server = "YOUR_IP";             // MQTT Broker / Node.js伺服器IP
   const int   mqtt_port   = 1883;                  // MQTT 連線埠
   const char* mqtt_topic  = "sensor/data";         // 發布主題
   const char* device_id   = "ESP8266_Kitchen";     // 裝置識別標籤
   ```
3. 開機預熱與校準注意事項：
   * MQ 感測器於 `setup()` 階段會自動執行清潔空氣校準（`MQCalibration`）。
   * 開機時請務必確保環境處於**空氣清新**狀態，以避免基準值（Ro）校準偏差。
4. Arduino IDE開發板設定：
   * 板子選擇：**NodeMCU 1.0 (ESP-12E Module)** 或對應ESP8266型號。
   * 序列號埠（Port）：選擇對應的COM Port。
   * 選擇 **上傳 / 燒錄**。

---

## MQTT 資料傳輸格式

裝置連線成功後，每隔5秒會向主題 `sensor/data`（或自訂Topic）發送如下JSON結構之數據包：

```json
{
  "device_id": "ESP8266_Kitchen",
  "humidity": 65.0,
  "temperature": 26.5,
  "lpg": 12,
  "co": 5,
  "smoke": 8,
  "fire": 0
}
```

### 欄位說明

* `device_id`: 裝置唯一標識名稱
* `humidity`: 環境相對濕度（%）
* `temperature`: 環境攝氏溫度（°C）
* `lpg`: 液化石油氣濃度預估值（ppm）
* `co`: 一氧化碳濃度預估值（ppm）
* `smoke`: 煙霧濃度預估值（ppm）
* `fire`: 火焰狀態標誌（`1` 表示偵測到火焰/火源，`0` 表示無火源）