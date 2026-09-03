from datetime import datetime, timedelta, timezone

import pytest

from tagmap_collector.models import LocationSource, Tracker, TrackerLocation
from tagmap_collector.providers.base import ProviderAuthError, TrackerProvider
from tagmap_collector.providers.fake import FakeProvider
from tagmap_collector.runner import Collector
from tagmap_collector.sink import MemorySink

T0 = datetime(2026, 9, 2, 15, 0, tzinfo=timezone.utc)


def loc(dev="d1", lat=-34.6, lon=-58.4, at=T0, acc=50.0):
    return TrackerLocation(provider_device_id=dev, latitude=lat, longitude=lon, accuracy_m=acc, observed_at=at)


class StaticProvider(TrackerProvider):
    def __init__(self, locs):
        self.locs = locs
        self.calls = 0

    def list_trackers(self):
        return [Tracker(provider_device_id="d1", name="Daniel")]

    def get_latest_locations(self, trackers):
        self.calls += 1
        return self.locs


class AuthDeadProvider(TrackerProvider):
    def list_trackers(self):
        raise ProviderAuthError("401 invalid_grant")

    def get_latest_locations(self, trackers):
        return []


def test_model_requires_timezone():
    with pytest.raises(ValueError):
        TrackerLocation(provider_device_id="x", latitude=0, longitude=0, observed_at=datetime(2026, 1, 1))


def test_model_rejects_bad_coords():
    with pytest.raises(ValueError):
        loc(lat=95)


def test_dedup_key_rounds_coords_and_seconds():
    a = loc(lat=-34.6000001, at=T0.replace(microsecond=500))
    b = loc(lat=-34.6000004, at=T0)
    assert a.dedup_key() == b.dedup_key()


def test_collector_dedups_across_cycles():
    p = StaticProvider([loc(), loc(at=T0 + timedelta(minutes=10))])
    s = MemorySink()
    c = Collector(p, s, interval_minutes=15)
    r1 = c.run_once()
    r2 = c.run_once()
    assert r1.inserted == 2
    assert r2.inserted == 0
    assert len(s.locations) == 2
    assert s.heartbeats[-1][0] == "ok"


def test_collector_reports_auth_expired():
    s = MemorySink()
    r = Collector(AuthDeadProvider(), s, 15).run_once()
    assert not r.ok and r.auth_expired
    assert s.heartbeats[-1][0] == "auth_expired"


def test_fake_provider_pipeline():
    s = MemorySink()
    r = Collector(FakeProvider(), s, 15).run_once()
    assert r.ok and r.trackers == 2 and r.inserted == 2
    assert {t.name for t in s.trackers} == {"Daniel", "Mochila"}


def test_sample_json_format():
    d = loc().to_sample_json("Daniel")
    assert set(d) == {"trackerId", "name", "latitude", "longitude", "accuracyMeters", "observedAt", "receivedAt", "source"}
    assert d["observedAt"].endswith("+00:00")


def test_secrets_validation(tmp_path):
    from tagmap_collector.providers.google_find_hub.adapter import GoogleFindHubProvider
    repo = tmp_path / "vendor"; (repo / "Auth").mkdir(parents=True)
    (repo / "Auth" / "secrets.json").write_text('{"aas_token": "x"}')
    with pytest.raises(ProviderAuthError, match="incompleto"):
        GoogleFindHubProvider(repo_path=repo)


def test_process_actions_sound():
    class SoundProvider(StaticProvider):
        def play_sound(self, provider_device_id):
            from tagmap_collector.models import ActionResult
            return ActionResult(ok=True, message="aceptado")

    s = MemorySink()
    s.actions = [{"id": "a1", "provider_device_id": "d1", "action": "sound_start"},
                 {"id": "a2", "provider_device_id": "d1", "action": "sound_stop"}]
    n = Collector(SoundProvider([]), s, 15).process_actions()
    assert n == 2
    assert s.finished[0] == ("a1", True, "aceptado")
    assert s.finished[1][0] == "a2" and s.finished[1][1] is False   # stop no soportado por StaticProvider
