# Platform Logos and Release Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bundle official logos for the 8 default chat platforms, and publish signed, versioned GitHub Releases from a public repo on every push to `main`.

**Architecture:**
- **Logos:** brand logos ship as vector drawables. `PackageAppInfoProvider.icon(pkg)` checks a `BrandLogos` map before falling back to the installed app's icon.
- **Build file:** `app/build.gradle.kts` gains an env/`local.properties`-driven release signing config and version.
- **Pipeline:** two GitHub Actions workflows, one for releases and one for PR checks, copied from the owner's SpendWise `release-apk.yml`.
- **Setup:** the keystore, secrets, repo creation and first push are done by the controller in Task 3, not a subagent, because they touch secrets and external services.

**Tech Stack:** Kotlin/Compose (existing), Android vector drawables, Simple Icons SVG paths (CC0, simple-icons@16.38.0), GitHub Actions (`actions/checkout@v4`, `actions/setup-java@v4` temurin 17, `gradle/actions/setup-gradle@v4`), the `gh` CLI and `keytool`.

**Spec:** `docs/superpowers/specs/2026-11-22-logos-and-release-pipeline-design.md`

## Global Constraints

- **No secret in the repo, the APK or the logs.** No keystore, passwords or API key may be committed or echoed. The Claude API key stays in Settings only; it is not added to BuildConfig, CI or `local.properties`.
- **Public repo:** `jia-wei-00/one-inbox`. The default branch is `main`.
- **Version:**
  - Tags are `vX.Y.Z`, and the first release is `v1.0.0`.
  - Each push to `main` bumps the minor version of the newest tag. A re-run of a tagged commit reuses its tag.
  - `versionCode = github.run_number + 100` in CI. Locally it stays `1` / `"1.0"`.
- **Signing env names:** `RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`, read from env or `local.properties`. The CI secret is `RELEASE_KEYSTORE_BASE64`.
- **Release APK name:** `one-inbox-vX.Y.Z.apk`. `isMinifyEnabled = false`.
- **Logo order for the 8 packages:** bundled logo, then installed icon, then null. Other packages: installed icon, then null.
- **Commits:** trailer `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`.

## Review Focus

1. **X logo on a dark background.** X's brand color is black on `#141218`, so the tile needs a visible outline in dark mode. → Task 1 screenshot `brandLogos_gallery` (dark) has a visible X tile.
2. **Logo cache.** The bundled logo must go through the same LruCache with no per-call decoding. → Task 1 test `bundledLogoIsCachedAndBeatsInstalledIcon` (loader never called for brand packages).
3. **First run with no tags** must produce `v1.0.0`, not `v0.1.0` or an error. → Task 2 step: run the version script locally with no tags and with `v1.0.0`/`v1.3.0` tags.
4. **Missing keystore secret** must fail the release instead of publishing an unsigned APK. → Task 2 workflow guard, checked by reading the YAML; the controller proves it end to end in Task 3.
5. **Local builds without signing values** must still build (`assembleDebug`, and an unsigned `assembleRelease`). → Task 2 step: `./gradlew :app:assembleRelease` with no keystore → BUILD SUCCESSFUL, unsigned.

---

### Task 1: Bundled platform logos

**Files:**
- Create:
  - `app/src/main/res/drawable/logo_whatsapp.xml`, `logo_messenger.xml`, `logo_instagram.xml`, `logo_telegram.xml`, `logo_discord.xml`, `logo_linkedin.xml`, `logo_x.xml`
  - `app/src/main/java/one/inbox/core/BrandLogos.kt`
  - `NOTICE.md`
- Modify: `app/src/main/java/one/inbox/core/AppInfoProvider.kt`
- Test: `app/src/test/java/one/inbox/core/BrandLogosTest.kt`, `app/src/test/java/one/inbox/screenshots/BrandLogosScreenshotTest.kt`; update `AppInfoProviderTest`

**Interfaces — Produces:**
```kotlin
object BrandLogos {
    @DrawableRes fun forPackage(pkg: String): Int?   // the 8 defaults → drawable; else null
}
```
- `com.whatsapp` and `com.whatsapp.w4b` both map to `logo_whatsapp`. The others map to their own drawable: `com.facebook.orca`, `com.instagram.android`, `org.telegram.messenger`, `com.discord`, `com.linkedin.android`, `com.twitter.android`.
- `PackageAppInfoProvider` gains a constructor seam `loadBrandLogo: (Int) -> Drawable?`. The default uses `AppCompatResources`/`ContextCompat.getDrawable(context, id)`.
- `icon(pkg)` does: cache hit → `BrandLogos.forPackage(pkg)?.let(loadBrandLogo)` → `loadIcon(pkg)`. The result is rasterized at the existing 48dp and cached.

**Drawables:**
- Each is a 48×48 viewport vector with a rounded-square background (corner radius 11) in the brand color, plus the Simple Icons glyph path (24-unit box) scaled to about 60% and centered via `<group>`.
- **Colors:** WhatsApp `#25D366`, Messenger `#0084FF`, Instagram a linear gradient `#F58529→#DD2A7B→#8134AF` (`aapt:attr` gradient), Telegram `#26A5E4`, Discord `#5865F2`, LinkedIn `#0A66C2`. The glyph is white.
- **X:** background `#000000` with a 1.5-unit `#3A3742` stroke so it reads on `#141218`.
- **Paths:** fetch them from `https://cdn.jsdelivr.net/npm/simple-icons@16.38.0/icons/<slug>.svg` (slugs: whatsapp, messenger, instagram, telegram, discord, linkedin, x). Save downloads into a scratch directory outside the repo, then copy only the `d` path into the XML.
- **`NOTICE.md`:** "Platform logos from Simple Icons (CC0). Trademarks belong to their owners; used only to identify the source app."

- [ ] **Step 1: Write the failing tests**
  - `BrandLogosTest.mapsAllDefaultsAndNothingElse`:
    - every key in `ChatApps.DEFAULT` → not null;
    - `com.whatsapp.w4b` gives the same drawable as `com.whatsapp`;
    - `com.example.other` → null.
  - `AppInfoProviderTest.bundledLogoIsCachedAndBeatsInstalledIcon`:
    - for `com.whatsapp`, with both loaders counting calls: the brand loader is called once across two `icon()` calls, and `loadIcon` is called 0 times;
    - for `com.example.other`: `loadIcon` is called once and the brand loader 0 times.
  - `BrandLogosScreenshotTest.brandLogos_gallery`: renders the 7 drawables as `AppTile`s at 48dp plus `Avatar` badges, at `w360dp-h780dp-night-xxhdpi`.
- [ ] **Step 2: Run them.** `./gradlew :app:testDebugUnitTest --tests "one.inbox.core.*" --tests "one.inbox.screenshots.BrandLogosScreenshotTest"` → FAIL.
- [ ] **Step 3: Implement** the drawables, `BrandLogos`, the provider change and `NOTICE.md`.
- [ ] **Step 4: Run the full suite.** `./gradlew :app:testDebugUnitTest -Proborazzi.test.record=true :app:assembleDebug` → green. Read `brandLogos_gallery.png`: each logo must be recognizable and centered, and the X tile visible. Also re-read `inbox_railOpen.png` and `catchup_full.png`; their fake icons may change if the tests used real packages.
- [ ] **Step 5: Device check.** `adb -s bf5381fc install -r app/build/outputs/apk/debug/app-debug.apk`, launch, screencap and Read it. Never clear data.
- [ ] **Step 6: Commit** — `feat(ui): bundled logos for the default chat platforms`

---

### Task 2: Release signing, version wiring and workflows

**Files:**
- Modify: `app/build.gradle.kts`, `.gitignore` (add `*.jks`, `*.keystore`, `release.properties`)
- Create: `.github/workflows/release.yml`, `.github/workflows/ci.yml`, `scripts/next-version.sh`
- Modify: `README.md` (a "Releases" section)

**Decisions:**
- **`app/build.gradle.kts`:**
  - Add a `secret(name)` helper that reads `local.properties` first, then `System.getenv` (the same as SpendWise).
  - `versionCode = secret("VERSION_CODE").toIntOrNull() ?: 1` and `versionName = secret("VERSION_NAME").ifEmpty { "1.0" }`.
  - `signingConfigs.create("release")` only when `RELEASE_KEYSTORE_PATH` points to an existing file. `release { signingConfig = … }` only then. Otherwise `assembleRelease` builds an unsigned APK.
- **`scripts/next-version.sh`:**
  - Prints the next `X.Y.Z`.
  - If HEAD already has a `vX.Y.Z` tag, print that version.
  - Otherwise take the newest `v*.*.*` tag (`--sort=-v:refname`) and print `major.(minor+1).0`.
  - With no tags, print `1.0.0`.
- **`release.yml`:**
  - Triggers: `push: branches: [main]` and `workflow_dispatch`. `permissions: contents: write`. `concurrency: {group: release, cancel-in-progress: false}`.
  - Checkout uses `fetch-depth: 0` and `fetch-tags: true`.
  - `VERSION_NAME` comes from the script, and `VERSION_CODE` is `$(( ${{ github.run_number }} + 100 ))`.
  - Steps: setup-java 17, setup-gradle.
  - Decode `RELEASE_KEYSTORE_BASE64` to `$RUNNER_TEMP/release.jks`, failing with `::error::` if empty.
  - Run `./gradlew :app:testDebugUnitTest :app:assembleRelease --no-daemon` with the 3 password and alias secrets plus `RELEASE_KEYSTORE_PATH`.
  - Verify the signature with `$ANDROID_HOME/build-tools/<latest>/apksigner verify` (pick the newest installed build-tools dir).
  - Copy the APK to `one-inbox-v$VERSION_NAME.apk`.
  - If the release exists: `gh release upload --clobber`. Otherwise `gh release create "v$VERSION_NAME" … --target "$GITHUB_SHA" --title "v$VERSION_NAME" --generate-notes`. `gh release create` creates the tag.
  - Always remove the keystore.
- **`ci.yml`:** on `pull_request`, run `./gradlew :app:testDebugUnitTest :app:assembleDebug --no-daemon`.
- **README "Releases" section:**
  - How versions bump, and how to force a major bump: push a `vN.0.0` tag first.
  - Where the APK is.
  - The 4 secret names.
  - A note that the signing key lives outside the repo and must be backed up.

- [ ] **Step 1: Check the version script.** In a scratch git repo outside the project:
  - no tags → `1.0.0`;
  - tags `v1.0.0`, `v1.3.0` on older commits → `1.4.0`;
  - HEAD tagged `v1.4.0` → `1.4.0`.
- [ ] **Step 2: Implement** the Gradle changes, script, workflows, `.gitignore` and README.
- [ ] **Step 3: Unsigned build.** `./gradlew :app:assembleRelease` with no signing values → BUILD SUCCESSFUL, producing `app-release-unsigned.apk`.
- [ ] **Step 4: Signed build.**
  1. Generate a throwaway test keystore in the scratchpad (not the real one).
  2. Run `RELEASE_KEYSTORE_PATH=… RELEASE_KEYSTORE_PASSWORD=… RELEASE_KEY_ALIAS=… RELEASE_KEY_PASSWORD=… VERSION_NAME=9.9.9 VERSION_CODE=999 ./gradlew :app:assembleRelease` → `app-release.apk`.
  3. `apksigner verify --print-certs` passes.
  4. `aapt2 dump badging` shows `versionCode='999' versionName='9.9.9'`.
  5. Delete the throwaway keystore.
- [ ] **Step 5: Lint the workflows.** If `actionlint` is available, run it; otherwise do a careful read-through, checking YAML validity with `python -c "import yaml…"`.
- [ ] **Step 6: Run the full suite.** `./gradlew :app:testDebugUnitTest :app:assembleDebug` → green.
- [ ] **Step 7: Commit** — `ci: signed versioned releases on push to main`

---

### Task 3: One-time setup and first release (controller only — secrets and external services)

- [ ] **Step 1: Generate the keystore.**
  - `keytool -genkeypair -v -keystore C:/Users/wwwbi/.android/one-inbox-release.jks -alias one-inbox -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=One Inbox, O=Personal"`.
  - The passwords are random 24-character values generated in-process and written to `C:/Users/wwwbi/.android/one-inbox-release.properties` (`storePassword`, `keyPassword`, `keyAlias`). They are never printed. Refuse to overwrite if the files already exist.
- [ ] **Step 2: Merge to `main`.** `git checkout main && git merge --ff-only feature/redesign`. Run the full suite on `main` → green.
- [ ] **Step 3: Secret scan before publishing.**
  - `git ls-files` shows no `.jks` or `local.properties`.
  - `git grep -nE "sk-ant-|storePassword|BEGIN (RSA )?PRIVATE"` over all history (`git log -p --all | grep`) → no hits.
  - `.superpowers/` is excluded.
- [ ] **Step 4: Create the repo.** `gh repo create jia-wei-00/one-inbox --public --source . --remote origin --description "Personal Android app: one inbox for all chat notifications, with Claude catch-up"`. Do **not** push yet.
- [ ] **Step 5: Set the secrets** (stdin only):
  - `base64 -w0 keystore | gh secret set RELEASE_KEYSTORE_BASE64`
  - `gh secret set RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD`, each fed from the properties file.
  - `gh secret list` shows all 4.
- [ ] **Step 6: Push.** `git push -u origin main`, then watch the run with `gh run watch`. Expect: release `v1.0.0` exists with `one-inbox-v1.0.0.apk`, and `gh release view v1.0.0` lists the asset.
- [ ] **Step 7: Install check.** Download the APK with `gh release download v1.0.0`.
  - Run `apksigner verify --print-certs` and compare the certificate's SHA-256 to the local keystore's.
  - The release-signed APK can't install over the debug build on the Poco (different signature), so do **not** uninstall the debug build. Leave installing the release to the owner and tell them that.
- [ ] **Step 8: Delete the merged `feature/redesign` branch** locally (`git branch -d`).
