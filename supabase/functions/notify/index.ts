// TagMap — Edge Function `notify`: envía un geofence_event por FCM HTTP v1 (spec §8).
// Invocada por el trigger notify_geofence_event (pg_net) con {"event_id": "..."}.
// También alertas de sistema: {"alert_id": "..."} (tag sin señal, recolector con error).
// Autenticación propia: header x-notify-secret (verify_jwt = false).
// Secretos: NOTIFY_SECRET, FCM_SERVICE_ACCOUNT_JSON; SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY los inyecta Supabase.

import { createClient } from "npm:@supabase/supabase-js@2";
import { SignJWT, importPKCS8 } from "npm:jose@5";

const NOTIFY_SECRET = Deno.env.get("NOTIFY_SECRET")!;
const SA = JSON.parse(Deno.env.get("FCM_SERVICE_ACCOUNT_JSON") ?? "{}");
const supabase = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, {
  db: { schema: "tagmap" },
});

const CHANNELS = { ENTRY: "arrivals", EXIT: "departures" } as const;

let cachedToken: { value: string; exp: number } | null = null;

async function fcmAccessToken(): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cachedToken && cachedToken.exp > now + 60) return cachedToken.value;
  const key = await importPKCS8(SA.private_key, "RS256");
  const jwt = await new SignJWT({ scope: "https://www.googleapis.com/auth/firebase.messaging" })
    .setProtectedHeader({ alg: "RS256", typ: "JWT" })
    .setIssuer(SA.client_email).setAudience(SA.token_uri)
    .setIssuedAt(now).setExpirationTime(now + 3600)
    .sign(key);
  const res = await fetch(SA.token_uri, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: jwt }),
  });
  if (!res.ok) throw new Error(`token: ${res.status} ${await res.text()}`);
  const j = await res.json();
  cachedToken = { value: j.access_token, exp: now + j.expires_in };
  return cachedToken.value;
}

/** "19:30 ART": hora local del teléfono que recibe la push, en 24 h y con zona explícita. */
function fmtTime(iso: string, tz: string): string {
  try {
    return new Intl.DateTimeFormat("es-AR", {
      hour: "2-digit", minute: "2-digit", hourCycle: "h23", timeZone: tz, timeZoneName: "short",
    }).format(new Date(iso)).replace(",", "");
  } catch {
    return fmtTime(iso, "UTC");
  }
}

function buildMessage(ev: Record<string, any>, tz: string) {
  const t = fmtTime(ev.observed_at, tz);
  const ageMin = Math.round((Date.now() - new Date(ev.observed_at).getTime()) / 60000);
  const stale = ageMin > 30 ? ` (reporte de hace ${ageMin} min)` : "";
  return ev.event_type === "ENTRY"
    ? { title: `${ev.tracker_name} llegó a ${ev.place_name}`, body: `Detectado dentro del área a las ${t}${stale}.` }
    : { title: `${ev.tracker_name} salió de ${ev.place_name}`, body: `Última detección fuera del área a las ${t}${stale}.` };
}

async function devicesOf(ownerId: string) {
  const { data } = await supabase
    .from("mobile_devices").select("id, fcm_token, tz")
    .eq("user_id", ownerId).eq("notifications_enabled", true);
  return data ?? [];
}

async function sendPush(token: string, d: { id: string; fcm_token: string }, msg: { title: string; body: string },
                        channel: string, data: Record<string, string>): Promise<boolean> {
  const res = await fetch(`https://fcm.googleapis.com/v1/projects/${SA.project_id}/messages:send`, {
    method: "POST",
    headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      message: { token: d.fcm_token, notification: msg, android: { priority: "high", notification: { channel_id: channel } }, data },
    }),
  });
  if (res.ok) return true;
  const body = await res.text();
  console.error(`FCM ${res.status} para device ${d.id}: ${body}`);
  if (res.status === 404 || body.includes("UNREGISTERED")) {
    await supabase.from("mobile_devices").delete().eq("id", d.id);
  }
  return false;
}

/** Alerta de sistema (tag sin señal, recolector con error): {"alert_id": ...} */
async function handleAlert(alertId: string): Promise<Response> {
  const { data: al, error } = await supabase.from("system_alerts").select("*").eq("id", alertId).single();
  if (error || !al) return new Response(`alert not found: ${error?.message}`, { status: 404 });
  const devices = await devicesOf(al.owner_id);
  if (!devices.length) return new Response("no devices", { status: 200 });

  let title = "TagMap", channel = "system";
  let body: string | ((d: { tz?: string }) => string) = al.message ?? "";
  if (al.kind === "tracker_stale" && al.tracker_id) {
    channel = "stale_trackers";
    const { data: t } = await supabase.from("trackers").select("name").eq("id", al.tracker_id).single();
    const { data: last } = await supabase.from("locations").select("observed_at")
      .eq("tracker_id", al.tracker_id).order("observed_at", { ascending: false }).limit(1).maybeSingle();
    title = `${t?.name ?? "Tag"} sin señal`;
    body = (d: { tz?: string }) => last ? `${al.message}. Último reporte: ${fmtTime(last.observed_at, d.tz || "UTC")}.` : `${al.message}.`;
  } else if (al.kind === "auth_expired") {
    title = "Recolector: sesión de Google vencida";
  } else if (al.kind === "collector_error") {
    title = "Recolector con error";
  }
  const token = await fcmAccessToken();
  let sent = 0;
  for (const d of devices) {
    const text = typeof body === "function" ? body(d) : body;
    if (await sendPush(token, d, { title, body: text }, channel, { alert_id: al.id, kind: al.kind })) sent++;
  }
  return Response.json({ sent, devices: devices.length });
}

Deno.serve(async (req) => {
  if (req.headers.get("x-notify-secret") !== NOTIFY_SECRET) {
    return new Response("forbidden", { status: 403 });
  }
  const { event_id, alert_id } = await req.json();
  if (alert_id) return handleAlert(alert_id);

  const { data: ev, error } = await supabase.from("app_events").select("*").eq("id", event_id).single();
  if (error || !ev) return new Response(`event not found: ${error?.message}`, { status: 404 });
  if (ev.status !== "CREATED") return new Response("already handled", { status: 200 });

  const { data: profile } = await supabase.from("profiles").select("quiet_hours").eq("id", ev.owner_id).single();
  const tz = profile?.quiet_hours?.tz ?? "America/Argentina/Buenos_Aires";

  const devices = await devicesOf(ev.owner_id);
  if (!devices.length) {
    await supabase.from("geofence_events").update({ status: "SUPPRESSED", suppress_reason: "no_devices" }).eq("id", event_id);
    return new Response("no devices", { status: 200 });
  }

  const token = await fcmAccessToken();
  let sent = 0;
  for (const d of devices) {
    const msg = buildMessage(ev, d.tz || tz);
    const data = { event_id: ev.id, event_type: ev.event_type, tracker_id: ev.tracker_id, place_id: ev.place_id, observed_at: ev.observed_at };
    if (await sendPush(token, d, msg, CHANNELS[ev.event_type as "ENTRY" | "EXIT"], data)) sent++;
  }

  await supabase.from("geofence_events")
    .update(sent > 0
      ? { status: "SENT", notified_at: new Date().toISOString() }
      : { status: "SUPPRESSED", suppress_reason: "fcm_failed" })
    .eq("id", event_id);

  return Response.json({ sent, devices: devices.length });
});
