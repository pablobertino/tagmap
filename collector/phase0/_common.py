from __future__ import annotations

from pathlib import Path

from tagmap_collector.config import Settings, setup_logging
from tagmap_collector.providers.factory import build_provider

SAMPLES_DIR = Path(__file__).resolve().parent / "samples"


def bootstrap():
    settings = Settings.load(require_supabase=False)
    setup_logging(settings.log_level)
    SAMPLES_DIR.mkdir(exist_ok=True)
    return settings, build_provider(settings)
