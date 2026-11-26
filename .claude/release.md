# Kept release profile (read by the global `release-to-play` skill)

Package `com.jdevsappstudio.kept` (debug uses the same applicationId, `-debug` versionName suffix, so debug and
release can't sit side by side: different signatures). Firebase project `kept-jdevs`.

## Version
Edit only `appVersionName` in `app/build.gradle.kts`; `versionCode = MAJOR*10000 + MINOR*100 + PATCH`. Tag `v<version>`.

## Signing
Upload key `kept-upload` in the owner's keystore folder, `keystore.properties` (gitignored) at the repo root.
Play App Signing on, **quantum-ready (hybrid) key**: Play signs SDK 37+ with the new classical+PQC key (85:AF:0E:CB…79:FB)
and older Android with the *previous* classical key (87:0A:E6:3D…3D:A4, Console → App signing → Previous app signing keys).
**All three** SHA-256s (both app-signing + upload F0:9F:6D…3B) must be on the Firebase Android app, or App Check says
'App attestation failed' on phones below Android 17 (cost 2 h on 26 Nov). Check a Play install with
`apksigner verify -v --print-certs base.apk` (the V3.0 signer is what older phones present).

## MANDATORY before any Play upload: release APK launch check
R8 can strip Firebase registrars (Keel 1.0.4 crashed on launch from Play). Back up the debug app's data first
(`adb exec-out run-as <pkg> tar -cf - databases files shared_prefs > x.tar`), uninstall, `adb install` the release APK,
launch, grep logcat for `FATAL EXCEPTION|NoSuchMethod|ClassNotFound|Could not instantiate`. Then restore the debug build + data.

## No billing, no ads in the MVP (H45/H66). App Check enforced (H48): release = Play Integrity.

## QA checklist (store build)
Onboarding → share a link → card read in the cloud (App Check passes) → search → Ask one question → Settings → About.
