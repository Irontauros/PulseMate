cd C:\Users\franc\AndroidStudioProjects\PulseMate
@'
# PulseMate

An Android app (Kotlin, Jetpack Compose) that controls Bluetooth LE haptic devices from two different brands through one shared interface, and wraps them in 20 original games. Each game has its own look, font and vibration feedback, and everything is drawn in code: no game engine, no image files, no downloaded fonts.

## Highlights

- **Two Bluetooth protocols behind one interface.** One brand is driven by BLE advertising, the other through a GATT connection. Games never know which brand is connected.
- **A reverse-engineered protocol.** The second device did not follow its documented protocol, so it was worked out with an on-screen probe and structured on-device experiments.
- **20 games** with custom Canvas rendering, frame-based game loops, touch, swipe and accelerometer input, simple AI opponents and saved best scores.
- **Vibration is never the only clue.** Every game can be played with no device connected, because each vibration signal is also shown on screen.

## Games

| # | Game | How it plays |
| --- | --- | --- |
| 1 | Haptic Quiz | Multiple-choice questions by category, with a free-play mode |
| 2 | Merge Drop | Drop balls that merge into larger ones |
| 3 | Memory | Flip cards to find pairs |
| 4 | Tilt Maze | Tilt the phone to roll a character to the goal |
| 5 | Fruity Snake | Classic snake that eats fruit and grows |
| 6 | Brick Pop | Breakout: bounce a ball off a paddle to clear bricks |
| 7 | Fruit Slice | Swipe to slice fruit as it flies up |
| 8 | Water Sort | Pour colored layers between tubes |
| 9 | Whack-a-Mole | Tap moles as they pop out of holes |
| 10 | Shape Fit | Fit pieces of different shapes into a board |
| 11 | Connect 4 | Play against the computer |
| 12 | Battleship | Place ships and guess the computer's grid |
| 13 | Lock Pick | Turn a pick to find the sweet spot of each pin |
| 14 | Sketch Drop | Draw lines so a ball falls onto a target and stays there |
| 15 | Tile Echo | Memorize and repeat a growing sequence of lit tiles |
| 16 | Chase | Steer a beetle through a maze, eat every seed, escape three chasers |
| 17 | Gravity Flip | Tap to flip gravity and dodge obstacles while collecting gems |
| 18 | Chain Pop | Grow bubbles so that bursts chain through their neighbors |
| 19 | Treasure Dig | Dig a sand grid to find a buried chest, guided by cold-to-hot hints |
| 20 | Reel In | Press and hold to reel in a fish, keeping line tension in the green zone |

The two remote-control screens (one per brand) are the first two cards in the hub. Each has 12 buttons and keeps its device running after you leave the screen.

## Tech stack

| Area | What is used |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose with Material 3 |
| Drawing | Compose Canvas and DrawScope |
| Game loop | Coroutines with `withFrameNanos`, time step capped at 50 ms |
| Input | `awaitEachGesture`, swipe tracking, press-and-hold, accelerometer |
| Bluetooth | BLE advertising (`BluetoothLeAdvertiser`), BLE scanning and GATT client (`BluetoothGatt`) |
| Storage | SharedPreferences for best scores, one file per game |
| Permissions | Runtime Bluetooth permissions on Android 12 and later, legacy permissions below that |

## Architecture

The app is a single activity with a small hand-written navigation layer, a shared haptic interface and one self-contained folder per game.

- **Haptic interface.** Every game talks to a `HapticController` with `setLevel(1..9)`, `stop()`, `reset()` and `getLevel()`. `LoveSpouseController` implements it and also forwards each command to the second brand's link, so one call drives whichever devices are on.
- **Per-game folder.** Each game has the same four files: `Style` (palette and font), `World` (pure game logic with no UI), `Haptics` (wraps the controller with pulses) and `Screen` (the Compose screen).
- **Event pattern.** The world records events (for example a catch or a crash) in a list. The screen reads and clears it each frame and turns each event into a vibration, a message or a screen change. This keeps the rules testable and the UI thin.

## Bluetooth layer

**Brand 1: BLE advertising (Love Spouse).** The device is never connected to. It listens for advertisement packets, so the app broadcasts a manufacturer-data payload that encodes the intensity level (1 to 9). Changing the level swaps the advertisement, and stopping sends a stop payload.

**Brand 2: GATT connection (Prettylove).** This device must be connected. `PrettyLoveLink` is a process-wide singleton, so the connection survives leaving a screen. It scans for the device, connects, discovers services and writes commands to one writable characteristic. A watchdog reconnects after a drop.

- Writing `[00, p]` selects one of 12 built-in patterns, and `00 00` stops the device.
- For game levels, the link switches the device on and off quickly to approximate intensities 1 to 9.

**How the second protocol was found.** The documented protocol did not match this device. An on-screen probe listed every device with its services, the device was identified by name, writable characteristics were tested, and a test mode sent each pattern number while the tester described what each one felt like. Those reports were used to confirm and label the 12 patterns.

## Design rules

- Every game has its own palette, built-in Android font and menu card art.
- Vibration is never the only clue, and color is never the only clue either.
- Best scores are saved as soon as they are beaten, so leaving a game never loses them.
- Games lock portrait on entry, and vibration stops when the app goes to the background.

## Build and run

1. Open the project in Android Studio.
2. Connect a physical Android phone. Bluetooth features need real hardware, so an emulator will not work for them.
3. Run the `app` configuration, and grant the Bluetooth permissions when asked.

Every game can be played without a device connected.

## Limitations

- Game levels on the second brand are approximate, because the device does not follow very fast on and off commands. The planned fix is mapping the levels onto its 12 patterns.
- No automated tests. Verification so far has been manual, on one phone and one model of each device.
- Game difficulty values are first guesses and need play-testing with more players.
- A pattern-test mode is still in the second brand's link, switched off.

## Notes

Built and tested by me, with Claude used as a coding assistant for the Kotlin source. I defined the product and design rules, ran the device experiments and tested every build on a real phone.

## License

See [LICENSE](LICENSE).
'@ | Set-Content -Encoding UTF8 README.md
