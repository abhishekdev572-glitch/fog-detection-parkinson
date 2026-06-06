/*
   NICLA SENSE ME → IMU → INT8 Quantized → BLE → Android
   Window = (120, 6)
   Rate   = 60 Hz
   Payload = 720 bytes

   FIXED: Quantization params now match fog_activity_optimized.tflite
   ─────────────────────────────────────────────────────────────────
   OLD (wrong):  acc_scale=0.0039  acc_zero=-128
                 gyro_scale=0.015  gyro_zero=-128
   NEW (correct): scale=2.8394392  zero_point=-18  (all 6 channels)

   Verified from model using:
     input_details['quantization_parameters']['scales'][0]     → 2.8394392
     input_details['quantization_parameters']['zero_points'][0] → -18
     Per-tensor quantization (same params for all 6 channels)
*/

#include <Arduino_BHY2.h>
#include <ArduinoBLE.h>

// =============================
// SETTINGS
// =============================
#define SAMPLE_RATE_HZ  60
#define WINDOW_SIZE     120
#define CHANNELS        6

BLEService imuService("12345678-1234-1234-1234-1234567890ab");

BLECharacteristic imuCharacteristic(
  "abcd1234-1234-1234-1234-abcdef123456",
  BLENotify,
  240
);

// =============================
// Sensors from Bosch hub
// =============================
SensorXYZ accel(SENSOR_ID_ACC);
SensorXYZ gyro(SENSOR_ID_GYRO);

// =============================
// Quantization params
// Read directly from tflite model — DO NOT change these
// Per-tensor: same scale and zero_point for all 6 channels
// =============================
const float INPUT_SCALE      = 2.8394392f;
const int   INPUT_ZERO_POINT = -18;

// =============================
// Buffers
// =============================
int8_t imu_buffer[WINDOW_SIZE][CHANNELS];
int    idx = 0;

unsigned long lastSample = 0;
const unsigned long sampleInterval = 1000UL / SAMPLE_RATE_HZ;


// =============================
// Quantization
// Formula: q = clamp(round(x / scale) + zero_point, -128, 127)
//
// Example sanity check (device lying flat, Z-axis = gravity):
//   If accel outputs m/s²: az ≈ 9.81
//     q = round(9.81 / 2.8394) + (-18) = round(3.45) - 18 = 3 - 18 = -15 ✅
//   If accel outputs g-force: az ≈ 1.0
//     q = round(1.0 / 2.8394) + (-18) = round(0.35) - 18 = 0 - 18 = -18
//     → all values near -18 means Nicla outputs g → use GFORCE_SCALE below
// =============================
inline int8_t quantize(float x, float scale, int zero_point)
{
  int q = (int)round(x / scale) + zero_point;
  if (q >  127) q =  127;
  if (q < -128) q = -128;
  return (int8_t)q;
}


// =============================
// Send BLE in 240-byte chunks
// 720 bytes total → exactly 3 chunks
// =============================
void sendBLE(uint8_t* data, int len)
{
  const int chunk = 240;
  for (int i = 0; i < len; i += chunk)
  {
    int size = min(chunk, len - i);
    imuCharacteristic.writeValue(data + i, size);
    delay(5);  // pacing — increase to 8ms if packet drops occur
  }
}


// =============================
// Setup
// =============================
void setup()
{
  Serial.begin(115200);
  delay(1000);

  Serial.println("=================================");
  Serial.println("  NICLA IMU INT8 Streaming");
  Serial.println("=================================");
  Serial.print("  Sample rate  : "); Serial.print(SAMPLE_RATE_HZ); Serial.println(" Hz");
  Serial.print("  Window size  : "); Serial.println(WINDOW_SIZE);
  Serial.print("  Channels     : "); Serial.println(CHANNELS);
  Serial.print("  Payload      : "); Serial.println(WINDOW_SIZE * CHANNELS, DEC);
  Serial.print("  Quant scale  : "); Serial.println(INPUT_SCALE, 7);
  Serial.print("  Quant zero   : "); Serial.println(INPUT_ZERO_POINT);
  Serial.println("=================================");

  // Bosch sensor hub
  BHY2.begin();
  accel.begin();
  gyro.begin();
  accel.configure(SAMPLE_RATE_HZ, 0);
  gyro.configure(SAMPLE_RATE_HZ, 0);

  // BLE init
  if (!BLE.begin())
  {
    Serial.println("BLE failed to start — halting");
    while (1);
  }

  BLE.setLocalName("NICLA_IMU_INT8");
  BLE.setAdvertisedService(imuService);
  imuService.addCharacteristic(imuCharacteristic);
  BLE.addService(imuService);
  BLE.advertise();

  Serial.println("BLE advertising started");
  Serial.println("Waiting for connection...");
}


// =============================
// Main Loop
// =============================
void loop()
{
  BLEDevice central = BLE.central();

  if (!central)
    return;

  Serial.println("---------------------------------");
  Serial.print("Connected: ");
  Serial.println(central.address());
  Serial.println("---------------------------------");

  idx        = 0;
  lastSample = millis();

  // Print one debug header
  bool firstWindow = true;

  while (central.connected())
  {
    BHY2.update();

    unsigned long now = millis();

    if (now - lastSample >= sampleInterval)
    {
      lastSample = now;

      // Read raw sensor values
      // Units must match your training data:
      //   accel → m/s²  (if Nicla outputs g-force, see GFORCE_SCALE note above)
      //   gyro  → deg/s or rad/s (must match training CSV units)
      float ax = accel.x();
      float ay = accel.y();
      float az = accel.z();
      float gx = gyro.x();
      float gy = gyro.y();
      float gz = gyro.z();

      // Quantize all 6 channels using model's embedded params
      imu_buffer[idx][0] = quantize(ax, INPUT_SCALE, INPUT_ZERO_POINT);
      imu_buffer[idx][1] = quantize(ay, INPUT_SCALE, INPUT_ZERO_POINT);
      imu_buffer[idx][2] = quantize(az, INPUT_SCALE, INPUT_ZERO_POINT);
      imu_buffer[idx][3] = quantize(gx, INPUT_SCALE, INPUT_ZERO_POINT);
      imu_buffer[idx][4] = quantize(gy, INPUT_SCALE, INPUT_ZERO_POINT);
      imu_buffer[idx][5] = quantize(gz, INPUT_SCALE, INPUT_ZERO_POINT);

      idx++;

      if (idx == WINDOW_SIZE)
      {
        // ── Debug print on first window only ──────────────────
        // Check these values in Serial Monitor after flashing:
        //
        // Good (m/s², device flat, gravity on Z):
        //   raw  :  ~0.0,  ~0.0,  ~9.81,  ~0.0, ~0.0, ~0.0
        //   quant:  ~-18,  ~-18,  ~-15,   ~-18, ~-18, ~-18
        //
        // Bad (values all clipped to 127 or -128):
        //   → units mismatch, check GFORCE_SCALE note in quantize()
        if (firstWindow)
        {
          Serial.println("--- First window sample [0] ---");
          Serial.print("  acc raw  (m/s²): ");
          Serial.print(ax, 3); Serial.print(", ");
          Serial.print(ay, 3); Serial.print(", ");
          Serial.println(az, 3);
          Serial.print("  gyro raw (deg/s): ");
          Serial.print(gx, 3); Serial.print(", ");
          Serial.print(gy, 3); Serial.print(", ");
          Serial.println(gz, 3);
          Serial.print("  acc quantized  : ");
          Serial.print(imu_buffer[0][0]); Serial.print(", ");
          Serial.print(imu_buffer[0][1]); Serial.print(", ");
          Serial.println(imu_buffer[0][2]);
          Serial.print("  gyro quantized : ");
          Serial.print(imu_buffer[0][3]); Serial.print(", ");
          Serial.print(imu_buffer[0][4]); Serial.print(", ");
          Serial.println(imu_buffer[0][5]);
          Serial.println("-------------------------------");
          firstWindow = false;
        }

        Serial.println("Sending window (720 bytes)");
        sendBLE((uint8_t*)imu_buffer, sizeof(imu_buffer));
        idx = 0;
      }
    }
  }

  Serial.println("Central disconnected");
  idx = 0;
}
