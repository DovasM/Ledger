# Ledger — Money Manager

Android app · Kotlin/Compose frontend · Rust/SQLite backend via UniFFI

## Stack

| Layer | Tech |
|---|---|
| UI | Kotlin + Jetpack Compose + Material 3 |
| Navigation | Jetpack Navigation Compose |
| DI | Hilt |
| Backend | Rust (sqlx + SQLite) |
| FFI | UniFFI (auto-generates Kotlin bindings from `core/src/ledger.udl`) |

## Project structure

```
Ledger/
├── app/                          # Android module
│   └── src/main/java/com/ledger/app/
│       ├── MainActivity.kt
│       ├── LedgerApp.kt
│       ├── data/
│       │   ├── LedgerBridge.kt   # Kotlin wrapper over Rust
│       │   └── di/AppModule.kt   # Hilt bindings
│       └── ui/
│           ├── theme/            # Color, Type, Theme (Editorial Ledger DS)
│           ├── components/       # Shared: BottomNavBar, LedgerCard, etc.
│           ├── navigation/       # NavGraph + Screen routes
│           └── screens/          # 15 screens
├── core/                         # Rust library
│   ├── src/
│   │   ├── lib.rs                # LedgerDb impl + UniFFI exports
│   │   ├── ledger.udl            # FFI interface definition
│   │   └── db/                   # SQLite models + pool setup
│   ├── Cargo.toml
│   └── build.rs
└── gradle/libs.versions.toml
```

## Building the Rust library

### Prerequisites
```bash
cargo install cargo-ndk uniffi-bindgen-kotlin
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android
```

### Compile for Android
```bash
cd core
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o ../app/src/main/jniLibs build --release
```

### Generate Kotlin bindings
```bash
uniffi-bindgen-kotlin \
  --lib-file target/aarch64-linux-android/release/libledger_core.so \
  --out-dir ../app/src/main/java/com/ledger/app/uniffi \
  src/ledger.udl
```

## Fonts

Download and place in `app/src/main/res/font/`:
- `manrope_regular.ttf`, `manrope_medium.ttf`, `manrope_semibold.ttf`, `manrope_bold.ttf`, `manrope_extrabold.ttf`
- `inter_regular.ttf`, `inter_medium.ttf`, `inter_semibold.ttf`

Both available free from Google Fonts.

## Running the app

1. Build Rust library (above)
2. Open project in Android Studio
3. Run on device / emulator (API 26+)
