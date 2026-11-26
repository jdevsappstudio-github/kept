# Kept release profile (read by the global `release-to-play` skill)

Package `com.jdevsappstudio.kept` (debug uses the same applicationId, `-debug` versionName suffix, so debug and
release can't sit side by side: different signatures). Firebase project `kept-jdevs`.

## Version
Edit only `appVersionName` in `app/build.gradle.kts`; `versionCode = MAJOR*10000 + MINOR*100 + PATCH`. Tag `v<version>`.

## Signing
Upload key `kept-upload` in the owner's keystore folder, `keystore.properties` (gitignored) at the repo root.
Play App Signing on. After the first upload: add Play's **app signing** SHA-256 (and the upload SHA-256) to the
Firebase Android app, or Play Integrity / App Check fails in the store build.

## MANDATORY before any Play upload: release APK launch check
R8 can strip Firebase registrars (Keel 1.0.4 crashed on launch from Play). Back up the debug app's data first
(`adb exec-out run-as <pkg> tar -cf - databases files shared_prefs > x.tar`), uninstall, `adb install` the release APK,
launch, grep logcat for `FATAL EXCEPTION|NoSuchMethod|ClassNotFound|Could not instantiate`. Then restore the debug build + data.

## No billing, no ads in the MVP (H45/H66). App Check enforced (H48): release = Play Integrity.

## QA checklist (store build)
Onboarding → share a link → card read in the cloud (App Check passes) → search → Ask one question → Settings → About.
