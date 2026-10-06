# Hotspot Keeper

Keeps your Android personal hotspot on permanently until you manually turn it off.

## Why this exists

Android's built-in hotspot timeout shuts the hotspot down after a period of inactivity (no devices connected). This app prevents that by periodically toggling the hotspot on, so it stays alive even when nothing is connected.

## Features

- Runs a foreground service with a persistent notification
- Every 30 seconds, checks if the hotspot is on; if it's off, turns it back on
- One-tap toggle in the app to start/stop the keeper
- **Data usage tracking**: live line chart showing data drawn over time, plus session and total usage counters
- No root required

## How it works

- The keeper service samples total device traffic every 30 seconds and records it
- The main screen shows a live line chart (MB over time) and text counters for this session and all-time usage
- The notification also shows current session usage

## Requirements

- Android 8.0 (Oreo) or newer
- Location permission (Android requires it for hotspot APIs)
- Nearby devices permission on Android 13+

## Build

Open the project in Android Studio and run on your device.

## Install

Download the APK from the Releases page once built.

## License

MIT