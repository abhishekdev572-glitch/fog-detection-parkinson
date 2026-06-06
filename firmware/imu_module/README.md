# Nicla Sense ME IMU INT8 BLE Streamer

This project reads IMU data from an Arduino Nicla Sense ME, quantizes the sensor values to `int8`, groups them into fixed windows, and streams each window over BLE to a connected client such as an Android app.

The sketch is implemented in [nicla_imu_int8.ino](/mnt/c/Users/KIIT0001/Downloads/IMU%20module-20260329T170047Z-1-001/IMU%20module/nicla_imu_int8/nicla_imu_int8.ino).

## What The Sketch Does

The firmware performs this pipeline:

1. Initializes the Bosch sensor hub through `Arduino_BHY2`.
2. Starts the accelerometer and gyroscope at `60 Hz`.
3. Reads 6 channels per sample:
   - Accelerometer: `ax`, `ay`, `az`
   - Gyroscope: `gx`, `gy`, `gz`
4. Quantizes each float sample to `int8` using the TensorFlow Lite model input parameters.
5. Stores samples in a fixed window of `120` rows.
6. Sends the full window over BLE in `3` notification packets of `240` bytes each.

## Data Shape

- Sample rate: `60 Hz`
- Window size: `120`
- Channels per sample: `6`
- Buffer shape: `(120, 6)`
- Data type after quantization: `int8`
- Total payload per window: `120 x 6 = 720 bytes`

At 60 Hz, one window represents about `2 seconds` of IMU data.

## Sensor Sources

The sketch uses:

- `SensorXYZ accel(SENSOR_ID_ACC)` for accelerometer data
- `SensorXYZ gyro(SENSOR_ID_GYRO)` for gyroscope data

Both are configured in `setup()` using:

- `accel.configure(SAMPLE_RATE_HZ, 0);`
- `gyro.configure(SAMPLE_RATE_HZ, 0);`

## Quantization

The code converts raw sensor values into `int8` with:

```cpp
q = clamp(round(x / scale) + zero_point, -128, 127)
```

The quantization parameters are hard-coded to match the TensorFlow Lite model used by the project:

- `INPUT_SCALE = 2.8394392`
- `INPUT_ZERO_POINT = -18`

Important:

- These values are taken from the model input quantization parameters.
- The sketch assumes per-tensor quantization, meaning the same scale and zero point are used for all 6 channels.
- The header comment explicitly says these values replaced older incorrect settings.

## Important Assumption About Units

The sensor units must match the units used when the ML model was trained.

The sketch comments indicate the expected units are likely:

- Accelerometer: `m/s^2`
- Gyroscope: `deg/s` or `rad/s`, depending on the training data

If the device outputs values in different units than the model expects, the quantized values may be wrong or saturate to `127` or `-128`.

The code includes a sanity check example for a flat device:

- If acceleration is in `m/s^2`, `az` should be close to `9.81`
- The quantized Z value should then be around `-15`

If all values stay near `-18` or clip heavily, the raw sensor units likely do not match the model input assumptions.

## BLE Interface

The sketch creates:

- BLE service UUID: `12345678-1234-1234-1234-1234567890ab`
- BLE characteristic UUID: `abcd1234-1234-1234-1234-abcdef123456`
- Local BLE name: `NICLA_IMU_INT8`

The characteristic:

- uses `BLENotify`
- has a packet size of `240` bytes

Because the full IMU window is `720 bytes`, each window is sent as:

1. First BLE notification: bytes `0-239`
2. Second BLE notification: bytes `240-479`
3. Third BLE notification: bytes `480-719`

There is a `delay(5)` between BLE chunks to reduce packet loss.

## Runtime Flow

### On startup

The sketch:

- starts serial communication at `115200`
- initializes BHY2 sensors
- starts BLE advertising
- waits for a BLE central device to connect

### After a BLE client connects

The sketch:

- resets the sample index
- starts collecting timed samples
- fills the `imu_buffer`
- sends a full `720-byte` window whenever `120` samples have been collected

### After disconnect

The sketch:

- prints a disconnect message to serial
- resets the sample index to `0`

## Serial Monitor Output

The firmware prints useful debug information over serial:

- startup configuration
- BLE connection and disconnection status
- one debug dump for the first completed window
- a log line each time a `720-byte` window is sent

For the first completed window, it prints:

- raw accelerometer sample
- raw gyroscope sample
- quantized accelerometer sample
- quantized gyroscope sample

This is helpful for verifying that sensor values and quantization look reasonable after flashing.

## Arduino Dependencies

The sketch includes these libraries:

- `Arduino_BHY2`
- `ArduinoBLE`

Install them through the Arduino IDE or Arduino CLI before compiling.

## How To Use

1. Open `nicla_imu_int8.ino` in the Arduino IDE.
2. Select the correct Nicla Sense ME board and serial port.
3. Make sure `Arduino_BHY2` and `ArduinoBLE` are installed.
4. Upload the sketch to the board.
5. Open Serial Monitor at `115200 baud`.
6. Connect to the device over BLE using a client that listens for notifications on the characteristic UUID above.
7. Read three consecutive `240-byte` notifications to reconstruct one `720-byte` IMU window.

## Expected Payload Layout

The transmitted data is the raw memory content of:

```cpp
int8_t imu_buffer[120][6];
```

So each sample is stored in this channel order:

1. `ax`
2. `ay`
3. `az`
4. `gx`
5. `gy`
6. `gz`

That means the receiver should interpret the payload as `120` consecutive samples, each containing `6` signed 8-bit values in the order above.

## Project Purpose

This firmware is designed for edge-ML or mobile-ML pipelines where:

- IMU data is captured on-device
- preprocessing is reduced to model-compatible quantization
- fixed windows are sent to another device such as an Android app
- the receiving side can run inference or store the input for later processing

## Limitations And Notes

- The sketch does not run the ML model on the Nicla itself.
- It only streams quantized IMU windows over BLE.
- The code assumes the model input quantization parameters remain unchanged.
- If the TensorFlow Lite model changes, the scale and zero point should be revalidated.
- The BLE receiver must handle window reconstruction from 3 notifications per inference window.

## File Structure

```text
nicla_imu_int8/
|-- nicla_imu_int8.ino
`-- README.md
```
