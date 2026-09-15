# Score Orbit

An Android-native scorekeeper for board games and casual play. Every player
is a colored dot on a ring, scores sit out by the screen edges facing their
player, and points are kept with a circular rotary-dial gesture that unwinds
with a spring return. Inspired by the iOS Score Anything scorekeeper app
([App Store listing](https://apps.apple.com/us/app/score-anything-scorekeeper/id1541777240)).

## Features

- 1–12 players with custom names and 24 dot colors
- Rotary swipe scoring with configurable points per revolution
- Tap-a-dot quick scoring with its own configurable value
- Tap a score to rotate it toward its player
- Undo / redo with a per-game score ledger
- Ranked leaderboard with medal highlights
- Highest- or lowest-wins, light/dark/system theme, haptic feedback
- Material 3 Expressive design with dynamic color

## Technical

Android Kotlin + Jetpack Compose (Material 3).
Package: `com.scoreorbit.android`

## Build

```bash
./gradlew assembleDebug
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

A signed release APK builds with:

```bash
./gradlew assembleRelease
```

Release signing reads the local keystore in `~/.local/share/score-orbit/`
(never committed); without it, release builds come out unsigned.

Headless UI screenshots regenerate with:

```bash
./gradlew recordPaparazziDebug
```
