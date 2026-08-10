# Ledger — Project To-Do List

## Real Data / ViewModels

Screens needing ViewModel integration. Unchecked ones still use hardcoded or local-only state:

- [ ] **NotificationsScreen** — rewrite with real data (budget alerts, goal milestones, recurring due dates, wallet balance warnings). Push notifications (system-level Android) deferred separately.
- [ ] **SharedExpensesScreen** — hardcoded group/expense list; needs a SharedExpenseViewModel + Room entity
- [ ] **EditProfileScreen** — placeholder name "Alex Johnson" / email; needs a UserProfileViewModel or DataStore
- [x] **AppearanceSettingsScreen** — wired to `SettingsViewModel`; theme/accent/density/number-format persisted via DataStore
- [x] **NotificationSettingsScreen** — wired to `SettingsViewModel`; all toggles + thresholds persisted via DataStore
- [x] **SecuritySettingsScreen** — wired to `SettingsViewModel`; toggles persisted via DataStore. Note: persistence only — nothing *enforces* the lock yet, tracked separately under [Security](#security-ui-exists-but-non-functional)
- [x] **WidgetSettingsScreen** — rewritten: real `hide amounts` preference, live previews from the widget snapshot, and `requestPinAppWidget` buttons per widget
- [ ] **ConnectAccountScreen** — local state for API key input; needs broker integration or at minimum DataStore persistence
- [ ] **ConnectedAccountDetailsScreen** — hardcoded chart/account data; needs real broker API or cached data

## Investment / Brokerage Features (all hardcoded)

- [ ] **InvestmentPortfolioScreen** — hardcoded allocation list; needs real portfolio data source
- [ ] **InvestmentPnLScreen** — hardcoded P&L and trades list
- [ ] **DividendsScreen** — hardcoded dividend history and upcoming dividends
- [ ] **AssetDetailsScreen** — hardcoded asset name/price; needs live or cached price data

## Push Notifications (Android system-level)

- [ ] Implement `NotificationManager` / `WorkManager` background checks:
  - Budget over 80% alert
  - Recurring transaction due today
  - Low wallet balance
  - Monthly summary (only if savings rate ≥ 20%)

## Settings / Preferences Persistence

- [x] Currency selection — persisted via DataStore
- [x] Theme (dark/light/system) — persisted via DataStore
- [x] Number format — persisted via DataStore
- [x] Appearance (accent color, density, home tab) — persisted via DataStore
- [x] Notification preferences (all toggles + thresholds) — persisted via DataStore
- [x] Security preferences (biometric, PIN, auto-lock, privacy) — persisted via DataStore

## Widgets

Built on Glance. Architecture note: widgets read a **cached snapshot** in a separate DataStore file
(`ledger_widget`) rather than opening the Rust DB from a broadcast receiver — see the "Home Screen
Widgets" section of `project.md`. Every new widget should use that snapshot, not the bridge.

### Shipped
- [x] **Quick Add (4×1)** — add-transaction button, receipt-scan button (hidden when `ai_enabled` is off), and up to 2 shortcuts to the categories used most in the last 60 days, which prefill the add form
- [x] **Left Today (2×2)** — remaining daily allowance (monthly budget ÷ days in month − today's spend) with a pacing bar; falls back to total balance + "Set a budget" when no budgets exist
- [x] **Streak (2×2)** — current streak, flame/check state, and the Mon–Sun week grid; shares `computeStreakStats` with `SpendingStreaksScreen`
- [x] Widget deep links via `ledger://open?route=…` (URI, not intent extras — PendingIntents ignore extras)
- [x] `hide amounts` preference — every figure becomes ••• on the home screen
- [x] Refresh triggers: transaction/budget/receipt/currency writes, app start, and the receivers' own update broadcast
- [x] **User-chosen shortcut categories** — pick up to 2 in Settings → Widgets, or leave it automatic (most-used); deleted/renamed categories drop out silently
- [x] **Allowance maths fixed** — each budget is paced inside its **own** period window (weekly limits against Mon–Sun, not the month); only budgeted-category spending counts against the limit, with unbudgeted spend shown separately; the streak keeps the stable `dailyAllowance` bar
- [x] **Rollover, user-controlled** — `allowance_rollover` (default on) carries a surplus or deficit both ways; `allowance_window` (`weekly` | `monthly`, default monthly) chooses when the carried balance resets. Independent of `Budget.period`. Widget shows the **per-day** effect (`todayAllowance − baseDaily`), not the carried pool
- [x] **BudgetsScreen fixed** — was comparing a whole month's spending against a weekly limit and labelling weekly budgets "MONTHLY OVERVIEW". Now shares `categoryPaces` with the widgets, shows each budget's period on its card, and switches the headline to "MONTHLY EQUIVALENT" when any budget is non-monthly. Also adopts `MoneyFormat`
- [x] **Streak off-by-one** — current-streak loop covered 181 days vs the best-streak loop's 180, so a perfect record showed "current 181 / best 180"
- [x] **Tightest-category line** — the summary widget always names the budget furthest through its limit (no longer gated on the alert threshold); `isAlerting` only picks the colour. Scales to any number of budgets because it shows exactly one
- [x] **Per-instance category tracking** — each placed Left Today widget chooses "All budgets" or a single category via `AllowanceWidgetConfigActivity` (`android:configure` + `reconfigurable`), stored in Glance state by `GlanceId`. Place it twice to watch two categories. Snapshot carries `categoryAllowances` for every budgeted category since it can't know what an instance wants
- [x] **Currency picker** — `SettingsScreen` currency row had an empty `onClick`, so `currency_code` was stuck on USD and every amount rendered as `$`

### Next widgets
- [ ] **AI insight of the day (4×1)** — one Gemma-generated sentence. Inference (~20s, 2.7 GB) **cannot** run in a widget update: a WorkManager job (daily, charging + idle) must generate the sentence and cache it, with the widget only reading the cached string. Blocked on Phase 5 "Budget insights narration" below
- [ ] **Goal progress (2×2)** — progress ring over the goal's photo (`GoalImageStore` already exists)
- [ ] **Category pacing (4×2)** — 3 budget rows, each with a bar and a marker for where you *should* be by this day of the month. Cap at 3 rows and reuse the pinned-or-automatic pattern from the Quick Add shortcuts (default: the 3 categories nearest their alert threshold, user-overridable) — a category list is unbounded and must never drive widget height
- [ ] **Upcoming bills (4×2)** — next 3 from `listRecurring()` with a "in 3 d." badge; shares logic with the push-notification work
- [ ] **Net worth (2×2)** — wallets − debts plus a trend sparkline
- [ ] **Month heatmap (4×3)** — calendar grid coloured by daily spend, no-spend days highlighted

### Widget polish
- [ ] Adopt `ui/util/MoneyFormat.kt` across the app's remaining screens — `BudgetsScreen` and the widgets use it, but most others still hardcode `"$%,.2f"` and ignore the currency preference
- [ ] Stale widget-snapshot keys — `w_daily_allowance` lingers in the DataStore after being renamed to `w_today_allowance`. Harmless (nothing reads it) but confusing when debugging the `.preferences_pb`
- [ ] Replace `previewLayout` XMLs with richer picker previews, and `widget_preview_generic.xml` with a real `previewImage` for API < 31
- [ ] Dark-theme pass on the widgets — day/night `ColorProvider`s are declared but untested on a dark home screen
- [x] **Wallet-level budget — data layer** — `budgets.wallet_id` shipped in `m4`; a budget can now cap one funding wallet regardless of category. The scope selector in `AddEditBudgetScreen` still only offers Overall or Category (tracked under Wallet Operations)
- [x] **Carry-over budgets** — the "Carry unspent amount to next period" switch is wired end to end (`budgets.carry_over`, `m4`)

## Transaction Splitting & Shared Expenses

- [ ] **Split transaction between wallets** — allow a single transaction to be divided across multiple wallets (e.g. $100 paid 60% from Checking, 40% from Cash). Needs a `TransactionSplit` Room entity linking transaction → wallet → amount
- [ ] **Link transaction to shared expense** — Add/Edit transaction screens need a "Shared Expense" section where the user can assign the transaction to an existing group or create a new split on the spot (who owes what)
- [ ] **Combo: wallet split + shared expense** — support a transaction that is both split across wallets AND shared with other people simultaneously
- [ ] **SharedExpensesScreen** (already listed above) must reflect these linked transactions rather than being standalone hardcoded data

## AI Receipt Scanning (ML Kit OCR + Gemma 3n)

### Phase 1 — ML Kit OCR
- [x] Add `com.google.mlkit:text-recognition:16.0.1` dependency
- [x] Add `CAMERA` permission to AndroidManifest.xml
- [x] Create `ReceiptOcrRepository.kt` — takes Bitmap, returns raw extracted text via ML Kit TextRecognition (suspend function using suspendCoroutine)
- [x] Create `ReceiptViewModel.kt` — `@HiltViewModel`, processes image, exposes pipeline state
- [x] Create `ReceiptScanScreen.kt` — camera permission request, gallery fallback picker, image preview, loading indicator, result preview, error handling
- [x] Register `Screen.ReceiptScan("receipt_scan")` in NavGraph.kt
- [x] Add receipt-scan entry point (shortcut icon) in `AddTransactionScreen.kt`

### Phase 2 — Gemma 3n Local AI
- [x] Add on-device Llama/Gemma engine (Rust bridge, not `aicore`) dependency
- [x] Create `GemmaRepository.kt` — lazy model init on `Dispatchers.IO`, receipt parsing returning structured expense data, JSON parsed via `kotlinx.serialization`, graceful fallback on parse failure
- [x] Create `GemmaModelViewModel.kt` — model state (`NotDownloaded / Downloading / Verifying / Ready / UpdateAvailable / Error / Deleting`), `initializeModel()`-equivalent flow
- [x] Create `AiModelScreen.kt` — shows model status, download button, size warning, progress indicator, success state
- [x] Register `Screen.AiModelSettings` in NavGraph.kt and add entry in SettingsScreen

### Phase 3 — Full Pipeline (OCR → Gemma → Transaction)
- [x] `ReceiptViewModel` orchestrates OCR → Gemma → preview → user confirm → `bridge.createTransaction()`; `State` sealed class (`Idle / OcrRunning / AiRunning / Preview / Error`)
- [x] Manages pipeline state + editable preview fields (store, items, category, date) that user can modify before confirming
- [x] `ReceiptScanScreen.kt` — full pipeline UI: image pick → loading states → editable preview card → save/cancel
- [x] Guard: if Gemma not ready → error state directs user to AI settings instead of crashing
- [x] Guard: if OCR returns empty → "Čekyje teksto nerasta" error message

### Phase 3.5 — Extended beyond original scope (done ahead of schedule)
- [x] **Per-item receipt parsing** — Gemma returns one JSON object per product line (`ParsedReceipt(store, date, total, items)`); one transaction created per item instead of a single total
- [x] **AI category wand** — `AutoAwesome` button on both `ReceiptScanScreen` item rows and `AddTransactionScreen` that calls `GemmaRepository.suggestCategory(...)` to auto-fill/regenerate a category from the title, serialized via a `Mutex` (single native engine)
- [x] **Split-item mode on `AddTransactionScreen`** — toggle that replaces the single category+title fields with an editable line-item list, each with its own wand button and a live Σ-vs-total indicator; saves one transaction per item via `TransactionViewModel.createSplitTransactions(...)`
- [x] **AI auto-load** — opt-in preference (`ai_auto_load` in `PreferencesRepository`) to warm the model into memory on app startup if already downloaded; one-time prompt to enable it from `AiModelScreen`

### Phase 4 — Extended OCR Features
- [ ] **Money Manager transfers are not imported** — the backup's `transfer` table (35 rows in a real file) is ignored entirely, so account-to-account moves are simply missing after an import. Blocked on Ledger having a transfer type at all (see Wallet Operations below)
- [ ] **Bank statement import** — PDF or bank app screenshot; ML Kit extracts all transactions at once and bulk-creates them; naturally extends existing `TransactionImportScreen` with a new "Import from screenshot/PDF" tab
- [ ] **Receipt photo attached to transaction** — camera captures receipt and attaches it as an image to an existing transaction (proof of purchase, not a new transaction); OCR additionally fills the `note` field from the receipt text

### Phase 5 — Extended Gemma Features
- [x] **Real-time category suggestion** — wand button in `AddTransactionScreen` (and receipt item rows) suggests a category from the title/name via `suggestCategory`; on-demand (button press) rather than debounced-while-typing, but functionally covers the use case
- [ ] **Natural language search** — extend `GlobalSearchScreen` so user can type "kiek išleidau maistui šį mėnesį" and get a Gemma-generated answer backed by real transaction data
- [ ] **Budget insights narration** — Gemma analyses last 3 months of transactions and generates personalised observations (e.g. "Restoranuose išleidi 40% daugiau nei praėjusį mėnesį"); fits into `BudgetInsightsScreen`
- [ ] **Spending anomaly detection** — Gemma detects unusual expenses vs historical patterns and alerts the user; ties into `NotificationsScreen`
- [ ] **Savings goal forecast** — based on current spending trends Gemma explains in natural language whether the user will reach their goal on time; fits into `GoalDetailsScreen`

### Phase 6 — Gemma Infrastructure & Quality
- [x] **Dynamic categories in prompt** — existing category names are passed into `parseReceipt(...)` and `suggestCategory(...)` at call time (prefer-existing prompt), so Gemma matches the user's actual categories instead of hardcoded ones
- [ ] **User correction memory** — when user overrides Gemma's category suggestion, save the mapping (e.g. "Bolt" → Transportas) to DataStore as user preferences; inject into prompt next time so Gemma "learns" from corrections
- [x] **Fallback UI on parse failure** — `State.Preview` carries an `aiFailed` flag (set when the parse yields zero items); `ReceiptScanScreen` then shows a warning card ("AI couldn't read this receipt") and opens an empty row for manual entry instead of a silently blank form. Message is English, matching the rest of the receipt flow
- [x] **Model version management** — `GemmaModelRepository` checks bundled model version vs `GemmaModelInfo.CURRENT_VERSION`, surfaces `ModelStatus.UpdateAvailable`
- [x] **Model download progress UI** — `GemmaModelRepository.downloadModel()` emits real `Downloading(progressPercent, bytesDownloaded)` states shown in `AiModelScreen`
- [x] **Offline mode communication** — privacy card at the top of `AiModelScreen` ("Viskas vyksta jūsų telefone — visi AI skaičiavimai atliekami lokaliai..."). This was already implemented before the checkbox was ticked
- [x] **Master AI disable toggle** — `ai_enabled` preference (defaults **true** so an update doesn't silently strip AI from existing installs), switch at the top of `AiModelScreen`. When off: the model is unloaded from memory, `LedgerApp` skips auto-load, and every AI control is hidden — receipt-scan entry points in `AddTransactionScreen` *and* `DashboardScreen`, plus the ✨ wands in single and split mode. The model file stays on disk and the auto-load preference keeps its value, so re-enabling restores the previous setup
- [ ] **Context from transaction history** — when suggesting categories, pass user's last 50 transactions to prompt so Gemma can infer patterns (e.g. "Bolt Food" → Maistas, not Transportas, because user always tagged it that way)

## Core Money Management (Missing Use Cases)

### Wallet Operations
- [x] **Transfer between wallets — data layer** — `transfers` table with its own CRUD; moves balance between both wallets and never touches income or expense totals (folding it into `transactions` would make every report count it twice)
- [x] **Transfer between wallets — UI** — `AddTransferScreen` + `TransferViewModel`, reached from the swap icon in the WalletsList top bar. From/to pickers, amount, note, and an after-the-move balance preview so an overdrawing transfer is visible before saving; refuses the same wallet on both sides
- [ ] **Transfers are not listed anywhere** — you can create one but not see or delete it afterwards. `listTransfers` and `deleteTransfer` exist; WalletDetailsScreen is the natural home
- [x] **Overall budget** — a budget with no category caps everything you spend and is now the *only* source of the daily allowance. Category budgets are no longer summed into a total, which had invented a figure nobody chose and hid every unbudgeted purchase from the allowance. `AddEditBudgetScreen` gained an Overall/Category scope selector
- [x] **`carry_over` wired** — the "Carry unspent amount to next period" switch stored nothing before. It composes with `allowance_rollover` rather than conflicting: carry-over moves the previous period's residual into this period's ceiling, rollover spreads that ceiling across remaining days. One period back, symmetric
- [x] **Carry-over no longer reaches back before the budget existed** — a 200/month budget created today inherited the previous month's 2644 of spending and opened at minus 2244, shown as "2692 over" on day one. The previous period now only counts if the budget already covered it
- [x] **Second overall budget is refused instead of ignored** — creating one succeeded and then did nothing, because only one is ever used. `create_budget` rejects it with a message the screen now displays, the **newest** wins for legacy data, and `BudgetsScreen` lists any unused ones so they can be deleted
- [x] **Off-budget wallets** — `wallets.off_budget` keeps a work or investment account out of budgets, the daily allowance and the streak while still counting toward net worth. Without it an overall budget covers everything by definition, so work activity ate the personal budget. Toggle on the add and edit wallet screens
- [x] **Reports filter off-budget wallets** — all eight analysis screens now use `rememberReportTransactions`, which applies the `reports_include_off_budget` preference (default off) in one place. Toggle in Settings → Reports & Insights. `TransactionUiState.transactions` stays unfiltered so editing, search and the transaction list can still reach an off-budget row by id
- [x] **Off-budget wallets marked in WalletsList** — the row shows an "Off budget" label instead of requiring you to open the wallet
- [ ] **Wallet-scoped overall budget has no UI** — the data layer supports `category_id = null, wallet_id = X`, but the scope selector only offers Overall or Category
- [x] **BudgetsScreen shows the overall budget** — its own "Everything" row with period, carry-over marker, spend against the effective limit and a progress bar, tappable to edit. The overview card now leads with the overall budget when one exists, and otherwise says plainly that there is no daily allowance instead of presenting the sum of category limits as a total
- [ ] **Quick cash in/out** — fast cash transaction from home screen or wallet screen without filling full form; just amount + income/expense toggle

### Currency
- [ ] **Live exchange rates** — fetch rates from a free API (e.g. exchangerate.host or frankfurter.app); store base currency in DataStore (already there); convert all amounts on display when wallet currency differs from base currency
- [ ] **Multi-currency wallets** — each wallet has its own currency; totals on NetWorthScreen and Dashboard convert to base currency using live rates

### Data Export
- [ ] **CSV export** — `CsvExport.kt` already exists but is not wired to any UI; connect to CustomReportScreen export button
- [ ] **PDF report export** — monthly/annual report as a shareable PDF file (use Android PdfDocument API)
- [ ] **Excel (.xlsx) export** — for accounting purposes; use Apache POI or a lightweight alternative

### Security (UI exists but non-functional)
- [ ] **Real biometric lock** — wire `BiometricPrompt` API so the app actually locks/unlocks; currently SecuritySettingsScreen saves the toggle but nothing enforces it
- [ ] **Real PIN lock** — store hashed PIN in EncryptedSharedPreferences; enforce on app resume if auto-lock timer has elapsed
- [ ] **SQLite encryption** — encrypt the database with SQLCipher (`net.zetetic:android-database-sqlcipher`) for data-at-rest protection

### Onboarding
- [ ] **First-launch wizard** — step-by-step onboarding: (1) set base currency, (2) add first wallet, (3) add first category, (4) optional: add first transaction; replace or gate `SeedDataUtil` so real users don't get fake Alex Johnson data
- [ ] **Empty state screens** — when no wallets/transactions/goals exist, show helpful empty states with a CTA instead of blank lists

### Social / Collaboration
- [ ] **Family/couple mode** — two users share one account and see a combined budget; needs a sync mechanism (could be as simple as shared backup file)
- [ ] **Shared expenses send link** — SharedExpensesScreen already has UI; add ability to share a split via link or message so other person can see what they owe

## Automatic Backups

- [ ] **Local backup** — export full Room database as a `.ledgerbackup` file (JSON or binary) on a schedule (daily/weekly) using WorkManager; store in app-scoped external storage
- [ ] **Google Drive / cloud backup** — optional upload of backup file to user's Drive via Google Drive API
- [ ] **Restore from backup** — UI flow in Settings to pick a `.ledgerbackup` file and restore (with confirmation warning that current data will be replaced)
- [ ] **Backup settings screen** — frequency (daily/weekly/manual), last backup time, cloud on/off toggle; wire to `BackupSettingsScreen` or add section to existing SettingsScreen

## Tests

- [ ] **Unit tests — ViewModels** — test state transitions, filtering logic, budget calculations, streak computation for: `TransactionViewModel`, `BudgetViewModel`, `WalletViewModel`, `GoalViewModel`, `DebtViewModel`, `RecurringViewModel`, `CategoryViewModel`
- [ ] **Unit tests — data layer** — test Room DAOs with in-memory database (insert, update, delete, query filters) for every entity
- [ ] **Unit tests — business logic** — streak calculation, daily allowance, savings rate, cash flow forecast projection, net worth computation, quarterly/annual aggregation
- [ ] **Integration tests** — end-to-end ViewModel + Repository + Room using Hilt test components and `kotlinx-coroutines-test`
- [ ] **UI tests (Compose)** — key user flows with `ComposeTestRule`: add transaction, add budget, add goal, import file, navigate settings
- [ ] **Test coverage target** — aim for ≥80% on ViewModel and data layers before release

## Minor / Polish

- [x] **Wallet balance drifted on edit and delete** — `wallets.balance` is a stored running total, but `delete_transaction` never removed the deleted amount and `update_transaction` kept the old one after an edit. Both now reverse the previous effect before applying the new
- [x] **Money mutations are atomic** — insert-plus-balance and a transfer's two balance updates now run inside a single DB transaction; previously any of them could half-apply. `create_transfer` also verifies both wallets exist, since nothing enforces the foreign keys at runtime
- [x] **Warn before deleting a category or wallet that has data** — both dialogs now count the affected rows in the database first and say the real number, instead of a generic warning. The wallet dialog states how many transactions will be permanently deleted; the category dialog says how many keep their label but lose the link, and that the budget goes with it
- [ ] **Stale `armeabi-v7a` native library** — `app/src/main/jniLibs/armeabi-v7a/libuniffi_ledger.so` is from April and predates the `category_id` schema. Harmless today because `abiFilters` is `arm64-v8a` + `x86_64`, but it would ship a broken build if that ABI is ever re-enabled. Either rebuild it or delete the folder
- [x] **Regression tests for the database layer** — `core/tests/` runs on the host in ~2s with `CARGO_TARGET_DIR=C:/lt-host cargo test --tests`, against a real SQLite file opened through the same `open_database` the app uses, so every test replays the migration chain from empty. 12 tests, each one a bug that actually shipped: same-day ordering, back-dating, paging, edit-keeps-the-date, balance reversal on edit and delete, income-vs-expense category links, off_budget persistence, duplicate overall budget, transfers, wallet and category deletion. Needed `rlib` in `crate-type` and host stubs for the llama C FFI, without which the test binary cannot link
- [x] **New transactions looked unsaved** — ordering was by `occurred_at` alone, which is a date, so everything entered today tied and SQLite returned the ties in an arbitrary order. A just-added transaction landed below the day's imported rows and read as a failed save; the user retried and got a duplicate. `TX_ORDER` now breaks ties on `created_at` then `id`
- [x] **Add transaction could be submitted twice** — the save is async and the screen only closes in the callback, so a second tap in that window wrote the row again. Confirmed on the device: two identical rows 17 ms apart. The button now disables itself on submit
- [x] **`occurred_at` split from `created_at`** — one column was carrying two meanings: when the money was spent and when the row was written. They are the same for a manual entry made today and different for every import and every back-dated entry, so a bank statement imported in August landed the whole thing on August and wrecked every report at once. `m6` adds `occurred_at`, backfills it from `created_at` (2052 rows, none null) and indexes it. The FFI exposes only `occurred_at`; `created_at` stays in the table as an audit trail with no Kotlin binding, so the two meanings cannot blur again. All 95 affected Kotlin sites were found by the compiler rather than by search
- [x] **`recurring_transactions.category`** — had the same rename problem; now carries `category_id` and resolves through it
- [x] **Dead `ON DELETE CASCADE`s** — SQLite ignores foreign keys without `PRAGMA foreign_keys=ON`, which this pool never sets, so all three declared cascades did nothing. Deleting a wallet left its transactions and recurring rows behind; deleting a transaction or tag left link rows. Each `delete_*` now cleans up explicitly and `clean_orphans` sweeps existing strays
- [x] **Deleting a category orphaned its budget** — invisible forever, since every screen resolves a budget through its category. The budget is now deleted with it
- [x] **Category links ignored income vs expense** — `resolve_category_id` matched on name alone, so 175 expense transactions linked to the income-side twin (Gifts, Other, NEATITIKMUO all exist in both directions after a Money Manager import). Now matches on name **and** `is_expense`, with a repair pass for existing rows
- [ ] **HelpSupportScreen** — FAQ items are hardcoded; acceptable as static content but could be loaded from remote
- [x] **EditTransaction date picker** — verified against the device DB. It writes `occurred_at`, and `update_transaction`'s `COALESCE(?, occurred_at, created_at)` means an edit that does not touch the date leaves it alone instead of moving the transaction to today
- [ ] Seed data utility (`SeedDataUtil.kt`) — decide if this stays for dev only or gets removed before release
- [ ] CSV export (`CsvExport.kt`) — wire to Custom Report export button

## Completed

- [x] Dashboard income/expense uses calendar month (not rolling 30-day)
- [x] BudgetInsights reduced to 2 tabs — removed duplicate Categories and Trends tabs
- [x] FinancialCalendarScreen deleted — Settings entry redirected to CashFlowForecast
- [x] SpendingStreaksScreen rewritten with real streak computation, week grid, and 7 achievements
- [x] **Renaming a category now follows through to its transactions** — transactions stored only the category *name*, so a rename orphaned every one of them (93 stranded on "Servicez" in a real database) and a budget on that category would have matched nothing. Transactions now carry `category_id`; reads resolve the name through it, writes resolve a name to an id (creating the category when new), and delete detaches the link while keeping the historical label. Migration backfills existing rows. UDL unchanged, so no UniFFI regeneration — only a rebuilt `.so`
- [x] TransactionImportScreen — Money Manager .mmbackup import
- [x] Import: Money Manager's built-in categories no longer collapse into "Other" — they carry an empty `title` in the backup (the name is localised at runtime from the uid), so 1110 of 1993 transactions in a real backup landed in one bucket with Groceries and Cafe merged. `ImportViewModel.defaultCategoryNames` maps the uids back; icon mapping extended so the defaults don't all fall through to the same generic vector
- [x] RecurringTransactionsScreen — connected to DB via RecurringViewModel
- [x] BudgetsScreen — connected to DB via BudgetViewModel
- [x] NetWorthScreen — real wallet + debt data
- [x] GlobalSearchScreen — searches transactions, categories, wallets, goals, debts, recurring
- [x] AnnualSummaryScreen — real transaction data
- [x] MonthlyReportScreen / QuarterlyReportScreen — real transaction data
- [x] CashFlowForecastScreen — real forecasting from transactions + recurring
- [x] MonthlyStatisticsScreen — real data
- [x] DebtTrackerScreen — connected to DB via DebtViewModel
