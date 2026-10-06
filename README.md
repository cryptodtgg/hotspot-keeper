# Hotspot Keeper

Keeps your Android personal hotspot on permanently until you manually turn it off.

## Why this exists

Android's built-in hotspot timeout shuts the hotspot down after a period of inactivity (no devices connected). This app prevents that by periodically toggling the hotspot on, so it stays alive even when nothing is connected.

## Features

- Runs a foreground service with a persistent notification
- Every 30 seconds, checks if the hotspot is on; if it's off, turns it back on
- One-tap toggle in the app to start/stop the keeper
- **Data usage tracking**: live line chart showing data drawn over time, plus session and total usage counters
- **Background sampling**: a lightweight alarm samples data usage every 15 minutes even when the keeper service is stopped — no foreground service, no notification, no wake lock
- **Client list (caller ID)**: shows every device connected to your hotspot — name, MAC address, IP — refreshed every 10 seconds
- **Kick anyone off**: tap a client in the list to disconnect them instantly, or hit "Kick Everyone" to clear the whole network
- No root required

## How it works

- The keeper service samples total device traffic every 30 seconds and records it
- A separate `DataSamplingReceiver` alarm fires every 15 minutes to record usage when the keeper is off
- `HotspotClientManager` reads connected clients via hidden WifiManager reflection methods
- The main screen shows a live line chart, usage counters, and the live client list

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