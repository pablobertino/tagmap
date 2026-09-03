# Fase 0 — Prueba técnica en Windows

Objetivo: confirmar que los tags Xiaomi (p. ej. `Daniel`) se pueden leer con `GoogleFindMyTools`, medir con qué frecuencia cambian las posiciones y cuánto dura la autenticación. **Criterio de aprobación:** posiciones válidas de al menos un tag durante 48 h sin intervención manual.

## 1. Requisitos

- Windows 10/11, Google Chrome actualizado.
- Python 3.12 (`winget install Python.Python.3.12`).
- Git (`winget install Git.Git`).
- Una cuenta Google que tenga los tags en Find Hub. Se recomienda una cuenta secundaria si es posible; si no, la principal funciona.

## 2. Instalar GoogleFindMyTools

Desde `C:\CLAUDE\XIAOMI_TAG`:

```powershell
cd collector
python -m venv .venv
.\.venv\Scripts\Activate.ps1
git clone https://github.com/leonboe1/GoogleFindMyTools vendor\GoogleFindMyTools
pip install -r vendor\GoogleFindMyTools\requirements.txt
pip install -r requirements.txt
```

`vendor/` está en `.gitignore`; el repo externo nunca se versiona aquí.

## 3. Autenticar (una sola vez, produce `secrets.json`)

```powershell
cd vendor\GoogleFindMyTools
python main.py
```

Se abre Chrome; iniciá sesión con la cuenta Google. Al terminar, el script lista los dispositivos y crea `Auth/secrets.json` (la ruta exacta la indica el propio programa; puede variar entre versiones).

Copiá ese archivo a `collector\secrets.json`:

```powershell
Copy-Item Auth\secrets.json ..\..\secrets.json
cd ..\..
```

> `secrets.json` contiene tokens de Google y claves E2EE. Está en `.gitignore`. No lo pegues en chats, issues ni logs.

## 4. Verificar el adaptador

```powershell
python -m phase0.list_trackers
```

Debe imprimir una tabla con los tags (`Daniel`, etc.) y su `provider_device_id`. Si falla con `ImportError` o `AttributeError`, la versión de GoogleFindMyTools cambió sus módulos: ajustá **solo** `providers/google_find_hub/adapter.py` (ver comentarios `# ADAPT`). Nada más del proyecto depende de esos nombres.

## 5. Obtener una posición por tag

```powershell
python -m phase0.sample_once
```

Escribe `phase0/samples/sample-<fecha>.json` con el formato normalizado de la spec §21. Revisá que `latitude`, `longitude`, `observedAt` y `accuracyMeters` tengan sentido. Anotá qué campos vienen vacíos: eso define el contrato de datos.

## 6. Muestreo de 48–72 h

```powershell
python -m phase0.sample_loop --interval-minutes 15 --hours 72
```

Cada ciclo agrega una línea a `phase0/samples/loop.jsonl` (solo posiciones nuevas) y una a `phase0/samples/health.jsonl` (ok/error por ciclo). Dejalo corriendo en una ventana de PowerShell; si la PC se suspende, el ciclo se reanuda al despertar.

Para que Windows no suspenda: `powercfg /change standby-timeout-ac 0`.

## 7. Informe

```powershell
python -m phase0.report
```

Imprime por tag: cantidad de posiciones únicas, intervalo mediano entre cambios, precisión mediana, mayor hueco sin datos, y si hubo errores de autenticación. Guardá el resultado en `docs/FASE0-resultado.md`.

## 8. Decisiones que salen de aquí

| Pregunta | Dónde impacta |
|---|---|
| ¿Viene `accuracy`? ¿En qué unidad? | Clasificación buena/media/baja (spec §13) |
| ¿Viene `observed_at` real o solo hora de consulta? | Todo el historial; sin esto no hay "hace 8 min" fiable |
| ¿Cuántas posiciones distintas hay por consulta? (a veces la red devuelve varias) | RPC `ingest_locations` acepta lote |
| ¿Cada cuánto cambia realmente? | Intervalo del recolector (15 min por defecto) |
| ¿Expira el token? ¿Cuándo? | Alerta administrativa + procedimiento de renovación |
| ¿Existe "hacer sonar" para Xiaomi Tag? | Acción `sound` de Fase 4 |

Con esto se congela `docs/CONTRATO-DATOS.md` y arranca Fase 1.
