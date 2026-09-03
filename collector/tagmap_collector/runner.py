"""Ciclo del recolector: listar → localizar → normalizar → enviar → heartbeat."""

from __future__ import annotations

import logging
import signal
import threading
import time
from dataclasses import dataclass

from .models import TrackerLocation
from .providers import ProviderAuthError, TrackerProvider
from .sink import Sink

log = logging.getLogger(__name__)


@dataclass
class CycleResult:
    ok: bool
    trackers: int = 0
    fetched: int = 0
    inserted: int = 0
    error: str | None = None
    auth_expired: bool = False


class Collector:
    def __init__(self, provider: TrackerProvider, sink: Sink, interval_minutes: int) -> None:
        self.provider = provider
        self.sink = sink
        self.interval = interval_minutes * 60
        self._stop = threading.Event()
        self._seen: set[tuple] = set()      # dedup en memoria para no reenviar lo mismo cada ciclo
        self._sync_every = 12               # re-sincronizar lista de trackers cada N ciclos
        self._cycle = 0

    def process_actions(self) -> int:
        """Ejecuta los pedidos pendientes de la app (hacer sonar, etc.). Devuelve cuántos procesó."""
        actions = self.sink.take_actions()
        for a in actions:
            kind, dev = a.get("action"), a.get("provider_device_id")
            try:
                if kind == "sound_start":
                    r = self.provider.play_sound(dev)
                elif kind == "sound_stop":
                    r = self.provider.stop_sound(dev)
                elif kind == "refresh":
                    r = type("R", (), {"ok": True, "message": "se consultará en este ciclo"})()
                else:
                    r = type("R", (), {"ok": False, "message": f"acción desconocida {kind}"})()
                self.sink.finish_action(a["id"], bool(r.ok), r.message)
                log.info("Acción %s para %s: %s (%s)", kind, dev, "ok" if r.ok else "falló", r.message)
            except Exception as exc:  # noqa: BLE001
                self.sink.finish_action(a["id"], False, f"{type(exc).__name__}: {exc}")
                log.exception("Acción %s falló", kind)
        return len(actions)

    def run_once(self) -> CycleResult:
        self._cycle += 1
        try:
            try:
                self.process_actions()
            except Exception:  # noqa: BLE001 — los pedidos no deben frenar la recolección
                log.exception("Pedidos pendientes fallaron; sigo con las posiciones")
            trackers = self.provider.list_trackers()
            if self._cycle == 1 or self._cycle % self._sync_every == 0:
                self.sink.sync_trackers(trackers)

            locs = self.provider.get_latest_locations(trackers)
            fresh = self._filter_seen(locs)
            inserted = self.sink.ingest_locations(fresh)
            self.sink.heartbeat("ok")
            return CycleResult(ok=True, trackers=len(trackers), fetched=len(locs), inserted=inserted)
        except ProviderAuthError as exc:
            log.error("Autenticación de Find Hub vencida: %s", exc)
            self.sink.heartbeat("auth_expired", "Reautenticar con Chrome y actualizar GFMT_SECRETS_B64")
            return CycleResult(ok=False, error=str(exc), auth_expired=True)
        except Exception as exc:  # noqa: BLE001
            log.exception("Ciclo falló")
            self.sink.heartbeat("error", f"{type(exc).__name__}: {exc}")
            return CycleResult(ok=False, error=str(exc))

    def _filter_seen(self, locs: list[TrackerLocation]) -> list[TrackerLocation]:
        out = []
        for l in locs:
            k = l.dedup_key()
            if k in self._seen:
                continue
            self._seen.add(k)
            out.append(l)
        if len(self._seen) > 50_000:
            self._seen.clear()  # la base ya deduplica; esto es solo ahorro de tráfico
        return out

    def run_forever(self) -> None:
        def _handle(sig, _frame):
            log.info("Señal %s: deteniendo", sig)
            self._stop.set()

        for s in (signal.SIGINT, signal.SIGTERM):
            try:
                signal.signal(s, _handle)
            except ValueError:
                pass  # no estamos en el hilo principal

        log.info("Recolector iniciado, intervalo %d s", self.interval)
        backoff = 0
        while not self._stop.is_set():
            started = time.monotonic()
            r = self.run_once()
            if r.ok:
                backoff = 0
                log.info("Ciclo ok: %d tags, %d posiciones, %d nuevas", r.trackers, r.fetched, r.inserted)
                wait = self.interval
            elif r.auth_expired:
                wait = max(self.interval, 3600)  # no martillar a Google con credenciales vencidas
            else:
                backoff = min(backoff + 1, 4)
                wait = self.interval * backoff
            elapsed = time.monotonic() - started
            self._stop.wait(max(0, wait - elapsed))
        log.info("Recolector detenido")
