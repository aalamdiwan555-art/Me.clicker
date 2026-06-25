package com.drclicker.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.drclicker.diagnostics.databinding.ActivityMainBinding
import com.drclicker.diagnostics.services.AutoAcceptEngineService
import com.drclicker.diagnostics.services.FloatingOverlayService

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: SharedPreferences
    private lateinit var engineToggle: SwitchCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("dr_clicker_prefs", Context.MODE_PRIVATE)
        engineToggle = binding.engineToggle

        setupEngineToggle()
        setupTemplateUpload()
        loadSavedPreferences()
    }

    private fun setupEngineToggle() {
        engineToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                performActivationChecks()
            } else {
                deactivateEngine()
            }
        }
    }

    private fun performActivationChecks() {
        // Check A: Accessibility Service
        if (!isAccessibilityServiceEnabled()) {
            Toast.makeText(
                this,
                getString(R.string.enable_accessibility),
                Toast.LENGTH_LONG
            ).show()
            engineToggle.isChecked = false
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            return
        }

        // Check B: Overlay Permission
        if (!Settings.canDrawOverlays(this)) {
            prefs.edit().putBoolean("pending_activation", true).apply()
            Toast.makeText(
                this,
                getString(R.string.enable_overlay),
                Toast.LENGTH_LONG
            ).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return
        }

        // Check C: Activation
        activateEngine()
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains("com.drclicker.diagnostics/.services.AutoAcceptEngineService")
    }

    private fun activateEngine() {
        // Start Floating Overlay Service
        val overlayIntent = Intent(this, FloatingOverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(overlayIntent)
        } else {
            startService(overlayIntent)
        }

        // Start AutoAccept Engine
        val engineIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        engineIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(engineIntent)

        // Update UI
        binding.statusText.text = "Online - Engine Active"
        binding.statusIndicator.setBackgroundColor(getColor(R.color.status_online))

        prefs.edit().putBoolean("engine_active", true).apply()
    }

    private fun deactivateEngine() {
        // Stop Floating Overlay Service
        val overlayIntent = Intent(this, FloatingOverlayService::class.java)
        stopService(overlayIntent)

        // Update UI
        binding.statusText.text = "Offline - Tap to activate"
        binding.statusIndicator.setBackgroundColor(getColor(R.color.status_offline))

        prefs.edit().putBoolean("engine_active", false).apply()
    }

    private fun setupTemplateUpload() {
        binding.uploadTemplateBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK).apply {
                type = "image/*"
            }
            startActivityForResult(intent, REQUEST_TEMPLATE_PICK)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_TEMPLATE_PICK && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                binding.templatePreview.setImageURI(uri)
                // Save template URI
                prefs.edit().putString("template_uri", uri.toString()).apply()
                Toast.makeText(this, "Template saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadSavedPreferences() {
        // Load price range
        val minPrice = prefs.getString("min_price", "") ?: ""
        val maxPrice = prefs.getString("max_price", "") ?: ""
        binding.minPriceInput.setText(minPrice)
        binding.maxPriceInput.setText(maxPrice)

        // Load distance range
        val minPickup = prefs.getString("min_pickup", "") ?: ""
        val maxDrop = prefs.getString("max_drop", "") ?: ""
        binding.minPickupInput.setText(minPickup)
        binding.maxDropInput.setText(maxDrop)

        // Restore engine state
        if (prefs.getBoolean("engine_active", false)) {
            binding.statusText.text = "Online - Engine Active"
            binding.statusIndicator.setBackgroundColor(getColor(R.color.status_online))
        }
    }

    override fun onPause() {
        super.onPause()
        // Save current preferences
        prefs.edit().apply {
            putString("min_price", binding.minPriceInput.text.toString())
            putString("max_price", binding.maxPriceInput.text.toString())
            putString("min_pickup", binding.minPickupInput.text.toString())
            putString("max_drop", binding.maxDropInput.text.toString())
            apply()
        }
    }

    companion object {
        private const val REQUEST_TEMPLATE_PICK = 1001
    }
}
