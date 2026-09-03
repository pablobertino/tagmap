"""Paso 7 de Fase 0: informe del muestreo."""

from __future__ import annotations

import json
import statistics
from collections import defaultdict
from datetime import datetime

from ._common import SAMPLES_DIR


def _median(xs):
    return statistics.median(xs) if xs else None


def _fmt_min(m):
    return "-" if m is None else f"{m:.0f} min"


def main() -> None:
    loop = SAMPLES_DIR / "loop.jsonl"
    health = SAMPLES_DIR / "health.jsonl"
    if not loop.exists():
        print("No hay loop.jsonl. Ejecutá phase0.sample_loop primero.")
        return

    by_tag: dict[str, list[dict]] = defaultdict(list)
    for line in loop.read_text(encoding="utf-8").splitlines():
        d = json.loads(line)
        by_tag[d["name"]].append(d)

    print("# Resultado Fase 0\n")
    print("| Tag | Posiciones | Intervalo mediano | Mayor hueco | Precisión mediana | Sin precisión |")
    print("|---|---|---|---|---|---|")
    for name, rows in sorted(by_tag.items()):
        rows.sort(key=lambda r: r["observedAt"])
        ts = [datetime.fromisoformat(r["observedAt"]) for r in rows]
        gaps = [(b - a).total_seconds() / 60 for a, b in zip(ts, ts[1:])]
        accs = [r["accuracyMeters"] for r in rows if r.get("accuracyMeters") is not None]
        no_acc = sum(1 for r in rows if r.get("accuracyMeters") is None)
        acc_med = _median(accs)
        print(
            f"| {name} | {len(rows)} | {_fmt_min(_median(gaps))} | {_fmt_min(max(gaps) if gaps else None)} "
            f"| {'-' if acc_med is None else f'{acc_med:.0f} m'} | {no_acc} |"
        )

    if health.exists():
        hs = [json.loads(l) for l in health.read_text(encoding="utf-8").splitlines()]
        ok = sum(1 for h in hs if h["ok"])
        auth = sum(1 for h in hs if h.get("error", "") and str(h["error"]).startswith("AUTH"))
        first, last = hs[0]["at"], hs[-1]["at"]
        print(f"\nCiclos: {len(hs)} (ok {ok}, error {len(hs) - ok}, auth {auth}). Desde {first} hasta {last}.")
        span_h = (datetime.fromisoformat(last) - datetime.fromisoformat(first)).total_seconds() / 3600
        verdict = "APROBADO" if span_h >= 48 and ok / max(len(hs), 1) > 0.9 and auth == 0 else "NO APROBADO"
        print(f"Cobertura: {span_h:.1f} h. Criterio 48 h sin intervención: {verdict}")


if __name__ == "__main__":
    main()
