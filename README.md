# JioTV Android TV

A standalone native Android TV starter inspired by the channel library and Jio account/service flow in [`andromyth/jiotv-lg-webos`](https://github.com/andromyth/jiotv-lg-webos).

> **Status: initial starter, not yet a validated full JioTV client.** The stream resolver and manifest proxy are incomplete. Some channels will fail until this is completed and tested with an authorized Jio account. The app does not bypass subscription checks or DRM.

## Included

- Native Android TV launcher and landscape layout.
- D-pad-friendly channel browse and filters.
- OTP send/verify adapter for the endpoints used by the upstream project.
- Channel list retrieval.
- Media3 player dependencies (HLS/DASH) and initial playback wiring.
- GitHub Actions Android debug APK build workflow.

## Build

Open this project in Android Studio and let it install Android SDK 36 and the Gradle plugin dependencies, then run **Build > Build APK(s)**. Or, using a local Gradle installation compatible with the Android Gradle Plugin:

```sh
gradle assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`.

## Install on Android TV

Enable Developer Options and USB/network debugging, then install the APK via Android Studio or `adb install app-debug.apk`. Launch **JTV Android TV**, sign in using Settings and a Jio OTP, and refresh the channels.

## Current limitations

The original webOS repository contains a Node-based service with token refresh, stream URL resolution, signed URL handling, HLS/DASH manifest rewriting, and a local loopback proxy. This initial Android project only covers the basic OTP/channel contract and a narrow direct HLS path. It does not yet port the complete resolver/proxy or implement Widevine DRM configuration. Build success alone does not guarantee working playback; device testing and API response validation are required.

## Security and legal notes

Use only a Jio subscription and account you are authorized to use. No credentials, OTPs, or session tokens should be committed. Unofficial endpoints may change. This project is community work and is not affiliated with or endorsed by Jio or Xiaomi.
