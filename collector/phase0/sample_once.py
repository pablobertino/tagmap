"""Paso 5 de Fase 0: una posición por tag en formato normalizado (spec §21)."""

import json
from datetime import datetime, timezone

from ._common import SAMPLES_DIR, bootstrap


def main() -> None:
    _, provider = bootstrap()
    trackers = provider.list_trackers()
    names = {t.provider_device_id: t.name for t in trackers}
    locs = provider.get_latest_locations(trackers)

    samples = [loc.to_sample_json(names[loc.provider_device_id]) for loc in locs]
    raw = [loc.raw for loc in locs]

    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    out = SAMPLES_DIR / f"sample-{stamp}.json"
    out.write_text(json.dumps({"normalized": samples, "raw": raw}, indent=2, default=str), encoding="utf-8")

    print(json.dumps(samples, indent=2))
    print(f"\n{len(samples)} posiciones de {len(trackers)} tags -> {out}")
    missing = [t.name for t in trackers if t.provider_device_id not in {l.provider_device_id for l in locs}]
    if missing:
        print(f"Sin posición: {', '.join(missing)}")


if __name__ == "__main__":
    main()
