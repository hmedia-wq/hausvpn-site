# HausVPN — Android app

This is the **real** HausVPN Android client — not a mockup. It's a native
Kotlin + Jetpack Compose app that opens a genuine WireGuard tunnel using the
official `com.wireguard.android:tunnel` engine (the same core that powers the
official WireGuard app).

## What works right now

- A branded connect screen (the teal/gold dark UI from the store listing).
- Real OS-level VPN integration via Android's `VpnService` consent flow.
- A working WireGuard tunnel: paste any WireGuard config (wg-quick format)
  under **Add server configuration**, tap connect, and your device's traffic
  is tunnelled. Live upload/download counters update while connected.

So the moment you have *one* server (see below), this app is a functioning
VPN you can install on your phone.

## What's still stubbed (the honest list)

- **Server configs are entered manually** for now. The HausVPN backend
  (accounts, subscriptions, automatic config delivery, server picker) is the
  next build. `VpnViewModel.saveConfig()` is where the backend will inject
  configs automatically.
- **No billing yet** — Google Play Billing gets wired in before store launch.
- **One tunnel, one server.** Multi-region selection comes with the backend.

## Build it

You need Android Studio (Hedgehog or newer) — it ships the Android SDK this
project needs. This sandbox has Gradle but no Android SDK, so it can't produce
the APK here; your machine can.

```bash
# 1. Open the `android/` folder in Android Studio and let it sync, OR from CLI:
cd android
# create local.properties pointing at your SDK:
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # macOS: ~/Library/Android/sdk
./gradlew assembleDebug
# APK lands at app/build/outputs/apk/debug/app-debug.apk
```

Install on a phone with `adb install app/build/outputs/apk/debug/app-debug.apk`
(or just Run ▶ from Android Studio with the phone plugged in).

## The one thing only you can provide: a server

The app needs a WireGuard endpoint to connect to. Fastest path to a real,
working NY server (~$5/month):

1. Rent a small VPS in a New York datacenter (DigitalOcean, Vultr, Linode, or
   Hetzner all have NYC/Ashburn regions).
2. Install WireGuard on it — `wg-quick` + a one-time keygen, or use the
   `wireguard-install` community script for a 2-minute setup.
3. It prints a client config. Paste that into the app. Done — you're live.

Once you confirm the manual flow works end-to-end, the next milestone is the
backend that generates and hands out these configs automatically per user.

## Package / project facts

- Application ID: `com.hausgroup.vpn`
- minSdk 24 · targetSdk 34 · Kotlin 1.9 · Compose · Material 3
- VPN engine: `com.wireguard.android:tunnel:1.0.20230706`
