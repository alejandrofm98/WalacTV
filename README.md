# WalacTV

Android TV app for IPTV browsing and playback.

## Local setup

This repository does not include production secrets or provider-specific config.

1. Add your local IPTV base URL to `local.properties`:

```properties
walactv.iptvBaseUrl=https://your-provider-host
```

2. Build the app:

```bash
./gradlew :app:assembleDebug
```

## Notes

- `local.properties` is ignored by git and is the right place for machine-local config.
- The app asks for IPTV username and password on first launch and stores them locally on the device.
