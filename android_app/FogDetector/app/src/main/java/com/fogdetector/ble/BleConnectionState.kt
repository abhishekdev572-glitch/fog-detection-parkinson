package com.fogdetector.ble

enum class BleConnectionState {
    IDLE,
    SCANNING,
    FOUND,
    CONNECTING,
    CONNECTED,
    NOT_FOUND,
    ERROR,
    DISCONNECTED;

    val isConnected get() = this == CONNECTED
    val isBusy      get() = this == SCANNING || this == CONNECTING || this == FOUND
}
