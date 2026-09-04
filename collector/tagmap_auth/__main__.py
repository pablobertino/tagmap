"""tagmap-auth — registrá tu cuenta de Google Find Hub en TagMap.

Se ejecuta UNA vez en una PC con Google Chrome (y otra vez si Google vence la sesión):

  1. Entrás con tu usuario de TagMap (el mismo email y contraseña de la app).
  2. Se abre Chrome dos veces: iniciás sesión con TU cuenta de Google (la que tiene los tags)
     y, la segunda vez, Google te pide el bloqueo de pantalla del teléfono para liberar las
     claves de cifrado de Find Hub.
  3. El resultado (secrets.json) se sube cifrado a TagMap y se borra de esta PC.

A partir de ahí el recolector en la nube toma tus tags solo. Nadie más tiene que hacer nada.

Uso:
  python -m tagmap_auth                 flujo completo con Chrome
  python -m tagmap_auth --file X.json   subir un secrets.json ya generado (sin Chrome)
  python -m tagmap_auth --status        ver si tenés cuenta registrada y cómo está el recolector
  python -m tagmap_auth --remove        borrar tu cuenta de Google de TagMap
"""

from __future__ import annotations

import argparse
import getpass
import json
import os
import shutil
import sys
from pathlib import Path

import httpx

# Proyecto Naima / schema tagmap. La publishable key es pública por diseño (RLS protege los datos).
SUPABASE_URL = os.getenv("SUPABASE_URL", "https://rlaxxavhzrrrmjrkymlm.supabase.co")
SUPABASE_ANON_KEY = os.getenv("SUPABASE_ANON_KEY", "sb_publishable_W8ACK0W--geUC-H67-DGpg_JkT67QAu")
REQUIRED = ("fcm_credentials", "username", "aas_token", "shared_key", "owner_key")


# ----------------------------------------------------------------- Supabase (REST, sin SDK)

class TagMapSession:
    def __init__(self, url: str, anon_key: str) -> None:
        self.url = url.rstrip("/")
        self.anon = anon_key
        self.token: str | None = None
        self.http = httpx.Client(timeout=30)

    def login(self, email: str, password: str) -> None:
        r = self.http.post(
            f"{self.url}/auth/v1/token?grant_type=password",
            headers={"apikey": self.anon, "Content-Type": "application/json"},
            json={"email": email, "password": password},
        )
        if r.status_code != 200:
            msg = r.json().get("error_description") or r.json().get("msg") or r.text
            raise SystemExit(f"No pude entrar a TagMap: {msg}")
        self.token = r.json()["access_token"]

    def rpc(self, name: str, params: dict | None = None):
        r = self.http.post(
            f"{self.url}/rest/v1/rpc/{name}",
            headers={
                "apikey": self.anon, "Authorization": f"Bearer {self.token}",
                "Content-Type": "application/json", "Accept-Profile": "tagmap", "Content-Profile": "tagmap",
            },
            json=params or {},
        )
        if r.status_code >= 300:
            try:
                msg = r.json().get("message") or r.text
            except Exception:  # noqa: BLE001
                msg = r.text
            raise SystemExit(f"Error de TagMap en {name}: {msg}")
        return r.json() if r.content else None


# ----------------------------------------------------------------- vendor

def find_vendor() -> Path:
    candidates = []
    if os.getenv("GFMT_REPO_PATH"):
        candidates.append(Path(os.environ["GFMT_REPO_PATH"]))
    if getattr(sys, "frozen", False):                       # ejecutable PyInstaller
        candidates.append(Path(getattr(sys, "_MEIPASS", ".")) / "GoogleFindMyTools")
    here = Path(__file__).resolve().parent.parent
    candidates += [here / "vendor" / "GoogleFindMyTools", Path.cwd() / "vendor" / "GoogleFindMyTools"]
    for c in candidates:
        if (c / "Auth" / "token_cache.py").exists():
            return c.resolve()
    raise SystemExit("No encuentro GoogleFindMyTools (vendor). Definí GFMT_REPO_PATH o ejecutá desde collector\\.")


def run_google_flow(vendor: Path) -> dict:
    """Ejecuta la autenticación del vendor y devuelve el contenido de secrets.json."""
    if getattr(sys, "frozen", False):
        # el vendor escribe secrets.json junto a sus fuentes; en el .exe eso es de solo lectura → copiar a %TEMP%
        work = Path(os.getenv("LOCALAPPDATA", os.getenv("TEMP", "."))) / "tagmap-auth" / "GoogleFindMyTools"
        if work.exists():
            shutil.rmtree(work, ignore_errors=True)
        shutil.copytree(vendor, work)
        vendor = work
    secrets_file = vendor / "Auth" / "secrets.json"
    backup = None
    if secrets_file.exists():
        backup = secrets_file.with_suffix(".json.bak")
        shutil.move(str(secrets_file), str(backup))
        print(f"(había un secrets.json; lo guardé como {backup.name})")

    sys.path.insert(0, str(vendor))
    os.chdir(vendor)
    from Auth.aas_token_retrieval import get_aas_token            # Chrome #1: login → aas_token, username, fcm
    from SpotApi.GetEidInfoForE2eeDevices.get_owner_key import get_owner_key   # Chrome #2: bloqueo → shared/owner key
    from NovaApi.ListDevices.nbe_list_devices import request_device_list
    from ProtoDecoders.decoder import get_canonic_ids, parse_device_list_protobuf

    print("\n[1/3] Sesión de Google (se abre Chrome; iniciá sesión con la cuenta que tiene los tags)")
    get_aas_token()
    print("\n[2/3] Claves de cifrado de Find Hub (Chrome de nuevo; Google pide el bloqueo de pantalla del teléfono)")
    get_owner_key()
    print("\n[3/3] Comprobando que la cuenta responde…")
    devices = get_canonic_ids(parse_device_list_protobuf(request_device_list()))
    print(f"    {len(devices)} dispositivo(s): " + ", ".join(n for n, _ in devices))

    data = json.loads(secrets_file.read_text(encoding="utf-8"))
    missing = [k for k in REQUIRED if k not in data]
    if missing:
        raise SystemExit(f"secrets.json quedó incompleto (faltan {missing}). Repetí el proceso.")
    return data


def wipe(vendor: Path) -> None:
    for p in (vendor / "Auth" / "secrets.json",):
        try:
            p.unlink()
        except FileNotFoundError:
            pass


# ----------------------------------------------------------------- main

def main() -> int:
    ap = argparse.ArgumentParser(description="Registrar tu cuenta de Google Find Hub en TagMap")
    ap.add_argument("--file", help="subir un secrets.json existente en vez de abrir Chrome")
    ap.add_argument("--status", action="store_true", help="ver estado de tu cuenta registrada")
    ap.add_argument("--remove", action="store_true", help="borrar tu cuenta de Google de TagMap")
    ap.add_argument("--email", help="email de TagMap (si no, se pregunta)")
    args = ap.parse_args()

    print("TagMap · registro de cuenta de Google Find Hub\n")
    email = args.email or input("Email de TagMap: ").strip()
    password = getpass.getpass("Contraseña de TagMap: ")
    s = TagMapSession(SUPABASE_URL, SUPABASE_ANON_KEY)
    s.login(email, password)
    print("Sesión de TagMap OK.\n")

    if args.status:
        rows = s.rpc("app_google_account")
        if not rows:
            print("No tenés cuenta de Google registrada.")
        else:
            r = rows[0]
            print(f"Cuenta Google: {r['google_email']}  ·  registrada {r['registered_at'][:16]}  ·  renovada {r['updated_at'][:16]}")
            print(f"Recolector {r['collector_id']}: {r['status']}  ·  último ciclo {(r['last_seen_at'] or '-')[:16]}  {r.get('message') or ''}")
        return 0

    if args.remove:
        if input("¿Borrar tu cuenta de Google de TagMap? (escribí SI): ").strip() != "SI":
            return 1
        s.rpc("app_remove_google_account")
        print("Cuenta eliminada. El recolector dejará de consultar tus tags.")
        return 0

    if args.file:
        data = json.loads(Path(args.file).read_text(encoding="utf-8"))
        missing = [k for k in REQUIRED if k not in data]
        if missing:
            raise SystemExit(f"{args.file}: faltan claves {missing}")
        vendor = None
    else:
        vendor = find_vendor()
        data = run_google_flow(vendor)

    cid = s.rpc("app_register_google_account", {"p_secrets": data})
    print(f"\nListo: cuenta {data.get('username', '?')} registrada en TagMap (recolector {cid}).")
    print("En unos minutos tus dispositivos aparecen en la app. Podés cerrar esta ventana.")
    if vendor is not None:
        wipe(vendor)
    return 0


if __name__ == "__main__":
    try:
        code = main()
    except KeyboardInterrupt:
        code = 130
    except SystemExit as e:
        if e.code not in (0, None):
            print(f"\nERROR: {e.code}" if isinstance(e.code, str) else "")
        code = e.code if isinstance(e.code, int) else 1
    if getattr(sys, "frozen", False):
        input("\nEnter para salir…")
    sys.exit(code)
