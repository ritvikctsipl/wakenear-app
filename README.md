# WakeNear 🛏️➡️⏰

Location-based alarm for commuters. Set a destination, fall asleep, wake up when you arrive.

## Features
- Tap map or search to set destination
- Adjustable wake-up radius (200m - 2km)
- Background location tracking (works with screen off)
- Loud alarm + vibration that wakes you up
- Save favorite locations
- Dark theme (sleep-friendly)
- Completely free — no API keys needed

## Download
1. Go to [Actions](../../actions) tab
2. Click the latest successful build
3. Download `wakenear-debug` artifact
4. Transfer APK to your phone and install (enable "Install from unknown sources")

## Build Locally
1. Open in Android Studio
2. Build > Make Project
3. Run on device

## Requirements
- Android 8.0+ (API 26)
- GPS enabled
- Location permissions granted

## Tech Stack
- Kotlin + Jetpack Compose
- OpenStreetMap (osmdroid) — no Google Maps needed
- Room database for favorites
- Foreground service for background tracking

## License
MIT — Comprint
