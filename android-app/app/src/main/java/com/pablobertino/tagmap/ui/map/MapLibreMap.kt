package com.pablobertino.tagmap.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.geojson.Polygon
import kotlin.math.cos
import kotlin.math.sin

/** Marcador de tag: última posición, color propio y radio de precisión (spec §6.2). */
data class MapMarker(
    val id: String,
    val label: String,
    val lat: Double,
    val lon: Double,
    val colorHex: String,
    val accuracyM: Float?,
    val stale: Boolean,
)

/** Punto de recorrido (spec §6.4). */
data class MapPathPoint(
    val lat: Double, val lon: Double, val gapBefore: Boolean,
    val label: String = "",        // hora del reporte, se muestra junto al punto
    val colorHex: String? = null,  // color del día (null = color por defecto del estilo)
)

/** Lugar favorito / geocerca (spec §6.5). */
data class MapPlace(val id: String, val name: String, val lat: Double, val lon: Double, val radiusM: Int, val colorHex: String)

/** Cámara inicial: centrar en un punto, o ajustar a todo lo visible. */
sealed class MapFocus {
    data class Center(val lat: Double, val lon: Double, val zoom: Double = 14.0) : MapFocus()
    data object FitAll : MapFocus()
}

/** Zoom máximo al encuadrar: si el tag se movió poco, igual se ve el barrio (no solo una manzana). */
private const val MAX_FIT_ZOOM = 15.5

private const val SRC_MARKERS = "tagmap-markers"
private const val SRC_PATH = "tagmap-path"
private const val SRC_PATH_GAP = "tagmap-path-gap"
private const val SRC_ACCURACY = "tagmap-accuracy"
private const val SRC_PLACES = "tagmap-places"
private const val SRC_PLACE_CENTERS = "tagmap-place-centers"

/**
 * Mapa MapLibre integrado en Compose. Abstracción `MapProvider` de la spec §5:
 * la lógica de negocio solo conoce MapMarker / MapPathPoint / MapPlace.
 *
 * @param focusKey cuando cambia, la cámara se mueve según [focus].
 */
@Composable
fun TagMapView(
    markers: List<MapMarker>,
    path: List<MapPathPoint> = emptyList(),
    places: List<MapPlace> = emptyList(),
    style: MapStyle = MapStyle.STREETS,
    focus: MapFocus = MapFocus.FitAll,
    focusKey: Any? = null,
    modifier: Modifier = Modifier,
    onMarkerClick: ((String) -> Unit)? = null,
    onPlaceClick: ((String) -> Unit)? = null,
    onLongPress: ((lat: Double, lon: Double) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { MapView(context).apply { onCreate(null) } }
    val state = remember { MapState() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDestroy()
        }
    }

    Box(modifier) {
    AndroidView(
        factory = {
            mapView.apply {
                getMapAsync { map ->
                    state.map = map
                    map.uiSettings.isAttributionEnabled = true
                    map.addOnMapClickListener { point ->
                        val screen = map.projection.toScreenLocation(point)
                        val marker = map.queryRenderedFeatures(screen, "$SRC_MARKERS-circle").firstOrNull()
                        if (marker != null && onMarkerClick != null) {
                            onMarkerClick(marker.getStringProperty("id")); return@addOnMapClickListener true
                        }
                        val place = map.queryRenderedFeatures(screen, "$SRC_PLACE_CENTERS-pin", "$SRC_PLACES-label", "$SRC_PLACES-fill").firstOrNull()
                        if (place != null && onPlaceClick != null) {
                            onPlaceClick(place.getStringProperty("id")); return@addOnMapClickListener true
                        }
                        false
                    }
                    if (onLongPress != null) {
                        map.addOnMapLongClickListener { p -> onLongPress(p.latitude, p.longitude); true }
                    }
                    state.applyStyle(style) {
                        state.render(markers, path, places)
                        state.applyFocus(focus, markers, path, places)
                        state.lastFocusKey = focusKey
                    }
                }
            }
        },
        update = {
            if (style != state.currentStyle) {
                state.applyStyle(style) { state.render(markers, path, places) }
            } else {
                state.render(markers, path, places)
            }
            if (focusKey != state.lastFocusKey) {
                state.lastFocusKey = focusKey
                state.applyFocus(focus, markers, path, places)
            }
        },
        modifier = Modifier.matchParentSize(),
    )
    Column(Modifier.align(Alignment.BottomStart).padding(8.dp)) {
        SmallFloatingActionButton(onClick = { state.zoomBy(1.0) }, modifier = Modifier.padding(bottom = 6.dp)) {
            Icon(Icons.Default.Add, "Acercar")
        }
        SmallFloatingActionButton(onClick = { state.zoomBy(-1.0) }) { Icon(Icons.Default.Remove, "Alejar") }
    }
    }
}

private class MapState {
    var map: MapLibreMap? = null
    var lastFocusKey: Any? = null
    var currentStyle: MapStyle? = null
    private var ready = false

    fun applyStyle(style: MapStyle, onLoaded: () -> Unit) {
        val map = map ?: return
        currentStyle = style
        ready = false
        val builder = when (val spec = style.styleSpec()) {
            is StyleSpec.Uri -> Style.Builder().fromUri(spec.uri)
            is StyleSpec.Json -> Style.Builder().fromJson(spec.json)
        }
        map.setStyle(builder) { s -> installLayers(s, dark = style == MapStyle.NIGHT); onLoaded() }
    }

    private fun installLayers(style: Style, dark: Boolean) {
        val text = if (dark) "#E8EEF6" else "#212121"
        val halo = if (dark) "#0E1A2B" else "#FFFFFF"
        val pathColor = if (dark) "#F5A524" else "#1E88E5"
        style.addSource(GeoJsonSource(SRC_PLACES))
        style.addSource(GeoJsonSource(SRC_PLACE_CENTERS))
        style.addSource(GeoJsonSource(SRC_ACCURACY))
        style.addSource(GeoJsonSource(SRC_PATH_GAP))
        style.addSource(GeoJsonSource(SRC_PATH))
        style.addSource(GeoJsonSource(SRC_MARKERS))

        // Lugares favoritos: polígono circular en metros reales
        style.addLayer(FillLayer("$SRC_PLACES-fill", SRC_PLACES).withProperties(
            PropertyFactory.fillColor(Expression.get("color")),
            PropertyFactory.fillOpacity(0.18f),
        ))
        style.addLayer(LineLayer("$SRC_PLACES-outline", SRC_PLACES).withProperties(
            PropertyFactory.lineColor(Expression.get("color")),
            PropertyFactory.lineWidth(2f),
            PropertyFactory.lineDasharray(arrayOf(1.5f, 1.5f)),
        ))
        // Pin en el centro del lugar + nombre debajo
        style.addLayer(CircleLayer("$SRC_PLACE_CENTERS-pin", SRC_PLACE_CENTERS).withProperties(
            PropertyFactory.circleColor(Expression.get("color")),
            PropertyFactory.circleRadius(7f),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(2.5f),
        ))
        style.addLayer(CircleLayer("$SRC_PLACE_CENTERS-dot", SRC_PLACE_CENTERS).withProperties(
            PropertyFactory.circleColor("#FFFFFF"),
            PropertyFactory.circleRadius(2f),
        ))
        style.addLayer(SymbolLayer("$SRC_PLACES-label", SRC_PLACE_CENTERS).withProperties(
            PropertyFactory.textField(Expression.get("name")),
            PropertyFactory.textFont(TEXT_FONT),
            PropertyFactory.textSize(12f),
            PropertyFactory.textOffset(arrayOf(0f, 1.3f)),
            PropertyFactory.textColor(Expression.get("color")),
            PropertyFactory.textHaloColor(halo),
            PropertyFactory.textHaloWidth(1.5f),
            PropertyFactory.textAllowOverlap(true),
        ))

        // Círculo de precisión (radio en px aproximado por zoom)
        style.addLayer(CircleLayer("$SRC_ACCURACY-circle", SRC_ACCURACY).withProperties(
            PropertyFactory.circleColor(Expression.get("color")),
            PropertyFactory.circleOpacity(0.15f),
            PropertyFactory.circleStrokeColor(Expression.get("color")),
            PropertyFactory.circleStrokeOpacity(0.4f),
            PropertyFactory.circleStrokeWidth(1f),
            PropertyFactory.circleRadius(
                Expression.interpolate(
                    Expression.exponential(2f), Expression.zoom(),
                    Expression.stop(10, Expression.division(Expression.get("acc"), Expression.literal(150f))),
                    Expression.stop(16, Expression.division(Expression.get("acc"), Expression.literal(2.4f))),
                )
            ),
        ))
        // Recorrido: tramos con hueco largo en línea discontinua (spec §13)
        style.addLayer(LineLayer("$SRC_PATH_GAP-line", SRC_PATH_GAP).withProperties(
            PropertyFactory.lineColor("#607D8B"),
            PropertyFactory.lineWidth(2f),
            PropertyFactory.lineDasharray(arrayOf(2f, 2f)),
        ))
        style.addLayer(LineLayer("$SRC_PATH-line", SRC_PATH).withProperties(
            PropertyFactory.lineColor(Expression.coalesce(Expression.get("color"), Expression.literal(pathColor))),
            PropertyFactory.lineWidth(3f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        ))
        style.addLayer(CircleLayer("$SRC_PATH-dots", SRC_PATH).withProperties(
            PropertyFactory.circleColor(Expression.coalesce(Expression.get("color"), Expression.literal(pathColor))),
            PropertyFactory.circleRadius(4f),
            PropertyFactory.circleStrokeColor(halo),
            PropertyFactory.circleStrokeWidth(1f),
        ))
        style.addLayer(SymbolLayer("$SRC_PATH-time", SRC_PATH).withProperties(
            PropertyFactory.textField(Expression.get("t")),
            PropertyFactory.textFont(TEXT_FONT),
            PropertyFactory.textSize(10f),
            PropertyFactory.textOffset(arrayOf(0.8f, 0f)),
            PropertyFactory.textAnchor(Property.TEXT_ANCHOR_LEFT),
            PropertyFactory.textColor(text),
            PropertyFactory.textHaloColor(halo),
            PropertyFactory.textHaloWidth(1.2f),
            PropertyFactory.textAllowOverlap(false),
            PropertyFactory.textOptional(true),
        ).apply { minZoom = 12f })
        style.addLayer(CircleLayer("$SRC_MARKERS-circle", SRC_MARKERS).withProperties(
            PropertyFactory.circleColor(Expression.get("color")),
            PropertyFactory.circleRadius(9f),
            PropertyFactory.circleStrokeColor(
                Expression.switchCase(Expression.get("stale"), Expression.literal("#F5A524"), Expression.literal(halo))
            ),
            PropertyFactory.circleStrokeWidth(3f),
        ))
        style.addLayer(SymbolLayer("$SRC_MARKERS-label", SRC_MARKERS).withProperties(
            PropertyFactory.textField(Expression.get("label")),
            PropertyFactory.textFont(TEXT_FONT),
            PropertyFactory.textSize(12f),
            PropertyFactory.textOffset(arrayOf(0f, 1.6f)),
            PropertyFactory.textColor(text),
            PropertyFactory.textHaloColor(halo),
            PropertyFactory.textHaloWidth(1.5f),
            PropertyFactory.textAllowOverlap(true),
        ))
        ready = true
    }

    fun render(markers: List<MapMarker>, path: List<MapPathPoint>, places: List<MapPlace>) {
        val style = map?.style ?: return
        if (!ready) return

        style.getSourceAs<GeoJsonSource>(SRC_MARKERS)?.setGeoJson(FeatureCollection.fromFeatures(markers.map { m ->
            Feature.fromGeometry(Point.fromLngLat(m.lon, m.lat)).apply {
                addStringProperty("id", m.id)
                addStringProperty("label", m.label)
                addStringProperty("color", m.colorHex)
                addBooleanProperty("stale", m.stale)
            }
        }))

        style.getSourceAs<GeoJsonSource>(SRC_ACCURACY)?.setGeoJson(FeatureCollection.fromFeatures(
            markers.filter { (it.accuracyM ?: 0f) > 0f }.map { m ->
                Feature.fromGeometry(Point.fromLngLat(m.lon, m.lat)).apply {
                    addStringProperty("color", m.colorHex)
                    addNumberProperty("acc", m.accuracyM!!)
                }
            }
        ))

        style.getSourceAs<GeoJsonSource>(SRC_PLACES)?.setGeoJson(FeatureCollection.fromFeatures(places.map { p ->
            Feature.fromGeometry(circlePolygon(p.lat, p.lon, p.radiusM.toDouble())).apply {
                addStringProperty("id", p.id)
                addStringProperty("name", p.name)
                addStringProperty("color", p.colorHex)
            }
        }))
        style.getSourceAs<GeoJsonSource>(SRC_PLACE_CENTERS)?.setGeoJson(FeatureCollection.fromFeatures(places.map { p ->
            Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)).apply {
                addStringProperty("id", p.id)
                addStringProperty("name", p.name)
                addStringProperty("color", p.colorHex)
            }
        }))

        // Solo se unen reportes seguidos; con hueco largo (gapBefore) no se dibuja nada:
        // no sabemos por dónde pasó, y una línea sugeriría un camino inexistente.
        val solid = mutableListOf<Feature>()
        val gap = emptyList<Feature>()
        path.zipWithNext { a, b ->
            if (!b.gapBefore) {
                val line = LineString.fromLngLats(listOf(Point.fromLngLat(a.lon, a.lat), Point.fromLngLat(b.lon, b.lat)))
                solid.add(Feature.fromGeometry(line).apply { b.colorHex?.let { addStringProperty("color", it) } })
            }
        }
        val dots = path.map { p ->
            Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)).apply {
                addStringProperty("t", p.label)
                p.colorHex?.let { addStringProperty("color", it) }
            }
        }
        style.getSourceAs<GeoJsonSource>(SRC_PATH)?.setGeoJson(FeatureCollection.fromFeatures(solid + dots))
        style.getSourceAs<GeoJsonSource>(SRC_PATH_GAP)?.setGeoJson(FeatureCollection.fromFeatures(gap))
    }

    fun zoomBy(delta: Double) {
        val map = map ?: return
        map.easeCamera(CameraUpdateFactory.zoomBy(delta))
    }

    fun applyFocus(focus: MapFocus, markers: List<MapMarker>, path: List<MapPathPoint>, places: List<MapPlace>) {
        val map = map ?: return
        when (focus) {
            is MapFocus.Center -> map.easeCamera(CameraUpdateFactory.newLatLngZoom(LatLng(focus.lat, focus.lon), focus.zoom))
            MapFocus.FitAll -> {
                // Encuadrar tags y recorrido; los lugares solo si no hay nada más que mostrar
                val pts = (markers.map { LatLng(it.lat, it.lon) } + path.map { LatLng(it.lat, it.lon) })
                    .ifEmpty { places.map { LatLng(it.lat, it.lon) } }
                when {
                    pts.isEmpty() -> Unit
                    pts.size == 1 -> map.easeCamera(CameraUpdateFactory.newLatLngZoom(pts.first(), MAX_FIT_ZOOM))
                    else -> {
                        val b = LatLngBounds.Builder().also { bb -> pts.forEach { bb.include(it) } }.build()
                        val cam = map.getCameraForLatLngBounds(b, intArrayOf(120, 120, 120, 120))
                        if (cam == null) {
                            map.easeCamera(CameraUpdateFactory.newLatLngBounds(b, 120))
                        } else {
                            val clamped = CameraPosition.Builder(cam).zoom(minOf(cam.zoom, MAX_FIT_ZOOM)).build()
                            map.easeCamera(CameraUpdateFactory.newCameraPosition(clamped))
                        }
                    }
                }
            }
        }
    }
}

/** Polígono de 48 lados que aproxima un círculo de `radiusM` metros. */
private fun circlePolygon(lat: Double, lon: Double, radiusM: Double): Polygon {
    val n = 48
    val dLat = radiusM / 111_320.0
    val dLon = radiusM / (111_320.0 * cos(Math.toRadians(lat)))
    val ring = (0..n).map { i ->
        val a = 2 * Math.PI * i / n
        Point.fromLngLat(lon + dLon * cos(a), lat + dLat * sin(a))
    }
    return Polygon.fromLngLats(listOf(ring))
}
