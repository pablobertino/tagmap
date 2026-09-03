"""Proveedores de posiciones. La interfaz TrackerProvider aísla al resto del sistema
de GoogleFindMyTools (spec §18)."""

from .base import ProviderAuthError, ProviderError, TrackerProvider

__all__ = ["TrackerProvider", "ProviderError", "ProviderAuthError"]
