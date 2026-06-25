package com.drclicker.diagnostics.utils

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "dr_clicker_prefs",
        Context.MODE_PRIVATE
    )

    // Price Range
    fun getMinPrice(): Int = prefs.getString("min_price", "")?.let {
        if (it.isEmpty()) 0 else it.toIntOrNull() ?: 0
    } ?: 0

    fun getMaxPrice(): Int = prefs.getString("max_price", "")?.let {
        if (it.isEmpty()) 99999 else it.toIntOrNull() ?: 99999
    } ?: 99999

    // Distance Range
    fun getMinPickup(): Float = prefs.getString("min_pickup", "")?.let {
        if (it.isEmpty()) 0.0f else it.toFloatOrNull() ?: 0.0f
    } ?: 0.0f

    fun getMaxDrop(): Float = prefs.getString("max_drop", "")?.let {
        if (it.isEmpty()) 999.0f else it.toFloatOrNull() ?: 999.0f
    } ?: 999.0f

    // Engine State
    fun isEngineActive(): Boolean = prefs.getBoolean("engine_active", false)

    fun setEngineActive(active: Boolean) {
        prefs.edit().putBoolean("engine_active", active).apply()
    }

    // Template
    fun getTemplateUri(): String? = prefs.getString("template_uri", null)

    fun setTemplateUri(uri: String) {
        prefs.edit().putString("template_uri", uri).apply()
    }
}
