package com.pablobertino.tagmap.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pablobertino.tagmap.TagMapApp
import com.pablobertino.tagmap.data.AppContainer
import com.pablobertino.tagmap.data.AppTracker
import com.pablobertino.tagmap.data.CollectorStatus
import com.pablobertino.tagmap.data.LocationPoint
import com.pablobertino.tagmap.data.Place
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset

private val CreationExtras.container: AppContainer
    get() = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TagMapApp).container

// ------------------------------------------------------------------ login

enum class AuthMode { SIGN_IN, SIGN_UP, RESET }

data class LoginUiState(
    val mode: AuthMode = AuthMode.SIGN_IN,
    val loading: Boolean = false,
    val error: String? = null,
    val info: String? = null,
)

class LoginViewModel(private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(LoginUiState())
    val ui: StateFlow<LoginUiState> = _ui.asStateFlow()
    val lastEmail: String get() = c.prefs.lastEmail

    fun setMode(m: AuthMode) { _ui.value = LoginUiState(mode = m) }

    fun submit(email: String, password: String) {
        val mode = _ui.value.mode
        if (email.isBlank() || (mode != AuthMode.RESET && password.length < 6)) {
            _ui.update { it.copy(error = if (email.isBlank()) "Ingresá tu email" else "La contraseña debe tener al menos 6 caracteres") }
            return
        }
        _ui.update { it.copy(loading = true, error = null, info = null) }
        viewModelScope.launch {
            runCatching {
                when (mode) {
                    AuthMode.SIGN_IN -> { c.authRepository.signIn(email, password); null }
                    AuthMode.SIGN_UP -> if (c.authRepository.signUp(email, password)) null
                        else "Te enviamos un correo para confirmar la cuenta. Abrí el link desde el teléfono."
                    AuthMode.RESET -> { c.authRepository.sendPasswordReset(email); "Te enviamos un correo con el link para cambiar la contraseña." }
                }
            }.onSuccess { info ->
                c.prefs.lastEmail = email.trim()
                com.pablobertino.tagmap.push.TagMapMessagingService.syncToken(c)
                _ui.update { it.copy(loading = false, info = info, mode = if (info != null) AuthMode.SIGN_IN else it.mode) }
            }.onFailure { e -> _ui.update { it.copy(loading = false, error = friendly(e)) } }
        }
    }

    companion object {
        val Factory = viewModelFactory { initializer { LoginViewModel(container) } }
    }
}

// --------------------------------------------------------------- trackers

data class TrackersUiState(
    val trackers: List<AppTracker> = emptyList(),
    val collectors: List<CollectorStatus> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    val lastSync: OffsetDateTime? = null,
)

class TrackersViewModel(private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(TrackersUiState())
    val ui: StateFlow<TrackersUiState> = _ui.asStateFlow()
    val mapStyle: StateFlow<String> = c.prefs.mapStyle
    fun setMapStyle(id: String) = c.prefs.setMapStyle(id)

    init { refresh() }

    fun refresh() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val trackers = c.tagRepository.trackers()
                val collectors = runCatching { c.tagRepository.collectors() }.getOrDefault(emptyList())
                trackers to collectors
            }.onSuccess { (t, col) ->
                _ui.value = TrackersUiState(t, col, loading = false, lastSync = OffsetDateTime.now())
            }.onFailure { e ->
                _ui.update { it.copy(loading = false, error = friendly(e)) }
            }
        }
    }

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { c.tagRepository.setTrackerEnabled(id, enabled) }.onSuccess { refresh() }
        }
    }

    fun signOut() { viewModelScope.launch { c.authRepository.signOut() } }

    companion object {
        val Factory = viewModelFactory { initializer { TrackersViewModel(container) } }
    }
}

// ----------------------------------------------------------------- detail

/** Rango de historial (spec §6.4): presets o fechas elegidas (días locales, inclusive). */
sealed class HistoryRange(val label: String) {
    data object Today : HistoryRange("Hoy")
    data object Three : HistoryRange("3 días")
    data object Week : HistoryRange("7 días")
    data class Custom(val from: LocalDate, val to: LocalDate) : HistoryRange("Elegir…")

    fun bounds(zone: ZoneId = ZoneId.systemDefault()): Pair<OffsetDateTime, OffsetDateTime> {
        val now = OffsetDateTime.now(ZoneOffset.UTC)
        return when (this) {
            Today -> now.minusDays(1) to now
            Three -> now.minusDays(3) to now
            Week -> now.minusDays(7) to now
            is Custom -> from.atStartOfDay(zone).toOffsetDateTime() to
                to.plusDays(1).atStartOfDay(zone).toOffsetDateTime()
        }
    }

    companion object {
        // getter, no val: evita el ciclo de inicialización sealed class ↔ data objects (NPE en getLabel)
        val presets: List<HistoryRange> get() = listOf(Today, Three, Week)
    }
}

data class DetailUiState(
    val tracker: AppTracker? = null,
    val range: HistoryRange = HistoryRange.Today,
    val points: List<LocationPoint> = emptyList(),
    val accuracyMax: Float? = null,
    val distanceM: Double = 0.0,
    val places: List<Place> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

class DetailViewModel(private val c: AppContainer, private val trackerId: String) : ViewModel() {
    private val _ui = MutableStateFlow(DetailUiState())
    val ui: StateFlow<DetailUiState> = _ui.asStateFlow()
    val mapStyle: StateFlow<String> = c.prefs.mapStyle
    fun setMapStyle(id: String) = c.prefs.setMapStyle(id)

    init { load() }

    fun setRange(r: HistoryRange) { _ui.update { it.copy(range = r) }; load() }
    fun setAccuracyMax(m: Float?) { _ui.update { it.copy(accuracyMax = m) }; load() }

    fun load() {
        val s = _ui.value
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val tracker = c.tagRepository.tracker(trackerId)
                val (from, to) = s.range.bounds()
                val pts = dedupNearby(c.tagRepository.locations(trackerId, from, to, accuracyMax = s.accuracyMax))
                val places = runCatching { c.placesRepository.places() }.getOrDefault(emptyList())
                Triple(tracker, pts, places)
            }.onSuccess { (t, pts, places) ->
                _ui.update { it.copy(tracker = t, points = pts, distanceM = travelledDistanceM(pts), places = places, loading = false) }
            }.onFailure { e ->
                _ui.update { it.copy(loading = false, error = friendly(e)) }
            }
        }
    }

    /** Elimina posiciones repetidas consecutivas (spec §6.4). */
    private fun dedupNearby(pts: List<LocationPoint>): List<LocationPoint> {
        val out = mutableListOf<LocationPoint>()
        for (p in pts) {
            val last = out.lastOrNull()
            if (last != null && haversineM(last.latitude, last.longitude, p.latitude, p.longitude) < 15) continue
            out.add(p)
        }
        return out
    }

    fun update(name: String, icon: String, color: String) {
        viewModelScope.launch {
            runCatching { c.tagRepository.updateTracker(trackerId, name.trim(), icon, color) }.onSuccess { load() }
        }
    }

    companion object {
        fun factory(trackerId: String) = viewModelFactory { initializer { DetailViewModel(container, trackerId) } }
    }
}

// ----------------------------------------------------------------- utils

/**
 * Distancia recorrida aproximada (spec §13): suma de tramos, descartando los que son ruido
 * (menores que la precisión combinada) o imposibles (más de 1000 km/h).
 */
fun travelledDistanceM(pts: List<LocationPoint>): Double {
    var total = 0.0
    pts.zipWithNext { a, b ->
        val d = haversineM(a.latitude, a.longitude, b.latitude, b.longitude)
        val noise = (a.accuracyM ?: 100f) + (b.accuracyM ?: 100f)
        val hours = Duration.between(a.observed, b.observed).toMillis() / 3_600_000.0
        val kmh = if (hours > 0) d / 1000 / hours else Double.MAX_VALUE
        if (d > noise && kmh <= 1000) total += d
    }
    return total
}

fun distanceText(m: Double): String = when {
    m < 1000 -> "${m.toInt()} m"
    m < 100_000 -> String.format(java.util.Locale.getDefault(), "%.1f km", m / 1000)
    else -> "${(m / 1000).toInt()} km"
}

fun haversineM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val r = 6371008.8
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
        Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2)
    return 2 * r * Math.asin(Math.sqrt(a))
}

fun friendly(e: Throwable): String {
    val m = e.message ?: e::class.simpleName ?: "error"
    return when {
        m.contains("Invalid login", true) || m.contains("invalid_credentials", true) -> "Email o contraseña incorrectos"
        m.contains("already registered", true) || m.contains("user_already_exists", true) -> "Ese email ya tiene cuenta"
        m.contains("rate limit", true) -> "Demasiados intentos, esperá un minuto"
        m.contains("UnknownHost", true) || m.contains("Unable to resolve", true) -> "Sin conexión"
        m.contains("PGRST106", true) -> "El schema tagmap no está expuesto en la API de Supabase"
        else -> m.take(200)
    }
}
