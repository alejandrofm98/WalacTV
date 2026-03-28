# GitHub Releases Updates

La app consulta por defecto el ultimo release publico de GitHub:

- `https://api.github.com/repos/alejandrofm98/WalacTV/releases/latest`

Y toma estos datos del release:

- `tag_name` para la version visible en ajustes
- `body` para el changelog
- `browser_download_url` del asset `.apk` para descargar la actualizacion

## Flujo para publicar una nueva version

1. Sube `versionCode` y `versionName` en `app/build.gradle.kts`
2. Genera el APK release:

```bash
./gradlew :app:assembleRelease
```

3. Renombra el APK si quieres mantener nombres claros, por ejemplo:

- `WalacTV-1.5.apk`

4. Crea un release publico en GitHub con tag:

- `v1.5`

5. Sube el APK como asset del release
6. Verifica que el release tenga un asset `.apk` publico antes de probar el auto-updater

## Optional vs obligatoria

- opcional: deja `minSupportedCode` en una version anterior soportada
- obligatoria: pon `minSupportedCode` igual a `latestVersionCode`

## Importante

- el APK nuevo debe estar firmado con la misma clave que la app instalada
- publica primero el release y verifica que el asset `.apk` descarga bien
- si el release no tiene asset `.apk`, la app no puede ofrecer esa version para descarga y seguira usando la ultima actualizacion cacheada valida
