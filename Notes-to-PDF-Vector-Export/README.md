# Notes to PDF: Vector Export

Android app that converts shared Samsung Notes (`.sdocx`) files into PDFs on the device. Independent project; not affiliated with or endorsed by Samsung.

## Use
1. In Samsung Notes, open a note and choose Share.
2. Choose the Samsung Notes file format (`.sdocx`), then this app.
3. Choose free image PDF export or watch an available rewarded ad to unlock vector export for that selected note.
4. Save the PDF using Android's document picker.

Free image export works offline, without a daily quota. Vector export unlocks only after the SDK reports the reward; closing an ad early does not unlock it. Ad availability requires connectivity and is not guaranteed. Images remain raster images inside vector PDFs. Some Samsung Notes elements may render differently.

There is no Pro tier, purchase or subscription.

## Build
Use JDK 17 or 21, Android SDK platform 36, build tools 36.0.0 and the included Gradle 8.13 wrapper. Configure `sdk.dir` in your local `local.properties`.

```sh
chmod +x gradlew
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

Gradle generates a local debug key. It will differ from the key used for previously supplied preview APKs, so installing over those may require uninstalling the old preview (which clears its app data).

Debug uses Google's official demo AdMob IDs and bypasses publisher UMP forms. It is not a consent-flow test. Release uses publisher AdMob settings and UMP consent gating. Copy `publisher.properties.example` to `publisher.properties`, configure your own IDs, verify the live privacy/source URLs, and leave `keystorePath` empty to generate an unsigned bundle for later signing with `jarsigner`. For local Gradle signing, set `keystorePath`, `keyAlias`, `NOTES_STORE_PASSWORD` and `NOTES_KEY_PASSWORD`. Keep the key and passwords private.

```sh
./gradlew :app:bundleRelease
```

For closed testing, configure AdMob test mode for the exact app version before using a release build. No production bundle is included here.

## Source and licensing
The converter is based on [James Ding's sdocx](https://github.com/twangodev/sdocx). Converter sources are in `engine/`, and Android-side integration/rendering code is in `app/`. See [LICENSING.md](LICENSING.md), [MODIFICATIONS.md](MODIFICATIONS.md) and bundled third-party notices. Google SDKs retain their own licenses.

## Contact and version

Version 1.7.4 (code 12). For support or privacy requests, email [alogergely@gmail.com](mailto:alogergely@gmail.com). Balog Gergely Sándor deletes support emails and their attachments one month after the support case is closed. The app links to the [published privacy policy](https://cubicrush.github.io/Notes-to-PDF-Vector-Export/Notes-to-PDF-Vector-Export/docs/privacy.html).
