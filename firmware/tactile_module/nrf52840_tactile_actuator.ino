#include <bluefruit.h>
#include <Wire.h>

/* ============================================================
 *  DRV2605L — I2C Haptic Driver (ERM, open-loop, max amplitude)
 *  SDA = D4 (pin 4), SCL = D5 (pin 5), addr = 0x5A
 * ============================================================ */
#define DRV2605_ADDR     0x5A
#define DRV_MODE_RTP     0x05
#define DRV_RTP_MAX      0x7F
#define DRV_RTP_OFF      0x00

/* ============================================================
 *  BLE — Nordic UART Service
 * ============================================================ */
#define NUS_SERVICE_UUID  "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
#define NUS_TX_CHAR_UUID  "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
#define NUS_RX_CHAR_UUID  "6e400003-b5a3-f393-e0a9-e50e24dcca9e"
#define BLE_DEVICE_NAME   "ESP32_TACTILE"
#define BLE_TX_POWER      4

/* ============================================================
 *  Timing
 * ============================================================ */
#define FOG_VIBRATION_MS        7000
#define STATUS_LOG_INTERVAL_MS  5000
#define ALERT_PULSES            3
#define ALERT_ON_MS             200
#define ALERT_OFF_MS            300

/* ============================================================
 *  Commands
 * ============================================================ */
#define CMD_MOTOR_ON   0x01
#define CMD_MOTOR_OFF  0x00

/* ============================================================
 *  BLE Objects
 * ============================================================ */
BLEService        nusService(NUS_SERVICE_UUID);
BLECharacteristic nusTxChar(NUS_TX_CHAR_UUID);
BLECharacteristic nusRxChar(NUS_RX_CHAR_UUID);

/* ============================================================
 *  Global State
 * ============================================================ */
static bool     bleConnected      = false;
static bool     motorRunning      = false;
static bool     disconnectPending = false;
static uint32_t motorStartTime    = 0;
static uint32_t fogEventCount     = 0;
static uint32_t lastStatusTime    = 0;

/* ============================================================
 *  Forward Declarations
 * ============================================================ */
void drvWrite(uint8_t reg, uint8_t val);
uint8_t drvRead(uint8_t reg);
bool setupDRV2605();
void startMotor();
void stopMotor();
void playDisconnectAlert();
void setupBLE();
void startAdvertising();
void onConnect(uint16_t connHandle);
void onDisconnect(uint16_t connHandle, uint8_t reason);
void onTxWrite(uint16_t connHandle, BLECharacteristic* chr,
               uint8_t* data, uint16_t len);

/* ============================================================
 *  DRV2605L I2C Helpers
 * ============================================================ */
void drvWrite(uint8_t reg, uint8_t val) {
  Wire.beginTransmission(DRV2605_ADDR);
  Wire.write(reg);
  Wire.write(val);
  uint8_t err = Wire.endTransmission();
  if (err) {
    Serial.print("[DRV] I2C write error reg=0x");
    Serial.print(reg, HEX);
    Serial.print(" err=");
    Serial.println(err);
  }
}

uint8_t drvRead(uint8_t reg) {
  Wire.beginTransmission(DRV2605_ADDR);
  Wire.write(reg);
  Wire.endTransmission(false);
  Wire.requestFrom((uint8_t)DRV2605_ADDR, (uint8_t)1);
  return Wire.available() ? Wire.read() : 0xFF;
}

/* ============================================================
 *  DRV2605L Init
 *  Open-loop ERM = chip drives motor at full voltage without
 *  back-EMF limiting — maximum possible amplitude.
 * ============================================================ */
bool setupDRV2605() {
  Wire.setPins(4, 5);   // SDA=D4, SCL=D5 on XIAO nRF52840
  Wire.begin();
  delay(50);            // let chip power up fully

  uint8_t status = drvRead(0x00);
  Serial.print("[DRV] Status register: 0x");
  Serial.println(status, HEX);

  if (status == 0xFF) {
    Serial.println("[DRV] ERROR — chip not found! Check wiring.");
    return false;
  }

  drvWrite(0x01, 0x00);   // exit standby, internal trigger mode
  delay(10);

  // ERM motor type (bit7=0), max BEMF gain bits
  drvWrite(0x1A, 0x36);

  // ── Open-loop ERM: max drive voltage ─────────────────────
  // These two registers directly set how hard the motor is driven.
  // 0xFF = absolute maximum — strongest vibration possible.
  drvWrite(0x16, 0xFF);   // RATED_VOLTAGE  — max
  drvWrite(0x17, 0xFF);   // OD_CLAMP       — overdrive ceiling removed

  // CONTROL3: enable open-loop ERM (bit0=1)
  // Open-loop ignores back-EMF sensing → no self-limiting → full power
  uint8_t ctrl3 = drvRead(0x1D);
  drvWrite(0x1D, ctrl3 | 0x01);

  // ERM waveform library 1
  drvWrite(0x03, 0x01);

  // Switch to RTP mode — amplitude set directly via register 0x02
  drvWrite(0x01, DRV_MODE_RTP);
  delay(5);

  // Start silent
  drvWrite(0x02, DRV_RTP_OFF);

  Serial.println("[DRV] DRV2605L ready — ERM open-loop, MAXIMUM amplitude");
  return true;
}

/* ============================================================
 *  Motor Control
 * ============================================================ */
void startMotor() {
  motorStartTime = millis();   // restart 7-s window, non-accumulating
  motorRunning   = true;
  drvWrite(0x02, DRV_RTP_MAX); // 0x7F = max amplitude
  Serial.print("[MOTOR] ON — max amplitude, 7s restarted. FOG #");
  Serial.println(fogEventCount);
}

void stopMotor() {
  drvWrite(0x02, DRV_RTP_OFF);
  motorRunning = false;
  Serial.println("[MOTOR] OFF");
}

void playDisconnectAlert() {
  // 3 short strong buzzes — blocking safe, BLE already disconnected
  for (int p = 0; p < ALERT_PULSES; p++) {
    Serial.print("[ALERT] Buzz ");
    Serial.println(p + 1);
    drvWrite(0x02, DRV_RTP_MAX);
    delay(ALERT_ON_MS);
    drvWrite(0x02, DRV_RTP_OFF);
    delay(ALERT_OFF_MS);
  }
  Serial.println("[ALERT] Done");
}

/* ============================================================
 *  BLE Setup
 * ============================================================ */
void setupBLE() {
  Bluefruit.begin();
  Bluefruit.setTxPower(BLE_TX_POWER);
  Bluefruit.setName(BLE_DEVICE_NAME);

  Bluefruit.Periph.setConnectCallback(onConnect);
  Bluefruit.Periph.setDisconnectCallback(onDisconnect);

  nusService.begin();

  nusTxChar.setProperties(CHR_PROPS_WRITE | CHR_PROPS_WRITE_WO_RESP);
  nusTxChar.setPermission(SECMODE_NO_ACCESS, SECMODE_OPEN);
  nusTxChar.setMaxLen(20);
  nusTxChar.setWriteCallback(onTxWrite);
  nusTxChar.begin();

  nusRxChar.setProperties(CHR_PROPS_READ | CHR_PROPS_NOTIFY);
  nusRxChar.setPermission(SECMODE_OPEN, SECMODE_NO_ACCESS);
  nusRxChar.setMaxLen(20);
  nusRxChar.begin();

  Serial.println("[BLE] Nordic UART Service configured");
}

void startAdvertising() {
  Bluefruit.Advertising.clearData();
  Bluefruit.ScanResponse.clearData();

  Bluefruit.Advertising.addFlags(BLE_GAP_ADV_FLAGS_LE_ONLY_GENERAL_DISC_MODE);
  Bluefruit.Advertising.addService(nusService);
  Bluefruit.ScanResponse.addName();

  Bluefruit.Advertising.restartOnDisconnect(true);
  Bluefruit.Advertising.setInterval(32, 244);
  Bluefruit.Advertising.start(0);

  Serial.print("[BLE] Advertising as '");
  Serial.print(BLE_DEVICE_NAME);
  Serial.println("'");
}

/* ============================================================
 *  BLE Callbacks
 * ============================================================ */
void onConnect(uint16_t connHandle) {
  bleConnected      = true;
  disconnectPending = false;
  Serial.println("[BLE] Connected");
}

void onDisconnect(uint16_t connHandle, uint8_t reason) {
  bleConnected = false;
  Serial.print("[BLE] Disconnected 0x");
  Serial.println(reason, HEX);

  if (motorRunning) {
    // Let 7-s window finish, then loop() plays alert
    disconnectPending = true;
    Serial.println("[BLE] Will finish 7s then play disconnect alert");
  } else {
    // Motor idle — alert immediately
    playDisconnectAlert();
  }
}

void onTxWrite(uint16_t connHandle, BLECharacteristic* chr,
               uint8_t* data, uint16_t len) {
  if (len == 0) return;

  uint8_t cmd = data[0];
  Serial.print("[CMD] 0x");
  if (cmd < 0x10) Serial.print("0");
  Serial.println(cmd, HEX);

  switch (cmd) {
    case CMD_MOTOR_ON:
      fogEventCount++;
      startMotor();   // restarts 7s window, does NOT accumulate
      break;
    case CMD_MOTOR_OFF:
      // Non-FOG: do NOT cut motor early, let 7s window expire
      Serial.println("[CMD] Non-FOG — window continues");
      break;
    default:
      Serial.print("[CMD] Unknown 0x");
      if (cmd < 0x10) Serial.print("0");
      Serial.println(cmd, HEX);
      break;
  }
}

/* ============================================================
 *  setup()
 * ============================================================ */
void setup() {
  Serial.begin(115200);
  uint32_t t0 = millis();
  while (!Serial && (millis() - t0 < 3000)) { delay(10); }

  Serial.println("[SETUP] DRV2605L + BLE Tactile Firmware");
  Serial.println("[SETUP] I2C: SDA=D4(pin4), SCL=D5(pin5), addr=0x5A");
  Serial.println("[SETUP] Mode: ERM open-loop, maximum amplitude");

  if (!setupDRV2605()) {
    Serial.println("[SETUP] FATAL — DRV2605L not found. Fix wiring then reset.");
    while (1) { delay(1000); }
  }

  setupBLE();
  startAdvertising();

  lastStatusTime = millis();
  Serial.println("[SETUP] Ready");
}

/* ============================================================
 *  loop()
 * ============================================================ */
void loop() {
  uint32_t now = millis();

  // Heartbeat log every 5 s
  if (now - lastStatusTime >= STATUS_LOG_INTERVAL_MS) {
    lastStatusTime = now;
    Serial.print("[LOOP] BLE:");
    Serial.print(bleConnected ? "CONN" : "DISC");
    Serial.print(" Motor:");
    Serial.print(motorRunning ? "ON" : "OFF");
    if (motorRunning) {
      uint32_t elapsed = now - motorStartTime;
      uint32_t remain  = (elapsed < FOG_VIBRATION_MS)
                         ? (FOG_VIBRATION_MS - elapsed) : 0;
      Serial.print(" (");
      Serial.print(remain / 1000);
      Serial.print("s left)");
    }
    Serial.print(" FOG#:");
    Serial.println(fogEventCount);
  }

  // Auto-off after 7-second window
  if (motorRunning && (now - motorStartTime >= FOG_VIBRATION_MS)) {
    Serial.println("[MOTOR] 7s window complete");
    stopMotor();
    if (disconnectPending) {
      disconnectPending = false;
      playDisconnectAlert();
    }
  }

  delay(1);
}