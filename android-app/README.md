# android-app — Fase 2 (pendiente)

Arranca cuando `docs/CONTRATO-DATOS.md` esté congelado tras Fase 0.

Stack decidido: Kotlin, Jetpack Compose, Material 3, Hilt, Room, WorkManager, DataStore,
`supabase-kt` (postgrest, auth, realtime), Google Maps SDK detrás de `MapProvider`, FCM.

Para desarrollar sin Google Find Hub: `PROVIDER=fake python -m tagmap_collector --once` carga datos de prueba en Supabase.
