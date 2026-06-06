package com.fogdetector.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.fogdetector.R
import com.fogdetector.ble.BleConnectionState
import com.fogdetector.databinding.ActivityMainBinding
import com.fogdetector.ml.PredictionResult
import com.fogdetector.notification.NotificationHelper
import com.fogdetector.util.PreferencesManager

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val vm: MainViewModel by viewModels()

    // Track previous FOG state to avoid repeated notifications
    private var prevFog = false

    // ── Permission launcher ──────────────────────────────────────────────────
    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val denied = results.filterValues { !it }.keys
            if (denied.isEmpty()) {
                startScanIfReady()
            } else {
                Toast.makeText(
                    this,
                    "Permissions required: ${denied.joinToString()}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    // ── Blink animation for FOG indicator ────────────────────────────────────
    private val blinkAnim = AlphaAnimation(1f, 0.2f).apply {
        duration       = 500
        repeatMode     = Animation.REVERSE
        repeatCount    = Animation.INFINITE
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        setupButtons()
        observeViewModel()
        checkModelOrWarn()
        requestPermissionsIfNeeded()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_settings) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // ── UI setup ──────────────────────────────────────────────────────────────
    private fun setupButtons() {
        binding.btnScan.setOnClickListener {
            requestPermissionsIfNeeded()
        }
        binding.btnDisconnect.setOnClickListener {
            vm.disconnect()
            prevFog = false
        }
        binding.btnTactileOn.setOnClickListener  { vm.sendTactileOn() }
        binding.btnTactileOff.setOnClickListener { vm.sendTactileOff() }
    }

    // ── ViewModel observers ───────────────────────────────────────────────────
    private fun observeViewModel() {

        vm.imuState.observe(this) { state ->
            binding.tvImuStatus.text  = "IMU:     ${state.label()}"
            binding.tvImuStatus.setTextColor(state.color())
            updateScanButton()
        }

        vm.tactileState.observe(this) { state ->
            binding.tvTactileStatus.text = "Tactile: ${state.label()}"
            binding.tvTactileStatus.setTextColor(state.color())
            binding.btnTactileOn.isEnabled  = state.isConnected
            binding.btnTactileOff.isEnabled = state.isConnected
            updateScanButton()
        }

        vm.prediction.observe(this) { result ->
            result ?: return@observe
            renderPrediction(result)
        }

        vm.modelLoaded.observe(this) { loaded ->
            if (!loaded) showModelMissingDialog()
        }
    }

    // ── Prediction rendering ─────────────────────────────────────────────────
    private fun renderPrediction(r: PredictionResult) {
        // FOG status card
        val fogColor = if (r.isFog)
            ContextCompat.getColor(this, R.color.fog_red)
        else
            ContextCompat.getColor(this, R.color.fog_green)

        binding.cardFogStatus.setCardBackgroundColor(fogColor)
        binding.tvFogLabel.text = if (r.isFog) "⚠  FOG DETECTED" else "✓  NORMAL"

        if (r.isFog) binding.tvFogLabel.startAnimation(blinkAnim)
        else         binding.tvFogLabel.clearAnimation()

        binding.tvFogPercent.text = "${r.fogPercent} %"
        binding.progressFog.progress = r.fogPercent

        // Activity
        binding.tvActivityLabel.text = r.activityLabel
        binding.tvActivityOther.text      = "Other:       ${fmt(r.activityProbs[0])}"
        binding.tvActivityStationary.text = "Stationary: ${fmt(r.activityProbs[1])}"
        binding.tvActivityWalking.text    = "Walking:     ${fmt(r.activityProbs[2])}"

        // Auto-tactile
        if (PreferencesManager.getAutoTactile(this)) {
            if (r.isFog)  vm.sendTactileOn()
            else          vm.sendTactileOff()
        }

        // Auto-notify — only fire on transition to FOG
        if (r.isFog && !prevFog && PreferencesManager.getAutoNotify(this)) {
            NotificationHelper.notifyFogDetected(this, r.fogPercent)
        }
        if (!r.isFog && prevFog) NotificationHelper.cancelFogNotification(this)
        prevFog = r.isFog
    }

    // ── Permissions ───────────────────────────────────────────────────────────
    private fun requestPermissionsIfNeeded() {
        val needed = buildList<String> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!granted(Manifest.permission.BLUETOOTH_SCAN))    add(Manifest.permission.BLUETOOTH_SCAN)
                if (!granted(Manifest.permission.BLUETOOTH_CONNECT)) add(Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                if (!granted(Manifest.permission.ACCESS_FINE_LOCATION)) add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (!granted(Manifest.permission.POST_NOTIFICATIONS)) add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (needed.isEmpty()) startScanIfReady()
        else permLauncher.launch(needed.toTypedArray())
    }

    private fun granted(perm: String) =
        ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED

    private fun startScanIfReady() {
        if (vm.imuState.value?.isBusy == false && vm.tactileState.value?.isBusy == false) {
            vm.startScan()
        }
    }

    private fun updateScanButton() {
        val busy = vm.imuState.value?.isBusy == true || vm.tactileState.value?.isBusy == true
        val bothConn = vm.imuState.value?.isConnected == true && vm.tactileState.value?.isConnected == true
        binding.btnScan.isEnabled       = !busy && !bothConn
        binding.btnDisconnect.isEnabled = vm.imuState.value?.isConnected == true || vm.tactileState.value?.isConnected == true
    }

    // ── Model missing dialog ──────────────────────────────────────────────────
    private fun checkModelOrWarn() {
        try {
            assets.open("fog_model.tflite").close()
        } catch (_: Exception) {
            showModelMissingDialog()
        }
    }

    private fun showModelMissingDialog() {
        AlertDialog.Builder(this)
            .setTitle("Model file missing")
            .setMessage(
                "Place your compiled TFLite model at:\n\n" +
                "  app/src/main/assets/fog_model.tflite\n\n" +
                "Expected spec:\n" +
                "  Input:    [1, 720] int8\n" +
                "  Output[0]: [1, 3]  int8  (activity)\n" +
                "  Output[1]: [1, 1]  int8  (FOG)"
            )
            .setPositiveButton("OK", null)
            .show()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private fun fmt(f: Float) = "${(f * 100).toInt()} %"

    private fun BleConnectionState.label() = when (this) {
        BleConnectionState.IDLE         -> "Idle"
        BleConnectionState.SCANNING     -> "Scanning…"
        BleConnectionState.FOUND        -> "Found"
        BleConnectionState.CONNECTING   -> "Connecting…"
        BleConnectionState.CONNECTED    -> "Connected ✓"
        BleConnectionState.NOT_FOUND    -> "Not Found"
        BleConnectionState.ERROR        -> "Error ✗"
        BleConnectionState.DISCONNECTED -> "Disconnected"
    }

    private fun BleConnectionState.color() = ContextCompat.getColor(
        this@MainActivity,
        when (this) {
            BleConnectionState.CONNECTED  -> R.color.connected_green
            BleConnectionState.SCANNING,
            BleConnectionState.CONNECTING,
            BleConnectionState.FOUND      -> R.color.connecting_amber
            else                          -> R.color.disconnected_red
        }
    )
}
