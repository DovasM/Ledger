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
| Home screen widgets | Glance (`glance-appwidget` + `glance-material3`) 1.1.1 |
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
│       │   ├── viewmodel/                 # 15 ViewModels
│       │   ├── screens/                   # 60+ Composable screens
│       │   ├── components/                # Shared components
│       │   ├── theme/                     # Color, Type, Theme
│       │   └── util/                      # CategoryIcons, CsvExport, GoalImageStore,
│       │                                  #   MoneyFormat, StreakCalculator
│       ├── widget/                        # Glance home-screen widgets
│       │   ├── WidgetSnapshot.kt          # Snapshot model + DataStore repository
│       │   ├── WidgetUpdater.kt           # Bridge → snapshot → updateAll; WidgetEntryPoint
│       │   ├── WidgetTheme.kt             # Glance colors + route-intent builder
│       │   ├── LedgerWidgetReceiver.kt    # Shared receiver base (goAsync refresh)
│       │   ├── AllowanceWidgetConfigActivity.kt  # android:configure target (per-instance)
│       │   ├── QuickAddWidget.kt
│       │   ├── DailyAllowanceWidget.kt
│       │   └── StreakWidget.kt
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

Prerequisites: Android NDK installed. `build.rs` finds it through **`ANDROID_NDK_HOME`**, not
`local.properties`.

**`build.rs` builds llama.cpp via cmake on every build** (only Vulkan is behind a feature flag), so
this is a multi-minute compile, and two Windows-specific settings are mandatory:

- **`CMAKE_GENERATOR=Ninja`** — otherwise cmake picks the Visual Studio generator and dies with
  *"The BaseOutputPath/OutputPath property is not set for project 'VCTargetsPath.vcxproj'"*. If a
  build already got that far, its `CMakeCache.txt` pins the wrong generator and every later build
  fails with *"Does not match the generator used previously"* — delete
  `$CARGO_TARGET_DIR/<target>/release/build/ledger-core-*/out` before retrying.
- **`CARGO_TARGET_DIR=C:\lt`** — the default `target/` path blows past Windows' 260-character limit
  during cmake sub-project compilation.

Ninja and cmake ship inside the Android SDK (`Sdk/cmake/<version>/bin`) and are usually not on PATH.

```bash
NDK=$ANDROID_SDK/ndk/<version>
CMAKEBIN=$ANDROID_SDK/cmake/<version>/bin
TC=$NDK/toolchains/llvm/prebuilt/windows-x86_64/bin

export ANDROID_NDK_HOME=$NDK
export PATH="$CMAKEBIN:$PATH"
export CMAKE_GENERATOR=Ninja
export CMAKE_MAKE_PROGRAM="$CMAKEBIN/ninja.exe"
export CARGO_TARGET_DIR=C:/lt
export CC_aarch64_linux_android="$TC/aarch64-linux-android26-clang.cmd"
export CXX_aarch64_linux_android="$TC/aarch64-linux-android26-clang++.cmd"
```

Note `abiFilters` is `arm64-v8a` + `x86_64`, so `armeabi-v7a` does not need rebuilding.

**Check the real exit code.** `cargo build … | tail` reports *tail's* status, so a failed build looks
like a success. Use `set -o pipefail`, or redirect to a file and echo `$?`.

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
cd core
CARGO_TARGET_DIR=C:/lt-host cargo run --release --bin uniffi-bindgen -- \
  generate src/ledger.udl --language kotlin \
  --out-dir ../app/src/main/java/com/ledger/app/uniffi/
```

Note the `--` before `generate`, the out-dir (the generated file lives at
`com/ledger/app/uniffi/uniffi/ledger/ledger.kt`, **not** at the repo-root path an older version of
this file claimed), and a **separate `CARGO_TARGET_DIR`** so the host build does not fight the
Android one over the same lock.

This only works because `build.rs` now skips the llama.cpp build for non-Android targets. It used
to build it for whatever target cargo was pointed at, so this command tried to compile llama.cpp
with MSVC and died — which made regenerating the bindings look impossible on Windows.

The UniFFI checksum baked into the `.so` must match the generated Kotlin file. Stale bindings cause
a crash (`UnsatisfiedLinkError: uniffi_..._checksum_...`) on startup. **Changing the UDL means
rebuilding the `.so` too** — regenerating only one half is what produces that crash.

`ktlint` is not installed, so the generator warns it could not auto-format. Harmless.

## Tests

The database layer has a regression suite under `core/tests/`. It runs on the host in ~2 seconds
and needs no device:

```bash
cd core
CARGO_TARGET_DIR=C:/lt-host cargo test --tests
```

`common::TestDb` opens a real SQLite **file** in the temp directory through the same
`open_database` the app calls, so every test replays the full migration chain from empty — a broken
migration fails the suite. A file rather than `:memory:` on purpose: the pool opens several
connections and an in-memory database is per-connection, so half the migrations would land
somewhere the next query cannot see. Each `TestDb` deletes its file on drop.

Two things make this possible and must not be undone:
- **`crate-type` includes `rlib`.** With only `cdylib`/`staticlib` an integration test cannot link
  the crate, which is why there were no tests for so long.
- **`llama.rs` stubs its C FFI off Android** (`#[cfg(not(target_os = "android"))] mod host_stubs`).
  `build.rs` only compiles llama.cpp for Android, so on the host those symbols do not exist and the
  test binary fails to link with eight `LNK2019`s.

**Every test in there is a bug that actually shipped**, and that is the bar for adding one: write
the test when a real defect is found, and confirm it fails against the unfixed code before
committing the fix. A test that has never been seen to fail proves nothing. Reproduce first, then
fix, then re-run.

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

### Receipt parsing (OCR → Gemma → per-item transactions)

`ReceiptScanScreen` → `ReceiptViewModel` → `ReceiptOcrRepository` (ML Kit) + `GemmaRepository`.

- OCR text plus the user's existing expense categories are fed to `GemmaRepository.parseReceipt(text, categories)`, which prompts Gemma for **one JSON object per product line**: `ParsedReceipt(store, date, total, items: List<ParsedItem>)` where `ParsedItem(name, price, category)`.
- Runs with `nCtx=2048` / `nPredict=512` (larger than the old single-total prompt), JSON primed with a leading `{`. `parseReceiptJson` first tries a strict parse of the whole object, then falls back to regex-extracting fields + individual item objects — the Q4 model often emits slightly malformed/truncated JSON on long receipts.
- The prompt pipe-joins existing category names so the model reuses them instead of inventing near-duplicates; it may coin a short new English category only when nothing fits.
- **Per-item auto-categorization** reuses the (otherwise dormant) `GemmaRepository.suggestCategory(name, categories)` classifier: after the preview populates, `ReceiptScanScreen` sequentially fills any item the scan left blank, and each row has an `AutoAwesome` wand button to (re)generate its category on demand. Both go through `ReceiptViewModel.suggestCategory(name)`, which is guarded by a `Mutex` (`inferenceMutex`) — there's one native engine, so suggestion `generate()` calls must be serialized. The prompt is **prefer-existing**: it lists current categories and tells the model to reply with one of them *exactly* if it fits, and only invent a short new English name when none does (forcing a pick from the list produced bad matches like "steak" → "Services"). The answer is then matched case-insensitively to an existing category, else the model's raw answer is used (and auto-created at save time). `EditableItem.suggesting` drives a per-row spinner.
- `ReceiptViewModel.confirmAndCreate(...)` creates **one transaction per edited item**, matching each item's category case-insensitively against existing categories and auto-creating any missing one (icon `shopping_bag`, color chosen from a palette by name hash). The store name is saved as the transaction note. `State`: `Idle → OcrRunning → AiRunning → Preview → Saving`.
- `AddTransactionScreen` has a `DocumentScanner` icon in the top bar (top-right, next to the close button) that navigates straight to `Screen.ReceiptScan` — a shortcut for when the user realizes they'd rather scan a receipt than type a transaction by hand.
- **The same `AutoAwesome` wand is on `AddTransactionScreen`**: `TransactionViewModel.suggestCategory(title, categories)` mirrors the receipt version (own `inferenceMutex`, prefer-existing-else-capitalized-new) and drives `selectedCategory` from the transaction title. It's passed the expense/income category list per the current toggle. Note: the button is disabled until a title is entered (the title field sits below the category field), and a newly-invented category is stored as the transaction's category label but not auto-added to the managed category list.
- **`AddTransactionScreen` split mode** (a `Switch`, default off): flips the single category+title fields for an editable item list (`EditableLineItem` + private `LineItemRow`, each with the ✨ wand) plus a live Σ-vs-total remaining indicator. The amount field becomes the receipt total (reference only — a mismatch warns but doesn't block). Saving calls `TransactionViewModel.createSplitTransactions(...)`, which — like the receipt `confirmAndCreate` — creates **one transaction per item**, auto-creating missing categories (icon `shopping_bag`, `isExpense = !isIncome`) and applying the shared wallet/date/note/tags. Single mode is unchanged.

### Master AI switch

`ai_enabled` in `PreferencesRepository` — **defaults to `true`**, because AI features already shipped and an app update must not silently remove them.

- **Read from two places:** `SettingsViewModel.aiEnabled` (for ordinary screens) and `GemmaUiState.aiEnabled` (for `AiModelScreen`, which owns the switch). Screens needing to hide an AI control inject `SettingsViewModel` — don't reach for `GemmaModelViewModel` just to read this flag.
- **Turning it off** (`GemmaModelViewModel.setAiEnabled(false)`) unloads the model immediately — reclaiming the ~2.7 GB is the main reason to flip it. The model *file* is deliberately left on disk and `ai_auto_load` keeps its value, so re-enabling restores the previous setup (and reloads if auto-load was on).
- **Everything gated on it:** the receipt-scan entry points in **both** `AddTransactionScreen` (top-bar `DocumentScanner`) and `DashboardScreen` (add-sheet "Skenuoti čekį" card — easy to miss), the ✨ wand in `AddTransactionScreen` single mode and in `LineItemRow` (passed down as an `aiEnabled` param), and — in `AiModelScreen` — the inference-engine, auto-load, and test cards plus the one-time auto-load prompt. `LedgerApp.maybeAutoLoadModel` also bails out early.
- **Deliberately NOT gated:** the `SettingsScreen → AI Modelis` entry (it's the only way back to the switch) and the model-file card with its Delete button (someone turning AI off usually wants the 2.7 GB back).
- `ReceiptScanScreen` itself has no guard — with both entry points hidden the route is unreachable. Add one if a third entry point ever appears.

### Receipt parse failure

`GemmaRepository.parseReceiptJson` returns a `ParsedReceipt` with an empty `items` list when the model emits garbage even the regex fallback can't salvage. `ReceiptViewModel` detects that (`aiFailed = receipt.items.isEmpty()`) and passes it through `State.Preview`, so `ReceiptScanScreen` shows a warning card and the heading changes to "Enter products" — rather than an empty form that looks like a clean scan. The existing `LaunchedEffect` already seeds one blank row, and the auto-categorize loop skips blank names, so no wasted inference.

### AI auto-load

Opt-in preference to warm the model into memory automatically. Keys in `PreferencesRepository`: `ai_auto_load` (the toggle) and `ai_auto_load_prompted` (whether the one-time prompt has been shown).
- **Startup:** `LedgerApp.onCreate` reads `aiAutoLoad` via a Hilt `EntryPoint` (`LedgerAppEntryPoint`, extended with `preferencesRepository()` + `gemmaModelRepository()`) and, if the model file is `Ready`, loads it on an application-scoped IO coroutine — best-effort, failures swallowed. `onTrimMemory` still unloads under memory pressure.
- **Toggle + one-time prompt:** `AiModelScreen` shows a `Switch` (→ `GemmaModelViewModel.setAutoLoad`) when the model is Ready, plus a one-time `AlertDialog` (`enableAutoLoadFromPrompt` / `dismissAutoLoadPrompt`) offering to enable it.

---

## Home Screen Widgets

Three Glance widgets live in `widget/`: **QuickAdd** (4×1), **DailyAllowance** (2×2), **Streak** (2×2).

### Widgets never touch the Rust bridge

`WidgetSnapshotRepository` owns a **separate DataStore file** (`ledger_widget`) holding everything the
widgets render — balances, today's spend, daily allowance, streak, week grid, top categories — plus a
copy of `currency_code` / `number_format_index` so a widget reads exactly one store. A widget refresh
is therefore a preference read, not a SQLite open from a broadcast receiver, and widgets stay correct
when the app process is cold.

Amounts are stored **with the currency code they were computed in**. When multi-currency wallets land,
only `WidgetUpdater` changes; no widget code does.

`WidgetUpdater.refresh()` reads the bridge, recomputes the snapshot, writes it, and calls
`updateAll()` on all three widgets. It is called from:
- `TransactionViewModel` (create / split / update / delete), `BudgetViewModel` (budgets set the allowance),
  and `ReceiptViewModel.confirmAndCreate`
- `LedgerApp.onCreate` — covers imports, due recurring transactions, and the day rolling over
- `LedgerWidgetReceiver.onUpdate` (shared base) via `goAsync()` — covers a freshly placed widget and
  the 30-minute `updatePeriodMillis` on the two data widgets (QuickAdd uses `0`; it has no clock-sensitive data)

### Shared computation, not a second implementation

`ui/util/StreakCalculator.kt` (`computeStreakStats`, `StreakStats`, `CategoryPace`, `DayState`) is
used by **both** `SpendingStreaksScreen` and `WidgetUpdater`, so the widget can never disagree with
the screen. It takes `(transactions, budgets, categories, today)` — categories are required to map
`Budget.categoryId` to the category *name* that transactions carry.

**Four rules the allowance maths follows.** Each one fixes a way the number used to lie, and each was
caught by a real number on screen — check against `files/datastore/ledger_widget.preferences_pb` on
device when something looks off.

1. **Every budget is paced inside its own period window** (`BudgetPeriod.start/end/lengthInDays`):
   weekly against Mon–Sun, monthly against the calendar month, yearly against the year. Flattening a
   weekly limit into a month turned "2030 per week" into "8990 per month" and then spread it over the
   remaining days of the *month*. `monthlyEquivalent` exists only for adding mixed periods together
   in a headline figure — never for pacing.
2. **Only spending in budgeted categories counts against the allowance.** The limit is built from
   categories that *have* budgets, so charging unbudgeted spending against it made a user go negative
   without breaking any actual budget. Unbudgeted spend is surfaced separately
   (`StreakStats.unbudgetedToday`) rather than hidden.
3. **Two allowance figures, deliberately:**
   - `staticDaily` / `dailyAllowance` — `limit ÷ daysInPeriod`. A *stable* bar, which is what judging
     a past day for the streak needs, and the baseline the widget compares against.
   - `todayDaily` / `todayAllowance` — depends on `AllowanceSettings`:
     - **rollover on** (default): `(windowBudget − spentInWindowBeforeToday) ÷ daysLeftInWindow`.
       Symmetric — a cheap day funds tomorrow, an expensive one bites into it.
     - **rollover off**: `min(staticDaily, …)`, so the figure can only ever move *down*.

   `AllowanceSettings(rollover, window)` comes from the `allowance_rollover` / `allowance_window`
   preferences (`weekly` | `monthly`, defaults `true` / `monthly`), edited on the Left Today card in
   `WidgetSettingsScreen`. The window is **independent of `Budget.period`** on purpose — a monthly
   budget can still be lived week to week. Budget limits are pro-rated into the window via
   `staticDaily × daysInWindow`, which is what makes mixed periods work in either direction.

   **Show the per-day effect, never the carried pool.** `StreakStats.carriedIntoToday` is the whole
   surplus (5 unspent days of a 2030/month budget = 327), but it is spread across the days that
   remain, so today only gains 12.60. The widget renders `todayAllowance − baseDaily`; printing 327
   next to a daily figure reads as if all of it were spendable now.
4. **The streak's two loops must cover the same window.** The current-streak `while` used an
   inclusive `today − STREAK_LOOKBACK_DAYS` bound (181 days) while the best-streak `for` ran exactly
   180, so a perfect record reported current 181 / best 180.

### Answering "can I still spend on groceries?"

A single blended figure can't, so the Left Today widget attacks it from two directions.

**`StreakStats.tightestCategory`** returns the `CategoryPace` furthest through its own limit —
deliberately **not** gated on `isAlerting`, because a widget that only speaks up once you're already
over is the problem rather than the fix. `isAlerting` drives colour only. It always yields at most
one category, so the widget reads the same with 3 budgets or 30.

**Per-instance tracking.** Each placed Left Today instance stores what it tracks in Glance state
(`DailyAllowanceWidget.KEY_TRACKS`, `TRACK_ALL` = the summary), read via
`getAppWidgetState(context, PreferencesGlanceStateDefinition, id)`. A shared preference would change
every placed copy at once, which defeats placing two. Because the snapshot can't know which instance
wants which category, it carries `categoryAllowances` for **every** budgeted category.

`AllowanceWidgetConfigActivity` is the `android:configure` target (plus
`android:widgetFeatures="reconfigurable"` so it can be reopened from a long-press). It sets
`RESULT_CANCELED` up front — backing out must not leave a stranded widget — resolves the
`appWidgetId` to a `GlanceId` via `GlanceAppWidgetManager.getGlanceIdBy`, writes with
`updateAppWidgetState`, then calls `update()` on that one instance. It runs `widgetUpdater.refresh()`
first, since a widget can be placed before the app has ever been opened.

A tracked category that is later renamed or deleted falls back to the summary rather than rendering
an empty widget.

`BudgetsScreen` consumes the same `categoryPaces`, so each card shows its **own period's** spend
against its own limit (it used to compare a month's spending to a weekly limit) and labels the period
on the card. The overview total switches to "MONTHLY EQUIVALENT" whenever any budget is non-monthly,
because the headline is then a converted figure rather than what the user typed.

`ui/util/MoneyFormat.kt` (`formatAmount`, `formatAmountCompact`, `currencySymbol`) is the first place
`currency_code` is actually honoured — most screens still hardcode `"$%,.2f"` and should migrate to it.
`SettingsScreen` now has a real currency picker (`CurrencyPickerDialog`); `SettingsViewModel.setCurrency`
and `setNumberFormatIndex` push a widget refresh because the snapshot caches both.

### The widget's one adaptive line

`DailyAllowanceWidget.Footnote` picks one of three messages, most urgent first: a category at/over its
alert threshold → unbudgeted spending today → plain "of $X". Keeping it to a single line is what lets
a 2×2 carry per-category information at all.

### Quick Add shortcut categories

`WidgetSnapshotRepository.pinnedCategories` (empty = automatic) holds the user's picks, capped at
`MAX_PINNED_CATEGORIES`. `WidgetUpdater` prefers them, drops any that no longer exist, and falls back
to the most-used categories of the last 60 days. `write()` never touches that key or `hideAmounts`, so
both survive every refresh. The picker lives in the Quick Add card of `WidgetSettingsScreen`; picking
past the cap drops the oldest so chips stay tappable. **Any future multi-category widget should reuse
this pinned-or-automatic-with-a-cap pattern** rather than trying to fit an unbounded category list.

### Deep links

Widgets open the app through a URI, **not** intent extras: `PendingIntent.filterEquals` ignores extras,
so buttons differing only by extra collapse into one PendingIntent and every button opens the same
screen. `widgetRouteIntent(context, route)` builds `ledger://open?route=<encoded>`; `MainActivity`
(now `launchMode="singleTop"`) reads it in `onCreate` **and** `onNewIntent` into a `pendingRoute` flow
that a `LaunchedEffect` navigates on, wrapped in `runCatching` so an unknown route can't crash launch.

`Screen.AddTransaction` is now `add_transaction?category={category}` — **navigate via
`createRoute()`, never `.route`**, which carries the literal placeholder.

### WidgetSettingsScreen

Rewritten from a fictional on/off list (Android widgets are placed from the launcher, not toggled in
app settings) into: a real `hide amounts` preference, live previews driven by the same snapshot the
widgets render, and per-widget `AppWidgetManager.requestPinAppWidget` buttons (API 26+, exactly our
minSdk). Launchers may refuse the pin request, so the long-press instruction stays on screen.

## Wallet balances

`wallets.balance` is a **stored running total**, not a sum computed on read. Anything that changes a
transaction or a transfer must therefore move the balance too, and two paths did not:
`delete_transaction` left the removed amount in the balance forever, and `update_transaction` kept
the old figure after an edit. Both now reverse the previous effect before applying the new one.

**Every money mutation runs inside a `pool.begin()` transaction.** An insert plus a balance update,
or a transfer's two balance updates, must land together or not at all — a half-applied write is
silent corruption in a finance app. `resolve_category_id` is deliberately called *before* the
transaction opens: a stray category left by a failed insert is harmless, an unbalanced wallet is not.

`create_transfer` also checks both wallets exist first, because nothing enforces the foreign keys at
runtime (see the cascade note below).

## Schema migrations

`core/src/db/mod.rs` owns a `schema_version` table and numbered migrations (`m1_baseline_tables`,
`m2_category_links`, `m3_indexes`, `m4_transfers_currency_budgets`, `m5_off_budget_wallets`,
`m6_transaction_occurred_at`). Add the next one as `mN_…` plus an `if applied < N` arm.

**Every migration must be idempotent, without exception.** Databases predating the version table
bootstrap at 0 and re-run everything, and that property is what let the table be introduced
mid-life at all. In practice: `CREATE … IF NOT EXISTS`, `UPDATE … WHERE <not already done>`, and
for `ALTER TABLE … ADD COLUMN` (which has no `IF NOT EXISTS` in SQLite) swallow the error.

**Do not edit a migration that has shipped.** `m1` still creates the *old* `budgets` shape; `m4`
reshapes it. That is deliberate — a fresh database replays history and lands in the same state as
an upgraded one, which is the only way the two stay comparable.

**Relaxing `NOT NULL` needs a table rebuild.** SQLite cannot `ALTER` it away.
`rebuild_budgets_if_needed` creates the new table, copies, drops, renames — guarded by
`PRAGMA table_info`, which is what makes it idempotent. Verified to preserve rows.

## Transfers

A transfer is neither income nor expense. Folded into `transactions` it would show as an expense in
one wallet and income in the other, and **every report would count it twice**. It gets its own
table, which also matches Money Manager's backup shape so imports map one-to-one.

`create_transfer` moves balance between both wallets and `delete_transfer` puts it back; neither
touches income or expense totals. Deleting a wallet deletes transfers on either side of it.

`AddTransferScreen` (+ `TransferViewModel`, route `Screen.AddTransfer`) is reached from the swap
icon in the WalletsList top bar. It shows both balances *after* the move so an overdrawing transfer
is visible before saving, and refuses the same wallet on both sides. Transfers are not listed
anywhere yet — created but not viewable or deletable from the UI.

## Off-budget wallets

`wallets.off_budget` keeps an account out of budgets, the daily allowance and the streak, while it
still counts toward net worth. It exists because an overall budget covers *everything* by
definition, so a work or investment account's activity would otherwise eat the personal budget.

**The flag lives on the wallet, not on the budget.** "This account should not count" is a property
of the account: a work account stays out however many budgets exist, and a new personal wallet joins
automatically. Scoping per budget would need a join table and re-editing every budget whenever an
account is added — and `budgets.wallet_id` (which narrows one budget to one wallet) cannot express
"my personal spending lives across three wallets".

`computeStreakStats` takes `offBudgetWalletIds` and filters expenses and streak days by it; callers
pass `wallets.filter { it.offBudget }`.

**Analysis screens call `rememberReportTransactions(txState)`, never `txState.transactions`.** That
helper applies the `reports_include_off_budget` preference (default off) in one place — eight
screens each re-deriving the rule is how they drift apart. `TransactionUiState.transactions` stays
complete on purpose: editing, search and the transaction list must be able to reach an off-budget
row by id, so filtering at the source would have hidden rows from screens that must show them.

The toggle lives in Settings → Reports & Insights, and `WalletsListScreen` marks excluded accounts.

## Wallet currency

`wallets.currency` exists as of `m4`. Before that the Money Manager import passed the account's
currency code as the wallet **description**, so every imported wallet was described as "EUR"; the
migration moves any three-uppercase-letter description into the new column. New wallets take the
base currency preference. Conversion between currencies is not implemented — the column records
what a wallet holds, nothing more.

## Budgets

Three scopes, decided by which ids are set:

| `category_id` | `wallet_id` | Meaning |
|---|---|---|
| null | null | **Overall** — caps everything you spend. Drives the daily allowance |
| null | set | Overall, narrowed to one wallet |
| set | — | A category limit. Paces that category; never sets the allowance |

**Only the overall budget produces the daily allowance.** Category budgets used to be *summed* into
a total, which invented a figure nobody chose — two budgets of 2000/week and 30/week implied a
"monthly budget" of 8990 — and it left every purchase outside those categories invisible to the
allowance. They are limits on their own domain, not slices of a whole, so they are no longer added
up. With no overall budget there is simply no allowance, and the widget falls back to showing
balance. `StreakStats.overall` (an `OverallPace`) carries the figure.

**`carry_over` and `allowance_rollover` compose; they do not double-count.** `carry_over` moves the
*previous period's* residual into this period's ceiling; `allowance_rollover` redistributes this
period's ceiling across its remaining days. Spend 800 of an August 1000 with both on, and September's
ceiling is 1200, which September's rollover then spreads over September. Carry-over reaches back
exactly **one period**, and only if the budget already existed for it — without that guard a fresh
200/month budget inherited the previous month's 2644 of spending and opened at minus 2244, reported
as "2692 over" on the day it was created.

**There can be only one overall budget.** "At most X in total" is a single number; a second is a
contradiction, and it used to be accepted and then silently ignored. `create_budget` now refuses it.
Legacy data may still hold several — the **newest** counts as the current intent, and
`BudgetsScreen` lists the others as unused so none sits there doing nothing invisibly.

Chaining carry-over further back would walk unbounded history for a number nobody could
trace — and it is symmetric, so an overspent period reduces the next.

Unique indexes now cover `categories(name, is_expense)` and `budgets(category_id, wallet_id,
period)`. The latter matters because `CategoryPace` **sums** every budget for a category, so
duplicates silently inflated the limit. NULLs compare distinct in SQLite, so several wallet-only
budgets can still share a period.

`BudgetsScreen` renders the overall budget as its own "Everything" row and leads the overview card
with it; when there is none it says there is no daily allowance rather than presenting the sum of
category limits as if it were a total. A **wallet-scoped** overall budget
(`category_id = null, wallet_id = X`) is supported by the data layer but the scope selector only
offers Overall or Category.

## How transactions link to categories

A transaction stores **both** `category_id` (the link) and `category` (the label it was filed under).

Originally it stored only the name, so renaming a category left every transaction pointing at a
string nothing owned any more — 93 transactions in a real database were stranded on "Servicez" after
it was renamed to "Services". Everything downstream matches by name (`groupBy { it.category }` in
eight screens, plus `CategoryPace`), so a budget on the renamed category would have matched nothing
and the widget would have counted that spending as unbudgeted.

- **Reads** resolve the name through the link: `COALESCE(c.name, t.category)` via `TX_SELECT` in
  `core/src/lib.rs`. A rename therefore shows up everywhere with no extra work.
- **Writes** still take a category *name* — the whole app works that way. `resolve_category_id`
  matches it case-insensitively **on name *and* `is_expense`**, and creates the category when it is
  genuinely new. Matching on name alone is wrong: a Money Manager import legitimately produces the
  same name in both directions (Gifts, Other, NEATITIKMUO), and 175 expense transactions ended up
  linked to the income twin — invisible until that category was renamed or deleted. Note this also
  changed `AddTransactionScreen` single mode: an invented category name now joins the managed list
  instead of existing only as a loose label.
- **`recurring_transactions` gets the identical treatment** — it stored a bare name too, so a rename
  skipped it. `RECURRING_SELECT` resolves through the link the same way.
- **Rename** (`update_category`) refreshes the stored label too, so the fallback stays current.
- **Delete** (`delete_category`) clears `category_id` and keeps the label, so history still reads as
  what it was filed under. It does *not* rely on `ON DELETE SET NULL` — SQLite enforces foreign keys
  only under `PRAGMA foreign_keys=ON`, which this pool does not set.
- **Migration** (`migrate_transaction_categories`) adds the column, backfills it by name, and
  recreates a category for any name left without one. `ALTER TABLE … ADD COLUMN` has no
  `IF NOT EXISTS` in SQLite, so the error from re-running it is deliberately ignored.

The UDL is unchanged — `Transaction.category` is still a string — so this needed **no UniFFI
regeneration**, only a rebuilt `.so`.

### Every `ON DELETE CASCADE` in this schema is inert

SQLite honours foreign keys only under `PRAGMA foreign_keys=ON`, and this pool never sets it. The
three cascades declared in `db/mod.rs` (`transactions.wallet_id`, both columns of
`transaction_tags`) therefore did nothing, and deleting a wallet left its transactions behind —
still counted by every report while belonging to an account that no longer existed.

**Each `delete_*` cleans up explicitly instead.** Deleting a wallet removes its transactions, their
tag links and its recurring rows; deleting a transaction or a tag removes the links; deleting a
category detaches its transactions and recurring rows and deletes its budget (a budget whose
category is gone can never be seen or edited again, because every screen resolves it through the
category). `clean_orphans` sweeps anything earlier builds already stranded.

Enabling the pragma was considered and rejected: it would turn loose historical rows into hard
write failures at runtime, and explicit cleanup is deterministic and testable. **If you add a table
with a foreign key, write the cleanup into the delete path — do not rely on the declaration.**

## Data Entities

Defined in `core/src/ledger.udl` and mirrored as Kotlin data classes in `ledger.kt`:

| Entity | Key fields |
|---|---|
| `Transaction` | id, wallet_id, title, category, amount, is_income, note, **occurred_at** |
| `Wallet` | id, name, description, currency, balance, off_budget, created_at |
| `Transfer` | id, from_wallet_id, to_wallet_id, amount, note, created_at |
| `SavingsGoal` | id, name, current_amount, target_amount, deadline, created_at |
| `Category` | id, name, icon_name, color_hex, is_expense, created_at |
| `Budget` | id, category_id?, wallet_id?, limit_amount, period, alert_threshold, carry_over, created_at |
| `Debt` | id, name, debt_type, total_amount, remaining_amount, apr, monthly_payment, created_at |
| `RecurringTransaction` | id, title, amount, category, wallet_id, is_income, frequency, next_date, created_at |
| `Tag` | id, name, created_at |
| `PriceAlert` | id, symbol, asset_name, target_price, direction, active, created_at |
| `MonthSummary` | total_income, total_expenses, net_savings, transaction_count |

### `occurred_at` vs `created_at`

A transaction has two dates and only one of them is a fact about money. `occurred_at` is when the
spending happened; `created_at` is when the row was written. They diverge on every import (a bank
statement from March, imported in August) and on every back-dated manual entry, and until `m6` the
single `created_at` column was carrying both meanings — so a bulk import would pile the whole
statement onto today and wreck every report at once.

**Every report, chart, streak, budget and widget reads `occurred_at`.** The FFI deliberately exposes
*only* `occurred_at`; `created_at` stays in the table as an audit trail with no Kotlin binding,
because having both visible is exactly what let the meanings blur. Reads go through `TX_SELECT`,
which is `COALESCE(t.occurred_at, t.created_at)` so a row written before the migration still sorts
correctly. `update_transaction` uses `COALESCE(?, occurred_at, created_at)`, so passing null edits a
transaction without silently moving its date to today.

Other entities keep a plain `created_at` and it means what it says — notably `budget.created_at`,
which the carry-over guard in `StreakCalculator` uses to refuse to roll a budget over into months
that predate it.

**Never order transactions by `occurred_at` alone.** It is a date, so everything entered on the same
day ties, and SQLite is free to return ties in any order — a just-saved transaction landed somewhere
arbitrary among the day's other rows, which on a busy day means below the fold and reads as "the app
didn't save it". `TX_ORDER` is the single ordering both list queries use:
`COALESCE(occurred_at, created_at) DESC, created_at DESC, id DESC`. The `created_at` tiebreak is a
second reason the column earns its place in the table, and the total order is also what makes
`LIMIT`/`OFFSET` paging safe — without it a row can appear on two pages or on none. Covered by
`newest_transaction_is_first_among_the_same_day` and `paging_never_repeats_or_drops_a_row`.

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
- App icon: an ivory serif **L** with a mint leaf on a forest-green tile, transcribed from
  `ledger_logo_14E_refined_final.svg` into three vectors — `drawable/ic_launcher_background.xml`
  (linear gradient `#003F32 → #00513F → #096A55` plus a mint radial glow),
  `ic_launcher_foreground.xml` (the mark), `ic_launcher_monochrome.xml` (Android 13+ themed icon).
  **Paths keep the source SVG's own coordinate space** (482pt tile) and one shared `<group>`
  (`scale 0.149378 · translate 13.967,14.49`) maps that tile onto the 72dp the launcher shows, so
  the artwork keeps its designed proportions and the mask replaces the source's rounded rect. That
  puts the furthest point of the mark 31.4dp from centre, inside the 33dp safe radius. **Keep the
  group identical in all three files** — the themed icon drifts out of register otherwise. Editing
  the artwork means re-transcribing from the SVG, not nudging numbers here.
  Two things in the source are deliberately dropped: the inset border stroke and the top-edge rim
  light, both of which hug a tile edge that no longer exists under a mask. The drop shadow survives
  as the source's own offset copies at `fillAlpha` 0.27/0.30 — vector drawables have no blur.
- UI text is in **English** across the app, including the receipt-scan flow and the widgets — **exception:** `AiModelScreen` (including the auto-load toggle/prompt) and its two entry points are in Lithuanian: the `DashboardScreen` add-sheet "Skenuoti čekį" card and the `SettingsScreen` "AI Modelis" row. Match English for anything new unless it sits inside the AI-model screen.

---

## Common Pitfalls

1. **Vulkan GPU build requires MSVC + short CARGO_TARGET_DIR** — `vulkan-shaders-gen` is a host-side tool; NDK's clang can't build it on Windows (cross-compiler only). Fix: install MSVC Build Tools so `detect_host_compiler()` finds `cl.exe`. Also set `CARGO_TARGET_DIR=C:\lt` — the default `target/` path hits Windows' 260-char limit during cmake sub-project compilation. Additionally: NDK sysroot lacks `vulkan.hpp` and `spirv/unified1/spirv.hpp`; these come from `third_party/Vulkan-Headers` and `third_party/SPIRV-Headers` (added as `-I` flags in `CMAKE_CXX_FLAGS` in `build.rs`). Build script: `build-rust-android-vulkan.ps1`.

1b. **Vulkan runtime pitfalls** (branch `vulkan-experiment`):
   - **`GGML_VULKAN` must be defined for the `cc` bridge too.** The cmake define for llama.cpp does NOT propagate to the separate `cc` compile of `llama_simple.cpp`. If missing, the bridge silently compiles its CPU `#else` branch and never attempts GPU. Fix: `bridge.define("GGML_VULKAN", None)` in `build.rs` (gated on `vulkan_feature`).
   - **`n_gpu_layers=0` does NOT give a CPU-only ggml scheduler.** With default `main_gpu=0`+`split_mode=LAYER`, llama.cpp still adds the GPU to `model->devices`, so the scheduler routes ops to it and `llama_decode` throws. For strict CPU, set `mp.split_mode=LLAMA_SPLIT_MODE_NONE` + `mp.main_gpu=-1` (triggers `model->devices.clear()`, see llama.cpp `src/llama.cpp:252`).
   - **`GGML_BACKEND_DL=OFF`** + `cargo:rustc-link-lib=static=ggml-vulkan`, else `ggml_backend_vk_reg` → UnsatisfiedLinkError.
   - **Adreno fails `createComputePipeline: ErrorUnknown`** at runtime. Handled by a load-time 1-token `warmup()` in `llama_simple_create`: it forces pipeline compilation early, catches the C++ exception (must NOT cross the C FFI boundary — wrap in try/catch), and calls `reload_as_cpu()`. This moves the ~11s reload out of the first scan and pre-faults mmap'd weights (first-scan decode 4.9→13.4 tok/s).
   - **GPU offload is a CONFIRMED DEAD END on Adreno 740 / Qualcomm proprietary driver** (S23). Diagnostics pinpointed the failing kernel: `mul_mat_vec_q4_k_f32_f32` (the mandatory Q4_K decode matmul). Disabling fp16 (`GGML_VK_DISABLE_F16`) was tried and ruled out — the pure-f32 variant fails too. It's a driver compiler bug on llama.cpp's quantized shaders, not a config issue (coopmat/int-dot/bf16 were never built — NDK glslc lacks the extensions). Only Mesa **Turnip** via adrenotools would work, which is out of scope. The app correctly attempts GPU → warms up → falls back to CPU.
   - **Diagnosing Vulkan failures requires un-silencing two log streams.** The bridge had a no-op `llama_log_set` callback AND Android drops `std::cerr` (where ggml-vulkan prints `"Compute pipeline creation failed for <shader>"`). `llama_simple.cpp` now forwards ggml logs to logcat (`ggml_log_forward`, tag `LlamaGGML`), pipes `stderr`→logcat via a reader thread (`redirect_stderr_to_logcat`, tag `LlamaStderr`), and dumps `ggml_backend_dev_*` after init (`log_backend_devices`, tag `LlamaSimple`). These are the tools to re-test any future driver.

2. **Stale `.so` crash** — After any Rust API change, must rebuild `.so` AND regenerate Kotlin bindings. The UniFFI checksum validation runs on every app start.

2b. **Renaming a UDL field is a search tool — use it, don't shortcut it.** `Transaction.created_at` → `occurred_at` broke 95 Kotlin lines, and that list *was* the audit: every place that had been quietly treating write-time as spend-time. A repo-wide find-and-replace of `createdAt` would have been catastrophic, because `Wallet`, `Budget`, `Category`, `Goal` and `Debt` all still have a `createdAt` that legitimately means write-time. Fix only the lines the compiler names, then re-compile and repeat until clean; a clean build is then proof that nothing else was touched.

2. **`Flow<T>=` parse error** — When a property type is a generic (`Flow<String>`, `Flow<Boolean>`), always put a space before `=` in assignments. `Flow<String>=` is parsed by Kotlin as `>=` (greater-than-or-equal).

3. **`GemmaModelRepository` thread** — `downloadModel()` must run on `Dispatchers.IO` (it's a `flow {}` builder, caller is responsible). `computeSha256` is CPU-bound — also runs on IO in the download flow.

4. **UniFFI blocking** — All Rust methods are synchronous from Kotlin's perspective (they block using `rt.block_on`). Always call bridge methods from `viewModelScope.launch(Dispatchers.IO)`.

5. **SeedDataUtil** — Seeds fake "Alex Johnson" data on every fresh install. Must be gated or removed before production release.

6. **Edit tool requires prior Read** — When editing any file, the Read tool must be called first in the same conversation.

7. **Soft keyboard covering input fields** — the app is edge-to-edge (`enableEdgeToEdge()` → `decorFitsSystemWindows=false`), so the manifest's `windowSoftInputMode="adjustResize"` does NOT resize the Compose content and the IME overlaps bottom fields. Fix is global: `NavHost` carries `Modifier.fillMaxSize().imePadding()` in `NavGraph.kt`, so every screen's content lifts above the keyboard and focused `TextField`s scroll into view. Don't re-add `imePadding()` per screen (double padding).

8. **Glance APIs are split across packages, and the compiler error is unhelpful.** Three that cost a build each:
   - `ColorProvider(day = …, night = …)` is in **`androidx.glance.color`** (`DayNightColorProviders.kt`), not `androidx.glance.unit` (that one only takes a single color or a resId) and not `androidx.glance.appwidget.unit` (that file is about checkable colors). Its return type *is* `androidx.glance.unit.ColorProvider`.
   - `actionStartActivity(intent: Intent)` is in **`androidx.glance.appwidget.action`**. The `androidx.glance.action` version takes a `ComponentName`/`Class`, so importing the wrong one reports "actual type is Intent, but ComponentName was expected".
   - `defaultWeight()` is a **member of `RowScope`/`ColumnScope`**, so it cannot be used inside a helper extension function declared outside the layout lambda. Branch inline: `(if (wide) GlanceModifier.defaultWeight() else GlanceModifier.fillMaxWidth())`.

9. **Glance cannot reuse the app's theme or icons.** No Material3 `MaterialTheme`, no `ImageVector` from `material-icons-extended`. Colors are re-declared in `widget/WidgetTheme.kt`; icons must be vector **drawables** (`res/drawable/ic_widget_*.xml`) loaded via `ImageProvider(R.drawable.…)`. Category shortcuts deliberately render as text chips rather than mapping all 18 `categoryIconNames` to new drawables.

10. **Widget PendingIntents ignore extras.** Two widget buttons whose intents differ only in extras resolve to the same PendingIntent, so both open whatever the first one requested. Encode the destination in the intent's **data URI** (`ledger://open?route=…`) — that *is* compared by `filterEquals`. See the widgets section above.

11. **Money Manager's built-in categories have an empty `title`.** Their identity lives in the `uid` (`DefaultCafe`, `DefaultProducts`, `DefaultHome`, `other_expense`, …) and the displayed name is localised at runtime by that app — it is never written to the database. Reading `category.title` verbatim silently drops all 15 defaults and collapses their transactions into one bucket: on a real 1993-transaction backup that was **1110 transactions**, with Groceries (413) and Cafe (209) merged together. `ImportViewModel.defaultCategoryNames` maps the uids back to names; verify any additions against the `position` column, which orders the list exactly as the app renders it. Note that the transaction→category link lives in `sync_link` (`entityType='Transaction'`, `otherType='Category'`), not on the transaction row.

12. **The manifest must point at `@mipmap/ic_launcher`, not at the foreground drawable.** It pointed
    at `@drawable/ic_launcher_foreground`, so the launcher rendered the bare foreground on a
    transparent tile and the `adaptive-icon` XMLs in `mipmap-anydpi-v26/` — background, masking,
    themed icon — were dead files that nothing ever read. Editing them looks like it does nothing.

13. **Category names are normalized + deduped** — always create/edit categories through `CategoryViewModel`, which uses `ui/util/CategoryName.kt`: `capitalizeFirst` for LIVE text input (no trim, so spaces are still typable) and `normalizeCategoryName` (trim + capitalize) at save. `create`/`updateCategory` reject case-insensitive duplicates with a friendly error surfaced on the name field. The receipt path (`ReceiptViewModel.confirmAndCreate`) normalizes the same way and already matches existing categories case-insensitively before auto-creating.

---

## Development Roadmap

See `TODO.md` for the full prioritized list. High-level phases:

1. ~~**ML Kit OCR** — Camera + gallery receipt scanning~~ ✅ done
2. ~~**Gemma AI pipeline** — OCR → parse → editable preview → save transaction~~ ✅ done, extended with per-item parsing, category-suggestion wand, split-item transactions, and opt-in AI auto-load (see [AI / Gemma Integration](#ai--gemma-integration) above)
3. ~~**Home screen widgets** — Quick Add, Left Today, Streak~~ ✅ done (see [Home Screen Widgets](#home-screen-widgets)). Remaining widget ideas live in `TODO.md`: AI insight of the day, goal progress, upcoming bills, net worth, month heatmap
4. **Transaction splitting (wallets)** — Divide one transaction across multiple wallets (note: splitting across *categories/items* is already done — see above; this is splitting one purchase across multiple *funding wallets*)
5. **Shared expenses** — Group expense splitting (needs SharedExpenseViewModel + Room entity... or Rust entity)
6. **Automatic backups** — WorkManager daily/weekly export + optional Google Drive
7. **Real notifications** — WorkManager background checks for budget/bill/balance alerts
8. **Biometric / PIN lock** — Wire `BiometricPrompt` to security settings toggles
9. **Live currency rates** — `exchangerate.host` or `frankfurter.app` API
10. **Wallet transfers** — Dedicated transfer type in Rust schema
11. **Onboarding wizard** — Replace SeedDataUtil for real users
12. **Tests** — Unit tests for ViewModels + DAOs once schema stabilises
