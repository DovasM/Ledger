# Ledger — Project Instructions

> **For Claude:** Read this file before every task. After completing any task that changes architecture, adds a pattern, reveals a pitfall, or updates the build — edit the relevant section of this file to keep it current.

## Overview

Ledger is a personal finance Android app with a hybrid Kotlin + Rust architecture. The UI is built entirely with Jetpack Compose (Material 3). The data layer runs in a Rust SQLite backend that is bridged to Kotlin via UniFFI FFI bindings. All AI features use Google Gemma 4 E2B on-device.

---

## Architecture

```
┌─────────────────────────────────────────────────────┐
│                Jetpack Compose UI                   │
│         (60+ screens, Material 3, Hilt VMs)         │
└─────────────────────┬───────────────────────────────┘
                      │ StateFlow / collectAsStateWithLifecycle
┌─────────────────────▼───────────────────────────────┐
│              ViewModels (Hilt @HiltViewModel)        │
│     inject ILedgerBridge + Repositories              │
└──────────────┬──────────────────┬───────────────────┘
               │                  │
┌──────────────▼──────┐  ┌────────▼──────────────────┐
│  ILedgerBridge      │  │  PreferencesRepository     │
│  (Kotlin interface) │  │  GemmaModelRepository      │
└──────────────┬──────┘  └────────────────────────────┘
               │ JNA / UniFFI
┌──────────────▼──────────────────────────────────────┐
│          libuniffi_ledger.so (Rust)                  │
│     SQLite via sqlx + tokio async runtime            │
└─────────────────────────────────────────────────────┘
```

### Layer responsibilities

| Layer | Location | Role |
|---|---|---|
| Screens | `ui/screens/` | Composable UI only — reads VM state, calls VM functions |
| ViewModels | `ui/viewmodel/` | `@HiltViewModel`, holds `StateFlow`, calls bridge/repos |
| ILedgerBridge | `data/ILedgerBridge.kt` | Interface over all Rust DB methods (enables mocking) |
| LedgerBridge | `data/LedgerBridge.kt` | Concrete implementation, loads `.so`, calls UniFFI |
| Repositories | `data/` | DataStore, Gemma model management |
| Hilt DI | `data/di/AppModule.kt` | Provides `LedgerBridge` (via `@Provides`) + binds to `ILedgerBridge` (via `@Binds`) |
| Rust core | `core/src/` | SQLite CRUD, business logic, migrations |
| UniFFI bindings | `uniffi/uniffi/ledger/ledger.kt` | Auto-generated — **never edit manually** |

---

## Tech Stack

| Concern | Technology |
|---|---|
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 |
| Navigation | Jetpack Navigation Compose |
| DI | Hilt 2.52 (KSP) |
| State | `StateFlow` + `collectAsStateWithLifecycle` |
| Async | Kotlin Coroutines + Flow |
| Preferences | DataStore Preferences 1.1.1 |
| DB language | Rust (stable) |
| DB engine | SQLite via sqlx 0.8 |
| Async runtime (Rust) | tokio |
| FFI bridge | UniFFI 0.28 |
| Native interop | JNA 5.14.0 |
| AI / LLM | Google AI Edge `aicore` 0.0.1-exp01 (Gemma 4 E2B) |
| Background work | WorkManager 2.9.0 |
| Serialization | kotlinx.serialization 1.7.3 |
| Build | AGP 8.13.2, Gradle with version catalog |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 (Android 15) |
| JVM | Java 17 |

---

## Project Structure

```
Ledger/
├── app/
│   └── src/main/java/com/ledger/app/
│       ├── MainActivity.kt
│       ├── LedgerApp.kt                   # @HiltAndroidApp
│       ├── data/
│       │   ├── di/AppModule.kt            # Hilt modules
│       │   ├── ILedgerBridge.kt           # Interface (35 methods)
│       │   ├── LedgerBridge.kt            # FFI implementation
│       │   ├── PreferencesRepository.kt   # DataStore wrapper
│       │   ├── GemmaModelInfo.kt          # AI model constants
│       │   ├── GemmaModelRepository.kt    # Model download / verify / delete
│       │   └── SeedDataUtil.kt            # Dev seed data
│       ├── ui/
│       │   ├── navigation/NavGraph.kt     # All routes + NavHost
│       │   ├── viewmodel/                 # 12 ViewModels
│       │   ├── screens/                   # 60+ Composable screens
│       │   ├── components/                # Shared components
│       │   ├── theme/                     # Color, Type, Theme
│       │   └── util/                      # CategoryIcons, CsvExport, GoalImageStore
│       └── uniffi/uniffi/ledger/
│           └── ledger.kt                  # Auto-generated UniFFI bindings
│
├── core/                                  # Rust library
│   ├── Cargo.toml
│   ├── build.rs                           # UniFFI scaffolding gen
│   ├── .cargo/config.toml                 # NDK linker config
│   └── src/
│       ├── lib.rs                         # Main Rust code (~745 lines)
│       ├── ledger.udl                     # UniFFI interface definition
│       └── db/
│           ├── mod.rs                     # Pool setup, migrations
│           └── models.rs                  # SQLx FromRow structs
│
├── gradle/libs.versions.toml              # Dependency version catalog
├── TODO.md                                # Full development roadmap
└── project.md                             # This file
```

---

## How to Build

### Android App

```bash
./gradlew assembleDebug
./gradlew installDebug
```

### Rust Native Library

Prerequisites: Android NDK installed, path set in `local.properties` (`ndk.dir=...`).

```bash
cd core

# Install targets once
rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android

# Build all ABIs
NDK=$ANDROID_SDK/ndk/<version>
TOOLCHAIN=$NDK/toolchains/llvm/prebuilt/windows-x86_64/bin

CC_aarch64_linux_android="$TOOLCHAIN/aarch64-linux-android26-clang.cmd" \
CC_armv7_linux_androideabi="$TOOLCHAIN/armv7a-linux-androideabi26-clang.cmd" \
CC_x86_64_linux_android="$TOOLCHAIN/x86_64-linux-android26-clang.cmd" \
  cargo build --release \
    --target aarch64-linux-android \
    --target armv7-linux-androideabi \
    --target x86_64-linux-android

# Copy outputs
cp target/aarch64-linux-android/release/libuniffi_ledger.so   ../app/src/main/jniLibs/arm64-v8a/
cp target/armv7-linux-androideabi/release/libuniffi_ledger.so ../app/src/main/jniLibs/armeabi-v7a/
cp target/x86_64-linux-android/release/libuniffi_ledger.so    ../app/src/main/jniLibs/x86_64/
```

**Critical:** After changing `ledger.udl` or any Rust public API, regenerate the Kotlin bindings:

```bash
cargo run --bin uniffi-bindgen generate src/ledger.udl \
  --language kotlin --out-dir ../app/src/main/java/
```

The UniFFI checksum baked into the `.so` must match the generated Kotlin file. Stale bindings cause a crash (`UnsatisfiedLinkError: uniffi_..._checksum_...`) on startup.

---

## Adding a New Screen — Standard Pattern

### 1. Add the route to `NavGraph.kt`

```kotlin
// In the sealed class
object MyFeature : Screen("my_feature")

// In LedgerNavGraph()
composable(Screen.MyFeature.route) { MyFeatureScreen(navController) }
```

### 2. Create the ViewModel

```kotlin
@HiltViewModel
class MyFeatureViewModel @Inject constructor(
    private val bridge: ILedgerBridge
) : ViewModel() {

    private val _items = MutableStateFlow<List<MyItem>>(emptyList())
    val items: StateFlow<List<MyItem>> = _items.asStateFlow()

    init { load() }

    fun load() = viewModelScope.launch(Dispatchers.IO) {
        _items.value = bridge.listMyItems()
    }
}
```

### 3. Create the Screen

```kotlin
@Composable
fun MyFeatureScreen(
    navController: NavController,
    vm: MyFeatureViewModel = hiltViewModel()
) {
    val items by vm.items.collectAsStateWithLifecycle()
    // ...
}
```

### 4. Add navigation entry point

Add a `SettingsNavItem` or `IconButton` anywhere that calls `navController.navigate(Screen.MyFeature.route)`.

---

## Adding a New Rust Method

### 1. Define in `core/src/ledger.udl`

```udl
interface LedgerDb {
    // ... existing methods ...
    [Throws=LedgerError]
    sequence<MyItem> list_my_items();
};
```

### 2. Implement in `core/src/lib.rs`

```rust
pub fn list_my_items(&self) -> Result<Vec<MyItem>, LedgerError> {
    self.rt.block_on(async {
        let rows = sqlx::query_as::<_, db::models::MyItemRow>(
            "SELECT * FROM my_items ORDER BY created_at DESC"
        )
        .fetch_all(&self.pool)
        .await?;
        Ok(rows.into_iter().map(MyItem::from).collect())
    })
}
```

### 3. Add to `ILedgerBridge.kt`

```kotlin
fun listMyItems(): List<MyItem>
```

### 4. Add `override` to `LedgerBridge.kt`

```kotlin
override fun listMyItems(): List<MyItem> = db.listMyItems()
```

### 5. Rebuild `.so` and regenerate bindings

See the build section above.

---

## Dependency Injection

All DI lives in `data/di/AppModule.kt`:

```kotlin
// Provides the concrete Rust bridge singleton
@Module @InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun provideLedgerBridge(@ApplicationContext context: Context): LedgerBridge {
        return LedgerBridge().also { it.open(context); SeedDataUtil.seed(it) }
    }
}

// Binds interface → implementation (enables mocking in tests)
@Module @InstallIn(SingletonComponent::class)
abstract class BridgeBindingsModule {
    @Binds @Singleton
    abstract fun bindLedgerBridge(impl: LedgerBridge): ILedgerBridge
}
```

Repositories annotated `@Singleton @Inject constructor(...)` are provided automatically by Hilt without any AppModule entry.

---

## Preferences / Settings

`PreferencesRepository` wraps DataStore and exposes all user preferences as `Flow<T>`. `SettingsViewModel` converts them to `StateFlow` via `stateIn(viewModelScope, SharingStarted.Eagerly, default)`.

**Adding a new preference:**

1. Add a `private val KEY_X = ...Preferences.Key<T>("key_x")` in `PreferencesRepository`
2. Add `val myPref: Flow<T> = ds.data.map { it[KEY_X] ?: default }` (note the space before `=` — required to avoid Kotlin parsing `Flow<T>=` as `>=`)
3. Add `suspend fun setMyPref(v: T) { ds.edit { it[KEY_X] = v } }`
4. Expose as `StateFlow` in `SettingsViewModel`

---

## Navigation Reference

All routes are defined as objects inside the `Screen` sealed class in `NavGraph.kt`. Routes with parameters use path segments:

```kotlin
object EditTransaction : Screen("edit_transaction/{id}") {
    fun createRoute(id: String) = "edit_transaction/$id"
}
```

Navigate with: `navController.navigate(Screen.EditTransaction.createRoute(txId))`

---

## AI / Gemma Integration

The AI model lifecycle is managed by `GemmaModelRepository` + `GemmaModelViewModel`.

- Model file: stored in `context.filesDir/models/gemma4-e2b.task` (~1.3 GB)
- Metadata: `gemma4-e2b.json` (version, SHA-256, size, download date)
- Status: `ModelStatus` sealed class — `NotDownloaded | Downloading | Verifying | Ready | UpdateAvailable | Error | Deleting`
- Download: chunked HTTP with `isActive` cancellation check, atomic rename from `.tmp`
- Integrity: SHA-256 computed in 8 KB chunks (avoids OOM on large files)
- Storage check: `StatFs` on `context.filesDir` — requires 1.1× expected size free

The `AiModelScreen` navigates from `SettingsScreen → Screen.AiModelSettings`.

---

## Data Entities

Defined in `core/src/ledger.udl` and mirrored as Kotlin data classes in `ledger.kt`:

| Entity | Key fields |
|---|---|
| `Transaction` | id, wallet_id, title, category, amount, is_income, note, created_at |
| `Wallet` | id, name, description, balance, created_at |
| `SavingsGoal` | id, name, current_amount, target_amount, deadline, created_at |
| `Category` | id, name, icon_name, color_hex, is_expense, created_at |
| `Budget` | id, category_id, limit_amount, period, alert_threshold, created_at |
| `Debt` | id, name, debt_type, total_amount, remaining_amount, apr, monthly_payment, created_at |
| `RecurringTransaction` | id, title, amount, category, wallet_id, is_income, frequency, next_date, created_at |
| `Tag` | id, name, created_at |
| `PriceAlert` | id, symbol, asset_name, target_price, direction, active, created_at |
| `MonthSummary` | total_income, total_expenses, net_savings, transaction_count |

---

## UI Conventions

- All screens accept `navController: NavController` as first parameter
- Back navigation: `navController.popBackStack()`
- Top bar: `TopAppBar` with `containerColor = SurfaceContainerLow`
- Primary scaffold background: `containerColor = SurfaceContainerLow`
- Cards: use `LedgerCard` (outlined) or `LedgerFloatingCard` (elevated) from `ui/components/`
- Colors: import from `com.ledger.app.ui.theme.*` — use `Primary`, `OnSurface`, `OnSurfaceVariant`, etc.
- No comments in code unless the WHY is non-obvious
- No trailing summary comments
- UI text is in Lithuanian (app language)

---

## Common Pitfalls

1. **Stale `.so` crash** — After any Rust API change, must rebuild `.so` AND regenerate Kotlin bindings. The UniFFI checksum validation runs on every app start.

2. **`Flow<T>=` parse error** — When a property type is a generic (`Flow<String>`, `Flow<Boolean>`), always put a space before `=` in assignments. `Flow<String>=` is parsed by Kotlin as `>=` (greater-than-or-equal).

3. **`GemmaModelRepository` thread** — `downloadModel()` must run on `Dispatchers.IO` (it's a `flow {}` builder, caller is responsible). `computeSha256` is CPU-bound — also runs on IO in the download flow.

4. **UniFFI blocking** — All Rust methods are synchronous from Kotlin's perspective (they block using `rt.block_on`). Always call bridge methods from `viewModelScope.launch(Dispatchers.IO)`.

5. **SeedDataUtil** — Seeds fake "Alex Johnson" data on every fresh install. Must be gated or removed before production release.

6. **Edit tool requires prior Read** — When editing any file, the Read tool must be called first in the same conversation.

---

## Development Roadmap

See `TODO.md` for the full prioritized list. High-level phases:

1. **ML Kit OCR** — Camera + gallery receipt scanning
2. **Gemma AI pipeline** — OCR → parse → editable preview → save transaction
3. **Transaction splitting** — Divide one transaction across multiple wallets
4. **Shared expenses** — Group expense splitting (needs SharedExpenseViewModel + Room entity... or Rust entity)
5. **Automatic backups** — WorkManager daily/weekly export + optional Google Drive
6. **Real notifications** — WorkManager background checks for budget/bill/balance alerts
7. **Biometric / PIN lock** — Wire `BiometricPrompt` to security settings toggles
8. **Live currency rates** — `exchangerate.host` or `frankfurter.app` API
9. **Wallet transfers** — Dedicated transfer type in Rust schema
10. **Onboarding wizard** — Replace SeedDataUtil for real users
11. **Tests** — Unit tests for ViewModels + DAOs once schema stabilises
