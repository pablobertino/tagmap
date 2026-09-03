"""Adaptador sobre GoogleFindMyTools (https://github.com/leonboe1/GoogleFindMyTools).

Validado contra el commit d46e952 (2026-05-05) en Fase 0. ÚNICO archivo del proyecto que
conoce la estructura interna del vendor. Los bloques `# ADAPT` marcan las dependencias
con el vendor; si una versión nueva rompe algo, se toca solo esto.

Por qué no se usa `get_location_data_for_device` del vendor: imprime por consola, devuelve
None, descarta la precisión y espera la respuesta FCM sin timeout. Acá se reimplementa el
mismo flujo (request → FCM → descifrado) usando los bloques del vendor y devolviendo datos.

Requisitos de autenticación (secrets.json, generado una vez con Chrome en Windows):
  fcm_credentials, username, aas_token, shared_key, owner_key.
Con esos cinco valores el recolector funciona sin navegador (Fly.io).
"""

from __future__ import annotations

import base64
import hashlib
import json
import logging
import os
import sys
import threading
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Optional

from ...models import ActionResult, LocationSource, Tracker, TrackerLocation
from ..base import ProviderAuthError, ProviderError, TrackerProvider

log = logging.getLogger(__name__)

REQUIRED_SECRETS = ("fcm_credentials", "username", "aas_token", "shared_key", "owner_key")
LOCATION_TIMEOUT_S = 45
STATUS_NAMES = {0: "SEMANTIC", 1: "LAST_KNOWN", 2: "CROWDSOURCED", 3: "AGGREGATED"}


class GoogleFindHubProvider(TrackerProvider):
    def __init__(
        self,
        repo_path: str | Path,
        secrets_path: Optional[str | Path] = None,
        secrets_b64: Optional[str] = None,
    ) -> None:
        self.repo_path = Path(repo_path).resolve()
        if not self.repo_path.exists():
            raise ProviderError(f"No existe el clon de GoogleFindMyTools en {self.repo_path}")
        self.secrets_path = Path(secrets_path).resolve() if secrets_path else None
        self.secrets_b64 = secrets_b64
        self._install_secrets()
        self._import_vendor()
        self._identity_keys: dict[str, bytes] = {}
        self._pending: dict[str, Callable[[Any], None]] = {}
        self._pending_lock = threading.Lock()
        self._fcm_registered = False

    # ------------------------------------------------------------------ setup

    def _install_secrets(self) -> None:
        # ADAPT: Auth/token_cache.py lee `Auth/secrets.json` relativo a su propio archivo.
        target = self.repo_path / "Auth" / "secrets.json"
        target.parent.mkdir(parents=True, exist_ok=True)

        if self.secrets_b64:
            target.write_bytes(base64.b64decode(self.secrets_b64))
            log.info("secrets.json instalado desde GFMT_SECRETS_B64")
        elif self.secrets_path and self.secrets_path.exists() and self.secrets_path != target:
            target.write_bytes(self.secrets_path.read_bytes())
            log.info("secrets.json instalado desde %s", self.secrets_path)
        elif not target.exists():
            raise ProviderAuthError(
                "No hay secrets.json. Ejecutá la autenticación con Chrome (docs/FASE0-prueba-tecnica.md §3)."
            )
        try:
            os.chmod(target, 0o600)
        except OSError:
            pass

        try:
            data = json.loads(target.read_text(encoding="utf-8"))
        except (OSError, json.JSONDecodeError) as exc:
            raise ProviderAuthError(f"secrets.json ilegible: {exc}") from exc
        missing = [k for k in REQUIRED_SECRETS if not data.get(k)]
        if missing:
            # Sin esto el vendor intentaría abrir Chrome y pedir input(): imposible en la nube.
            raise ProviderAuthError(
                f"secrets.json incompleto, faltan {missing}. Ejecutá main.py del vendor en Windows, "
                "elegí un tag para forzar el login E2EE, y volvé a copiar el archivo."
            )

    def _import_vendor(self) -> None:
        if str(self.repo_path) not in sys.path:
            sys.path.insert(0, str(self.repo_path))
        os.chdir(self.repo_path)
        try:
            # ADAPT: entrypoints del vendor
            from Auth.fcm_receiver import FcmReceiver
            from FMDNCrypto.foreign_tracker_cryptor import decrypt as fmdn_decrypt
            from KeyBackup.cloud_key_decryptor import decrypt_aes_gcm
            from NovaApi.ExecuteAction.LocateTracker.decrypt_locations import is_mcu_tracker, retrieve_identity_key
            from NovaApi.ExecuteAction.LocateTracker.location_request import create_location_request
            from NovaApi.ExecuteAction.PlaySound.sound_request import create_sound_request
            from NovaApi.ListDevices.nbe_list_devices import request_device_list
            from NovaApi.nova_request import nova_request
            from NovaApi.scopes import NOVA_ACTION_API_SCOPE
            from NovaApi.util import generate_random_uuid
            from ProtoDecoders import Common_pb2, DeviceUpdate_pb2
            from ProtoDecoders.decoder import get_canonic_ids, parse_device_list_protobuf, parse_device_update_protobuf
        except ImportError as exc:
            raise ProviderError(
                f"GoogleFindMyTools cambió su API ({exc}). Ajustá los bloques # ADAPT en "
                "providers/google_find_hub/adapter.py"
            ) from exc

        self._v = dict(
            FcmReceiver=FcmReceiver, fmdn_decrypt=fmdn_decrypt, decrypt_aes_gcm=decrypt_aes_gcm,
            is_mcu_tracker=is_mcu_tracker, retrieve_identity_key=retrieve_identity_key,
            create_location_request=create_location_request, request_device_list=request_device_list,
            create_sound_request=create_sound_request,
            nova_request=nova_request, NOVA_ACTION_API_SCOPE=NOVA_ACTION_API_SCOPE,
            generate_random_uuid=generate_random_uuid, Common_pb2=Common_pb2, DeviceUpdate_pb2=DeviceUpdate_pb2,
            get_canonic_ids=get_canonic_ids, parse_device_list_protobuf=parse_device_list_protobuf,
            parse_device_update_protobuf=parse_device_update_protobuf,
        )

    # -------------------------------------------------------------- interface

    def list_trackers(self) -> list[Tracker]:
        v = self._v
        raw_hex = self._call_auth(v["request_device_list"])
        if not raw_hex:
            raise ProviderError("request_device_list devolvió vacío (¿token vencido? ver log [NovaRequest])")
        device_list = v["parse_device_list_protobuf"](raw_hex)

        # ADAPT: get_canonic_ids devuelve [(nombre, canonic_id)]. Se agrega el tipo para distinguir
        # teléfonos (IDENTIFIER_ANDROID) de tags.
        android_type = v["DeviceUpdate_pb2"].IDENTIFIER_ANDROID
        model_by_name = {
            d.userDefinedDeviceName: ("android_phone" if d.identifierInformation.type == android_type else "tag")
            for d in device_list.deviceMetadata
        }
        trackers = [
            Tracker(provider_device_id=cid, name=name, model=model_by_name.get(name))
            for name, cid in v["get_canonic_ids"](device_list)
        ]
        log.info("Find Hub devolvió %d dispositivos", len(trackers))
        return trackers

    def get_latest_locations(self, trackers: list[Tracker]) -> list[TrackerLocation]:
        out: list[TrackerLocation] = []
        for t in trackers:
            try:
                out.extend(self._locate(t))
            except ProviderAuthError:
                raise
            except Exception as exc:  # noqa: BLE001
                log.warning("Sin posición para %s: %s", t.name, exc)
        return out

    def play_sound(self, provider_device_id: str) -> ActionResult:
        return self._sound(provider_device_id, start=True)

    def stop_sound(self, provider_device_id: str) -> ActionResult:
        return self._sound(provider_device_id, start=False)

    def _sound(self, provider_device_id: str, start: bool) -> ActionResult:
        """ADAPT: NovaApi/ExecuteAction/PlaySound. Google responde 200 si aceptó el pedido;
        que el tag suene depende de que esté al alcance BLE de un teléfono propio."""
        v = self._v
        fcm_token = self._call_auth(self._ensure_fcm)
        payload = v["create_sound_request"](start, provider_device_id, fcm_token)
        resp = self._call_auth(v["nova_request"], v["NOVA_ACTION_API_SCOPE"], payload)
        if resp is None:
            return ActionResult(ok=False, message="Google rechazó el pedido (ver log [NovaRequest])")
        return ActionResult(ok=True, message="pedido aceptado por Google")

    # --------------------------------------------------------------- internals

    def _call_auth(self, fn: Callable[..., Any], *args: Any) -> Any:
        """Ejecuta una llamada al vendor traduciendo fallos de credenciales a ProviderAuthError."""
        try:
            return fn(*args)
        except (KeyError, SystemExit) as exc:
            # gpsoauth devuelve dict sin 'Auth' cuando el aas_token fue revocado; el vendor hace exit(1)
            # cuando no puede descifrar la identity key.
            raise ProviderAuthError(f"credenciales de Google inválidas o vencidas ({exc!r})") from exc
        except EOFError as exc:
            raise ProviderAuthError("el vendor pidió input() interactivo: falta algún secreto") from exc

    def _ensure_fcm(self) -> str:
        """Registra un único callback despachador en el FcmReceiver (singleton del vendor)."""
        receiver = self._v["FcmReceiver"]()
        if not self._fcm_registered:
            receiver.register_for_location_updates(self._dispatch_fcm)
            self._fcm_registered = True
        return receiver.credentials["fcm"]["registration"]["token"]

    def _dispatch_fcm(self, response_hex: str) -> None:
        try:
            update = self._v["parse_device_update_protobuf"](response_hex)
        except Exception as exc:  # noqa: BLE001
            log.debug("FCM payload no parseable: %s", exc)
            return
        req = update.fcmMetadata.requestUuid
        with self._pending_lock:
            cb = self._pending.pop(req, None)
        if cb:
            cb(update)

    def _locate(self, t: Tracker) -> list[TrackerLocation]:
        v = self._v
        fcm_token = self._call_auth(self._ensure_fcm)
        request_uuid = v["generate_random_uuid"]()
        done = threading.Event()
        holder: dict[str, Any] = {}

        def on_response(update: Any) -> None:
            holder["update"] = update
            done.set()

        with self._pending_lock:
            self._pending[request_uuid] = on_response

        payload = v["create_location_request"](t.provider_device_id, fcm_token, request_uuid)
        resp = self._call_auth(v["nova_request"], v["NOVA_ACTION_API_SCOPE"], payload)
        if resp is None:
            with self._pending_lock:
                self._pending.pop(request_uuid, None)
            raise ProviderAuthError("nova_request rechazado (ver log [NovaRequest])")

        if not done.wait(LOCATION_TIMEOUT_S):
            with self._pending_lock:
                self._pending.pop(request_uuid, None)
            log.info("%s: sin respuesta de Find Hub en %ss", t.name, LOCATION_TIMEOUT_S)
            return []

        return self._decrypt(t, holder["update"])

    def _decrypt(self, t: Tracker, update: Any) -> list[TrackerLocation]:
        """Réplica de decrypt_location_response_locations() del vendor, devolviendo modelos."""
        v = self._v
        info = update.deviceMetadata.information
        registration = info.deviceRegistration

        identity_key = self._identity_keys.get(t.provider_device_id)
        if identity_key is None:
            identity_key = self._call_auth(v["retrieve_identity_key"], registration)
            self._identity_keys[t.provider_device_id] = identity_key
        is_mcu = v["is_mcu_tracker"](registration)

        reports = info.locationInformation.reports.recentLocationAndNetworkLocations
        locs = list(reports.networkLocations)
        times = list(reports.networkLocationTimestamps)
        if reports.HasField("recentLocation"):
            locs.append(reports.recentLocation)
            times.append(reports.recentLocationTimestamp)

        semantic = v["Common_pb2"].Status.SEMANTIC
        out: list[TrackerLocation] = []
        for loc, ts in zip(locs, times):
            if loc.status == semantic:
                log.info("%s: reporte semántico '%s' sin coordenadas, ignorado",
                         t.name, loc.semanticLocation.locationName)
                continue
            rep = loc.geoLocation.encryptedReport
            try:
                if rep.publicKeyRandom == b"":
                    key_hash = hashlib.sha256(identity_key).digest()
                    plain = v["decrypt_aes_gcm"](key_hash, rep.encryptedLocation)
                else:
                    offset = 0 if is_mcu else loc.geoLocation.deviceTimeOffset
                    plain = v["fmdn_decrypt"](identity_key, rep.encryptedLocation, rep.publicKeyRandom, offset)
            except Exception as exc:  # noqa: BLE001
                log.warning("%s: no se pudo descifrar un reporte: %s", t.name, exc)
                continue

            proto = v["DeviceUpdate_pb2"].Location()
            proto.ParseFromString(plain)
            accuracy = float(loc.geoLocation.accuracy) if loc.geoLocation.accuracy else None
            out.append(TrackerLocation(
                provider_device_id=t.provider_device_id,
                latitude=proto.latitude / 1e7,
                longitude=proto.longitude / 1e7,
                accuracy_m=accuracy,
                observed_at=datetime.fromtimestamp(int(ts.seconds), tz=timezone.utc),
                source=LocationSource.OWN_DEVICE if rep.isOwnReport else LocationSource.NETWORK,
                raw={
                    "status": STATUS_NAMES.get(loc.status, str(loc.status)),
                    "altitude": proto.altitude,
                    "is_own_report": bool(rep.isOwnReport),
                    "device_time_offset": loc.geoLocation.deviceTimeOffset,
                },
            ))
        log.info("%s: %d reportes descifrados", t.name, len(out))
        return out
