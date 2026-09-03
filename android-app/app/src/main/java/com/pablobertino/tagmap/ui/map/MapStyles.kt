package com.pablobertino.tagmap.ui.map

/** Estilos de mapa sin clave ni facturación. */
enum class MapStyle(val id: String, val label: String) {
    NIGHT("night", "Noche"),
    STREETS("streets", "Calles"),
    LIGHT("light", "Claro"),
    TERRAIN("terrain", "Relieve"),
    SATELLITE("satellite", "Satélite");

    /** URI de estilo vectorial o JSON inline para estilos raster. */
    fun styleSpec(): StyleSpec = when (this) {
        NIGHT -> StyleSpec.Json(rasterStyle(
            tiles = "https://basemaps.cartocdn.com/dark_all/{z}/{x}/{y}@2x.png",
            attribution = "© OpenStreetMap contributors · © CARTO",
            maxZoom = 19,
        ))
        STREETS -> StyleSpec.Uri("https://tiles.openfreemap.org/styles/liberty")
        LIGHT -> StyleSpec.Uri("https://tiles.openfreemap.org/styles/positron")
        TERRAIN -> StyleSpec.Json(rasterStyle(
            tiles = "https://tile.opentopomap.org/{z}/{x}/{y}.png",
            attribution = "© OpenStreetMap contributors, SRTM · © OpenTopoMap (CC-BY-SA)",
            maxZoom = 17,
        ))
        SATELLITE -> StyleSpec.Json(rasterStyle(
            tiles = "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
            attribution = "Esri, Maxar, Earthstar Geographics",
            maxZoom = 19,
        ))
    }

    companion object {
        fun byId(id: String?): MapStyle = entries.firstOrNull { it.id == id } ?: STREETS
    }
}

sealed class StyleSpec {
    data class Uri(val uri: String) : StyleSpec()
    data class Json(val json: String) : StyleSpec()
}

/** Las capas de símbolos necesitan glifos; se toman de OpenFreeMap (mismo proveedor que los estilos vectoriales). */
const val GLYPHS_URL = "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf"
val TEXT_FONT = arrayOf("Noto Sans Regular")

private fun rasterStyle(tiles: String, attribution: String, maxZoom: Int): String = """
{
  "version": 8,
  "glyphs": "$GLYPHS_URL",
  "sources": {
    "base": {
      "type": "raster",
      "tiles": ["$tiles"],
      "tileSize": 256,
      "maxzoom": $maxZoom,
      "attribution": "$attribution"
    }
  },
  "layers": [ { "id": "base", "type": "raster", "source": "base" } ]
}
""".trimIndent()
