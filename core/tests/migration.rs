//! The upgrade path, tested against a hand-built database in the shape that shipped.
//!
//! This is the part of m7 that touches real money already on people's phones: a goal's balance and
//! the paid-off part of a debt lived in columns that the migration drops. If the seed is wrong the
//! money is gone with no way back, and no amount of testing the new code alone would show it.

use sqlx::sqlite::SqliteConnectOptions;
use sqlx::SqlitePool;
use std::path::PathBuf;
use std::str::FromStr;
use std::sync::atomic::{AtomicU32, Ordering};

static COUNTER: AtomicU32 = AtomicU32::new(0);

fn temp_path(tag: &str) -> PathBuf {
    let n = COUNTER.fetch_add(1, Ordering::SeqCst);
    let p = std::env::temp_dir().join(format!("ledger_mig_{tag}_{}_{}.db", std::process::id(), n));
    let _ = std::fs::remove_file(&p);
    p
}

/// The savings_goals and debts shape as of m6, with the schema_version table already at 6 so
/// `run_migrations` starts exactly where a real device would.
async fn build_pre_m7_database(path: &PathBuf) {
    let opts = SqliteConnectOptions::from_str(&format!("sqlite:{}", path.display()))
        .unwrap()
        .create_if_missing(true);
    let pool = SqlitePool::connect_with(opts).await.unwrap();

    for stmt in [
        "CREATE TABLE schema_version (version INTEGER PRIMARY KEY, applied_at TEXT NOT NULL)",
        "CREATE TABLE savings_goals (
            id TEXT PRIMARY KEY, name TEXT NOT NULL, current_amount REAL NOT NULL DEFAULT 0.0,
            target_amount REAL NOT NULL, deadline TEXT, created_at TEXT NOT NULL)",
        "CREATE TABLE debts (
            id TEXT PRIMARY KEY, name TEXT NOT NULL, debt_type TEXT NOT NULL,
            total_amount REAL NOT NULL, remaining_amount REAL NOT NULL, apr REAL NOT NULL,
            monthly_payment REAL NOT NULL, created_at TEXT NOT NULL)",
        "INSERT INTO savings_goals VALUES ('g1', 'Emergency fund', 8500.0, 10000.0, NULL, '2026-01-01T00:00:00Z')",
        "INSERT INTO savings_goals VALUES ('g2', 'Nothing saved yet', 0.0, 500.0, NULL, '2026-01-02T00:00:00Z')",
        "INSERT INTO debts VALUES ('d1', 'Car loan', 'loan', 5000.0, 3400.0, 4.5, 200.0, '2026-01-01T00:00:00Z')",
        "INSERT INTO debts VALUES ('d2', 'Untouched', 'loan', 1000.0, 1000.0, 3.0, 50.0, '2026-01-02T00:00:00Z')",
    ] {
        sqlx::query(stmt).execute(&pool).await.unwrap();
    }
    // Claim every version up to 6 so only m7 runs.
    for v in 1..=6 {
        sqlx::query("INSERT INTO schema_version (version, applied_at) VALUES (?, '2026-01-01T00:00:00Z')")
            .bind(v)
            .execute(&pool)
            .await
            .unwrap();
    }
    pool.close().await;
}

#[test]
fn m7_carries_existing_balances_into_the_history() {
    let path = temp_path("m7");
    let rt = tokio::runtime::Runtime::new().unwrap();
    rt.block_on(build_pre_m7_database(&path));

    let db = uniffi_ledger::open_database(path.to_string_lossy().to_string());

    let goals = db.list_goals().unwrap();
    let emergency = goals.iter().find(|g| g.id == "g1").unwrap();
    assert_eq!(emergency.current_amount_cents, 850000, "the saved money must survive the column being dropped");
    assert_eq!(emergency.target_amount_cents, 1000000);

    let opening = db.list_goal_contributions("g1".into()).unwrap();
    assert_eq!(opening.len(), 1);
    assert_eq!(opening[0].amount_cents, 850000);
    assert_eq!(opening[0].kind, "opening");

    let empty = goals.iter().find(|g| g.id == "g2").unwrap();
    assert_eq!(empty.current_amount_cents, 0);
    assert!(db.list_goal_contributions("g2".into()).unwrap().is_empty(), "an empty goal gets no opening row");

    let debts = db.list_debts().unwrap();
    let car = debts.iter().find(|d| d.id == "d1").unwrap();
    assert_eq!(car.remaining_amount_cents, 340000, "what is still owed must come out identical");
    assert_eq!(car.total_amount_cents, 500000);

    let paid = db.list_debt_payments("d1".into()).unwrap();
    assert_eq!(paid.len(), 1);
    assert_eq!(paid[0].amount_cents, 160000, "the part already paid off becomes the opening entry");
    assert_eq!(paid[0].kind, "opening");

    let untouched = debts.iter().find(|d| d.id == "d2").unwrap();
    assert_eq!(untouched.remaining_amount_cents, 100000);
    assert!(db.list_debt_payments("d2".into()).unwrap().is_empty());

    drop(db);
    let _ = std::fs::remove_file(&path);
}

/// Every migration in this project must be idempotent — databases predating the version table
/// bootstrap at 0 and replay everything. Opening twice must not double the money.
#[test]
fn m7_is_idempotent() {
    let path = temp_path("idem");
    let rt = tokio::runtime::Runtime::new().unwrap();
    rt.block_on(build_pre_m7_database(&path));

    let db = uniffi_ledger::open_database(path.to_string_lossy().to_string());
    assert_eq!(db.list_goals().unwrap().iter().find(|g| g.id == "g1").unwrap().current_amount_cents, 850000);
    drop(db);

    let reopened = uniffi_ledger::open_database(path.to_string_lossy().to_string());
    assert_eq!(
        reopened.list_goals().unwrap().iter().find(|g| g.id == "g1").unwrap().current_amount_cents,
        850000,
        "reopening must not seed a second opening row"
    );
    assert_eq!(reopened.list_goal_contributions("g1".into()).unwrap().len(), 1);
    assert_eq!(reopened.list_debt_payments("d1".into()).unwrap().len(), 1);

    drop(reopened);
    let _ = std::fs::remove_file(&path);
}

/// The post-m7 shape, money still REAL, version claimed up to 7 so only m8 runs.
async fn build_pre_m8_database(path: &PathBuf) {
    let opts = SqliteConnectOptions::from_str(&format!("sqlite:{}", path.display()))
        .unwrap()
        .create_if_missing(true);
    let pool = SqlitePool::connect_with(opts).await.unwrap();

    for stmt in [
        "CREATE TABLE schema_version (version INTEGER PRIMARY KEY, applied_at TEXT NOT NULL)",
        "CREATE TABLE categories (id TEXT PRIMARY KEY, name TEXT NOT NULL, icon_name TEXT NOT NULL DEFAULT 'label',
            color_hex TEXT NOT NULL DEFAULT '#00513F', is_expense INTEGER NOT NULL DEFAULT 1, created_at TEXT NOT NULL)",
        "CREATE TABLE wallets (id TEXT PRIMARY KEY, name TEXT NOT NULL, description TEXT NOT NULL DEFAULT '',
            balance REAL NOT NULL DEFAULT 0.0, created_at TEXT NOT NULL, currency TEXT NOT NULL DEFAULT '',
            off_budget INTEGER NOT NULL DEFAULT 0)",
        "CREATE TABLE transactions (id TEXT PRIMARY KEY, wallet_id TEXT NOT NULL REFERENCES wallets(id) ON DELETE CASCADE,
            title TEXT NOT NULL, category TEXT NOT NULL DEFAULT '', amount REAL NOT NULL, is_income INTEGER NOT NULL DEFAULT 0,
            note TEXT, created_at TEXT NOT NULL, category_id TEXT REFERENCES categories(id), occurred_at TEXT)",
        "CREATE TABLE savings_goals (id TEXT PRIMARY KEY, name TEXT NOT NULL, target_amount REAL NOT NULL,
            deadline TEXT, created_at TEXT NOT NULL)",
        "CREATE TABLE goal_contributions (id TEXT PRIMARY KEY, goal_id TEXT NOT NULL REFERENCES savings_goals(id),
            amount REAL NOT NULL, note TEXT, kind TEXT NOT NULL DEFAULT 'contribution', occurred_at TEXT NOT NULL,
            created_at TEXT NOT NULL)",
        "CREATE TABLE budgets (id TEXT PRIMARY KEY, category_id TEXT REFERENCES categories(id),
            wallet_id TEXT REFERENCES wallets(id), limit_amount REAL NOT NULL, period TEXT NOT NULL DEFAULT 'monthly',
            alert_threshold REAL NOT NULL DEFAULT 80, carry_over INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL)",

        "INSERT INTO wallets (id, name, balance, created_at, currency) VALUES ('w1', 'Checking', 1234.56, '2026-01-01T00:00:00Z', 'EUR')",
        // 0.29 is the one that matters: 0.29 * 100 is 28.999999999999996, so CAST without ROUND
        // would file 29 cents as 28 and quietly lose a cent on every such row.
        "INSERT INTO transactions (id, wallet_id, title, amount, is_income, created_at, occurred_at)
            VALUES ('t1', 'w1', 'awkward', 0.29, 0, '2026-03-01T00:00:00Z', '2026-03-01T00:00:00Z')",
        "INSERT INTO transactions (id, wallet_id, title, amount, is_income, created_at, occurred_at)
            VALUES ('t2', 'w1', 'also awkward', 7.20, 0, '2026-03-02T00:00:00Z', '2026-03-02T00:00:00Z')",
        // occurred_at NULL: m6 left the column nullable, and m8 makes it NOT NULL from created_at.
        "INSERT INTO transactions (id, wallet_id, title, amount, is_income, created_at, occurred_at)
            VALUES ('t3', 'w1', 'no occurred_at', 999999.99, 1, '2026-03-03T00:00:00Z', NULL)",
        "INSERT INTO savings_goals VALUES ('g1', 'Goal', 250.10, NULL, '2026-01-01T00:00:00Z')",
        "INSERT INTO goal_contributions VALUES ('c1', 'g1', 0.29, NULL, 'contribution', '2026-03-01T00:00:00Z', '2026-03-01T00:00:00Z')",
        "INSERT INTO budgets (id, category_id, wallet_id, limit_amount, period, alert_threshold, carry_over, created_at)
            VALUES ('b1', NULL, NULL, 250.00, 'monthly', 80.0, 1, '2026-01-01T00:00:00Z')",
    ] {
        sqlx::query(stmt).execute(&pool).await.unwrap();
    }
    for v in 1..=7 {
        sqlx::query("INSERT INTO schema_version (version, applied_at) VALUES (?, '2026-01-01T00:00:00Z')")
            .bind(v)
            .execute(&pool)
            .await
            .unwrap();
    }
    pool.close().await;
}

/// Every amount must come out as the exact number of cents the user typed, including the ones
/// binary floating point cannot hold.
#[test]
fn m8_converts_every_amount_exactly() {
    let path = temp_path("m8");
    let rt = tokio::runtime::Runtime::new().unwrap();
    rt.block_on(build_pre_m8_database(&path));

    let db = uniffi_ledger::open_database(path.to_string_lossy().to_string());

    let txs = db.list_all_transactions(100, 0).unwrap();
    let by_title = |t: &str| txs.iter().find(|x| x.title == t).unwrap().amount_cents;
    assert_eq!(by_title("awkward"), 29, "0.29 * 100 is 28.999999999999996 — CAST alone would say 28");
    assert_eq!(by_title("also awkward"), 720);
    assert_eq!(by_title("no occurred_at"), 99999999);

    // occurred_at was nullable until m8 and is filled from created_at where it was missing.
    let filled = txs.iter().find(|x| x.title == "no occurred_at").unwrap();
    assert_eq!(filled.occurred_at, "2026-03-03T00:00:00Z");

    assert_eq!(db.list_wallets().unwrap()[0].balance_cents, 123456);
    assert_eq!(db.list_goals().unwrap()[0].target_amount_cents, 25010);
    assert_eq!(db.list_goals().unwrap()[0].current_amount_cents, 29);
    assert_eq!(db.list_budgets().unwrap()[0].limit_amount_cents, 25000);
    assert_eq!(db.list_budgets().unwrap()[0].alert_threshold, 80.0, "percentages stay REAL");

    // The monthly summary sums integers, so it is exact rather than nearly right.
    let march = db.get_month_summary(2026, 3).unwrap();
    assert_eq!(march.total_expenses_cents, 29 + 720);
    assert_eq!(march.total_income_cents, 99999999);
    assert_eq!(march.net_savings_cents, 99999999 - 749);
    assert_eq!(march.transaction_count, 3);

    drop(db);
    let _ = std::fs::remove_file(&path);
}

#[test]
fn m8_is_idempotent_and_keeps_the_indexes() {
    let path = temp_path("m8idem");
    let rt = tokio::runtime::Runtime::new().unwrap();
    rt.block_on(build_pre_m8_database(&path));

    let db = uniffi_ledger::open_database(path.to_string_lossy().to_string());
    drop(db);
    let reopened = uniffi_ledger::open_database(path.to_string_lossy().to_string());
    assert_eq!(reopened.list_wallets().unwrap()[0].balance_cents, 123456, "a second open must not re-scale");
    assert_eq!(reopened.list_all_transactions(100, 0).unwrap().len(), 3);
    drop(reopened);

    // Dropping a table drops its indexes; the rebuild has to put them back or every category
    // breakdown and wallet filter silently goes back to a full table scan.
    rt.block_on(async {
        let opts = SqliteConnectOptions::from_str(&format!("sqlite:{}", path.display())).unwrap();
        let pool = SqlitePool::connect_with(opts).await.unwrap();
        let idx: Vec<(String,)> = sqlx::query_as("SELECT name FROM sqlite_master WHERE type='index' AND name LIKE 'idx_%'")
            .fetch_all(&pool)
            .await
            .unwrap();
        let names: Vec<String> = idx.into_iter().map(|x| x.0).collect();
        for expected in ["idx_tx_wallet", "idx_tx_category", "idx_tx_occurred", "idx_contrib_goal", "idx_budget_cat"] {
            assert!(names.contains(&expected.to_string()), "{expected} was dropped with its table and never recreated");
        }
        pool.close().await;
    });

    let _ = std::fs::remove_file(&path);
}

/// A fresh install runs m1 through m7 in one go. m2 sweeps orphans before m7 creates its tables,
/// which is a real ordering trap: a sweep referencing them there would fail on "no such table".
#[test]
fn a_fresh_database_migrates_all_the_way_and_works() {
    let path = temp_path("fresh");
    let db = uniffi_ledger::open_database(path.to_string_lossy().to_string());

    let g = db.create_goal("Laptop".into(), 100000, None).unwrap();
    db.add_contribution(g.id.clone(), 25000, None, None).unwrap();
    assert_eq!(db.list_goals().unwrap()[0].current_amount_cents, 25000);

    let d = db.create_debt("Card".into(), "credit".into(), 50000, 50000, 20.0, 2500).unwrap();
    db.add_debt_payment(d.id, 10000, None, None).unwrap();
    assert_eq!(db.list_debts().unwrap()[0].remaining_amount_cents, 40000);

    drop(db);
    let _ = std::fs::remove_file(&path);
}
