# Simple Scoring

An Android-native scorekeeper for board games and casual play, built with
the Material 3 Expressive design language: dynamic color, expressive shapes
and type, springy motion, bottom app bars, and extended FABs.

The tabletop scoreboard keeps every player visible at once — each player is
a colored dot on a ring, scores sit out by the screen edges facing their
player, and points are kept with a circular rotary-dial gesture that unwinds
with a spring return.

| Scoreboard | Settings | Score history |
| --- | --- | --- |
| ![Scoreboard](docs/board.png) | ![Settings](docs/settings.png) | ![Score history](docs/history.png) |

## Features

- 1–12 players with custom names and 24 dot colors
- Rotary swipe scoring: full turn = 10× step, either direction
- Tap a score to rotate it toward its player; tap a dot for quick +step
- Undo / redo, per-game score history, saved scoreboards
- Highest- or lowest-wins, adjustable step, Material You dynamic color

## Technical

Android Kotlin + Jetpack Compose (Material 3).
Package: `com.simplescoring.android`

## Build

```bash
./gradlew assembleDebug
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.

Headless UI screenshots (used above) regenerate with:

```bash
./gradlew recordPaparazziDebug
```
