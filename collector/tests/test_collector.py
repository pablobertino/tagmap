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


# ----------------------------------------------------------------- multi-cuenta

def test_load_accounts_multi_and_single():
    import base64, json
    from tagmap_collector.multi import load_accounts
    sec = {k: "x" for k in ("fcm_credentials", "username", "aas_token", "shared_key", "owner_key")}
    b64 = base64.b64encode(json.dumps([{"collector_id": "a-1", "secrets": sec}, {"collector_id": "b-1", "secrets": sec}]).encode()).decode()
    accs = load_accounts({"GFMT_ACCOUNTS_B64": b64})
    assert [a.collector_id for a in accs] == ["a-1", "b-1"]
    assert json.loads(base64.b64decode(accs[0].secrets_b64)) == sec
    single = load_accounts({"GFMT_SECRETS_B64": "abc", "COLLECTOR_ID": "solo"})
    assert single[0].collector_id == "solo" and single[0].secrets_b64 == "abc"
    import pytest
    with pytest.raises(ValueError):
        load_accounts({})
    dup = base64.b64encode(json.dumps([{"collector_id": "a", "secrets": sec}, {"collector_id": "a", "secrets": sec}]).encode()).decode()
    with pytest.raises(ValueError):
        load_accounts({"GFMT_ACCOUNTS_B64": dup})


def test_load_accounts_prefers_supabase(monkeypatch):
    import base64, json
    from tagmap_collector import multi
    sec = {k: "x" for k in ("fcm_credentials", "username", "aas_token", "shared_key", "owner_key")}
    monkeypatch.setattr(multi, "load_accounts_from_supabase", lambda url, key: [multi.Account("db-1", "zzz")])
    accs = multi.load_accounts({"SUPABASE_URL": "u", "SUPABASE_SERVICE_ROLE_KEY": "k", "GFMT_SECRETS_B64": "abc"})
    assert [a.collector_id for a in accs] == ["db-1"]
    # sin cuentas en la base → cae a env
    monkeypatch.setattr(multi, "load_accounts_from_supabase", lambda url, key: [])
    accs = multi.load_accounts({"SUPABASE_URL": "u", "SUPABASE_SERVICE_ROLE_KEY": "k", "GFMT_SECRETS_B64": "abc", "COLLECTOR_ID": "solo"})
    assert accs[0].collector_id == "solo"
    # error en la base → cae a env sin romper
    def boom(url, key): raise RuntimeError("x")
    monkeypatch.setattr(multi, "load_accounts_from_supabase", boom)
    assert multi.load_accounts({"SUPABASE_URL": "u", "SUPABASE_SERVICE_ROLE_KEY": "k", "GFMT_SECRETS_B64": "abc"})[0].secrets_b64 == "abc"
