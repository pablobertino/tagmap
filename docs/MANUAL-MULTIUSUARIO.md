# TagMap — Manual multiusuario

Cómo funciona TagMap con varias personas, cada una con su cuenta de Google y sus propios tags.

## Conceptos

- **Usuario de TagMap**: email + contraseña para entrar a la app. Lo crea el administrador (Pablo) en Supabase.
- **Cuenta de Google**: la cuenta que tiene los tags Xiaomi / teléfonos en *Find Hub*. Cada usuario registra la suya **una sola vez** con la herramienta `tagmap-auth`, desde una PC con Chrome. Sus dispositivos aparecen en su app automáticamente.
- **Compartir**: el dueño de un tag puede dejar que otro usuario lo vea (posición, historial) y ponga sus propias alarmas. El invitado no puede renombrar, hacer sonar ni borrar el historial.
- **Recolector**: proceso en la nube (GitHub Actions) que cada 15 min consulta Find Hub con todas las cuentas registradas y guarda las posiciones. No hay nada que hacer para que tome una cuenta nueva.

## Para el administrador

### Dar de alta a una persona

1. Supabase → **Authentication → Users → Add user → Create new user**: email, contraseña, marcar **Auto confirm user**. Pasale email y contraseña por un canal privado.
2. Enviale la app (`TagMap-debug.apk`) y `tagmap-auth.exe` (o indicale que lo corra desde tu PC).
3. Nada más. Cuando registre su cuenta de Google, en ≤15 min ve sus dispositivos.

### Generar `tagmap-auth.exe` (una vez por versión)

```powershell
cd C:\CLAUDE\XIAOMI_TAG\collector
.\.venv\Scripts\Activate.ps1
.\build_tagmap_auth.ps1
```
Sale en `collector\dist\tagmap-auth.exe` (~80 MB). No contiene secretos: solo la URL y la clave pública de Supabase. Se puede repartir por Drive/WhatsApp.

Sin el .exe, la alternativa es correr `python -m tagmap_auth` desde `collector\` con el venv activado (misma experiencia, pero en tu PC).

### Ver estado de las cuentas

SQL Editor:
```sql
select g.google_email, g.collector_id, c.status, c.last_seen_at, c.message
from tagmap.google_accounts g join tagmap.collectors c on c.id = g.collector_id;
```
`status = auth_expired` → esa persona debe volver a correr `tagmap-auth` (le llega una notificación push avisándole).

### Quitar a una persona

- Que deje de recolectar: ella corre `tagmap-auth --remove`, o vos en SQL: `delete from tagmap.google_accounts where google_email = 'X';` (borra también el secreto).
- Que no entre más: Supabase → Authentication → Users → Delete user (borra sus tags, lugares y alarmas en cascada).

## Para cada usuario

### 1. Instalar la app

Instalar `TagMap-debug.apk` (permitir "orígenes desconocidos" si lo pide). Entrar con el email y contraseña que te dio el administrador. Aceptar el permiso de notificaciones.

### 2. Registrar tu cuenta de Google (una vez, en una PC con Windows y Google Chrome)

1. Ejecutá `tagmap-auth.exe` (doble clic). Se abre una ventana negra.
2. Escribí tu **email y contraseña de TagMap** (los de la app, no los de Google). La contraseña no se ve al escribir.
3. Enter cuando lo pida. Se abre Chrome: iniciá sesión con **la cuenta de Google que tiene tus tags**. Cuando termine, la ventana dice "Retrieved Account Token" y Chrome se cierra solo.
4. Se abre Chrome otra vez: Google te pide el **bloqueo de pantalla de tu teléfono** (PIN o patrón). Es Google verificando que sos vos; con eso se liberan las claves que cifran las posiciones de tus tags.
5. La ventana lista tus dispositivos y dice "Listo: cuenta … registrada". Podés cerrarla. No queda nada guardado en la PC.

En 15 minutos como máximo tus dispositivos aparecen en la app.

Si algo falla a mitad de camino, simplemente volvé a ejecutar el .exe desde el principio.

### 3. Cuando Google vence la sesión

No tiene fecha fija: pasa si cambiás la contraseña de Google, cerrás sesión "en todos los dispositivos", cambiás el bloqueo de pantalla del teléfono o restablecés el celular. Te llega una notificación "Recolector: sesión de Google vencida"; repetí el paso 2.

### 4. Compartir un tag con otra persona

En la app: tocá el tag → ícono **Compartir** (junto al lápiz) → escribí el email con el que esa persona entra a TagMap → **Agregar**. Ella lo ve marcado como "compartido" y puede crear sus propios lugares y alarmas. Para quitarle el acceso: mismo diálogo → ✕ junto a su email.

### 5. Alarmas

- **Llegó / Salió**: creá un lugar (mantené pulsado el mapa), luego en **Lugares** activá *Llega* y *Sale* por tag. Aviso por notificación.
- **Sin señal**: si un tag pasa más de 12 h sin reportar te avisa. Se cambia por tag en **Editar tag → Avisar si no reporta en…** (Nunca / 6 / 12 / 24 / 48 h).
- Todo queda en la pantalla **Eventos** (campana).

## Preguntas frecuentes

**¿Por qué no se puede autorizar desde la app?** Google no tiene API pública para Find Hub. La única forma es interceptar la sesión en Chrome de escritorio y pedirle a Google las claves de cifrado con el bloqueo del teléfono. Eso no se puede hacer desde Android ni desde un servidor sin pedirle a la gente su contraseña de Google.

**¿Es seguro?** El archivo con la sesión se guarda cifrado en Supabase (Vault) y solo lo lee el recolector. Nunca pasa por chats ni por GitHub. En la PC se borra al terminar.

**¿Por qué el tag muestra "hace 3 h"?** La red de Google solo reporta cuando algún teléfono Android pasa cerca del tag. Si está quieto en un lugar sin gente, no hay reportes. No es un problema de TagMap.

**¿Puedo tener dos cuentas de Google?** Una por usuario de TagMap. Si necesitás dos, creá un segundo usuario y compartí los tags entre ambos.

## Interfaz web

https://pablobertino.github.io/tagmap/ — mismo usuario y contraseña que la app. Muestra lo mismo (mapa, historial, lugares, alarmas, eventos, compartir) desde cualquier navegador. Las notificaciones push siguen llegando solo a la app Android.
