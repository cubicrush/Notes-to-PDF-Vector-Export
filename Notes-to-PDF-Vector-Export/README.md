# Notes to PDF: Vector Export

Android app that converts shared Samsung Notes (`.sdocx`) files into PDFs on the device. Independent project; not affiliated with or endorsed by Samsung.

## Use
1. In Samsung Notes, open a note and choose Share.
2. Choose the Samsung Notes file format (`.sdocx`), then this app.
3. Choose free image PDF export or watch an available rewarded ad to unlock vector export for that selected note.
4. Save the PDF using Android's document picker.

Free image export works offline, without a daily quota. Vector export unlocks only after the SDK reports the reward; closing an ad early does not unlock it. Ad availability requires connectivity and is not guaranteed. Images remain raster images inside vector PDFs. This preview does not promise perfect fidelity for every Samsung Notes feature.

There is no Pro tier, purchase or subscription.

## Build
Use JDK 17 or 21, Android SDK platform 36, build tools 36.0.0 and the included Gradle 8.13 wrapper. Configure `sdk.dir` in your local `local.properties`.

```sh
chmod +x gradlew
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Gradle generates a local debug key. It will differ from the key used for previously supplied preview APKs, so installing over those may require uninstalling the old preview (which clears its app data).

Debug uses Google's official demo AdMob IDs and bypasses publisher UMP forms. It is not a consent-flow test. Release uses publisher AdMob settings and UMP consent gating. Copy `publisher.properties.example` to `publisher.properties`, configure your own IDs, verify the live privacy/source URLs, and supply a private upload keystore. Set `NOTES_STORE_PASSWORD` and `NOTES_KEY_PASSWORD` in the environment. Never commit keys or passwords.

```sh
./gradlew :app:bundleRelease
```

For closed testing, configure AdMob test mode for the exact app version before using a release build. No production bundle is included here.

## Source and licensing
The converter is based on [James Ding's sdocx](https://github.com/twangodev/sdocx). Converter sources are in `engine/`, and Android-side integration/rendering code is in `app/`. See [LICENSING.md](LICENSING.md), [MODIFICATIONS.md](MODIFICATIONS.md) and bundled third-party notices. Google SDKs retain their own licenses.

## Status
Publication preparation based on 1.7.2-preview (version code 10); this source snapshot includes changes made after the previously distributed preview APK. It is not a claim of byte-for-byte correspondence to that APK. No new APK/AAB accompanies this snapshot. Real-device conversion, rewarded-ad and consent testing remain necessary. The included privacy page is a draft pending verification of publisher settings.

Publisher: Balog Gergely Sándor — alogergely@gmail.com
