# Simple Scoring

An Android scorekeeper for board games and casual play, following the
iPhone thematic of [Score Anything – Scorekeeper](https://apps.apple.com/us/app/score-anything-scorekeeper/id1541777240):
a tabletop radial dial where each player is a colored dot on a ring and
scores are kept with a circular rotary gesture.

The [v0.3-iphone release](https://github.com/hannad4/simple-scoring/releases/tag/v0.3-iphone)
is built from this thematic (see `aef11ca`).

| Tabletop scoreboard | Score with a swipe | Game setup |
| --- | --- | --- |
| ![iOS tabletop scoreboard](docs/ios-scoreboard.png) | ![iOS swipe scoring](docs/ios-swipe.png) | ![iOS game settings](docs/ios-settings.png) |

*Reference screenshots from the original iOS app showing the theme this
build follows: player dots on a dark ring, scores rotated to face their
player, swipe-to-score with a live delta, per-game settings.*

## Features

- Add players with custom names
- Set a step value per turn
- Track scores across multiple players
- Undo last move
- Clear all scores
- Game history

## Technical

Android Kotlin + Jetpack Compose (Material 3).
Package: `com.simplescoring.android`

## Build

```bash
./gradlew assembleDebug
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`.
