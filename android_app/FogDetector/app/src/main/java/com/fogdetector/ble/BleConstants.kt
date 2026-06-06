package com.fogdetector.ble

import java.util.UUID

object BleConstants {
    // IMU device
    val IMU_SERVICE_UUID                    = UUID.fromString("12345678-1234-1234-1234-1234567890ab")
    val IMU_DATA_CHARACTERISTIC_UUID        = UUID.fromString("abcd1234-1234-1234-1234-abcdef123456")

    // Tactile device (Nordic UART Service)
    val TACTILE_SERVICE_UUID                = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    val TACTILE_CONTROL_CHARACTERISTIC_UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")

    // Standard BLE notification descriptor — do NOT change
    val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // Device name prefixes used for auto-scan matching
    const val IMU_DEVICE_NAME_PREFIX     = "NICLA_IMU_INT8"
    const val TACTILE_DEVICE_NAME_PREFIX = "ESP32_TACTILE"

    // Timing
    const val CONNECTION_TIMEOUT_MS = 10_000L
    const val SCAN_TIMEOUT_MS       = 10_000L

    // Packet: 120 samples × 6 channels × 1 byte (int8)
    const val PACKET_SIZE  = 720
    const val WINDOW_SIZE  = 120
    const val NUM_CHANNELS = 6

    // Tactile commands
    val TACTILE_ON_COMMAND  = byteArrayOf(0x01)
    val TACTILE_OFF_COMMAND = byteArrayOf(0x00)
}
