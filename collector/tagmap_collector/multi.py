"""Orquestador multi-cuenta: una cuenta de Google Find Hub por usuario de TagMap.

Cada cuenta corre en un subproceso propio (`python -m tagmap_collector --once`) porque el vendor
GoogleFindMyTools guarda estado global (tokens, receptor FCM) y no admite cambiar de cuenta en
el mismo proceso. Las cuentas se procesan en secuencia; un fallo en una no frena a las demás.

Fuente de cuentas, en orden:
  1. Supabase (tagmap.google_accounts, registradas por cada usuario con `tagmap-auth`) — vía RPC
     collector_list_accounts con la service_role key. Es la vía normal.
  2. GFMT_ACCOUNTS_B64   base64 de un JSON (respaldo manual):
                      [{"collector_id": "gha-tagmap-1", "secrets": {...contenido de secrets.json...}}, ...]
                      Generarlo con: python phase0/pack_accounts.py gha-tagmap-1=secrets.json gha-yelio-1=secrets_yelio.json
  3. GFMT_SECRETS_B64 + COLLECTOR_ID (cuenta única de siempre).
  ACCOUNTS_SOURCE=env fuerza saltar Supabase (pruebas).

Uso:
  python -m tagmap_collector.multi --once               un ciclo por cuenta y salir
  python -m tagmap_collector.multi --actions-only       solo pedidos pendientes (sonar) de cada cuenta
  python -m tagmap_collector.multi --max-minutes 340    ciclos cada COLLECT_INTERVAL_MINUTES durante N minutos
"""

from __future__ import annotations

import argparse
import base64
import json
import logging
import os
import subprocess
import sys
import time
from dataclasses import dataclass

from .config import MIN_INTERVAL_MINUTES, setup_logging

log = logging.getLogger("tagmap_collector.multi")


@dataclass(frozen=True)
class Account:
    collector_id: str
    secrets_b64: str


def load_accounts_from_supabase(url: str, key: str) -> list[Account]:
    """Cuentas registradas por los usuarios (secrets.json cifrado en Vault)."""
    from supabase import ClientOptions, create_client

    client = create_client(url, key, options=ClientOptions(schema="tagmap"))
    rows = client.rpc("collector_list_accounts", {}).execute().data or []
    out = []
    for r in rows:
        sec = r.get("secrets")
        if isinstance(sec, str):
            sec = json.loads(sec)
        out.append(Account(r["collector_id"], base64.b64encode(json.dumps(sec).encode()).decode()))
    return out


def load_accounts(env: dict[str, str] | None = None) -> list[Account]:
    env = env if env is not None else dict(os.environ)
    if env.get("ACCOUNTS_SOURCE", "supabase") != "env" and env.get("SUPABASE_URL") and env.get("SUPABASE_SERVICE_ROLE_KEY"):
        try:
            accs = load_accounts_from_supabase(env["SUPABASE_URL"], env["SUPABASE_SERVICE_ROLE_KEY"])
        except Exception as exc:  # noqa: BLE001
            log.warning("No pude leer cuentas de Supabase (%s); uso variables de entorno", exc)
            accs = []
        if accs:
            return accs
        log.info("Sin cuentas registradas en Supabase; uso variables de entorno")
    raw = env.get("GFMT_ACCOUNTS_B64")
    if raw:
        try:
            items = json.loads(base64.b64decode(raw))
        except Exception as exc:  # noqa: BLE001
            raise ValueError(f"GFMT_ACCOUNTS_B64 inválido: {exc}") from exc
        if not isinstance(items, list) or not items:
            raise ValueError("GFMT_ACCOUNTS_B64 debe ser una lista JSON no vacía")
        out = []
        for it in items:
            cid, sec = it.get("collector_id"), it.get("secrets")
            if not cid or not isinstance(sec, dict):
                raise ValueError("cada cuenta necesita collector_id y secrets (objeto)")
            out.append(Account(cid, base64.b64encode(json.dumps(sec).encode()).decode()))
        ids = [a.collector_id for a in out]
        if len(ids) != len(set(ids)):
            raise ValueError(f"collector_id repetido en GFMT_ACCOUNTS_B64: {ids}")
        return out
    single = env.get("GFMT_SECRETS_B64")
    if single:
        return [Account(env.get("COLLECTOR_ID", "local"), single)]
    raise ValueError("Faltan GFMT_ACCOUNTS_B64 o GFMT_SECRETS_B64")


def run_account(acc: Account, mode_flag: str) -> int:
    """Lanza el recolector de una cuenta. Devuelve el código de salida."""
    env = dict(os.environ)
    env.pop("GFMT_ACCOUNTS_B64", None)
    env["GFMT_SECRETS_B64"] = acc.secrets_b64
    env["COLLECTOR_ID"] = acc.collector_id
    log.info("== cuenta %s: %s", acc.collector_id, mode_flag)
    started = time.monotonic()
    proc = subprocess.run([sys.executable, "-m", "tagmap_collector", mode_flag], env=env)
    log.info("== cuenta %s terminó con código %d en %.0f s", acc.collector_id, proc.returncode, time.monotonic() - started)
    return proc.returncode


def run_all(accounts: list[Account], mode_flag: str) -> int:
    failures = 0
    for acc in accounts:
        try:
            if run_account(acc, mode_flag) != 0:
                failures += 1
        except Exception:  # noqa: BLE001
            log.exception("cuenta %s falló", acc.collector_id)
            failures += 1
    return failures


def main() -> int:
    ap = argparse.ArgumentParser()
    g = ap.add_mutually_exclusive_group()
    g.add_argument("--once", action="store_true", help="un ciclo por cuenta y salir")
    g.add_argument("--actions-only", action="store_true", help="solo pedidos pendientes por cuenta y salir")
    ap.add_argument("--max-minutes", type=float, default=None, help="correr ciclos durante N minutos")
    args = ap.parse_args()

    setup_logging(os.getenv("LOG_LEVEL", "INFO"))
    accounts = load_accounts()
    log.info("%d cuenta(s): %s", len(accounts), ", ".join(a.collector_id for a in accounts))

    if args.actions_only:
        return 1 if run_all(accounts, "--actions-only") else 0
    if args.once or not args.max_minutes:
        return 1 if run_all(accounts, "--once") else 0

    interval = max(MIN_INTERVAL_MINUTES, int(os.getenv("COLLECT_INTERVAL_MINUTES", "15"))) * 60
    deadline = time.monotonic() + args.max_minutes * 60
    while True:
        started = time.monotonic()
        try:
            accounts = load_accounts()   # cuentas nuevas o renovadas entran sin reiniciar el run
        except Exception as exc:  # noqa: BLE001
            log.warning("No pude recargar cuentas (%s); sigo con las anteriores", exc)
        run_all(accounts, "--once")
        wait = max(0.0, interval - (time.monotonic() - started))
        if deadline - time.monotonic() < wait + 60:
            log.info("Tiempo máximo alcanzado; salgo limpio")
            return 0
        time.sleep(wait)


if __name__ == "__main__":
    sys.exit(main())
