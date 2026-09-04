"""Empaqueta varias cuentas de Google Find Hub en un solo secreto GFMT_ACCOUNTS_B64.

Uso (PowerShell, dentro de collector\\):
  python phase0\\pack_accounts.py gha-tagmap-1=secrets.json gha-yelio-1=secrets_yelio.json > accounts.b64

Cada argumento es <collector_id>=<ruta a secrets.json>. El collector_id debe existir en
tagmap.collectors con el owner_id del usuario dueño de esa cuenta de Google.
El resultado (una sola línea) se pega en GitHub → Settings → Secrets → GFMT_ACCOUNTS_B64.
No subir accounts.b64 al repo (está en .gitignore).
"""

from __future__ import annotations

import base64
import json
import sys
from pathlib import Path

REQUIRED = ("fcm_credentials", "username", "aas_token", "shared_key", "owner_key")


def main(argv: list[str]) -> int:
    if not argv:
        print(__doc__, file=sys.stderr)
        return 2
    accounts = []
    for arg in argv:
        if "=" not in arg:
            print(f"argumento inválido: {arg} (esperado collector_id=ruta)", file=sys.stderr)
            return 2
        cid, path = arg.split("=", 1)
        data = json.loads(Path(path).read_text(encoding="utf-8"))
        missing = [k for k in REQUIRED if k not in data]
        if missing:
            print(f"{path}: faltan claves {missing}", file=sys.stderr)
            return 1
        accounts.append({"collector_id": cid.strip(), "secrets": data})
        print(f"ok {cid.strip()} ← {path} (cuenta {data.get('username', '?')})", file=sys.stderr)
    sys.stdout.write(base64.b64encode(json.dumps(accounts).encode()).decode())
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
