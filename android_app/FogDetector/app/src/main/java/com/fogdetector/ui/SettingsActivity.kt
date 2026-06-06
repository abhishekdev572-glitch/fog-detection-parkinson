package com.fogdetector.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.fogdetector.databinding.ActivitySettingsBinding
import com.fogdetector.util.PreferencesManager
import kotlin.math.roundToInt

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbarSettings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val current = PreferencesManager.getFogThreshold(this)
        // Slider range 5..95 → map to 0.05..0.95
        binding.sliderThreshold.value = (current * 100f).roundToInt().toFloat().coerceIn(5f, 95f)
        updateThresholdLabel(current)

        binding.sliderThreshold.addOnChangeListener { _, value, _ ->
            val thresh = value / 100f
            updateThresholdLabel(thresh)
            PreferencesManager.setFogThreshold(this, thresh)
        }

        binding.switchAutoTactile.isChecked = PreferencesManager.getAutoTactile(this)
        binding.switchAutoTactile.setOnCheckedChangeListener { _, checked ->
            PreferencesManager.setAutoTactile(this, checked)
        }

        binding.switchAutoNotify.isChecked = PreferencesManager.getAutoNotify(this)
        binding.switchAutoNotify.setOnCheckedChangeListener { _, checked ->
            PreferencesManager.setAutoNotify(this, checked)
        }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    private fun updateThresholdLabel(v: Float) {
        binding.tvThresholdValue.text = "FOG Threshold: ${(v * 100).roundToInt()} %"
    }
}
