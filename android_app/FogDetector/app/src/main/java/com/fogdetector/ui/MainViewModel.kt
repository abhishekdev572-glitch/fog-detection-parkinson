package com.fogdetector.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import androidx.lifecycle.switchMap
import com.fogdetector.ble.BleConnectionState
import com.fogdetector.ble.BleManager
import com.fogdetector.ml.FogDetectionModel
import com.fogdetector.ml.PredictionResult
import com.fogdetector.util.PreferencesManager

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val bleManager = BleManager(application)
    private val fogModel   = FogDetectionModel(application)

    // ── Observables ──────────────────────────────────────────────────────────
    val imuState     = bleManager.imuState
    val tactileState = bleManager.tactileState

    val prediction   = MutableLiveData<PredictionResult?>()
    val modelLoaded  = MutableLiveData(false)
    val fogThreshold = MutableLiveData(PreferencesManager.getFogThreshold(application))

    val bothConnected = imuState.map { it.isConnected }  // simple proxy; activity also checks tactile

    // ── Init ─────────────────────────────────────────────────────────────────
    init {
        bleManager.onPacketReceived = { rawPacket ->
            val threshold = fogThreshold.value ?: PreferencesManager.DEFAULT_FOG_THRESHOLD
            val result    = fogModel.predict(rawPacket, threshold)
            prediction.postValue(result)
        }
        modelLoaded.value = fogModel.load()
    }

    // ── BLE actions ──────────────────────────────────────────────────────────
    fun startScan()  = bleManager.startScan()

    fun disconnect() {
        bleManager.disconnectAll()
    }

    fun sendTactileOn()  = bleManager.sendTactileCommand(true)
    fun sendTactileOff() = bleManager.sendTactileCommand(false)

    // ── Settings ─────────────────────────────────────────────────────────────
    fun updateThreshold(v: Float) {
        val app = getApplication<Application>()
        PreferencesManager.setFogThreshold(app, v)
        fogThreshold.value = v
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────
    override fun onCleared() {
        super.onCleared()
        bleManager.close()
        fogModel.close()
    }
}
