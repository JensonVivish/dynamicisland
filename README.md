# DigiIsland

A lightweight Dynamic Island for Android 10+. Pure Android SDK (no AndroidX, no third-party libraries).

## What it does
- Notifications pop out of a black pill at the top of the screen (tap to expand, swipe up to dismiss, long-press to open)
- Incoming / ongoing calls with Answer / Decline and a live call timer
- Music controls (previous / pause / next) with a small equaliser while playing
- Charging and low-battery pop-ups
- Per-app block list, adjustable position and show time

## How it stays alive
- `IslandListener` (NotificationListenerService) is bound by the system, so it is restarted automatically
- `KeepAliveService` is a minimised foreground service (START_STICKY) and restarts at boot
- No timers or polling while idle; the overlay window only exists while something is being shown

## Build the APK on GitHub
1. Put the contents of this folder at the root of a repo
2. Push to `main` (or Actions -> Build APK -> Run workflow)
3. Download `DigiIsland-apk` from the run's Artifacts, unzip, install `app-release.apk`

The release build is signed with the debug key so it installs directly. Use your own keystore before publishing anywhere.

## First launch
Grant: Display over other apps, Notification access, Service notification, Unrestricted battery.
On Android 13+ (sideloaded): App info -> three-dot menu -> Allow restricted settings, then grant Notification access.
Use the Position sliders so the pill sits over your camera cut-out.
On Realme / Oppo / Xiaomi / Samsung also allow auto-start and set battery to "No restrictions" in system settings.
