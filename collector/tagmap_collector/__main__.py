"""Entrada: python -m tagmap_collector [--once]"""

from __future__ import annotations

import argparse
import sys
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer

from .config import Settings, setup_logging
from .providers.factory import build_provider
from .runner import Collector
from .sink import SupabaseSink


def _health_server(port: int, collector: Collector) -> None:
    """Endpoint /health para Fly.io checks."""

    class H(BaseHTTPRequestHandler):
        def do_GET(self):  # noqa: N802
            body = b'{"status":"ok"}'
            self.send_response(200)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, *_):  # silenciar
            pass

    HTTPServer(("0.0.0.0", port), H).serve_forever()


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--once", action="store_true", help="un solo ciclo y salir")
    ap.add_argument("--health-port", type=int, default=8080)
    args = ap.parse_args()

    settings = Settings.load()
    setup_logging(settings.log_level)

    provider = build_provider(settings)
    sink = SupabaseSink(settings.supabase_url, settings.supabase_service_role_key, settings.collector_id)
    collector = Collector(provider, sink, settings.interval_minutes)

    if args.once:
        r = collector.run_once()
        print(r)
        return 0 if r.ok else 1

    threading.Thread(target=_health_server, args=(args.health_port, collector), daemon=True).start()
    collector.run_forever()
    return 0


if __name__ == "__main__":
    sys.exit(main())
