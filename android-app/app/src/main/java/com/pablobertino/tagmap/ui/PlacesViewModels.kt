package com.pablobertino.tagmap.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pablobertino.tagmap.TagMapApp
import com.pablobertino.tagmap.data.AppContainer
import com.pablobertino.tagmap.data.AppEvent
import com.pablobertino.tagmap.data.AppTracker
import com.pablobertino.tagmap.data.GeofenceRule
import com.pablobertino.tagmap.data.Place
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val CreationExtras.container: AppContainer
    get() = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TagMapApp).container

// ----------------------------------------------------------------- places

data class PlacesUiState(
    val places: List<Place> = emptyList(),
    val rules: List<GeofenceRule> = emptyList(),
    val trackers: List<AppTracker> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

class PlacesViewModel(private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(PlacesUiState())
    val ui: StateFlow<PlacesUiState> = _ui.asStateFlow()
    val mapStyle: StateFlow<String> = c.prefs.mapStyle

    init { refresh() }

    fun refresh() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching {
                Triple(c.placesRepository.places(), c.placesRepository.rules(), c.tagRepository.trackers())
            }.onSuccess { (p, r, t) ->
                _ui.value = PlacesUiState(p, r, t, loading = false)
            }.onFailure { e -> _ui.update { it.copy(loading = false, error = friendly(e)) } }
        }
    }

    fun create(name: String, lat: Double, lon: Double, radiusM: Int, icon: String, color: String, onDone: (Place) -> Unit = {}) {
        viewModelScope.launch {
            runCatching { c.placesRepository.createPlace(name, lat, lon, radiusM, icon, color) }
                .onSuccess { onDone(it); refresh() }
                .onFailure { e -> _ui.update { it.copy(error = friendly(e)) } }
        }
    }

    fun update(p: Place) {
        viewModelScope.launch {
            runCatching { c.placesRepository.updatePlace(p) }
                .onSuccess { refresh() }
                .onFailure { e -> _ui.update { it.copy(error = friendly(e)) } }
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            runCatching { c.placesRepository.deletePlace(id) }
                .onSuccess { refresh() }
                .onFailure { e -> _ui.update { it.copy(error = friendly(e)) } }
        }
    }

    fun setRule(trackerId: String, placeId: String, entry: Boolean, exit: Boolean) {
        viewModelScope.launch {
            runCatching { c.placesRepository.setRule(trackerId, placeId, entry, exit) }
                .onSuccess { refresh() }
                .onFailure { e -> _ui.update { it.copy(error = friendly(e)) } }
        }
    }

    fun rule(trackerId: String, placeId: String): GeofenceRule? =
        _ui.value.rules.firstOrNull { it.trackerId == trackerId && it.placeId == placeId }

    companion object {
        val Factory = viewModelFactory { initializer { PlacesViewModel(container) } }
    }
}

// ----------------------------------------------------------------- events

data class EventsUiState(
    val events: List<AppEvent> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

class EventsViewModel(private val c: AppContainer) : ViewModel() {
    private val _ui = MutableStateFlow(EventsUiState())
    val ui: StateFlow<EventsUiState> = _ui.asStateFlow()

    init { refresh() }

    fun refresh() {
        _ui.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            runCatching { c.placesRepository.events() }
                .onSuccess { _ui.value = EventsUiState(it, loading = false) }
                .onFailure { e -> _ui.update { it.copy(loading = false, error = friendly(e)) } }
        }
    }

    fun markAllRead() {
        val ids = _ui.value.events.filter { it.unread }.map { it.id }
        viewModelScope.launch {
            runCatching { c.placesRepository.markRead(ids) }.onSuccess { refresh() }
        }
    }

    companion object {
        val Factory = viewModelFactory { initializer { EventsViewModel(container) } }
    }
}
