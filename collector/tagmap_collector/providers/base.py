from __future__ import annotations

from abc import ABC, abstractmethod

from ..models import ActionResult, RefreshResult, Tracker, TrackerLocation


class ProviderError(Exception):
    """Error genérico del conector."""


class ProviderAuthError(ProviderError):
    """Autenticación vencida o inválida. Requiere intervención del administrador."""


class TrackerProvider(ABC):
    """Equivalente Python de la interfaz Kotlin de la spec §18."""

    @abstractmethod
    def list_trackers(self) -> list[Tracker]: ...

    @abstractmethod
    def get_latest_locations(self, trackers: list[Tracker]) -> list[TrackerLocation]: ...

    def request_refresh(self, provider_device_id: str) -> RefreshResult:
        return RefreshResult(accepted=False, message="no soportado por este proveedor")

    def play_sound(self, provider_device_id: str) -> ActionResult:
        return ActionResult(ok=False, message="no soportado por este proveedor")

    def stop_sound(self, provider_device_id: str) -> ActionResult:
        return ActionResult(ok=False, message="no soportado por este proveedor")
