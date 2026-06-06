# nRF52840 Tactile Actuator Firmware

This project is firmware for an nRF52840-based board that controls a DRV2605L haptic driver over I2C and exposes a BLE interface for triggering a tactile motor.

The sketch is implemented in [nrf52840_tactile_actuator.ino](/mnt/c/Users/KIIT0001/Downloads/Tactile%20module-20260329T170053Z-1-001/Tactile%20module/nrf52840_tactile_actuator.ino).

## Project Purpose

The firmware is designed for a tactile alert module. A BLE client can send a simple command to trigger strong vibration feedback through a DRV2605L-driven ERM motor.

The code is aimed at a use case where:

- a BLE-connected system detects an event such as FOG
- the tactile module receives a command
- the motor vibrates strongly for a fixed duration
- the device can warn the user again if BLE disconnects

## Main Features

- DRV2605L haptic driver over I2C
- ERM motor support
- Open-loop mode for maximum vibration amplitude
- BLE peripheral mode using Nordic UART Service style UUIDs
- Single-byte command protocol
- Fixed 7-second vibration window
- Disconnect alert with 3 short buzzes
- Serial logging for setup, commands, state, and errors

## Hardware Used

Based on the code comments, this firmware expects:

- an nRF52840 board using the Adafruit Bluefruit stack
- a DRV2605L haptic driver
- an ERM vibration motor

The code comment specifically mentions:

- `Wire.setPins(4, 5);`
- `SDA = D4`
- `SCL = D5`
- I2C address = `0x5A`

The setup message also references `XIAO nRF52840`, so that is likely the intended board target.

## Wiring

Expected I2C wiring from the sketch:

- `SDA -> D4`
- `SCL -> D5`
- `DRV2605L I2C address -> 0x5A`

The firmware checks the DRV2605L status register during startup. If the chip is not found, the device enters a fatal loop and does not continue.

## BLE Interface

The firmware acts as a BLE peripheral and advertises with:

- Device name: `ESP32_TACTILE`
- TX power: `4`

Service and characteristic UUIDs:

- Service UUID: `6e400001-b5a3-f393-e0a9-e50e24dcca9e`
- Write characteristic UUID: `6e400002-b5a3-f393-e0a9-e50e24dcca9e`
- Read/notify characteristic UUID: `6e400003-b5a3-f393-e0a9-e50e24dcca9e`

Important note:

- The code names these characteristics `nusTxChar` and `nusRxChar`, but their behavior is what matters.
- The characteristic with UUID `6e400002-...` is configured as the writable command input.
- The characteristic with UUID `6e400003-...` is configured as read/notify, although this sketch does not actually send notifications through it.

## Command Protocol

The BLE command protocol is a single byte:

- `0x01` -> motor on
- `0x00` -> motor off command

### Actual behavior of each command

#### `0x01` Motor ON

When the firmware receives `0x01`:

- it increments the internal FOG event counter
- it starts the motor at maximum amplitude
- it sets the motor timer to the current time
- it runs the motor for a fixed `7000 ms`

If another `0x01` arrives while the motor is already running:

- the timer is restarted
- the 7-second vibration window starts over
- the duration does not accumulate

#### `0x00` Motor OFF

Despite the constant name `CMD_MOTOR_OFF`, the code does not stop the motor immediately.

Instead, when `0x00` is received:

- the firmware logs `Non-FOG — window continues`
- the motor keeps running until the current 7-second window expires

So in the current implementation, `0x00` behaves more like:

- "do not restart vibration"
- not "force stop now"

#### Unknown commands

Any other command byte is ignored and logged as unknown.

## Vibration Behavior

The motor is driven through the DRV2605L in RTP mode:

- `DRV_MODE_RTP = 0x05`
- `DRV_RTP_MAX = 0x7F`
- `DRV_RTP_OFF = 0x00`

The setup configures the DRV2605L for:

- ERM mode
- open-loop control
- maximum rated voltage
- maximum overdrive clamp

This is intended to produce the strongest possible vibration from the motor.

## Disconnect Alert Behavior

When BLE disconnects, the firmware behaves differently depending on whether the motor is active.

### If the motor is not running

It immediately plays a disconnect alert:

- `3` buzzes
- each buzz ON for `200 ms`
- each gap OFF for `300 ms`

### If the motor is already running

It does not interrupt the ongoing 7-second vibration window.

Instead:

- it marks `disconnectPending = true`
- lets the current vibration finish
- then plays the 3-buzz disconnect alert

## Timing Summary

- Vibration window: `7000 ms`
- Status heartbeat log interval: `5000 ms`
- Disconnect alert pulses: `3`
- Alert ON time: `200 ms`
- Alert OFF time: `300 ms`

## Runtime Flow

### On startup

The firmware:

- starts serial at `115200`
- waits briefly for serial to become available
- initializes the DRV2605L over I2C
- aborts if the DRV2605L is not detected
- initializes BLE
- starts BLE advertising

### During operation

The firmware:

- waits for BLE connections
- accepts single-byte commands through the writable BLE characteristic
- runs the motor for a timed 7-second window
- logs a status heartbeat every 5 seconds

### Auto stop

The motor is stopped automatically when:

- `millis() - motorStartTime >= 7000`

At that point:

- the motor output is set to `0x00`
- `motorRunning` becomes `false`
- a pending disconnect alert is played if needed

## Serial Output

The sketch logs:

- startup and hardware configuration
- DRV2605L detection status
- BLE advertising and connection state
- received commands
- motor start and stop events
- heartbeat status every 5 seconds
- disconnect alerts and I2C write errors

This makes the serial monitor useful for debugging wiring, BLE communication, and timing behavior.

## Arduino Dependencies

The sketch includes:

- `bluefruit.h`
- `Wire.h`

You will need the appropriate nRF52 / Bluefruit board support and libraries installed in the Arduino environment.

## How To Use

1. Open [nrf52840_tactile_actuator.ino](/mnt/c/Users/KIIT0001/Downloads/Tactile%20module-20260329T170053Z-1-001/Tactile%20module/nrf52840_tactile_actuator.ino) in the Arduino IDE.
2. Select the correct nRF52840 board.
3. Connect the DRV2605L to `D4` and `D5` for I2C.
4. Upload the firmware.
5. Open Serial Monitor at `115200 baud`.
6. Scan for the BLE device named `ESP32_TACTILE`.
7. Connect to the BLE service UUID listed above.
8. Write a single byte `0x01` to the writable command characteristic to trigger vibration.

## Notes And Caveats

- The BLE device name is `ESP32_TACTILE`, even though the code appears to target an nRF52840 board.
- The characteristic naming in the code does not match common Nordic UART naming conventions, so use the UUID behavior rather than the variable names.
- The firmware does not currently send any response payloads or status notifications to the client.
- `0x00` does not stop the motor immediately in the current implementation.
- The disconnect alert is blocking, but the code comments note that this is acceptable because BLE is already disconnected at that point.

## File Structure

```text
Tactile module/
|-- nrf52840_tactile_actuator.ino
`-- README.md
```
