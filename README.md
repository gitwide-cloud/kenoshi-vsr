# Kenoshi VSR — Video Stream Relay

**Your feed. Any screen.**

Kenoshi VSR connects your video feeds to the screens you want to watch them on. Receive NDI sources, monitor them directly, relay them to Google Cast devices, or share them as HTTP Live Streaming (HLS) and continuous HTTP MPEG-TS streams.

Choose a source, pick a destination, and let it flow. A simple tabbed interface keeps setup separate from your sessions, with source selection, output audio controls, and Normal, White and Dark themes close at hand.

**Compatible with NDI, Google Cast, and VLC.**

> Early Android development build. The application code is MIT licensed. The optional NDI SDK is proprietary, separately licensed, and not included in this repository. Compatibility is not certification or endorsement.

## Features

- One permanent Setup tab and up to two session tabs.
- Automatic NDI / NDI HX reception through the official SDK. The sender determines its codec; the app does not convert a source to HX by selecting a menu option.
- Full-quality or low-bandwidth proxy reception. Proxy quality depends on what the sender provides; it is not an HX selector.
- Native video monitoring, saved sessions and local HTTP/RTSP stream playback.
- On-phone hardware H.264 encoding and stereo AAC audio for forwarding, up to 720p and at most 30fps.
- Google Cast selection using the official framework and default media receiver.
- Token-scoped local HLS and continuous HTTP-TS URLs for media players, including VLC.
- Output mute, retry/stop, a foreground forwarding service, and explicit connection/error states.

## Latency modes

| Mode | HLS policy | HTTP player link | Tradeoff |
| --- | --- | --- | --- |
| Stable (default) | About 2-second keyframes; three complete segments before Cast starts | HLS | More buffering margin |
| Low latency (experimental) | About 0.5-second keyframes; two complete segments before Cast starts | Continuous MPEG-TS | Less buffering margin; may rebuffer on slow networks |

Continuous HTTP-TS sends transport packets as they are encoded, beginning at the next independent frame. It avoids waiting for a complete HLS segment. A player can still add its own cache/probing delay. In VLC, try reducing network caching to 150–300ms, then increase it if playback breaks up. These are test settings, not a guaranteed latency figure.

Shorter ordinary HLS segments are **not LL-HLS**. This implementation does not implement the Apple Low-Latency HLS protocol, and Google Cast may impose its own buffering. No sub-second Cast latency is promised.

## Use

1. Connect sender, phone and receiver/player to a reachable LAN.
2. Choose **Receive → Feed → NDI / NDI HX**.
3. Select group and receive bandwidth, scan, then choose a source.
4. Select **View in tab**, **Chromecast**, or **HTTP stream (HLS / TS)**.
5. For forwarding, select Stable or Low latency and open the session.
6. For Google Cast, choose the receiver in the session controls. For VLC or another player, copy the stream URL and use Open Network Stream.

Latency mode can also be changed inside a forwarding session, with a restart confirmation. The URL changes when output restarts. Casting always uses HLS; a Low latency HTTP-output session copies the continuous `.ts` URL. Both endpoints are served by the same bridge.

Only one forwarding encoder runs at a time. The two-tab limit does not imply two concurrent encoders. Native monitoring and sending/capture are separate features; screen/app/camera broadcasting is not implemented yet.

## Build without the NDI SDK

Requires JDK 17, Android SDK platform 35, and an SDK path through `ANDROID_HOME` or `local.properties`.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

On macOS/Linux use `bash ./gradlew`. The default build supports UI and compatible stream-URL playback; NDI discovery/reception/forwarding is explicitly unavailable. The Gradle wrapper downloads Gradle 8.9.

## Build with NDI reception

1. Obtain the **Android SDK** through [NDI's official download process](https://ndi.video/for-developers/ndi-sdk/download/) and review its terms. A Windows NDI Tools installation is not a substitute for the Android runtime.
2. Install Android NDK `27.0.12077973` and CMake `3.22.1` using Android Studio or sdkmanager.
3. Import your locally installed/extracted Android SDK:

```powershell
.\scripts\import-ndi-sdk.ps1 -SdkRoot 'C:\path\to\NDI Android SDK'
.\gradlew.bat -PwithNdi=true :app:assembleDebug
```

The importer expects `Include/` and `Lib/arm64-v8a/libndi.so`, copies accompanying runtime notices, and makes no network requests. Actual SDK layouts can differ. ARM64 is the initial native target.

Manual equivalent: headers under `ndi-sdk/include/`; runtime under `app/src/main/jniLibs/arm64-v8a/libndi.so`; supplied runtime notices/license under `app/src/main/assets/licenses/`. Those locations are Git-ignored. Adding them does not grant rights to redistribute the SDK or an APK containing it; its applicable terms remain separate.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. The legacy application ID is retained so development installs update existing data; branding is Kenoshi VSR.

## Verification and limits

- Real high-bandwidth NDI reception and forwarding tested on a Pixel 7.
- NDI → phone → local H.264/AAC stream decoded on a PC, including the continuous HTTP-TS endpoint.
- Low-latency HLS produced approximately 0.5-second segments on that phone.
- SHIELD accepted Google Cast playback; the user confirmed it worked. Long-run reliability, lip-sync and reduced glass-to-glass latency have not been benchmarked.
- Official NDI documentation describes HX2/HX3 reception. No real HX sender was available for this project's test, so Android HX interoperability remains to be verified. Selecting a proxy does not prove HX decoding.
- Local NDI monitoring remains video-only. Forwarded streams include stereo audio; multichannel surround and local-monitor audio are unfinished.
- Network/IP changes or source resolution changes can require restarting output. Runtime encoding support and performance vary by device. No production/endurance guarantee.
- The foreground service implements background forwarding, but screen-off/background endurance is not validated.
- Current lint can emit Kotlin metadata compatibility diagnostics while inspecting Google Cast's dependency; the application compiles and app lint checks run. See CI for clean-checkout results.

The HTTP origin is unauthenticated beyond its random URL path. Anyone who has that URL and network access can watch while the session is running. Do not publish the port directly to the internet. No analytics, cloud streaming backend, or SDK download automation is included.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Run UI/muxer/server tests without the SDK; use a physical device and a permitted NDI source for integration tests. Keep proprietary SDK files, generated APKs, device logs, credentials and private network details out of contributions.

## License and compatibility

Our source code is available under the [MIT License](LICENSE). Third-party runtimes and dependencies are not relicensed by it. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

NDI is a registered trademark of Vizrt NDI AB. Google Cast and VLC belong to their respective owners. This independent project is not affiliated with or endorsed by those organizations.
