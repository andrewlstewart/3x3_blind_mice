# Three Blind Cubers

[![Android CI](https://github.com/andrewlstewart/3x3_blind_mice/actions/workflows/android-ci.yml/badge.svg)](https://github.com/andrewlstewart/3x3_blind_mice/actions/workflows/android-ci.yml)

An Android app for **blindfolded (BLD) Rubik's cube solving** with a Bluetooth smart cube. Connects to a Moyu smart cube via BLE, tracks moves in real-time, times memorization and execution phases separately, and computes Old Pochmann memo sequences automatically.

## Features

### Solve Flow
The app guides a full BLD solve through six phases:

1. **IDLE** - Generate a scramble (Full, Corners-only, or Edges-only)
2. **SCRAMBLING** - Apply the scramble to your cube; moves are tracked and highlighted in real-time via Bluetooth
3. **SCRAMBLE_COMPLETE** - Scramble verified; tap to start memorization
4. **MEMORIZING** - Memo timer runs; first cube move automatically transitions to solving
5. **SOLVING** - Solve timer runs; tap to stop when finished (DNF prompt if cube isn't solved)
6. **COMPLETE** - Results card with memo time, solve time, move count, and OP memo sequence

### Old Pochmann Memo
When a scramble is generated, the app computes the **Old Pochmann letter-pair sequence** using the Speffz letter scheme (A-X). The memo is displayed after solve completion with:
- **Corner memo** - letter pairs for corner swaps (buffer: ULB, swap target: DRF)
- **Edge memo** - letter pairs for edge swaps (buffer: UR, swap target: UL)
- **Parity indicator** - shown when corner memo has an odd number of targets

### Bluetooth Smart Cube
- Connects to **Moyu WCU_MY3** (V10 AI) smart cubes via BLE
- AES-128 encrypted communication with MAC-derived keys
- Real-time move tracking with 5-bit move encoding
- Cube state synchronization and facelet parsing

## Architecture

The project follows **Clean Architecture** with MVVM:

```
app/src/main/java/com/example/threeblindcubers/
├── data/                    # Data layer
│   ├── bluetooth/           # BLE communication (MoyuCubeService, MoyuCrypto)
│   ├── database/            # Room DB (SolveEntity, SolveDao, SolveDatabase)
│   └── repository/          # Repository pattern (SolveRepository)
├── di/                      # Hilt dependency injection modules
├── domain/                  # Business logic
│   ├── cube/                # Cube algorithms
│   │   ├── CubeStateTracker.kt      # 54-facelet state with Kociemba numbering
│   │   ├── OldPochmannSolver.kt     # OP BLD memo tracing algorithm
│   │   └── ScrambleGenerator.kt     # Random scramble generation
│   └── models/              # Domain models (Move, Face, ScrambleMode, etc.)
└── ui/                      # Presentation layer
    ├── settings/            # Bluetooth settings sheet
    ├── test/                # Debug/test screen
    ├── theme/               # Material 3 theme
    └── timer/               # Main solve flow
        ├── components/      # Compose UI components
        ├── TimerScreen.kt   # Main screen
        ├── TimerViewModel.kt # State management
        └── TimerUiState.kt  # UI state model
```

## Tech Stack

| Category | Technology |
|----------|-----------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| State | ViewModel + StateFlow |
| DI | Hilt/Dagger |
| Database | Room ORM |
| Async | Kotlin Coroutines |
| Bluetooth | Android BLE (GATT) |
| Encryption | AES-128 ECB (javax.crypto) |
| Testing | JUnit 4 |

## Build & Run

### Prerequisites
- Android Studio (latest stable)
- Android SDK 36
- JDK 21

### Build
```bash
./gradlew assembleDebug
```

### Install
```bash
./gradlew installDebug
```

### Run Tests
```bash
# All unit tests
./gradlew testDebugUnitTest

# Specific test class
./gradlew testDebugUnitTest --tests "com.example.threeblindcubers.domain.cube.OldPochmannSolverTest"
```

### Clean
```bash
./gradlew clean
```

## Configuration

| Setting | Value |
|---------|-------|
| Min SDK | 26 (Android 8.0) |
| Target SDK | 36 |
| Compile SDK | 36 |
| Gradle | 9.1.0 |
| Kotlin | 2.0.21 |
| Compose BOM | 2024.09.00 |

## Testing

### Unit Tests (69 tests, all passing)
- **OldPochmannSolverTest** (18 tests) - OP memo tracing: solved cube, twisted corners, buffer exclusion, parity, formatting
- **CubeStateTrackerTest** - Facelet state tracking, move application, Kociemba numbering
- **CubeVisualizationMappingTest** - Cube net layout mapping

## License

This project is licensed under the **GNU General Public License v3.0** — see the [LICENSE](LICENSE) file for details.

### Third-Party Attribution

This project includes code from:

- **[Min2Phase](https://github.com/cs0x7f/min2phase)** by Shuang Chen — Rubik's Cube two-phase solver (GPL v3)
- **[CSTimer](https://github.com/cs0x7f/cstimer)** by cs0x7f — Moyu smart cube BLE protocol (GPL v3)
- **[Bouncy Castle](https://www.bouncycastle.org/)** — Cryptography library (MIT)

See [NOTICE](NOTICE) for full attribution details.
