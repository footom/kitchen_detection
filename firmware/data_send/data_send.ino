#include <DHT.h>
#include <ESP8266WiFi.h>
#include <PubSubClient.h>  // MQTT函式庫
#include <ArduinoJson.h>    // JSON處理函式庫

// ------------------- 硬體與腳位設定 -------------------
#define MQ_PIN                    (A0)
#define RL_VALUE                  (5)      // RL電阻值5K
#define RO_CLEAN_AIR_FACTOR       (9.83)   // RO在清潔空氣中的係數
#define CALIBARAION_SAMPLE_TIMES  (5)
#define CALIBRATION_SAMPLE_INTERVAL (50)
#define READ_SAMPLE_INTERVAL      (50)
#define READ_SAMPLE_TIMES         (5)

#define DHTPIN                    D2
#define DHTTYPE                   DHT11
#define isFlamePin                D1

#define GAS_LPG                   (0)
#define GAS_CO                    (1)
#define GAS_SMOKE                 (2)

float LPGCurve[3]   = {2.3, 0.21, -0.47};
float COCurve[3]    = {2.3, 0.72, -0.34};
float SmokeCurve[3] = {2.3, 0.53, -0.44};
float Ro            = 10;
int isFlame         = HIGH;

DHT dht(DHTPIN, DHTTYPE);

// ------------------- 網路與MQTT設定 -------------------
const char* ssid        = "YOUR_WIFI_NAME";  // 2.4GHz頻段網路名稱
const char* password    = "YOUR_WIFI_PASSWORD";
const char* mqtt_server = "YOUR_IP"; // Node.js / MQTT Broker IP
const int   mqtt_port   = 1883;
const char* mqtt_topic  = "sensor/data";

const char* device_id   = "ESP8266_Kitchen";  // 可以選IP名稱

WiFiClient espClient;
PubSubClient mqttClient(espClient);

// ------------------- 數據變數 -------------------
float humidityData;
float temperatureData;
float lpg, co, smoke;

// ------------------- 非阻擋式MQTT重連狀態 -------------------
unsigned long lastReconnectAttempt = 0;
const unsigned long RECONNECT_INTERVAL = 5000;

// 前置宣告
float MQCalibration(int mq_pin);
float MQRead(int mq_pin);
int MQGetGasPercentage(float rs_ro_ratio, int gas_id);
int MQGetPercentage(float rs_ro_ratio, float* pcurve);

// WiFi 連線邏輯
void setup_wifi() {
  delay(10);
  Serial.println();
  Serial.print("Connecting to ");
  Serial.println(ssid);

  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nWiFi connected");
  Serial.print("IP Address: ");
  Serial.println(WiFi.localIP());
}

// MQTT重連邏輯
bool reconnect_mqtt() {
  if (mqttClient.connected()) return true;

  unsigned long now = millis();
  if (now - lastReconnectAttempt < RECONNECT_INTERVAL) {
    return false;
  }
  lastReconnectAttempt = now;

  Serial.print("Attempting MQTT connection...");
  String clientId = "ESP8266Client-" + String(random(0xffff), HEX);
  if (mqttClient.connect(clientId.c_str())) {
    Serial.println("connected");
    return true;
  } else {
    Serial.print("failed, rc=");
    Serial.print(mqttClient.state());
    Serial.println(", will retry later");
    return false;
  }
}

void setup() {
  Serial.begin(115200);
  delay(500);
  Serial.println("\n--- System Booting ---");

  pinMode(isFlamePin, INPUT);
  dht.begin();

  Serial.println("Calibrating MQ sensor...");
  Ro = MQCalibration(MQ_PIN);
  Serial.print("MQ Calibrated! Ro = ");
  Serial.println(Ro);

  setup_wifi();
  mqttClient.setServer(mqtt_server, mqtt_port);
  Serial.println("--- Setup Complete ---");
}

void loop() {
  Serial.println("\n--- Loop Running ---");

  bool mqttReady = mqttClient.connected() || reconnect_mqtt();
  if (mqttReady) {
    mqttClient.loop();
  }

  // 讀取感測器數據
  humidityData    = dht.readHumidity();
  temperatureData = dht.readTemperature();
  
  float mqReadVal = MQRead(MQ_PIN);
  lpg   = MQGetGasPercentage(mqReadVal / Ro, GAS_LPG);
  co    = MQGetGasPercentage(mqReadVal / Ro, GAS_CO);
  smoke = MQGetGasPercentage(mqReadVal / Ro, GAS_SMOKE);
  isFlame = digitalRead(isFlamePin);

  // 打包數據為JSON格式
  StaticJsonDocument<256> doc;
  doc["device_id"]   = device_id;
  doc["humidity"]    = humidityData;
  doc["temperature"] = temperatureData;
  doc["lpg"]         = lpg;
  doc["co"]          = co;
  doc["smoke"]       = smoke;
  doc["fire"]        = (isFlame == LOW) ? 1 : 0;

  char jsonBuffer[256];
  serializeJson(doc, jsonBuffer);

  // 發布MQTT訊息
  if (mqttReady) {
    Serial.print("Publishing to ");
    Serial.print(mqtt_topic);
    Serial.print(": ");
    Serial.println(jsonBuffer);

    mqttClient.publish(mqtt_topic, jsonBuffer);
  } else {
    Serial.println("MQTT not connected, skip publish this round.");
  }

  delay(5000);
}

// ------------------- MQ-2演算法核心函式 -------------------
float MQResistanceCalculation(int raw_adc) {
  // 防護：若ADC為0 則設為1，避免除以零崩潰
  if (raw_adc <= 0) raw_adc = 1;
  return (((float)RL_VALUE * (1023 - raw_adc) / raw_adc));
}

float MQCalibration(int mq_pin) {
  float val = 0;
  for (int i = 0; i < CALIBARAION_SAMPLE_TIMES; i++) {
    val += MQResistanceCalculation(analogRead(mq_pin));
    delay(CALIBRATION_SAMPLE_INTERVAL);
  }
  val = val / CALIBARAION_SAMPLE_TIMES;
  return val / RO_CLEAN_AIR_FACTOR;
}

float MQRead(int mq_pin) {
  float rs = 0;
  for (int i = 0; i < READ_SAMPLE_TIMES; i++) {
    rs += MQResistanceCalculation(analogRead(mq_pin));
    delay(READ_SAMPLE_INTERVAL);
  }
  return rs / READ_SAMPLE_TIMES;
}

int MQGetGasPercentage(float rs_ro_ratio, int gas_id) {
  if (rs_ro_ratio <= 0) return 0; // 防護負數或零
  if (gas_id == GAS_LPG)   return MQGetPercentage(rs_ro_ratio, LPGCurve);
  if (gas_id == GAS_CO)    return MQGetPercentage(rs_ro_ratio, COCurve);
  if (gas_id == GAS_SMOKE) return MQGetPercentage(rs_ro_ratio, SmokeCurve);
  return 0;
}

int MQGetPercentage(float rs_ro_ratio, float* pcurve) {
  return (pow(10, (((log(rs_ro_ratio) - pcurve[1]) / pcurve[2]) + pcurve[0])));
}
