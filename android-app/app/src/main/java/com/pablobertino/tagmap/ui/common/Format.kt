package com.pablobertino.tagmap.ui.common

import androidx.compose.ui.graphics.Color
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val ColorRecent = Color(0xFF7CE0A3)
val ColorStale = Color(0xFFF5A524)
val ColorUnknown = Color(0xFF93A3B8)

/** Umbral reciente/antigua (spec §6.1). Configurable en Fase 3 desde profiles.recent_threshold_minutes. */
const val RECENT_THRESHOLD_MIN = 30

fun statusColor(ageMinutes: Int?): Color = when {
    ageMinutes == null -> ColorUnknown
    ageMinutes <= RECENT_THRESHOLD_MIN -> ColorRecent
    else -> ColorStale
}

fun ageText(ageMinutes: Int?): String = when {
    ageMinutes == null -> "sin datos"
    ageMinutes < 1 -> "hace instantes"
    ageMinutes < 60 -> "hace $ageMinutes min"
    ageMinutes < 60 * 48 -> "hace ${ageMinutes / 60} h"
    else -> "hace ${ageMinutes / 1440} d"
}

fun ageFrom(observed: OffsetDateTime?): Int? =
    observed?.let { Duration.between(it, OffsetDateTime.now()).toMinutes().toInt().coerceAtLeast(0) }

private val timeFmt = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
private val dateTimeFmt = DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale.getDefault())
private val fullFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

fun OffsetDateTime.local(): OffsetDateTime = atZoneSameInstant(ZoneId.systemDefault()).toOffsetDateTime()
fun OffsetDateTime.timeText(): String = local().format(timeFmt)
fun OffsetDateTime.dateTimeText(): String = local().format(dateTimeFmt)
fun OffsetDateTime.fullText(): String = local().format(fullFmt)

fun accuracyText(m: Float?): String = if (m == null) "precisión desconocida" else "±${m.toInt()} m"

fun qualityLabel(q: String?): String = when (q) {
    "good" -> "buena"
    "medium" -> "media"
    "low" -> "baja"
    else -> "-"
}

fun sourceLabel(s: String?): String = when (s) {
    "own_device" -> "teléfono propio"
    "find_hub_network" -> "red Find Hub"
    else -> "origen desconocido"
}

fun parseHexColor(hex: String, fallback: Color = Color(0xFF1E88E5)): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)
