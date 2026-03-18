# GitHub Releases Updates

La app consulta por defecto este archivo:

- `https://raw.githubusercontent.com/alejandrofm98/WalacTV/main/version.json`

Y espera que `apkUrl` apunte a un asset publico de GitHub Releases.

## Flujo para publicar una nueva version

1. Sube `versionCode` y `versionName` en `app/build.gradle.kts`
2. Genera el APK release:

```bash
./gradlew :app:assembleRelease
```

3. Renombra el APK si quieres mantener nombres claros, por ejemplo:

- `WalacTV-1.1.0.apk`

4. Crea un release publico en GitHub con tag:

- `v1.1.0`

5. Sube el APK como asset del release
6. Edita `version.json` en la rama `main`

Ejemplo:

```json
{
  "latestVersionName": "1.1.0",
  "latestVersionCode": 2,
  "minSupportedCode": 1,
  "apkUrl": "https://github.com/alejandrofm98/WalacTV/releases/download/v1.1.0/WalacTV-1.1.0.apk",
  "changelog": "Mejoras de series, audio y estabilidad"
}
```

## Optional vs obligatoria

- opcional: deja `minSupportedCode` en una version anterior soportada
- obligatoria: pon `minSupportedCode` igual a `latestVersionCode`

## Importante

- el APK nuevo debe estar firmado con la misma clave que la app instalada
- publica primero el release y verifica que el asset descarga bien
- despues actualiza `version.json`
