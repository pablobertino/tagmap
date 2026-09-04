package com.pablobertino.tagmap.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pablobertino.tagmap.data.LocationPoint
import com.pablobertino.tagmap.ui.DetailViewModel
import com.pablobertino.tagmap.ui.HistoryRange
import com.pablobertino.tagmap.ui.common.RECENT_THRESHOLD_MIN
import com.pablobertino.tagmap.ui.common.TagIcon
import com.pablobertino.tagmap.ui.common.accuracyText
import com.pablobertino.tagmap.ui.common.ageText
import com.pablobertino.tagmap.ui.common.dateTimeText
import com.pablobertino.tagmap.ui.common.fullText
import com.pablobertino.tagmap.ui.common.parseHexColor
import com.pablobertino.tagmap.ui.common.qualityLabel
import com.pablobertino.tagmap.ui.common.sourceLabel
import com.pablobertino.tagmap.ui.distanceText
import com.pablobertino.tagmap.ui.map.MapFocus
import com.pablobertino.tagmap.ui.map.MapMarker
import com.pablobertino.tagmap.ui.map.MapPathPoint
import com.pablobertino.tagmap.ui.map.MapPlace
import com.pablobertino.tagmap.ui.map.MapStyle
import com.pablobertino.tagmap.ui.map.MapStyleMenu
import com.pablobertino.tagmap.ui.map.TagMapView
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Hueco entre reportes a partir del cual el tramo se dibuja discontinuo (spec §13). */
private const val GAP_HOURS = 3L

val TAG_COLORS = listOf("#1E88E5", "#E53935", "#43A047", "#FB8C00", "#8E24AA", "#00ACC1", "#6D4C41", "#546E7A")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackerDetailScreen(
    trackerId: String,
    onBack: () -> Unit,
    vm: DetailViewModel = viewModel(factory = DetailViewModel.factory(trackerId)),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val styleId by vm.mapStyle.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    var pickingDates by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    val t = ui.tracker
    val isOwner = t?.isOwner != false
    LaunchedEffect(t?.id, t?.isOwner) { if (t != null) vm.loadShares() }

    val path = ui.points.mapIndexed { i, p ->
        val gap = i > 0 && Duration.between(ui.points[i - 1].observed, p.observed).toHours() >= GAP_HOURS
        MapPathPoint(p.latitude, p.longitude, gap)
    }
    val marker = t?.takeIf { it.hasLocation }?.let {
        MapMarker(it.id, it.name, it.latitude!!, it.longitude!!, it.color, it.accuracyM,
            stale = (it.ageMinutes ?: Int.MAX_VALUE) > RECENT_THRESHOLD_MIN)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        t?.let { Icon(TagIcon.resolve(it.icon, it.kind).vector, null, tint = parseHexColor(it.color)) }
                        Spacer(Modifier.size(8.dp))
                        Text(t?.name ?: "…")
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver") } },
                actions = {
                    MapStyleMenu(MapStyle.byId(styleId)) { vm.setMapStyle(it.id) }
                    if (isOwner && t?.kind == "tag") {
                        IconButton(onClick = { vm.playSound() }) { Icon(Icons.AutoMirrored.Filled.VolumeUp, "Hacer sonar") }
                    }
                    if (isOwner) {
                        IconButton(onClick = { sharing = true }) { Icon(Icons.Default.Share, "Compartir") }
                        IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, "Editar") }
                    }
                    IconButton(onClick = vm::load) { Icon(Icons.Default.Refresh, "Actualizar") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.fillMaxWidth().height(320.dp)) {
                TagMapView(
                    markers = listOfNotNull(marker),
                    path = path,
                    places = ui.places.map { MapPlace(it.id, it.name, it.latitude, it.longitude, it.radiusM, it.color) },
                    style = MapStyle.byId(styleId),
                    focus = MapFocus.FitAll,
                    focusKey = "${ui.range}-${ui.points.size}",
                    modifier = Modifier.fillMaxSize(),
                )
                if (ui.loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
            }

            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp)) {
                HistoryRange.presets.forEach { r ->
                    FilterChip(
                        selected = ui.range == r, onClick = { vm.setRange(r) },
                        label = { Text(r.label) }, modifier = Modifier.padding(end = 8.dp),
                    )
                }
                val custom = ui.range as? HistoryRange.Custom
                FilterChip(
                    selected = custom != null,
                    onClick = { pickingDates = true },
                    label = { Text(custom?.let { "${it.from.dm()} – ${it.to.dm()}" } ?: "Elegir fechas") },
                    modifier = Modifier.padding(end = 8.dp),
                )
                FilterChip(
                    selected = ui.accuracyMax != null,
                    onClick = { vm.setAccuracyMax(if (ui.accuracyMax == null) 150f else null) },
                    label = { Text("≤150 m") },
                )
            }

            if (t != null) {
                val coords = if (t.hasLocation) String.format(java.util.Locale.US, "%.6f, %.6f", t.latitude, t.longitude) else "-"
                Text(
                    buildString {
                        append(ageText(t.ageMinutes))
                        t.observedAt?.let { append(" · ").append(it.fullText()) }
                        append("\n").append(accuracyText(t.accuracyM))
                        append(" · calidad ").append(qualityLabel(t.quality))
                        append(" · ").append(sourceLabel(t.source))
                        t.atPlaces?.let { append("\nEn: ").append(it) }
                        append("\n").append(coords).append("  (tocar para copiar)")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp).clickable {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("coords", coords))
                        Toast.makeText(ctx, "Coordenadas copiadas", Toast.LENGTH_SHORT).show()
                    },
                )
            }
            if (!isOwner) {
                Text(
                    "Compartido por ${ui.sharedBy ?: "otro usuario"} · solo lectura; podés crear tus lugares y alarmas",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            } else if (ui.shares.isNotEmpty()) {
                Text(
                    "Compartido con ${ui.shares.joinToString { it.email }}",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).clickable { sharing = true },
                )
            }
            ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }
            ui.soundStatus?.let {
                Snackbar(modifier = Modifier.padding(8.dp),
                    action = { TextButton(onClick = vm::clearSoundStatus) { Text("OK") } }) { Text(it) }
            }

            Text(
                "Recorrido aproximado · ${ui.points.size} puntos · ${distanceText(ui.distanceM)} en línea recta entre reportes",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(ui.points.asReversed(), key = { it.id }) { p -> PointRow(p) }
            }
        }
    }

    if (pickingDates) {
        val state = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { pickingDates = false },
            confirmButton = {
                TextButton(
                    enabled = state.selectedStartDateMillis != null,
                    onClick = {
                        val from = state.selectedStartDateMillis!!.toLocalDate()
                        val to = (state.selectedEndDateMillis ?: state.selectedStartDateMillis!!).toLocalDate()
                        vm.setRange(HistoryRange.Custom(from, to)); pickingDates = false
                    },
                ) { Text("Ver") }
            },
            dismissButton = { TextButton(onClick = { pickingDates = false }) { Text("Cancelar") } },
        ) {
            DateRangePicker(state = state, title = { Text("Rango de fechas", Modifier.padding(16.dp)) }, showModeToggle = false)
        }
    }

    if (sharing && t != null) {
        var email by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { sharing = false; vm.clearShareError() },
            title = { Text("Compartir \"${t.name}\"") },
            text = {
                Column {
                    Text("La otra persona necesita una cuenta en TagMap con ese email. Verá posición e historial y podrá crear sus propias alarmas.",
                        style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") },
                            singleLine = true, modifier = Modifier.weight(1f))
                        TextButton(enabled = email.contains("@"), onClick = { vm.share(email); email = "" }) { Text("Agregar") }
                    }
                    ui.shareError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Spacer(Modifier.height(8.dp))
                    if (ui.shares.isEmpty()) Text("Todavía no lo compartiste con nadie.", style = MaterialTheme.typography.bodySmall)
                    ui.shares.forEach { sh ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(sh.email, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { vm.unshare(sh.userId) }) { Icon(Icons.Default.Close, "Quitar acceso") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { sharing = false; vm.clearShareError() }) { Text("Cerrar") } },
        )
    }

    if (editing && t != null) {
        var name by remember { mutableStateOf(t.name) }
        var icon by remember { mutableStateOf(TagIcon.resolve(t.icon, t.kind).id) }
        var color by remember { mutableStateOf(t.color) }
        var staleHours by remember { mutableStateOf(t.staleAlertHours) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Editar tag") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre") }, singleLine = true)
                    Spacer(Modifier.height(12.dp))
                    Text("Ícono", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        TagIcon.entries.forEach { ic ->
                            IconButton(onClick = { icon = ic.id }) {
                                Icon(ic.vector, ic.label,
                                    tint = if (icon == ic.id) parseHexColor(color) else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    Text("Color", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        TAG_COLORS.forEach { c ->
                            Box(
                                Modifier.padding(4.dp).size(32.dp).background(parseHexColor(c), CircleShape)
                                    .border(if (c == color) 3.dp else 0.dp, Color.White, CircleShape)
                                    .clickable { color = c }
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Avisar si no reporta en…", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        listOf(null, 6, 12, 24, 48).forEach { h ->
                            FilterChip(
                                selected = staleHours == h, onClick = { staleHours = h },
                                label = { Text(h?.let { "$it h" } ?: "Nunca") }, modifier = Modifier.padding(end = 6.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.update(name, icon, color)
                    if (staleHours != t.staleAlertHours) vm.setStaleAlertHours(staleHours)
                    editing = false
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun PointRow(p: LocationPoint) {
    ListItem(
        headlineContent = { Text(p.observed.dateTimeText()) },
        supportingContent = {
            Text("${accuracyText(p.accuracyM)} · ${qualityLabel(p.quality)} · ${sourceLabel(p.source)}")
        },
    )
}

private val dmFmt = DateTimeFormatter.ofPattern("d/M")
private fun LocalDate.dm(): String = format(dmFmt)
private fun Long.toLocalDate(): LocalDate = Instant.ofEpochMilli(this).atOffset(ZoneOffset.UTC).toLocalDate()
