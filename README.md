# Score Orbit

Scorekeeper for game night. No pens, no paper, no math arguments — everyone gets a colored dot on a ring, and you score by dragging in circles like an old rotary phone. Let go and the dial springs back into place.

Inspired by the Score Anything app on iOS, rebuilt as a native Android app.

![Score Orbit screens](docs/shots/showcase.png)

## How it plays

Set up 1–12 players with names and colors, pick how many points a full spin is worth, then just spin. Tapping a dot scores a smaller quick amount. Tapping a score rotates it so the person across the table can read it.

Changed your mind mid-game? Undo and redo are right there, and every entry lands in a ledger you can scroll back through. When the game ends, the leaderboard ranks everyone — highest or lowest wins, your call.

<p align="center">
  <img src="docs/shots/board.png" width="220" alt="The ring">
  <img src="docs/shots/players.png" width="220" alt="Set up players">
  <img src="docs/shots/settings.png" width="220" alt="Make it yours">
</p>
<p align="center">
  <img src="docs/shots/history.png" width="220" alt="Every point">
  <img src="docs/shots/leaderboard.png" width="220" alt="Who's winning">
</p>

Small stuff that matters: follows your system theme (or force light/dark), Material You dynamic color, and haptic feedback with adjustable strength.

## Build

```bash
./gradlew assembleDebug
```

Debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

Release needs the local keystore in `~/.local/share/score-orbit/` (not in the repo):

```bash
./gradlew assembleRelease
```

Screenshots are generated headless with Paparazzi, then framed into what's in `docs/`:

```bash
./gradlew recordPaparazziDebug
```
