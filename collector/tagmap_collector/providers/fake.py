"""Proveedor simulado para tests y para probar el pipeline sin Google."""

from __future__ import annotations

import random
from datetime import datetime, timedelta, timezone

from ..models import LocationSource, Tracker, TrackerLocation
from .base import TrackerProvider


class FakeProvider(TrackerProvider):
    def __init__(self, seed: int = 1) -> None:
        self._rng = random.Random(seed)
        self._trackers = [
            Tracker(provider_device_id="fake-daniel", name="Daniel"),
            Tracker(provider_device_id="fake-mochila", name="Mochila"),
        ]
        self._center = {"fake-daniel": (-34.6037, -58.3816), "fake-mochila": (-34.6090, -58.3900)}

    def list_trackers(self) -> list[Tracker]:
        return list(self._trackers)

    def get_latest_locations(self, trackers: list[Tracker]) -> list[TrackerLocation]:
        now = datetime.now(timezone.utc)
        out = []
        for t in trackers:
            lat, lon = self._center[t.provider_device_id]
            jitter = 0.0005
            out.append(
                TrackerLocation(
                    provider_device_id=t.provider_device_id,
                    latitude=lat + self._rng.uniform(-jitter, jitter),
                    longitude=lon + self._rng.uniform(-jitter, jitter),
                    accuracy_m=self._rng.choice([45.0, 90.0, 180.0, 400.0]),
                    observed_at=now - timedelta(minutes=self._rng.randint(0, 40)),
                    source=LocationSource.NETWORK,
                )
            )
        return out
