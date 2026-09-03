package com.pablobertino.tagmap.ui.places

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pablobertino.tagmap.data.AppTracker
import com.pablobertino.tagmap.data.GeofenceRule
import com.pablobertino.tagmap.data.Place
import com.pablobertino.tagmap.ui.PlacesViewModel
import com.pablobertino.tagmap.ui.common.TagIcon
import com.pablobertino.tagmap.ui.common.parseHexColor

/**
 * Lugares favoritos y, por cada uno, qué tags avisan al llegar / salir (spec §6.5).
 * Los lugares se crean manteniendo pulsado el mapa (pantalla Mapa).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesScreen(
    onBack: () -> Unit,
    vm: PlacesViewModel = viewModel(factory = PlacesViewModel.Factory),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Place?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lugares") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                actions = { IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, "Actualizar") } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(Modifier.fillMaxSize()) {
                item {
                    Text(
                        "Para crear un lugar, mantené pulsado el mapa donde quieras el centro. " +
                            "Las alarmas se evalúan con cada reporte nuevo (cada ~15 min), no al instante.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                ui.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
                items(ui.places, key = { it.id }) { p ->
                    PlaceCard(
                        place = p,
                        trackers = ui.trackers.filter { it.enabled },
                        rules = ui.rules.filter { it.placeId == p.id },
                        onEdit = { editing = p },
                        onToggle = { trackerId, entry, exit -> vm.setRule(trackerId, p.id, entry, exit) },
                    )
                }
                if (!ui.loading && ui.places.isEmpty()) {
                    item { Text("Todavía no hay lugares.", Modifier.padding(16.dp)) }
                }
            }
            if (ui.loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }

    editing?.let { p ->
        PlaceEditorDialog(
            initial = p, lat = p.latitude, lon = p.longitude,
            onDismiss = { editing = null },
            onSave = { name, radius, color -> vm.update(p.copy(name = name, radiusM = radius, color = color)); editing = null },
            onDelete = { vm.delete(p.id); editing = null },
        )
    }
}

@Composable
private fun PlaceCard(
    place: Place,
    trackers: List<AppTracker>,
    rules: List<GeofenceRule>,
    onEdit: () -> Unit,
    onToggle: (trackerId: String, entry: Boolean, exit: Boolean) -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(16.dp).background(parseHexColor(place.color), CircleShape))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(place.name, style = MaterialTheme.typography.titleMedium)
                    Text("radio ${place.radiusM} m", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Editar") }
            }
            Spacer(Modifier.size(8.dp))
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                Text("Llega", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(56.dp))
                Text("Sale", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(56.dp))
            }
            trackers.forEach { t ->
                val r = rules.firstOrNull { it.trackerId == t.id }
                val entry = r?.notifyEntry == true
                val exit = r?.notifyExit == true
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(TagIcon.resolve(t.icon, t.kind).vector, null, tint = parseHexColor(t.color), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.name, style = MaterialTheme.typography.bodyMedium)
                        r?.let {
                            Text(
                                when (it.currentState) {
                                    "INSIDE" -> "ahora: dentro"; "OUTSIDE" -> "ahora: fuera"
                                    "ENTRY_PENDING" -> "confirmando llegada…"; "EXIT_PENDING" -> "confirmando salida…"
                                    else -> "sin estado todavía"
                                },
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Box(Modifier.width(56.dp)) { Switch(checked = entry, onCheckedChange = { onToggle(t.id, it, exit) }) }
                    Box(Modifier.width(56.dp)) { Switch(checked = exit, onCheckedChange = { onToggle(t.id, entry, it) }) }
                }
            }
        }
    }
}
