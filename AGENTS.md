# AGENTS.md - WalacTV (Android)

> Guia para agentes de codigo que trabajen en WalacTV, el cliente
> Android TV del ecosistema IPTV. Secciones 0-3 son contexto obligatorio
> antes de tocar nada. Secciones 4-10 son referencia operativa.

## 0. Ecosistema y posicion del proyecto

WalacTV es el nodo cliente Android de un ecosistema de 3 proyectos
hermanos del mismo owner (`alejandrofm98`).

```
   +-------------+        +-------------+
   |  walactvWeb |        |   WalacTV   |
   |  Angular 20 |        |  Android TV |
   |  :4200      |        |  Kotlin     |
   +------+------+        +-----+-------+
          |   HTTP + JWT       |
          |  (REST + HLS web)  |  (REST + HLS android)
          v                    v
   +--------------------------------------+
   |          iptv-api                    |
   |     FastAPI @ localhost:3010         |
   |  - REST + HLS proxy                  |
   |  - Postgres / Supabase               |
   +---+----------+-----------+-----------+
       |          |           |
       | lee JSON | escribe   | escribe scraper_failures
       v          v           v
   +--------+ +----------+ +-----------+
   |walactv-| | iptv-data| | Postgres  |
   |scrapper| | volumen  | | tabla     |
   | (Ofelia| | compartido| | scraper_  |
   |  cron) | |  (JSONs) | | failures  |
   +--------+ +----------+ +-----------+
```

### Tabla de proyectos hermanos

| Proyecto           | Rol                  | Stack                                | Repo                                                 | Relacion con WalacTV                                        |
| ------------------ | -------------------- | ------------------------------------ | ---------------------------------------------------- | ----------------------------------------------------------- |
| iptv-api           | Backend central      | FastAPI, Postgres, Python 3.12       | `github.com/alejandrofm98/iptv-api`                  | Proveedor unico de datos. Todos los endpoints REST + HLS.   |
| walactvWeb         | Cliente web          | Angular 20                           | `github.com/alejandrofm98/walactvWeb`                | Mismo backend, mismo contrato de API.                       |
| walactv-scrapper   | Productor catalogos  | Python 3.12, asyncpg, Ofelia         | `github.com/alejandrofm98/walactv-scrapper`          | Escribe datos que iptv-api sirve al Android.                |

### Contratos API con iptv-api

Este cliente consume los siguientes endpoints (ver `iptv-api/AGENTS.md` seccion 4.1):

- `POST /api/auth/login` — autenticacion
- `GET /api/watch-progress` — continuar viendo
- `PUT /api/watch-progress/{id}` — guardar progreso
- `DELETE /api/watch-progress/{id}` — eliminar progreso
- `GET /api/content/stats?content_type=...` — estadisticas
- `GET /api/content/{kind}/{id}` — detalle individual
- `GET /api/content?...&country=...&page=...` — listado paginado
- `GET /api/content/countries?content_type=...` — paises disponibles
- `GET /api/content/groups?content_type=...` — grupos disponibles
- `GET /api/content/channels/all` — todos los canales
- `GET /api/series/{name}/episodes` — episodios de serie
- `GET /api/search?q=...` — busqueda
- `GET /api/home?country=...` — home catalog
- `GET /api/channel-favorites` — favoritos
- `GET /live/{username}/{password}/{channelId}` — stream en vivo
- `GET /movie/{username}/{password}/{providerId}` — stream VOD

**Importante**: los endpoints usan `content_type` en **plural** (`movies`,
`series`, `channels`). El servicio backend en `content_service.py`
acepta tanto singular como plural thanks a un fix reciente.

## 1. Contexto rapido

- **Stack**: Kotlin, Android TV (Leanback + Compose theme scaffolding),
  Firebase Firestore, Media3/ExoPlayer, Glide, Room (KSP2), Retrofit+Gson.
- **Min SDK**: 24. **Target SDK**: 36. **Compile SDK**: 36.
- **Kotlin**: 2.2.10. **KSP**: 2.3.2. **Room**: 2.7.2.
- **Puerto del backend**: `IPTV_BASE_URL` configurado en `local.properties`.
- **Build**: `./gradlew :app:assembleDebug` (wrapper, siempre).
- **Sin tests unitarios ni instrumentation tests** actualmente.

## 2. Arquitectura

### 2.1 Paquetes principales (`app/src/main/java/com/example/walactv/`)

```
com.example.walactv/
  CatalogModels.kt          # CatalogItem, WatchProgressItem, ContentKind
  RemoteCatalogModels.kt     # Parsing JSON del servidor a CatalogItem
  IptvRepository.kt          # Capa de red: HTTP a iptv-api
  WatchProgressRepository.kt # Continuar viendo (HTTP directo)
  PlayerFragment.kt          # Reproductor Media3/ExoPlayer
  PreferencesManager.kt      # SharedPreferences
  ContentCacheManager.kt     # Cache de contenido en Room
  ChannelCacheManager.kt     # Cache de canales en Room
  local/                     # Room DB: Entities, DAOs, Database
  ui/
    compose/                 # UI Compose: HomeScreen, CatalogLogic, PlaybackLogic
    theme/                   # Tema Compose
  StreamOption.kt            # Opciones de stream
```

### 2.2 Flujo de datos principal

```
iptv-api (FastAPI)
    |
    v HTTP REST + JWT
IptvRepository.kt
    |
    v parseRemoteCatalogItem()
CatalogItem / WatchProgressItem
    |
    v UI (Compose fragments)
PlayerFragment.kt (ExoPlayer)
    |
    v WatchProgressRepository
iptv-api (guardar progreso)
```

### 2.3 Room Database

- **Entidades**: `ChannelEntity`, `MovieEntity`, `SeriesEntity`
- **DAOs**: `ChannelDao`, `MovieDao`, `SeriesDao`
- **Database**: `ContentDatabase` (Room 2.7.2, KSP2)
- **Version**: 4, con `fallbackToDestructiveMigration()`
- **Ubicacion**: `app/src/main/java/com/example/walactv/local/`

### 2.4 Continuar viendo (Continue Watching)

El flujo de "Continuar viendo" es critico. Funciona asi:

1. `WatchProgressRepository.getContinueWatching()` carga items del backend
2. `CatalogLogic.loadContinueWatching()` construye cards con `stableId = "cw_{type}:{contentId}"`
3. Al hacer click, `PlaybackLogic.openContinueWatchingItem()` busca en `continueWatchingEntries`
4. Llama a `IptvRepository.fetchContentItem()` para obtener el item completo
5. `PlayerFragment` reproduce y guarda progreso cada 30 segundos

**Cuidado**: `fetchContentItem()` usa `GET /api/content/{kind}/{contentId}`.
El `contentId` puede ser un `provider_id` (string numerico) o un UUID.
El backend busca por UUID, tmdb_id O provider_id.

## 3. Patrones obligatorios

1. **Network calls**: usar `IptvRepository` o `WatchProgressRepository`.
   No crear conexiones HTTP directas en fragments o UI.
2. **Error handling**: envolver calls de red en try/catch. Loguear con TAG
   de la clase. Mostrar Toast solo para errores user-visible.
3. **Coroutines**: atar scopes a lifecycle. Usar `scope.launch` del fragment.
   No crear scopes ad-hoc sin cancelacion.
4. **Room**: DAOs con suspend. Usar `Dispatchers.IO` para queries.
   Los entities usan UUID como PK.
5. **Logging**: usar TAG constante por clase. `Log.d` para debug,
   `Log.w` para warnings, `Log.e` para errores.
6. **D-pad navigation**: preservar comportamiento D-pad en todo UI.
   No asumir touch.

## 4. Comandos de build y verificacion

### Build

```bash
./gradlew :app:assembleDebug          # Build debug (mas rapido)
./gradlew :app:build                  # Build completo
./gradlew :app:assembleRelease        # Build release (necesita signing)
./gradlew :app:installDebug           # Instalar en dispositivo
./gradlew :app:clean                  # Limpiar outputs
```

### Lint

```bash
./gradlew :app:lintDebug              # Lint solo debug
./gradlew :app:lint                   # Lint completo
./gradlew :app:lintFix                # Autofix seguro
```

### Tests (no hay actualmente)

```bash
./gradlew :app:testDebugUnitTest      # Unit tests
./gradlew :app:connectedDebugAndroidTest  # Instrumentation tests
```

### Verificacion rapida

- Solo cambios Kotlin/XML: `./gradlew :app:assembleDebug`
- Cambios en manifest/resources: `./gradlew :app:lintDebug`
- Cambios en dependencias: `./gradlew :app:build`
- Cambios en playback: verificar en dispositivo con D-pad

## 5. Guia de fuentes

### Layout y UI
- `app/src/main/res/layout/` — XMLs de layout
- `app/src/main/res/values/strings.xml` — strings user-visible
- `app/src/main/java/com/example/walactv/ui/compose/` — Compose UI
- `app/src/main/java/com/example/walactv/ui/theme/` — Tema Compose

### Network y datos
- `IptvRepository.kt` — todos los calls REST al backend
- `WatchProgressRepository.kt` — continuar viendo
- `RemoteCatalogModels.kt` — parsing JSON del servidor
- `CatalogModels.kt` — modelos de datos internos

### Playback
- `PlayerFragment.kt` — ExoPlayer + lifecycle
- `PlaybackLogic.kt` — dispatch de clicks y navegacion
- `CatalogLogic.kt` — carga de catalogo yContinue Watching

### Room (local cache)
- `local/Entities.kt` — entidades Room
- `local/Daos.kt` — DAOs
- `local/ContentDatabase.kt` — Database singleton

## 6. Kotlin Style

- Seguir estilo oficial Kotlin (configurado en Gradle).
- Indentacion: 4 espacios.
- Trailing commas donde el codigo existente las use.
- Expresiones orientadas a retorno cuando mejore legibilidad.
- Funciones pequenas; extraer helpers en vez de anidar mas.

### Imports
- Explicitos (sin wildcards).
- Orden: stdlib/platform -> AndroidX -> third-party -> local.
- Eliminar imports no usados inmediatamente.

### Naming
- Clases, fragments, data classes: `PascalCase`
- Funciones, propiedades: `camelCase`
- Constantes: `UPPER_SNAKE_CASE`
- Resource IDs, XML: `snake_case`
- Preferir ingles para identificadores nuevos.
- Preservar nombres de schema externo (Firestore, API): `nombre`,
  `grupo`, `hora`, `eventos`, `providerId`, etc.

### Nullability
- Usar Kotlin nullability en vez de sentinel values.
- Early returns > nested null checks.
- `requireContext()` / `requireActivity()` solo cuando el lifecycle lo garantiza.

### Coroutines
- Scopes atados al lifecycle del fragment.
- `Dispatchers.IO` para network/DB.
- Cancelar scopes custom en el callback de lifecycle correspondiente.

## 7. Manejo de errores

- No tragar excepciones silenciosamente.
- Loguear suficiente contexto para diagnosticar fallos de playback, parsing y red.
- Toast solo para errores user-visible y accionables.
- Try/catch estrechos alrededor de llamadas riesgosas.
- Parsing de payloads del servidor: fail soft, saltar entradas malformadas.

## 8. Android TV

- Preservar navegacion D-pad en todo momento.
- No asumir UI touch-centric.
- Cambios en player: conservadores. ExoPlayer es sensible a lifecycle.
- Preservar focusability y back-navigation al editar UI de playback.
- Preferir referencias a recursos sobre dimensiones/strings hardcodeados.

## 9. Convenciones de fragment y activity

- Transacciones de fragment localizadas y faciles de seguir.
- No introducir constructores de fragment con args no-default sin
  manejar recreacion.
- Para fragments nuevos: preferir `newInstance()` + args Bundle.
- Si se toca `PlayerFragment`, verificar paths de cleanup:
  `onPause`, `onStop`, `onDestroyView`.

## 10. Errores conocidos y patterns

### Continue Watching - contentId
El `contentId` que se guarda en watch-progress puede tener dos formatos:
- `"2063803"` (cuando `provider_id` existe en el JSON del servidor)
- `"movie:2063803"` o `"series:2063803"` (cuando `provider_id` es null,
  se usa `stableId` que ya tiene el prefijo)

El endpoint `GET /api/content/{kind}/{id}` busca por UUID, tmdb_id O
provider_id. Si el ID tiene prefijo de tipo, busque solo la parte
despues de `:`.

### Room + KSP2
Room 2.7.2 es la version minimas para KSP2 (ksp 2.3.2). No
downgrade a 2.6.1 sin verificar compatibilidad con KSP.

### Retrofit + Gson
El proyecto usa Retrofit 2.11.0 + Gson converter. No mezclar
con Moshi o kotlinx.serialization sin migrar toda la capa de red.

## 11. Smells del codebase a no empeorar

- No agregar mas logica a archivos muy grandes. Extraer helpers.
- No duplicar navegacion de playback entre browse y search.
- No agregar literals de strings para URLs/tags cuando una constante basta.
- Cuidado con nombres typados o inconsistentes; preservar compatibilidad
  primero, refactorizar despues.

## 12. Checklist antes de cerrar una tarea

1. Build compila: `./gradlew :app:assembleDebug`
2. Lint limpio: `./gradlew :app:lintDebug`
3. Si se toco playback: verificar en dispositivo con D-pad
4. Si se toco Room/parsing: verificar paths de datos faltantes y validos
5. Sin secrets en el diff
6. Cambio minimo que resuelve la tarea completamente

## 13. Acceso a base de datos

Para verificar datos en Postgres del backend, usar `pgcli` o `psql`.

Las credenciales estan en el `.env` del backend iptv-api (nunca commiteado).
Consultar al owner o revisar `utils/config.py` en iptv-api para las variables
`PG_HOST`, `PG_PORT`, `PG_DATABASE`, `PG_USER`, `PG_PASSWORD`.

Siempre verificar en la BD antes de asumir que un dato no existe o esta mal.
