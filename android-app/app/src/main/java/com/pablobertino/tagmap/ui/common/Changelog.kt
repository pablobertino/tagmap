package com.pablobertino.tagmap.ui.common

/** Historial de versiones visible en "Acerca de". Mantener en sincronía con CHANGELOG.md. */
data class Release(val version: String, val date: String, val changes: List<String>)

val CHANGELOG = listOf(
    Release("0.5.0", "2026-09-03", listOf(
        "Compartir tags con otros usuarios por email (ícono Compartir en el detalle).",
        "El invitado ve posición e historial y puede crear sus propios lugares y alarmas; no puede renombrar ni hacer sonar.",
        "Quitar el acceso borra las alarmas del invitado sobre ese tag.",
    )),
    Release("0.4.1", "2026-09-03", listOf(
        "Notificaciones push operativas (Firebase).",
        "La hora en las notificaciones es la del teléfono, en 24 h y con zona (ej. 18:30 GMT-4).",
        "Recolector cada 15 min de verdad (run largo en GitHub Actions).",
        "Arreglado: hacer sonar quedaba 'en curso' y frenaba al recolector.",
    )),
    Release("0.4.0", "2026-09-03", listOf(
        "Nueva identidad 'Carta náutica': azul profundo y ámbar, ícono nuevo.",
        "Apariencia elegible en Acerca de: Carta náutica, Claro o Instrumentos.",
        "Estilo de mapa 'Noche' (CARTO) por defecto en los temas oscuros.",
        "Pin y nombre en el centro de cada lugar; se puede tocar.",
        "Íconos de tag en cápsula de color en la lista.",
    )),
    Release("0.3.1", "2026-09-03", listOf(
        "Hacer sonar un tag (ícono de altavoz en el detalle). Tarda ~1 min: pasa por GitHub Actions.",
    )),
    Release("0.3.0", "2026-09-03", listOf(
        "Lugares favoritos: mantené pulsado el mapa para crear uno, con radio y color.",
        "Alarmas de llegada y salida por tag y lugar (pantalla Lugares).",
        "Centro de eventos con historial de llegadas/salidas.",
        "Notificaciones push (requiere configurar Firebase).",
        "Botones + / − de zoom y encuadre que no acerca de más cuando el tag se movió poco.",
        "Pantalla Acerca de con versión y novedades.",
    )),
    Release("0.2.0", "2026-09-03", listOf(
        "Login: ver contraseña, recuperar contraseña y crear cuenta.",
        "Ícono de la app.",
        "Íconos por tipo de dispositivo y editables por tag, con color.",
        "Estilos de mapa: Calles, Claro, Relieve y Satélite.",
        "Historial por rango de fechas a elección.",
        "Distancia recorrida aproximada.",
        "Mapa general centrado en el tag más reciente, con accesos por tag.",
    )),
    Release("0.1.0", "2026-09-02", listOf(
        "Lista de tags con antigüedad y precisión.",
        "Mapa general y detalle con historial de hoy / 3 / 7 días.",
    )),
)
