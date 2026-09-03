package com.pablobertino.tagmap.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Preferencias livianas por dispositivo (spec §6.7). */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("tagmap", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(sp.getString("theme", "nautical") ?: "nautical")
    val theme: StateFlow<String> = _theme

    private val _mapStyle = MutableStateFlow(sp.getString("map_style", null) ?: defaultMapFor(_theme.value))
    val mapStyle: StateFlow<String> = _mapStyle

    fun setTheme(id: String) {
        sp.edit().putString("theme", id).apply()
        _theme.value = id
        // si el usuario no eligió mapa a mano, el mapa sigue al tema
        if (!sp.contains("map_style")) _mapStyle.value = defaultMapFor(id)
    }

    fun setMapStyle(id: String) {
        sp.edit().putString("map_style", id).apply()
        _mapStyle.value = id
    }

    private fun defaultMapFor(theme: String) = if (theme == "light") "streets" else "night"

    var lastEmail: String
        get() = sp.getString("last_email", "") ?: ""
        set(v) = sp.edit().putString("last_email", v).apply()
}
