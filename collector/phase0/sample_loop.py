"""Paso 6 de Fase 0: muestreo continuo 48-72 h. Solo guarda posiciones nuevas.

Uso: python -m phase0.sample_loop --interval-minutes 15 --hours 72
"""

from __future__ import annotations

import argparse
import json
import logging
import time
from datetime import datetime, timezone

from tagmap_collector.config import MIN_INTERVAL_MINUTES
from tagmap_collector.providers import ProviderAuthError

from ._common import SAMPLES_DIR, bootstrap

log = logging.getLogger("phase0.loop")
LOOP_FILE = SAMPLES_DIR / "loop.jsonl"
HEALTH_FILE = SAMPLES_DIR / "health.jsonl"


def _append(path, obj: dict) -> None:
    with path.open("a", encoding="utf-8") as f:
        f.write(json.dumps(obj, default=str) + "\n")


def _load_seen() -> set[tuple]:
    seen: set[tuple] = set()
    if LOOP_FILE.exists():
        for line in LOOP_FILE.read_text(encoding="utf-8").splitlines():
            try:
                d = json.loads(line)
                seen.add((d["trackerId"], d["observedAt"], round(d["latitude"], 6), round(d["longitude"], 6)))
            except (json.JSONDecodeError, KeyError):
                continue
    return seen


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--interval-minutes", type=int, default=15)
    ap.add_argument("--hours", type=float, default=72)
    args = ap.parse_args()
    if args.interval_minutes < MIN_INTERVAL_MINUTES:
        ap.error(f"--interval-minutes mínimo {MIN_INTERVAL_MINUTES}")

    _, provider = bootstrap()
    seen = _load_seen()
    deadline = time.monotonic() + args.hours * 3600
    cycle = 0
    consecutive_auth_errors = 0

    log.info("Muestreo cada %d min durante %.0f h. %d posiciones previas.", args.interval_minutes, args.hours, len(seen))

    while time.monotonic() < deadline:
        cycle += 1
        started = datetime.now(timezone.utc)
        health = {"cycle": cycle, "at": started.isoformat(), "ok": False, "new": 0, "trackers": 0, "error": None}
        try:
            trackers = provider.list_trackers()
            names = {t.provider_device_id: t.name for t in trackers}
            locs = provider.get_latest_locations(trackers)
            new = 0
            for loc in locs:
                key = (loc.provider_device_id, loc.observed_at.isoformat(), round(loc.latitude, 6), round(loc.longitude, 6))
                if key in seen:
                    continue
                seen.add(key)
                _append(LOOP_FILE, loc.to_sample_json(names[loc.provider_device_id]))
                new += 1
            health.update(ok=True, new=new, trackers=len(trackers))
            consecutive_auth_errors = 0
            log.info("Ciclo %d: %d tags, %d posiciones nuevas", cycle, len(trackers), new)
        except ProviderAuthError as exc:
            consecutive_auth_errors += 1
            health["error"] = f"AUTH: {exc}"
            log.error("Ciclo %d: autenticación vencida (%d seguidos). Reautenticar con Chrome.", cycle, consecutive_auth_errors)
            if consecutive_auth_errors >= 3:
                _append(HEALTH_FILE, health)
                log.error("Abortando: 3 errores de autenticación consecutivos.")
                return
        except Exception as exc:  # noqa: BLE001
            health["error"] = f"{type(exc).__name__}: {exc}"
            log.exception("Ciclo %d falló", cycle)
        _append(HEALTH_FILE, health)

        elapsed = (datetime.now(timezone.utc) - started).total_seconds()
        time.sleep(max(0, args.interval_minutes * 60 - elapsed))


if __name__ == "__main__":
    main()
