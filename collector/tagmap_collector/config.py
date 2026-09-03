from __future__ import annotations

import logging
import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv

MIN_INTERVAL_MINUTES = 5  # spec §3.5


@dataclass(frozen=True)
class Settings:
    supabase_url: str
    supabase_service_role_key: str
    gfmt_repo_path: Path
    gfmt_secrets_path: Path | None
    gfmt_secrets_b64: str | None
    interval_minutes: int
    collector_id: str
    log_level: str

    @classmethod
    def load(cls, require_supabase: bool = True) -> "Settings":
        load_dotenv(Path(__file__).resolve().parent.parent / ".env")
        interval = int(os.getenv("COLLECT_INTERVAL_MINUTES", "15"))
        if interval < MIN_INTERVAL_MINUTES:
            raise ValueError(f"COLLECT_INTERVAL_MINUTES no puede ser menor a {MIN_INTERVAL_MINUTES}")
        url = os.getenv("SUPABASE_URL", "")
        key = os.getenv("SUPABASE_SERVICE_ROLE_KEY", "")
        if require_supabase and not (url and key):
            raise ValueError("Faltan SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY")
        secrets_path = os.getenv("GFMT_SECRETS_PATH")
        return cls(
            supabase_url=url,
            supabase_service_role_key=key,
            gfmt_repo_path=Path(os.getenv("GFMT_REPO_PATH", "./vendor/GoogleFindMyTools")),
            gfmt_secrets_path=Path(secrets_path) if secrets_path else None,
            gfmt_secrets_b64=os.getenv("GFMT_SECRETS_B64") or None,
            interval_minutes=interval,
            collector_id=os.getenv("COLLECTOR_ID", "local"),
            log_level=os.getenv("LOG_LEVEL", "INFO"),
        )


class RedactingFormatter(logging.Formatter):
    """Oculta coordenadas y tokens en logs (spec §11)."""

    def format(self, record: logging.LogRecord) -> str:
        msg = super().format(record)
        for needle in ("eyJ", "ya29.", "aas_et/"):
            if needle in msg:
                msg = msg.replace(needle, "[redacted]")
        return msg


def setup_logging(level: str = "INFO") -> None:
    handler = logging.StreamHandler()
    handler.setFormatter(RedactingFormatter("%(asctime)s %(levelname)s %(name)s: %(message)s"))
    logging.basicConfig(level=level.upper(), handlers=[handler], force=True)
