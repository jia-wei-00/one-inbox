# One Inbox: Platform Logos and Release Pipeline

Date: 2026-11-22 · Builds on: `2026-11-22-one-inbox-redesign-design.md`

## Intent

- **Logos:** the owner wants every chat platform to show its real logo, even when the app isn't installed on the phone. Today, uninstalled apps fall back to letter tiles.
- **Releases:** the owner wants a GitHub pipeline that builds and publishes the app with a release version and a git tag.
- **Repo:** the repository will be **public**.

## Decided by the owner

- **The repo is public:** `jia-wei-00/one-inbox`.
- **The Claude API key stays in Settings only.** It is not built into any APK, is not in GitHub secrets, and has no `local.properties` default. Today's encrypted Settings entry is unchanged.
- **Bundled logos and a SpendWise-style release pipeline:** the owner accepted both proposals.

## Goals

1. Bundle official brand logos as vector drawables for the 8 default platforms:
   - `com.whatsapp`, `com.whatsapp.w4b`
   - `com.facebook.orca` (Messenger)
   - `com.instagram.android`
   - `org.telegram.messenger`
   - `com.discord`
   - `com.linkedin.android`
   - `com.twitter.android` (X)
2. Use these logos wherever an app icon appears: the rail tiles, avatar badges, Settings rows, the catch-up items and the conversation header dot.
3. Add a GitHub Actions pipeline:
   - **Every push to `main`** builds a **signed release APK**, bumps the version, creates the tag `vX.Y.Z` and publishes a **GitHub Release** with the APK attached.
   - **Pull requests** run the unit tests and `assembleDebug` only.
4. One-time setup, done by Claude:
   - create the public repo;
   - generate the release keystore;
   - set the signing secrets;
   - push.

## Non-goals

- No Play Store, AAB or Firebase.
- No change to how the API key is stored or entered.
- No logo for apps outside the 8 defaults. Those keep the phone's icon, or the letter tile.

## Anti-goals

- **Any secret in the public repo, the APK or the logs.** This includes the keystore, passwords and any API key.
- **A release that can't be installed over the previous one.** The signing key and the increasing versionCode must stay stable.

## Approaches considered

| Part | Options | Chosen |
|---|---|---|
| Logos | (a) bundle official logos; (b) phone icons only | **(a)**, because (b) leaves letter tiles for uninstalled apps |
| API key | (a) build-time key from a GitHub secret; (b) local-only default; (c) Settings only | **(c)**: the owner's choice. (a) would leak the key through public release APKs. |
| Pipeline trigger | (a) auto-bump on every push to `main` (SpendWise pattern); (b) build only when a tag is pushed | **(a)**, the same pattern the owner already uses, with manual `vN.0.0` tags for major bumps |

## Design

### Logos (Claude's calls)

- **Source:** [Simple Icons](https://simpleicons.org) (CC0 SVG paths), checked to include whatsapp, messenger, instagram, telegram, discord, linkedin and x.
  - WhatsApp Business reuses the WhatsApp glyph.
  - Each logo is a white glyph on the brand's background color, drawn as a rounded-square tile: WhatsApp `#25D366`, Messenger `#0084FF`, Instagram gradient `#F58529→#DD2A7B→#8134AF`, Telegram `#26A5E4`, Discord `#5865F2`, LinkedIn `#0A66C2`, X `#000000` with a light outline in dark mode so it stays visible on `#141218`.
- **Where:** `res/drawable/logo_<name>.xml` vector drawables, plus a `BrandLogos` map from package to drawable.
- **Lookup order** in `AppInfoProvider.icon(pkg)`:
  1. the bundled logo, if the package is one of the 8;
  2. the installed app's icon;
  3. null (letter tile).

  **(Claude's call: bundled first, so a platform looks the same whether or not it's installed, and the badge style matches the design.)**
- Rasterize at the existing 48dp size into the existing LruCache.
- Add `res/raw/` attribution or a `NOTICE` entry. CC0 needs no attribution, but trademark use is noted. The logos are used only to identify the source app.

### Release pipeline (Claude's calls, mirroring `expense-tracker-kotlin/.github/workflows/release-apk.yml`)

- **`.github/workflows/release.yml`:**
  - **Trigger:** push to `main` and `workflow_dispatch`. `concurrency: release`, never cancel.
  - **Next version:** take the newest `vX.Y.Z` tag and bump the minor. The first release is `v1.0.0`. A re-run of a tagged commit reuses its tag.
  - **Version numbers:** `versionCode = github.run_number + 100` (offset so it is above any local build), and `versionName = X.Y.Z`. These come from the `VERSION_CODE` and `VERSION_NAME` env vars, which the Gradle file reads. Local builds stay `1` / `"1.0"`.
  - **Signing:** decode the keystore from the `RELEASE_KEYSTORE_BASE64` secret into `$RUNNER_TEMP`, and fail if it is missing. `app/build.gradle.kts` gains a release `signingConfig` read from env or `local.properties`: `RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`. The keystore file is removed afterwards.
  - **Build:** `./gradlew :app:testDebugUnitTest :app:assembleRelease`. Tests gate the release.
  - **Publish:** `gh release create vX.Y.Z one-inbox-vX.Y.Z.apk --generate-notes --target $GITHUB_SHA`. A re-run uploads with `--clobber`.
  - **Permissions:** `contents: write`.
- **`.github/workflows/ci.yml`:** on `pull_request`, run unit tests and `assembleDebug`.
- **Release build type:** `isMinifyEnabled = false`. This matches SpendWise and avoids R8 surprises with Room, Hilt and the Anthropic SDK. **(Claude's call.)**

### One-time setup (Claude performs it; the owner already said to make the repo public)

1. Generate the keystore with `keytool`: RSA 4096, 10000 days, alias `one-inbox`, random 24-character passwords.
   - Store it at `C:\Users\wwwbi\.android\one-inbox-release.jks`.
   - Store the passwords in `C:\Users\wwwbi\.android\one-inbox-release.properties`.
   - Both are outside the repo. The owner must back them up: losing them means future releases can't install over existing ones.
2. Set the four repo secrets with `gh secret set`, sending values over stdin and never echoing them.
3. Before the first push:
   - Merge `feature/redesign` into `main` so the first release includes the redesign.
   - `gh repo create jia-wei-00/one-inbox --public --source . --push`.

   The first push to `main` triggers `v1.0.0`.
4. **Pre-push check:** run `git ls-files`, plus a grep for `sk-ant`, `.jks`, `local.properties` and `password`, to prove no secret is tracked.

## Left to the builder

- The exact SVG path conversion and tile padding.
- Exact workflow step names.
- How to verify locally: `assembleRelease` with the local keystore via `local.properties`, then `apksigner verify`.
