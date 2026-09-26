# Score Orbit

A scorekeeper for game night on Android, inspired by the Score Anything app on iOS.

The problem it solves is a familiar one: keeping score on paper means hunting for a pen, doing arithmetic between turns, and arguing over the scorekeeper's chicken-scratch handwriting. Score Orbit puts every player on an easy to read shared dial. To score, drag around the ring the way you'd dial an old rotary phone; a full turn adds your configured points, and letting go snaps the dial back with a spring. Tapping a dot scores a smaller quick amount instead, and tapping a indicator rotates it so players can easily check their scores without everyone fighting over the phone.

![Score Orbit screens](docs/shots/showcase.png)

## How a game goes

You set up 1–12 players with names and colors, choose how many points a full revolution and a tap are worth, and whether the highest or lowest score wins. Then you just play - the board stays on the table and anyone can reach over and dial in their own points.

Scores land in a running ledger with undo and redo, so mis-taps are one button press to fix. When the game ends, the leaderboard ranks everyone with medal highlights for the top three.

<p align="center">
  <img src="docs/shots/board.png" width="220" alt="The ring">
  <img src="docs/shots/players.png" width="220" alt="Set up players">
  <img src="docs/shots/settings.png" width="220" alt="Settings">
</p>
<p align="center">
  <img src="docs/shots/history.png" width="220" alt="Score history">
  <img src="docs/shots/leaderboard.png" width="220" alt="Leaderboard">
</p>

## Details worth knowing

- **Tabletop-first layout.** Scores sit outside the ring facing each player's seat, and each dial dot parks near its corresponding player. The board dynamically adapts, comfortably accommodating anywhere between 1 and 12 players.
- **Teams.** Assign players to up to four teams and the board splits into tinted quadrants, one per team, each with its collective total. Players still score individually; the leaderboard ranks teams and solos separately.
- **Race to a target.** Set a Score to Win and the game ends in confetti the moment someone gets there — or leave it blank and play endless.
- **Gesture-safe board.** The bottom strip of the board ignores touches, so a swipe up to the home screen never changes the score by accident.
- **Never loses your game.** Setup, scores, and undo history save on every change and restore on launch - move the app to the background or come back tomorrow and the board is exactly as you left it.
- **Score History.** Review the game's full point history at any moment - keep the scorekeeper honest and quickly settle missed score disputes.
- **Adaptive everything.** Follows the system light/dark theme (or override it), picks up Material You dynamic colors on Android 12+, and haptic feedback with adjustable strength.
- **Built with Material 3 Expressive components** throughout: collapsing app bars, tonal buttons, segmented controls, and spring physics on the dial itself.

## Build

```bash
./gradlew assembleDebug
```

Debug APK lands at `app/build/outputs/apk/debug/app-debug.apk`.

Release needs the local keystore in `~/.local/share/score-orbit/` (not in the repo):

```bash
./gradlew assembleRelease
```

Screenshots are generated headless with Paparazzi:

```bash
./gradlew recordPaparazziDebug
```
