"""Modelos normalizados. Contrato de datos entre proveedor, recolector y Supabase (spec §21)."""

from __future__ import annotations

from datetime import datetime, timezone
from enum import Enum
from typing import Optional

from pydantic import BaseModel, Field, field_validator


class LocationSource(str, Enum):
    NETWORK = "find_hub_network"   # detectado por la red colaborativa
    OWN_DEVICE = "own_device"      # reportado por un teléfono propio
    UNKNOWN = "unknown"


class Tracker(BaseModel):
    provider_device_id: str = Field(description="ID canónico entregado por el conector")
    name: str
    model: Optional[str] = None
    supports_sound: bool = False


class TrackerLocation(BaseModel):
    provider_device_id: str
    latitude: float
    longitude: float
    accuracy_m: Optional[float] = None
    observed_at: datetime = Field(description="Hora de la detección original")
    received_at: datetime = Field(default_factory=lambda: datetime.now(timezone.utc))
    source: LocationSource = LocationSource.UNKNOWN
    provider_report_id: Optional[str] = None
    raw: Optional[dict] = Field(default=None, description="Dato original sin secretos, para diagnóstico")

    @field_validator("latitude")
    @classmethod
    def _lat(cls, v: float) -> float:
        if not -90 <= v <= 90:
            raise ValueError("latitud fuera de rango")
        return v

    @field_validator("longitude")
    @classmethod
    def _lon(cls, v: float) -> float:
        if not -180 <= v <= 180:
            raise ValueError("longitud fuera de rango")
        return v

    @field_validator("observed_at", "received_at")
    @classmethod
    def _tz(cls, v: datetime) -> datetime:
        if v.tzinfo is None:
            raise ValueError("los timestamps deben tener zona horaria")
        return v.astimezone(timezone.utc)

    def dedup_key(self) -> tuple:
        """Coincide con el índice único de la tabla locations."""
        return (
            self.provider_device_id,
            self.observed_at.replace(microsecond=0),
            round(self.latitude, 6),
            round(self.longitude, 6),
        )

    def to_sample_json(self, name: str) -> dict:
        """Formato de muestra de la spec §21."""
        return {
            "trackerId": self.provider_device_id,
            "name": name,
            "latitude": self.latitude,
            "longitude": self.longitude,
            "accuracyMeters": self.accuracy_m,
            "observedAt": self.observed_at.isoformat(),
            "receivedAt": self.received_at.isoformat(),
            "source": self.source.value,
        }


class RefreshResult(BaseModel):
    accepted: bool
    message: str = ""


class ActionResult(BaseModel):
    ok: bool
    message: str = ""
