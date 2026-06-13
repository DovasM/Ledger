# Ledger — Project To-Do List

## Real Data / ViewModels

Screens still using hardcoded or local-only state that need ViewModel integration:

- [ ] **NotificationsScreen** — rewrite with real data (budget alerts, goal milestones, recurring due dates, wallet balance warnings). Push notifications (system-level Android) deferred separately.
- [ ] **SharedExpensesScreen** — hardcoded group/expense list; needs a SharedExpenseViewModel + Room entity
- [ ] **EditProfileScreen** — placeholder name "Alex Johnson" / email; needs a UserProfileViewModel or DataStore
- [ ] **AppearanceSettingsScreen** — local toggle state only; persist theme/density/currency preferences via DataStore
- [ ] **NotificationSettingsScreen** — local toggle state only; persist notification preferences via DataStore
- [ ] **SecuritySettingsScreen** — local toggle state only; persist biometric/PIN/auto-lock settings via DataStore or EncryptedSharedPreferences
- [ ] **WidgetSettingsScreen** — hardcoded widget list; wire to real AppWidget configuration
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

- [ ] Implement actual Android home screen widgets (balance, budget progress, quick-add)
- [ ] Wire WidgetSettingsScreen to real AppWidgetProvider

## Transaction Splitting & Shared Expenses

- [ ] **Split transaction between wallets** — allow a single transaction to be divided across multiple wallets (e.g. $100 paid 60% from Checking, 40% from Cash). Needs a `TransactionSplit` Room entity linking transaction → wallet → amount
- [ ] **Link transaction to shared expense** — Add/Edit transaction screens need a "Shared Expense" section where the user can assign the transaction to an existing group or create a new split on the spot (who owes what)
- [ ] **Combo: wallet split + shared expense** — support a transaction that is both split across wallets AND shared with other people simultaneously
- [ ] **SharedExpensesScreen** (already listed above) must reflect these linked transactions rather than being standalone hardcoded data

## AI Receipt Scanning (ML Kit OCR + Gemma 3n)

### Phase 1 — ML Kit OCR
- [ ] Add `com.google.mlkit:text-recognition:16.0.1` dependency
- [ ] Add `CAMERA` permission to AndroidManifest.xml
- [ ] Create `ReceiptOcrRepository.kt` — takes Bitmap, returns raw extracted text via ML Kit TextRecognition (suspend function using suspendCoroutine)
- [ ] Create `ReceiptScanViewModel.kt` — `@HiltViewModel`, processes image, exposes `isLoading / rawText / error` state
- [ ] Create `ReceiptScanScreen.kt` — camera permission request, gallery fallback picker, image preview, "Nuskaityti čekį" button, loading indicator, scrollable raw text result card, error Snackbar
- [ ] Register `Screen.ReceiptScan("receipt_scan")` in NavGraph.kt
- [ ] Add "Nuskaityti čekį" entry point button in `AddTransactionScreen.kt`

### Phase 2 — Gemma 3n Local AI
- [ ] Add `com.google.ai.edge.aicore:aicore:0.0.1-exp01` dependency
- [ ] Create `GemmaRepository.kt` — lazy model init on `Dispatchers.IO`, `parseReceiptText(rawText)` returning `ParsedExpense(store, date, amount, category, items)`, JSON parsed via `kotlinx.serialization`, graceful fallback on parse failure
- [ ] Create `GemmaStatusViewModel.kt` — model state enum (`NOT_LOADED / LOADING / READY / ERROR`), `initializeModel()` function
- [ ] Create `AiSettingsScreen.kt` — shows model status, "Įkelti modelį" button, "~2GB" warning card, progress indicator, success state
- [ ] Register `Screen.AiSettings("ai_settings")` in NavGraph.kt and add entry in SettingsScreen

### Phase 3 — Full Pipeline (OCR → Gemma → Transaction)
- [ ] Create `ReceiptPipelineRepository.kt` — orchestrates OCR → Gemma → preview → user confirm → `bridge.createTransaction()`; emits `PipelineResult` sealed class states (`Idle / ExtractingText / ParsingExpense / Preview / Saving / Success / Error`)
- [ ] Create `ReceiptPipelineViewModel.kt` — manages pipeline state + editable preview fields (store, amount, category, date) that user can modify before confirming
- [ ] Update `ReceiptScanScreen.kt` — replace raw text display with full 5-step pipeline UI: image pick → loading states → editable preview card → save/cancel → success auto-navigate back after 1.5s
- [ ] Register `ReceiptPipelineRepository` in `AppModule.kt`
- [ ] Guard: if Gemma not ready → show dialog "Eiti į nustatymus?" instead of crashing
- [ ] Guard: if OCR returns empty → show "Nepavyko perskaityti čekio. Bandykite nufotografuoti geriau."

### Phase 4 — Extended OCR Features
- [ ] **Bank statement import** — PDF or bank app screenshot; ML Kit extracts all transactions at once and bulk-creates them; naturally extends existing `TransactionImportScreen` with a new "Import from screenshot/PDF" tab
- [ ] **Receipt photo attached to transaction** — camera captures receipt and attaches it as an image to an existing transaction (proof of purchase, not a new transaction); OCR additionally fills the `note` field from the receipt text

### Phase 5 — Extended Gemma Features
- [ ] **Real-time category suggestion** — while user types transaction title in `AddTransactionScreen`, Gemma suggests a category live (e.g. "Rimi" → Maistas, "Bolt" → Transportas); debounced, non-blocking
- [ ] **Natural language search** — extend `GlobalSearchScreen` so user can type "kiek išleidau maistui šį mėnesį" and get a Gemma-generated answer backed by real transaction data
- [ ] **Budget insights narration** — Gemma analyses last 3 months of transactions and generates personalised observations (e.g. "Restoranuose išleidi 40% daugiau nei praėjusį mėnesį"); fits into `BudgetInsightsScreen`
- [ ] **Spending anomaly detection** — Gemma detects unusual expenses vs historical patterns and alerts the user; ties into `NotificationsScreen`
- [ ] **Savings goal forecast** — based on current spending trends Gemma explains in natural language whether the user will reach their goal on time; fits into `GoalDetailsScreen`

### Phase 6 — Gemma Infrastructure & Quality
- [ ] **Dynamic categories in prompt** — instead of hardcoded Lithuanian category names, inject `bridge.listCategories()` list into the prompt at runtime so Gemma always matches the user's actual categories (critical — without this Gemma suggests categories the user doesn't have)
- [ ] **User correction memory** — when user overrides Gemma's category suggestion, save the mapping (e.g. "Bolt" → Transportas) to DataStore as user preferences; inject into prompt next time so Gemma "learns" from corrections
- [ ] **Fallback UI on parse failure** — when Gemma returns invalid JSON or garbage, currently silent defaults are returned; instead show clear UI message "AI nepavyko išanalizuoti — įvesk rankiniu būdu" and pre-fill fields as empty for manual entry
- [ ] **Model version management** — check current bundled model version vs latest available; notify user when update is needed; handle graceful migration
- [ ] **Model download progress UI** — `aicore` can download model in background; show real progress bar (%, MB downloaded) not just a generic LOADING spinner in `AiSettingsScreen`
- [ ] **Offline mode communication** — Gemma runs fully offline; explicitly communicate this to the user ("Visi AI skaičiavimai atliekami jūsų telefone — jūsų duomenys niekur nesiunčiami") as a key privacy advantage
- [ ] **Master AI disable toggle** — single switch in `AiSettingsScreen` to disable all Gemma features at once for users who don't want AI; when off, all AI-powered UI elements are hidden across the app
- [ ] **Context from transaction history** — when suggesting categories, pass user's last 50 transactions to prompt so Gemma can infer patterns (e.g. "Bolt Food" → Maistas, not Transportas, because user always tagged it that way)

## Core Money Management (Missing Use Cases)

### Wallet Operations
- [ ] **Transfer between wallets** — move funds from one wallet to another (e.g. Checking → Savings); currently requires two manual transactions as workaround; needs `transfer` transaction type in Rust schema and dedicated UI in WalletsListScreen or AddTransactionScreen
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

- [ ] **HelpSupportScreen** — FAQ items are hardcoded; acceptable as static content but could be loaded from remote
- [ ] **EditTransaction date picker** — verify date picker persists correctly to DB
- [ ] Seed data utility (`SeedDataUtil.kt`) — decide if this stays for dev only or gets removed before release
- [ ] CSV export (`CsvExport.kt`) — wire to Custom Report export button

## Completed

- [x] Dashboard income/expense uses calendar month (not rolling 30-day)
- [x] BudgetInsights reduced to 2 tabs — removed duplicate Categories and Trends tabs
- [x] FinancialCalendarScreen deleted — Settings entry redirected to CashFlowForecast
- [x] SpendingStreaksScreen rewritten with real streak computation, week grid, and 7 achievements
- [x] TransactionImportScreen — Money Manager .mmbackup import
- [x] RecurringTransactionsScreen — connected to DB via RecurringViewModel
- [x] BudgetsScreen — connected to DB via BudgetViewModel
- [x] NetWorthScreen — real wallet + debt data
- [x] GlobalSearchScreen — searches transactions, categories, wallets, goals, debts, recurring
- [x] AnnualSummaryScreen — real transaction data
- [x] MonthlyReportScreen / QuarterlyReportScreen — real transaction data
- [x] CashFlowForecastScreen — real forecasting from transactions + recurring
- [x] MonthlyStatisticsScreen — real data
- [x] DebtTrackerScreen — connected to DB via DebtViewModel
