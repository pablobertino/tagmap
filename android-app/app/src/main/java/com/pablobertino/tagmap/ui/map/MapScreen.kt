package com.pablobertino.tagmap.ui.map

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pablobertino.tagmap.ui.PlacesViewModel
import com.pablobertino.tagmap.ui.TrackersViewModel
import com.pablobertino.tagmap.ui.places.PlaceEditorDialog
import com.pablobertino.tagmap.ui.common.RECENT_THRESHOLD_MIN
import com.pablobertino.tagmap.ui.common.TagIcon
import com.pablobertino.tagmap.ui.common.ageText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onBack: () -> Unit,
    onOpenTracker: (String) -> Unit,
    onOpenPlaces: () -> Unit,
    vm: TrackersViewModel = viewModel(factory = TrackersViewModel.Factory),
    placesVm: PlacesViewModel = viewModel(factory = PlacesViewModel.Factory),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val placesUi by placesVm.ui.collectAsStateWithLifecycle()
    var newPlaceAt by remember { mutableStateOf<Pair<Double, Double>?>(null) }
    val places = placesUi.places.map { MapPlace(it.id, it.name, it.latitude, it.longitude, it.radiusM, it.color) }
    val styleId by vm.mapStyle.collectAsStateWithLifecycle()
    var focusCounter by remember { mutableIntStateOf(0) }
    var selectedId by remember { mutableStateOf<String?>(null) }

    val visible = ui.trackers.filter { it.enabled && it.hasLocation }
    val markers = visible.map { t ->
        MapMarker(
            id = t.id, label = t.name, lat = t.latitude!!, lon = t.longitude!!,
            colorHex = t.color, accuracyM = t.accuracyM,
            stale = (t.ageMinutes ?: Int.MAX_VALUE) > RECENT_THRESHOLD_MIN,
        )
    }
    // Sin selección: centrar en el tag con reporte más reciente (spec §6.2), no en todo el continente.
    val newest = visible.minByOrNull { it.ageMinutes ?: Int.MAX_VALUE }
    val focusTarget = visible.firstOrNull { it.id == selectedId } ?: newest
    val focus: MapFocus = if (selectedId == null && focusCounter % 2 == 1) MapFocus.FitAll
        else focusTarget?.let { MapFocus.Center(it.latitude!!, it.longitude!!, 14.0) } ?: MapFocus.FitAll

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mapa") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                actions = {
                    MapStyleMenu(MapStyle.byId(styleId)) { vm.setMapStyle(it.id) }
                    IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, "Actualizar") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { selectedId = null; focusCounter++ }) {
                Icon(Icons.Default.CenterFocusStrong, "Ver todos / más reciente")
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
                visible.forEach { t ->
                    val ic = TagIcon.resolve(t.icon, t.kind)
                    FilterChip(
                        selected = t.id == selectedId,
                        onClick = { selectedId = t.id; focusCounter++ },
                        label = { Text(t.name) },
                        leadingIcon = { Icon(ic.vector, null) },
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
            }
            Box(Modifier.fillMaxSize()) {
                TagMapView(
                    markers = markers,
                    places = places,
                    style = MapStyle.byId(styleId),
                    focus = focus,
                    focusKey = "${markers.size}-$selectedId-$focusCounter",
                    modifier = Modifier.fillMaxSize(),
                    onMarkerClick = onOpenTracker,
                    onPlaceClick = { onOpenPlaces() },
                    onLongPress = { lat, lon -> newPlaceAt = lat to lon },
                )
                Surface(tonalElevation = 3.dp, modifier = Modifier.align(Alignment.TopCenter).padding(8.dp)) {
                    Text(
                        "Último reporte: ${ageText(newest?.ageMinutes)} · no es en vivo · mantené pulsado para crear un lugar",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }

    newPlaceAt?.let { (lat, lon) ->
        PlaceEditorDialog(
            initial = null, lat = lat, lon = lon,
            onDismiss = { newPlaceAt = null },
            onSave = { name, radius, color -> placesVm.create(name, lat, lon, radius, "place", color); newPlaceAt = null },
        )
    }
}
