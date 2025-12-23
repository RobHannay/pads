# Worship Pads

A simple Android app for playing ambient worship pads during services.

## Features

- **12 Chromatic Keys**: Grid layout with all 12 musical keys
- **Smooth Transitions**: Fade in/out and crossfade between pads
- **Adjustable Fade Duration**: Control crossfade time (0.5s to 5s) in Settings
- **Stage-Ready**: Dark theme with screen always on
- **Lifecycle Aware**: Audio pauses when app is backgrounded

## Audio

Uses "Bridge (Ambient Pads III)" by Karl Verkade - professional quality ambient pad loops.

## Technical Details

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose with Material 3
- **Audio**: MediaPlayer with OGG files
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 34 (Android 14)

## Building

### Prerequisites
- Android Studio (latest version)
- JDK 17+
- Android SDK API 34

### Build
```bash
./gradlew assembleDebug
```

Or open in Android Studio and run.

## Usage

1. **Play**: Tap any key to fade in that pad
2. **Stop**: Tap the active key again to fade out
3. **Switch**: Tap a different key to crossfade
4. **Settings**: Tap the gear icon to adjust fade duration

## Project Structure

```
com.worshippads/
├── audio/
│   ├── PadPlayer.kt      # MediaPlayer wrapper with volume control
│   ├── AudioEngine.kt    # Playback, fading, state management
│   └── MusicalKey.kt     # Enum of 12 keys with resource names
├── ui/
│   └── PadGrid.kt        # Animated button grid
└── MainActivity.kt       # Main screen + settings
```

## License

MIT
