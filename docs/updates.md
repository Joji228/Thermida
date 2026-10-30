# Updates

Thermida checks GitHub for a newer version every time it launches. If one exists, a popup shows the
version and its **What's new** notes (the release notes), with **Download** and **Not now**.
**Settings → Updates → Check for updates** shows the same popup when a newer version exists.
Tapping **Download** fetches the APK, checks it against the SHA-256 in the release's `update.json`,
and opens Android's install prompt. You confirm once and the update replaces the app in place:
meals, weigh-ins, settings and API keys stay.

## What a release needs

Each GitHub release that is **not** a draft or pre-release, and is the latest one, needs two assets:

- `Thermida-<version>.apk`, the release-signed APK.
- `update.json`, which the app reads first (the release notes come from the release's description):

  ```json
  {"versionName": "0.0.1", "versionCode": 30, "apk": "Thermida-0.0.1.apk", "sha256": "<hex digest of the APK>"}
  ```

The app offers a release only when its `versionCode` is higher than the installed one. The
version name is what people read; the version code is what Android checks. The tag is `v<version>`.

Downloads must come from `github.com` or GitHub's asset hosts over HTTPS. Anything else is ignored.
The repo must be **public**: the app reads releases without a token, and no token is shipped. While it was private (October 2026) the check found nothing and updates were installed by hand.

## Publishing a release

1. Raise `versionCode` in `app/build.gradle.kts` (it must go up every release, or Android refuses
   the update) and set `versionName` to match the tag.
2. Build the release APK with the keystore kept on your PC (see `keystore.properties`):

   ```bash
   ./gradlew assembleRelease
   ```

3. Copy the APK to `Thermida-<version>.apk` and write `update.json` with its SHA-256 (PowerShell):

   ```powershell
   Copy-Item app/build/outputs/apk/release/app-release.apk Thermida-0.0.1.apk
   $h = (Get-FileHash Thermida-0.0.1.apk -Algorithm SHA256).Hash.ToLower()
   '{"versionName": "0.0.1", "versionCode": 30, "apk": "Thermida-0.0.1.apk", "sha256": "' + $h + '"}' | Set-Content -Encoding ascii update.json
   ```

4. Publish with the `gh` CLI:

   ```bash
   gh release create v0.0.1 Thermida-0.0.1.apk update.json --title "Thermida 0.0.1" --notes "What changed"
   ```

## Why the signing key matters

Android only installs an update that is signed with the same key as the installed app, and it
keeps the app's data only in that case. Losing the keystore, or signing one release with a
different key, means every phone must uninstall and start over. Back up `thermida-release.keystore`
and `keystore.properties` somewhere outside this repository.

The SHA-256 catches a corrupted or swapped download. The signature check is the real guard: the app
also compares the downloaded APK's signing key with its own before it opens the installer.

## Limits

- Android does not let an app install silently. There is always one system confirmation.
- The first install needs **Allow from this source** for Thermida. The app opens that page when
  Android asks.
- Google Play forbids apps that install other APKs, so a Play Store build must not include this
  updater. It belongs only in the sideloaded GitHub build.
