package com.pablobertino.tagmap.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Preferencias livianas por dispositivo (spec §6.7). */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("tagmap", Context.MODE_PRIVATE)

    private val _mapStyle = MutableStateFlow(sp.getString("map_style", "streets") ?: "streets")
    val mapStyle: StateFlow<String> = _mapStyle

    fun setMapStyle(id: String) {
        sp.edit().putString("map_style", id).apply()
        _mapStyle.value = id
    }

    var lastEmail: String
        get() = sp.getString("last_email", "") ?: ""
        set(v) = sp.edit().putString("last_email", v).apply()
}
