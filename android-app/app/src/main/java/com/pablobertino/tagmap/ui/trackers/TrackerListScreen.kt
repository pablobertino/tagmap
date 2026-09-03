package com.pablobertino.tagmap.ui.trackers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.pablobertino.tagmap.ui.TrackersViewModel
import com.pablobertino.tagmap.ui.common.AboutDialog
import com.pablobertino.tagmap.ui.common.TagIcon
import com.pablobertino.tagmap.ui.common.accuracyText
import com.pablobertino.tagmap.ui.common.parseHexColor
import com.pablobertino.tagmap.ui.common.ageText
import com.pablobertino.tagmap.ui.common.statusColor
import com.pablobertino.tagmap.ui.common.timeText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerListScreen(
    onOpenMap: () -> Unit,
    onOpenTracker: (String) -> Unit,
    onOpenPlaces: () -> Unit,
    onOpenEvents: () -> Unit,
    vm: TrackersViewModel = viewModel(factory = TrackersViewModel.Factory),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var about by remember { mutableStateOf(false) }
    if (about) AboutDialog { about = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tags") },
                actions = {
                    IconButton(onClick = onOpenPlaces) { Icon(Icons.Default.Place, "Lugares") }
                    IconButton(onClick = onOpenEvents) { Icon(Icons.Default.Notifications, "Eventos") }
                    IconButton(onClick = vm::refresh) { Icon(Icons.Default.Refresh, "Actualizar") }
                    IconButton(onClick = { about = true }) { Icon(Icons.Default.Info, "Acerca de") }
                    IconButton(onClick = vm::signOut) { Icon(Icons.AutoMirrored.Filled.Logout, "Salir") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onOpenMap) { Icon(Icons.Default.Map, "Mapa") }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(Modifier.fillMaxSize()) {
                item { SystemStatusBar(ui.collectors, ui.lastSync?.timeText()) }
                ui.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
                items(ui.trackers, key = { it.id }) { t ->
                    TrackerCard(t, onClick = { onOpenTracker(t.id) })
                }
                if (!ui.loading && ui.trackers.isEmpty() && ui.error == null) {
                    item { Text("Todavía no hay tags sincronizados.", Modifier.padding(16.dp)) }
                }
            }
            if (ui.loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun SystemStatusBar(collectors: List<com.pablobertino.tagmap.data.CollectorStatus>, lastSync: String?) {
    val bad = collectors.filter { it.status != "ok" }
    val text = when {
        bad.isNotEmpty() -> "Recolector con problemas: ${bad.joinToString { "${it.id} (${it.status})" }}"
        lastSync != null -> "Sincronizado $lastSync · las posiciones no son en tiempo real"
        else -> "Cargando…"
    }
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = if (bad.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
fun TrackerCard(t: AppTracker, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).clickable(onClick = onClick)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Icon(
                    TagIcon.resolve(t.icon, t.kind).vector, null,
                    tint = parseHexColor(t.color), modifier = Modifier.size(32.dp),
                )
                Box(Modifier.size(12.dp).background(statusColor(t.ageMinutes), CircleShape))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t.name, style = MaterialTheme.typography.titleMedium)
                val sub = if (t.hasLocation) {
                    listOfNotNull(
                        ageText(t.ageMinutes),
                        t.observedAt?.timeText(),
                        accuracyText(t.accuracyM),
                        t.atPlaces?.let { "en $it" },
                    ).joinToString(" · ")
                } else "Sin posición todavía"
                Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
