# Contributing

Open an issue describing the source format, device/Android version, output protocol, latency mode and reproduction steps. Remove tokens, private addresses and personal data from logs.

Run `./gradlew :app:testDebugUnitTest :app:lintDebug` (or `gradlew.bat` on Windows) with JDK 17 and Android SDK 35. Native builds additionally need the separately obtained NDI SDK, NDK 27.0.12077973 and CMake 3.22.1.

Keep changes focused and include a regression test for behavior changes. Describe physical-device checks separately from JVM tests. Do not claim support for an NDI format or receiver that has not been tested.

Never commit proprietary SDK headers/libraries, APKs, build caches, signing keys, credentials or device logs. Contributions are provided under the project's MIT license for original code; do not submit code you do not have the right to contribute.
