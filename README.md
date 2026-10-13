# AudioCast — Static-Image Audio Streaming for Twitch

AudioCast is a native Android app that livestreams **microphone audio** to
Twitch over RTMP while the outgoing video track is generated entirely from a
**single static image** you pick from your device.

> Static image + live audio = Twitch livestream.

The app never captures your screen, never uses a camera, and never saves
audio or video recordings. See [Privacy](#privacy).

---

## Features

- Twitch RTMP ingest (default `rtmp://live.twitch.tv/app`), configurable server
- Stream key encrypted at rest with Android Keystore (AES-256/GCM); never logged
- Banner picker via the Storage Access Framework — PNG / JPEG / WebP
- Any image size or aspect ratio is accepted and auto-scaled
  - **Fit**: whole image visible, remaining space is black
  - **Fill**: image covers the frame, overflow is cropped
- Output layouts: **1280×720 horizontal** or **720×1280 vertical**, 30 fps
  (24/60 selectable)
- Live **Change Banner** while streaming — swapped at a frame boundary with
  no reconnect and no audio interruption
- Microphone via `AudioRecord` → AAC-LC, 48 kHz, mono (stereo optional),
  96–160 kbps
- Mute / unmute, peak audio meter, stream duration, bitrate + congestion stats
- Foreground service (`microphone` type) keeps streaming in the background
  with a notification + Stop action
- Automatic reconnect with exponential backoff and a configurable cap
- H.264 video encoding via `MediaCodec`; low-bandwidth static-scene defaults

## Tech stack

| Layer | Choice |
|---|---|
| Language / UI | Kotlin, Jetpack Compose (Material 3) |
| Min / Target SDK | 26 / 35 |
| RTMP + encoders | [RootEncoder](https://github.com/pedroSG94/RootEncoder) `2.8.1` (`RtmpStream`, `VideoSource`, `MicrophoneSource`, `MediaCodec`) |
| Image selection | `ActivityResultContracts.OpenDocument` (SAF) + persistable URI permission |
| Settings | DataStore Preferences |
| Secrets | Android Keystore (AES/GCM) |
| Service | Foreground service, `FOREGROUND_SERVICE_MICROPHONE` |

The project deliberately uses `com.github.pedroSG94.RootEncoder:library` from
JitPack: it is a maintained Android RTMP client with H.264/AAC support, correct
timestamp handling, socket timeouts and reconnect support. The static-image
pipeline is implemented as a custom `VideoSource`
(`video/StaticImageSource.kt`) feeding a pre-composited 1280×720/720×1280
frame — the same mechanism the library's own `BitmapSource` uses, extended
with a safe mid-stream frame swap.

## Architecture

```
app/src/main/java/com/audiocast/app/
├── AudioCastApp.kt            Application + wiring
├── MainActivity.kt            NavHost (3 screens)
├── data/                      StreamConfig model + DataStore repository
├── security/SecureKeyStore.kt Keystore-encrypted stream key
├── image/
│   ├── FrameGeometry.kt       Pure fit/fill rect math (unit-tested)
│   └── BannerDecoder.kt       Uri → sampled Bitmap → composed output frame
├── video/StaticImageSource.kt VideoSource: draws the frame into the encoder
│                              surface at a fixed fps; supports live swap
├── audio/MeteredMicrophoneSource.kt  Mic capture + level metering + mute
├── streaming/
│   ├── StreamController.kt    Lifecycle, state machine, reconnect policy
│   ├── ReconnectPolicy.kt     Bounded retries w/ exponential backoff
│   └── TwitchIngest.kt        Endpoint builder + key-safe sanitization
├── service/StreamService.kt   Foreground service + notification + wake lock
├── viewmodel/                 Shared state for all screens
└── ui/screens/                Setup / Stream Studio / Settings
```

## Build

Requirements: JDK 17, Android SDK 35 + build-tools 35.0.0, Gradle 8.10.2
(the project was developed against exactly these versions).

```bash
# clone, then from the project root:
./gradlew assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk
```

Unit tests: `./gradlew testDebugUnitTest`
Instrumented tests (device/emulator needed): `./gradlew connectedDebugAndroidTest`

## Configure Twitch & go live

1. Twitch → **Creator Dashboard → Settings → Stream** → copy your
   **Primary Stream key** (never share it, never paste it into git/logs).
2. In the app: paste the key on the Setup screen → **Save key**
   (stored encrypted; visible only while typing).
3. **Choose Banner** → pick a PNG/JPEG/WebP image.
4. Pick Horizontal/Vertical and Fit/Fill → **Continue to Stream Studio**.
5. Grant microphone permission → **Start Stream**.
6. Verify on your Twitch channel page / Creator Dashboard stream manager.

> Recommendation: first test on an alternate/test Twitch account or with
> a test stream key.

## Privacy

- No `CAMERA` permission, no CameraX/Camera2 usage.
- No `MediaProjection`, no screen capture of any kind.
- No `READ/WRITE/MANAGE_EXTERNAL_STORAGE` — images are read through SAF URIs.
- No local recording: encoded frames go only to the RTMP socket; nothing is
  written to disk, cache included.
- The stream key is encrypted with a Keystore-backed AES-256/GCM key and is
  never logged, shown in errors, or sent anywhere except the RTMP handshake
  with the configured ingest server.
- No analytics, no ads, no trackers.
- Instrumented test `PrivacyManifestTest` enforces these invariants in CI.

## Known limitations

- **Vertical streams**: Twitch's player is landscape-oriented. A 720×1280
  vertical stream is delivered correctly but appears pillarboxed in Twitch's
  standard web player (it works well in mobile/Twitch-clips style surfaces).
- **Internal/playback audio** (capturing other apps' sound) is not included in
  this release — Android restricts it heavily; the mic path is the reliable
  baseline.
- RTMP is unencrypted TCP (`rtmps://` endpoints work if your ingest supports
  TLS; Twitch's public ingest is `rtmp://`).
- Reconnect resumes the same RTMP session; Twitch may take a few seconds to
  show the stream as healthy again after a network flap.
