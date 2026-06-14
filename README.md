# AudioLAN

AudioLAN is an Android app for sending and receiving low-latency PCM audio over a local network using VBAN-compatible UDP streams. It supports microphone streaming, device audio capture, receiver playback, network discovery, and an optional USB tethering transport path for IP audio over a USB cable.

## Screenshots

| Home | Stream List |
| --- | --- |
| <img src="references\Screenshot_20260615-002314.Moto App Launcher.png" alt="AudioLAN home screen" width="260"> | <img src="references/Screenshot_20260521-015318.Moto%20App%20Launcher.png" alt="AudioLAN stream list" width="260"> |

| Discovery | Settings |
| --- | --- |
| <img src="references/Screenshot_20260521-015326.Moto%20App%20Launcher.png" alt="AudioLAN discovery screen" width="260"> | <img src="references/Screenshot_20260521-015331.Moto%20App%20Launcher.png" alt="AudioLAN settings screen" width="260"> |

## Features

- Stream microphone audio from Android to VBAN receivers such as Voicemeeter.
- Stream Android playback/cast audio using MediaProjection audio capture.
- Receive VBAN audio streams on Android.
- Discover compatible streams and devices on the network.
- Configure per-stream host, port, transport mode, quality, volume, and enable state.
- Use Wi-Fi or USB tethering as the IP transport.
- Foreground services for microphone, cast, receiver, and discovery workflows.
- Dark UI with selectable accent colors, system accent support, and AMOLED background mode.

## Requirements

- Android Studio or Android Gradle Plugin compatible environment.
- JDK 17.
- Android SDK with compile SDK 35.
- Android device/emulator running Android 12 or newer, because `minSdk` is 31.
- For microphone streaming: microphone permission.
- For cast/audio playback capture: Android screen/audio capture consent.
- For Voicemeeter integration: VBAN enabled on the desktop side.

## Project Structure

```text
app/                  Android application module
app/src/main/java/    Kotlin source code
app/src/main/res/     Android resources, icons, themes
gradle/               Gradle wrapper files
commonMain/           Shared project area
```

## Build Debug APK

```powershell
.\gradlew.bat assembleDebug
```

Debug APK output:

```text
app\build\outputs\apk\debug\app-debug.apk
```

Install on a connected Android device:

```powershell
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

## Run Tests

```powershell
.\gradlew.bat test
```

## Release Signing

Release signing uses a local `keystore.properties` file. This file is intentionally ignored by Git and must not be committed.

Create `keystore.properties` in the repo root:

```properties
storeFile=audiolan-release.jks
storePassword=YOUR_STORE_PASSWORD
keyAlias=YOUR_KEY_ALIAS
keyPassword=YOUR_KEY_PASSWORD
```

Keep the following secure:

- Release `.jks` keystore file
- Keystore password
- Key alias
- Key password
- Any private backup containing those values

Build a signed release APK:

```powershell
.\gradlew.bat assembleRelease
```

APK output:

```text
app\build\outputs\apk\release\app-release.apk
```

Build a signed Android App Bundle for store publishing:

```powershell
.\gradlew.bat bundleRelease
```

AAB output:

```text
app\build\outputs\bundle\release\app-release.aab
```

## Versioning

Version values are defined in `app/build.gradle.kts`:

```kotlin
versionCode = 1
versionName = "Beta"
```

For every published update, increase `versionCode`. Android requires `versionCode` to be an integer.

## USB Tethering Mode

USB tethering mode uses Android USB tethering as an IP network link. It is not USB Audio Class and does not create a direct USB audio device. The app still sends and receives VBAN-compatible UDP packets, but the route can go over the USB tethering interface instead of Wi-Fi.

## Notes

- Receiver playback expects compatible PCM VBAN streams. The app currently focuses on 16-bit PCM playback and includes receiver-side jitter buffering and stereo playback handling.
- Network discovery is intended for receiver setup and stream detection.
- Release artifacts, local SDK config, build outputs, and signing files are excluded from Git through `.gitignore`.


