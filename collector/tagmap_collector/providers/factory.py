from __future__ import annotations

import os

from ..config import Settings
from .base import TrackerProvider


def build_provider(settings: Settings) -> TrackerProvider:
    """PROVIDER=fake para pruebas sin Google; por defecto GoogleFindMyTools."""
    if os.getenv("PROVIDER", "google").lower() == "fake":
        from .fake import FakeProvider

        return FakeProvider()
    from .google_find_hub import GoogleFindHubProvider

    return GoogleFindHubProvider(
        repo_path=settings.gfmt_repo_path,
        secrets_path=settings.gfmt_secrets_path,
        secrets_b64=settings.gfmt_secrets_b64,
    )
