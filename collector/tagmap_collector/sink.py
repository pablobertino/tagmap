"""Envío de datos normalizados a Supabase mediante las RPC internas (spec §10 Recolector)."""

from __future__ import annotations

import logging
from typing import Protocol

from supabase import Client, ClientOptions, create_client
from tenacity import retry, retry_if_exception_type, stop_after_attempt, wait_exponential

from .models import Tracker, TrackerLocation

log = logging.getLogger(__name__)

DB_SCHEMA = "tagmap"  # todo TagMap vive en su propio schema (proyecto compartido con Naima)


class Sink(Protocol):
    def sync_trackers(self, trackers: list[Tracker]) -> None: ...
    def ingest_locations(self, locations: list[TrackerLocation]) -> int: ...
    def heartbeat(self, status: str, message: str | None = None) -> None: ...
    def take_actions(self) -> list[dict]: ...
    def finish_action(self, action_id: str, ok: bool, result: str | None = None) -> None: ...


class TransientError(Exception):
    pass


class SupabaseSink:
    def __init__(self, url: str, service_role_key: str, collector_id: str) -> None:
        self.client: Client = create_client(url, service_role_key, options=ClientOptions(schema=DB_SCHEMA))
        self.collector_id = collector_id

    @retry(retry=retry_if_exception_type(TransientError), stop=stop_after_attempt(4),
           wait=wait_exponential(min=2, max=30), reraise=True)
    def _rpc(self, name: str, params: dict):
        try:
            return self.client.rpc(name, params).execute()
        except Exception as exc:  # noqa: BLE001
            text = str(exc).lower()
            if any(h in text for h in ("timeout", "connection", "502", "503", "504")):
                raise TransientError(str(exc)) from exc
            raise

    def sync_trackers(self, trackers: list[Tracker]) -> None:
        payload = [
            {
                "provider_device_id": t.provider_device_id,
                "name": t.name,
                "supports_sound": t.supports_sound,
                "kind": "phone" if t.model == "android_phone" else "tag",
            }
            for t in trackers
        ]
        res = self._rpc("collector_sync_trackers", {"p_collector_id": self.collector_id, "p_trackers": payload})
        log.info("Sincronizados %d trackers", len(res.data or []))

    def ingest_locations(self, locations: list[TrackerLocation]) -> int:
        if not locations:
            return 0
        payload = [
            {
                "provider_device_id": l.provider_device_id,
                "latitude": l.latitude,
                "longitude": l.longitude,
                "accuracy_m": l.accuracy_m,
                "observed_at": l.observed_at.isoformat(),
                "received_at": l.received_at.isoformat(),
                "source": l.source.value,
                "provider_report_id": l.provider_report_id,
                "raw": l.raw,
            }
            for l in sorted(locations, key=lambda x: x.observed_at)
        ]
        res = self._rpc("collector_ingest_locations", {"p_collector_id": self.collector_id, "p_locations": payload})
        inserted = int(res.data or 0)
        log.info("Enviadas %d posiciones, %d nuevas", len(payload), inserted)
        return inserted

    def take_actions(self) -> list[dict]:
        res = self._rpc("collector_take_actions", {"p_collector_id": self.collector_id})
        return list(res.data or [])

    def finish_action(self, action_id: str, ok: bool, result: str | None = None) -> None:
        self._rpc("collector_finish_action", {"p_id": action_id, "p_ok": ok, "p_result": result})

    def heartbeat(self, status: str, message: str | None = None) -> None:
        try:
            self._rpc("collector_heartbeat",
                      {"p_collector_id": self.collector_id, "p_status": status, "p_message": message})
        except Exception as exc:  # noqa: BLE001
            log.warning("heartbeat falló: %s", exc)


class MemorySink:
    """Para tests."""

    def __init__(self) -> None:
        self.trackers: list[Tracker] = []
        self.locations: list[TrackerLocation] = []
        self.heartbeats: list[tuple[str, str | None]] = []
        self.actions: list[dict] = []
        self.finished: list[tuple[str, bool, str | None]] = []
        self._seen: set[tuple] = set()

    def take_actions(self) -> list[dict]:
        out, self.actions = self.actions, []
        return out

    def finish_action(self, action_id: str, ok: bool, result: str | None = None) -> None:
        self.finished.append((action_id, ok, result))

    def sync_trackers(self, trackers: list[Tracker]) -> None:
        self.trackers = list(trackers)

    def ingest_locations(self, locations: list[TrackerLocation]) -> int:
        n = 0
        for l in locations:
            k = l.dedup_key()
            if k not in self._seen:
                self._seen.add(k)
                self.locations.append(l)
                n += 1
        return n

    def heartbeat(self, status: str, message: str | None = None) -> None:
        self.heartbeats.append((status, message))
