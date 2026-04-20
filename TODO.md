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
