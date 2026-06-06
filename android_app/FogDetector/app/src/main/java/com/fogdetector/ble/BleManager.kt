package com.fogdetector.ble

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.MutableLiveData

/**
 * Manages BLE connections to the IMU (NICLA_IMU_INT8) and Tactile (ESP32_TACTILE) devices.
 *
 * Usage:
 *   1. Call [startScan] — automatically connects both devices when found.
 *   2. Observe [imuState] / [tactileState] for connection updates.
 *   3. Set [onPacketReceived] to receive 720-byte inference windows.
 *   4. Call [sendTactileCommand] to vibrate the actuator.
 *   5. Call [disconnectAll] + [close] on cleanup.
 */
@SuppressLint("MissingPermission")
class BleManager(private val context: Context) {

    companion object {
        private const val TAG      = "BleManager"
        private const val MTU_SIZE = 512
    }

    // ── LiveData ──────────────────────────────────────────────────────────────
    val imuState     = MutableLiveData(BleConnectionState.IDLE)
    val tactileState = MutableLiveData(BleConnectionState.IDLE)

    /** Called on the calling thread when a full 720-byte window is ready. */
    var onPacketReceived: ((ByteArray) -> Unit)? = null

    // ── Internal state ────────────────────────────────────────────────────────
    private val btAdapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }
    private val handler = Handler(Looper.getMainLooper())

    private var imuGatt:     BluetoothGatt? = null
    private var tactileGatt: BluetoothGatt? = null
    private var imuDevice:     BluetoothDevice? = null
    private var tactileDevice: BluetoothDevice? = null

    private var isScanning = false

    // Rolling accumulation buffer for fragmented BLE packets
    private val dataBuffer = ByteArray(BleConstants.PACKET_SIZE)
    private var bufferPos  = 0

    // ── Scan ──────────────────────────────────────────────────────────────────
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val name = result.device.name ?: return
            when {
                name.startsWith(BleConstants.IMU_DEVICE_NAME_PREFIX) && imuDevice == null -> {
                    Log.d(TAG, "IMU found: $name")
                    imuDevice = result.device
                    imuState.postValue(BleConnectionState.FOUND)
                    connectImu()
                }
                name.startsWith(BleConstants.TACTILE_DEVICE_NAME_PREFIX) && tactileDevice == null -> {
                    Log.d(TAG, "Tactile found: $name")
                    tactileDevice = result.device
                    tactileState.postValue(BleConnectionState.FOUND)
                    connectTactile()
                }
            }
            if (imuDevice != null && tactileDevice != null) stopScan()
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed: $errorCode")
            isScanning = false
            imuState.postValue(BleConnectionState.ERROR)
            tactileState.postValue(BleConnectionState.ERROR)
        }
    }

    fun startScan() {
        if (isScanning) return
        imuDevice = null
        tactileDevice = null
        bufferPos = 0

        imuState.value     = BleConnectionState.SCANNING
        tactileState.value = BleConnectionState.SCANNING

        btAdapter?.bluetoothLeScanner?.startScan(scanCallback)
        isScanning = true

        handler.postDelayed({
            if (isScanning) {
                stopScan()
                if (imuDevice     == null) imuState.postValue(BleConnectionState.NOT_FOUND)
                if (tactileDevice == null) tactileState.postValue(BleConnectionState.NOT_FOUND)
            }
        }, BleConstants.SCAN_TIMEOUT_MS)
    }

    private fun stopScan() {
        if (!isScanning) return
        btAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
        isScanning = false
    }

    // ── Connect ───────────────────────────────────────────────────────────────
    private fun connectImu() {
        imuState.postValue(BleConnectionState.CONNECTING)
        imuGatt = imuDevice?.connectGatt(context, false, imuGattCb, BluetoothDevice.TRANSPORT_LE)
        scheduleTimeout(imuGatt, imuState)
    }

    private fun connectTactile() {
        tactileState.postValue(BleConnectionState.CONNECTING)
        tactileGatt = tactileDevice?.connectGatt(context, false, tactileGattCb, BluetoothDevice.TRANSPORT_LE)
        scheduleTimeout(tactileGatt, tactileState)
    }

    private fun scheduleTimeout(gatt: BluetoothGatt?, state: MutableLiveData<BleConnectionState>) {
        handler.postDelayed({
            if (state.value == BleConnectionState.CONNECTING) {
                Log.w(TAG, "Connection timeout")
                gatt?.disconnect()
                state.postValue(BleConnectionState.ERROR)
            }
        }, BleConstants.CONNECTION_TIMEOUT_MS)
    }

    // ── IMU GATT callback ─────────────────────────────────────────────────────
    private val imuGattCb = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "IMU connected, requesting MTU")
                    gatt.requestMtu(MTU_SIZE)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "IMU disconnected (status=$status)")
                    imuState.postValue(BleConnectionState.DISCONNECTED)
                    gatt.close()
                    if (imuGatt === gatt) imuGatt = null
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            Log.d(TAG, "MTU=$mtu, discovering services")
            gatt.discoverServices()
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                imuState.postValue(BleConnectionState.ERROR); return
            }
            val ch = gatt
                .getService(BleConstants.IMU_SERVICE_UUID)
                ?.getCharacteristic(BleConstants.IMU_DATA_CHARACTERISTIC_UUID)
            if (ch != null) {
                enableNotifications(gatt, ch)
                imuState.postValue(BleConnectionState.CONNECTED)
            } else {
                Log.e(TAG, "IMU data characteristic not found")
                imuState.postValue(BleConnectionState.ERROR)
            }
        }

        // API 33+
        override fun onCharacteristicChanged(gatt: BluetoothGatt, ch: BluetoothGattCharacteristic, value: ByteArray) {
            if (ch.uuid == BleConstants.IMU_DATA_CHARACTERISTIC_UUID) accumulate(value)
        }

        // API < 33
        @Suppress("DEPRECATION")
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            if (ch.uuid == BleConstants.IMU_DATA_CHARACTERISTIC_UUID) accumulate(ch.value)
        }
    }

    // ── Tactile GATT callback ─────────────────────────────────────────────────
    private val tactileGattCb = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED    -> gatt.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "Tactile disconnected (status=$status)")
                    tactileState.postValue(BleConnectionState.DISCONNECTED)
                    gatt.close()
                    if (tactileGatt === gatt) tactileGatt = null
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Tactile services discovered")
                tactileState.postValue(BleConnectionState.CONNECTED)
            } else {
                tactileState.postValue(BleConnectionState.ERROR)
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun enableNotifications(gatt: BluetoothGatt, ch: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(ch, true)
        val desc = ch.getDescriptor(BleConstants.CCCD_UUID) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(desc, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        } else {
            @Suppress("DEPRECATION")
            desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(desc)
        }
    }

    /**
     * Accumulates BLE notification chunks into a rolling 720-byte window.
     * Invokes [onPacketReceived] every time a complete window is assembled.
     * Overflow bytes from an over-sized delivery are kept at the front of the
     * next window — nothing is discarded.
     */
    private fun accumulate(data: ByteArray) {
        var src = 0
        while (src < data.size) {
            val remaining = BleConstants.PACKET_SIZE - bufferPos
            val toCopy    = minOf(data.size - src, remaining)
            System.arraycopy(data, src, dataBuffer, bufferPos, toCopy)
            bufferPos += toCopy
            src       += toCopy

            if (bufferPos == BleConstants.PACKET_SIZE) {
                onPacketReceived?.invoke(dataBuffer.copyOf())
                bufferPos = 0
            }
        }
    }

    /** Write a command to the Tactile actuator characteristic (NUS TX). */
    fun sendTactileCommand(on: Boolean) {
        val gatt = tactileGatt ?: run { Log.w(TAG, "Tactile not connected"); return }
        val ch   = gatt.getService(BleConstants.TACTILE_SERVICE_UUID)
                       ?.getCharacteristic(BleConstants.TACTILE_CONTROL_CHARACTERISTIC_UUID)
                   ?: run { Log.w(TAG, "Tactile characteristic missing"); return }
        val cmd  = if (on) BleConstants.TACTILE_ON_COMMAND else BleConstants.TACTILE_OFF_COMMAND
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(ch, cmd, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            ch.value = cmd
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(ch)
        }
    }

    fun disconnectAll() {
        stopScan()
        imuGatt?.disconnect()
        tactileGatt?.disconnect()
    }

    fun close() {
        disconnectAll()
        imuGatt?.close();     imuGatt     = null
        tactileGatt?.close(); tactileGatt = null
    }
}
