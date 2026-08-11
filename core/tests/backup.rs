//! Backup and restore.
//!
//! A backup is only worth having if it restores, and the app is still changing shape — so the case
//! that matters most is not "back up and restore today", it is "restore a file written two schema
//! versions ago". These tests cover both, and the refusals that stop a bad file from replacing good
//! data.

mod common;
use common::{day, TestDb};

use sqlx::sqlite::SqliteConnectOptions;
use sqlx::SqlitePool;
use std::path::PathBuf;
use std::str::FromStr;
use std::sync::atomic::{AtomicU32, Ordering};

static COUNTER: AtomicU32 = AtomicU32::new(0);

fn temp_path(tag: &str) -> PathBuf {
    let n = COUNTER.fetch_add(1, Ordering::SeqCst);
    let p = std::env::temp_dir().join(format!("ledger_backup_{tag}_{}_{}.db", std::process::id(), n));
    let _ = std::fs::remove_file(&p);
    p
}

/// A database with something of everything in it, so a restore has something to get wrong.
fn populate(t: &TestDb) -> String {
    let w = t.db.create_wallet("Checking".into(), String::new(), "EUR".into(), 100_000, false).unwrap().id;
    let savings = t.db.create_wallet("Savings".into(), String::new(), "EUR".into(), 50_000, false).unwrap().id;
    let cat = t.db.create_category("Groceries".into(), "cart".into(), "#00FF00".into(), true).unwrap();

    t.db.create_transaction(w.clone(), "shopping".into(), "Groceries".into(), 2_500, false, None, day("2026-03-01")).unwrap();
    t.db.create_transaction(w.clone(), "salary".into(), "Salary".into(), 300_000, true, None, day("2026-03-02")).unwrap();
    t.db.create_transfer(w.clone(), savings, 10_000, Some("saving up".into()), None).unwrap();

    let goal = t.db.create_goal("Laptop".into(), 200_000, None).unwrap();
    t.db.add_contribution(goal.id, 25_000, Some("bonus".into()), day("2026-03-03")).unwrap();

    let debt = t.db.create_debt("Card".into(), "credit".into(), 100_000, 100_000, 20.0, 5_000).unwrap();
    t.db.add_debt_payment(debt.id, 10_000, None, day("2026-03-04")).unwrap();

    t.db.create_budget(Some(cat.id), None, 15_000, "monthly".into(), 80.0, false).unwrap();
    t.db.create_budget(None, None, 31_000, "monthly".into(), 80.0, true).unwrap();
    t.db.create_recurring("Rent".into(), 145_000, "Housing".into(), w.clone(), false, "monthly".into(), "2026-04-01".into()).unwrap();

    let tag = t.db.create_tag("work".into()).unwrap();
    let tx = t.db.list_all_transactions(10, 0).unwrap()[0].id.clone();
    t.db.add_tag_to_transaction(tx, tag.id).unwrap();
    w
}

#[test]
fn a_backup_reports_what_it_wrote() {
    let t = TestDb::new();
    populate(&t);
    let dest = temp_path("report");

    let info = t.db.backup_database(dest.to_string_lossy().to_string()).unwrap();
    assert!(dest.exists(), "the file has to actually be there");
    assert_eq!(info.wallets, 2);
    assert_eq!(info.transactions, 2);
    assert_eq!(info.transfers, 1);
    assert_eq!(info.goals, 1);
    assert_eq!(info.debts, 1);
    assert_eq!(info.budgets, 2);
    assert_eq!(info.recurring, 1);
    assert_eq!(info.schema_version, 9);

    let _ = std::fs::remove_file(&dest);
}

/// Everything must come back: the rows, the history tables, and the derived figures that are
/// computed from them.
#[test]
fn a_restore_puts_everything_back() {
    let t = TestDb::new();
    let wallet = populate(&t);
    let dest = temp_path("roundtrip");
    t.db.backup_database(dest.to_string_lossy().to_string()).unwrap();

    let balance_before = t.db.list_wallets().unwrap().into_iter().find(|w| w.id == wallet).unwrap().balance_cents;
    let goal_before = t.db.list_goals().unwrap()[0].current_amount_cents;
    let debt_before = t.db.list_debts().unwrap()[0].remaining_amount_cents;

    // Wreck it thoroughly.
    for tx in t.db.list_all_transactions(100, 0).unwrap() {
        t.db.delete_transaction(tx.id).unwrap();
    }
    t.db.delete_goal(t.db.list_goals().unwrap()[0].id.clone()).unwrap();
    t.db.create_transaction(wallet.clone(), "noise".into(), "Groceries".into(), 999, false, None, day("2026-05-05")).unwrap();
    assert!(t.db.list_goals().unwrap().is_empty());

    let restored = t.db.restore_backup(dest.to_string_lossy().to_string()).unwrap();
    assert_eq!(restored.transactions, 2);

    let txs = t.db.list_all_transactions(100, 0).unwrap();
    assert_eq!(txs.len(), 2, "the noise added after the backup must be gone, not merged in");
    assert!(txs.iter().all(|x| x.title != "noise"));

    assert_eq!(t.db.list_wallets().unwrap().into_iter().find(|w| w.id == wallet).unwrap().balance_cents, balance_before);
    assert_eq!(t.db.list_goals().unwrap()[0].current_amount_cents, goal_before);
    assert_eq!(t.db.list_debts().unwrap()[0].remaining_amount_cents, debt_before);
    assert_eq!(t.db.list_goal_contributions(t.db.list_goals().unwrap()[0].id.clone()).unwrap().len(), 1);
    assert_eq!(t.db.list_transfers(10, 0).unwrap().len(), 1);
    assert_eq!(t.db.list_budgets().unwrap().len(), 2);
    assert_eq!(t.db.list_recurring().unwrap().len(), 1);
    assert_eq!(t.db.list_transaction_tags(txs[1].id.clone()).unwrap().len() + t.db.list_transaction_tags(txs[0].id.clone()).unwrap().len(), 1);

    let _ = std::fs::remove_file(&dest);
}

/// A backup written before m7, m8 and m9 existed: money is still REAL, the wallet balance is still
/// stored, and goal_contributions does not exist yet. Restoring it has to migrate it forward, not
/// fail on the columns it is missing. While the app is still changing shape this is the normal case.
async fn write_v6_backup(path: &PathBuf) {
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
            title TEXT NOT NULL, category TEXT NOT NULL DEFAULT '', amount REAL NOT NULL,
            is_income INTEGER NOT NULL DEFAULT 0, note TEXT, created_at TEXT NOT NULL,
            category_id TEXT REFERENCES categories(id), occurred_at TEXT)",
        "CREATE TABLE transfers (id TEXT PRIMARY KEY, from_wallet_id TEXT NOT NULL REFERENCES wallets(id),
            to_wallet_id TEXT NOT NULL REFERENCES wallets(id), amount REAL NOT NULL, note TEXT, created_at TEXT NOT NULL)",
        "CREATE TABLE savings_goals (id TEXT PRIMARY KEY, name TEXT NOT NULL, current_amount REAL NOT NULL DEFAULT 0.0,
            target_amount REAL NOT NULL, deadline TEXT, created_at TEXT NOT NULL)",
        "CREATE TABLE debts (id TEXT PRIMARY KEY, name TEXT NOT NULL, debt_type TEXT NOT NULL, total_amount REAL NOT NULL,
            remaining_amount REAL NOT NULL, apr REAL NOT NULL, monthly_payment REAL NOT NULL, created_at TEXT NOT NULL)",
        "CREATE TABLE budgets (id TEXT PRIMARY KEY, category_id TEXT REFERENCES categories(id),
            wallet_id TEXT REFERENCES wallets(id), limit_amount REAL NOT NULL, period TEXT NOT NULL DEFAULT 'monthly',
            alert_threshold REAL NOT NULL DEFAULT 80, carry_over INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL)",
        "CREATE TABLE recurring_transactions (id TEXT PRIMARY KEY, title TEXT NOT NULL, amount REAL NOT NULL,
            category TEXT NOT NULL DEFAULT '', wallet_id TEXT NOT NULL, is_income INTEGER NOT NULL DEFAULT 0,
            frequency TEXT NOT NULL DEFAULT 'monthly', next_date TEXT NOT NULL, created_at TEXT NOT NULL,
            category_id TEXT REFERENCES categories(id))",
        "CREATE TABLE tags (id TEXT PRIMARY KEY, name TEXT NOT NULL UNIQUE, created_at TEXT NOT NULL)",
        "CREATE TABLE transaction_tags (transaction_id TEXT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
            tag_id TEXT NOT NULL REFERENCES tags(id) ON DELETE CASCADE, PRIMARY KEY (transaction_id, tag_id))",
        "CREATE TABLE price_alerts (id TEXT PRIMARY KEY, symbol TEXT NOT NULL, asset_name TEXT NOT NULL DEFAULT '',
            target_price REAL NOT NULL, direction TEXT NOT NULL DEFAULT 'above', active INTEGER NOT NULL DEFAULT 1,
            created_at TEXT NOT NULL)",

        "INSERT INTO wallets (id, name, balance, created_at, currency) VALUES ('w1', 'Old Checking', 65.43, '2026-01-01T00:00:00Z', 'EUR')",
        "INSERT INTO transactions (id, wallet_id, title, amount, is_income, created_at, occurred_at)
            VALUES ('t1', 'w1', 'old shopping', 0.29, 0, '2026-02-01T00:00:00Z', '2026-02-01T00:00:00Z')",
        "INSERT INTO transactions (id, wallet_id, title, amount, is_income, created_at, occurred_at)
            VALUES ('t2', 'w1', 'old pay', 12.34, 1, '2026-02-02T00:00:00Z', NULL)",
        "INSERT INTO savings_goals VALUES ('g1', 'Old Goal', 85.00, 100.00, NULL, '2026-01-01T00:00:00Z')",
        "INSERT INTO debts VALUES ('d1', 'Old Debt', 'loan', 50.00, 34.00, 4.5, 2.00, '2026-01-01T00:00:00Z')",
        "INSERT INTO budgets (id, category_id, wallet_id, limit_amount, period, alert_threshold, carry_over, created_at)
            VALUES ('b1', NULL, NULL, 250.00, 'monthly', 80.0, 0, '2026-01-01T00:00:00Z')",
    ] {
        sqlx::query(stmt).execute(&pool).await.unwrap();
    }
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
fn an_older_backup_is_migrated_forward_on_restore() {
    let t = TestDb::new();
    populate(&t);

    let old = temp_path("v6");
    tokio::runtime::Runtime::new().unwrap().block_on(write_v6_backup(&old));

    // Inspecting it reports the version it was written at, and does not touch it.
    let before = std::fs::metadata(&old).unwrap().len();
    let info = t.db.inspect_backup(old.to_string_lossy().to_string()).unwrap();
    assert_eq!(info.schema_version, 6);
    assert_eq!(info.wallets, 1);
    assert_eq!(info.transactions, 2);
    assert_eq!(std::fs::metadata(&old).unwrap().len(), before, "inspecting must not migrate the user's file");

    t.db.restore_backup(old.to_string_lossy().to_string()).unwrap();

    // Money arrives as cents, rounded the way m8 rounds it.
    let txs = t.db.list_all_transactions(100, 0).unwrap();
    assert_eq!(txs.len(), 2);
    let by = |title: &str| txs.iter().find(|x| x.title == title).unwrap();
    assert_eq!(by("old shopping").amount_cents, 29, "0.29 must not lose its last cent");
    assert_eq!(by("old pay").amount_cents, 1_234);

    // occurred_at was nullable back then; m8 fills it from created_at.
    assert_eq!(by("old pay").occurred_at, "2026-02-02T00:00:00Z");

    // The wallet balance was a stored total then and is derived now — and still reads the same.
    assert_eq!(t.db.list_wallets().unwrap()[0].balance_cents, 6_543);

    // The goal balance lived in a column that no longer exists; m7 turns it into an opening entry.
    let goal = &t.db.list_goals().unwrap()[0];
    assert_eq!(goal.current_amount_cents, 8_500);
    assert_eq!(goal.target_amount_cents, 10_000);
    let opening = t.db.list_goal_contributions(goal.id.clone()).unwrap();
    assert_eq!(opening.len(), 1);
    assert_eq!(opening[0].kind, "opening");

    // Same for the debt.
    let debt = &t.db.list_debts().unwrap()[0];
    assert_eq!(debt.remaining_amount_cents, 3_400);
    assert_eq!(t.db.list_debt_payments(debt.id.clone()).unwrap()[0].amount_cents, 1_600);

    assert_eq!(t.db.list_budgets().unwrap()[0].limit_amount_cents, 25_000);

    let _ = std::fs::remove_file(&old);
}

/// A file written by a future build cannot be understood, and guessing at it would be worse than
/// refusing it.
#[test]
fn a_newer_backup_is_refused() {
    let t = TestDb::new();
    let future = temp_path("future");
    tokio::runtime::Runtime::new().unwrap().block_on(async {
        let opts = SqliteConnectOptions::from_str(&format!("sqlite:{}", future.display()))
            .unwrap()
            .create_if_missing(true);
        let pool = SqlitePool::connect_with(opts).await.unwrap();
        sqlx::query("CREATE TABLE schema_version (version INTEGER PRIMARY KEY, applied_at TEXT NOT NULL)")
            .execute(&pool).await.unwrap();
        sqlx::query("INSERT INTO schema_version VALUES (99, '2027-01-01T00:00:00Z')")
            .execute(&pool).await.unwrap();
        pool.close().await;
    });

    let err = t.db.inspect_backup(future.to_string_lossy().to_string());
    assert!(err.is_err());
    assert!(t.db.restore_backup(future.to_string_lossy().to_string()).is_err());

    let _ = std::fs::remove_file(&future);
}

#[test]
fn a_file_that_is_not_a_backup_is_refused() {
    let t = TestDb::new();
    populate(&t);
    let before = t.db.list_all_transactions(100, 0).unwrap().len();

    let junk = temp_path("junk");
    std::fs::write(&junk, b"this is not a database, it is a shopping list").unwrap();
    assert!(t.db.restore_backup(junk.to_string_lossy().to_string()).is_err());

    assert!(t.db.inspect_backup("/no/such/file.db".into()).is_err());

    // A refusal must leave the live data exactly as it was.
    assert_eq!(t.db.list_all_transactions(100, 0).unwrap().len(), before);

    let _ = std::fs::remove_file(&junk);
}

/// Backing up twice to the same place is the normal case — yesterday's file gets replaced.
#[test]
fn a_backup_overwrites_the_previous_one() {
    let t = TestDb::new();
    let w = populate(&t);
    let dest = temp_path("overwrite");

    let first = t.db.backup_database(dest.to_string_lossy().to_string()).unwrap();
    assert_eq!(first.transactions, 2);

    t.db.create_transaction(w, "one more".into(), "Groceries".into(), 500, false, None, day("2026-03-09")).unwrap();
    let second = t.db.backup_database(dest.to_string_lossy().to_string()).unwrap();
    assert_eq!(second.transactions, 3, "the second backup must reflect the newer data");

    assert_eq!(t.db.inspect_backup(dest.to_string_lossy().to_string()).unwrap().transactions, 3);

    let _ = std::fs::remove_file(&dest);
}

/// Restoring into an empty database is what happens after a reinstall, which is the whole point.
#[test]
fn a_backup_restores_into_a_fresh_install() {
    let source = TestDb::new();
    populate(&source);
    let dest = temp_path("reinstall");
    source.db.backup_database(dest.to_string_lossy().to_string()).unwrap();

    let fresh = TestDb::new();
    assert!(fresh.db.list_all_transactions(10, 0).unwrap().is_empty());

    let restored = fresh.db.restore_backup(dest.to_string_lossy().to_string()).unwrap();
    assert_eq!(restored.transactions, 2);
    assert_eq!(fresh.db.list_wallets().unwrap().len(), 2);
    assert_eq!(fresh.db.list_goals().unwrap()[0].current_amount_cents, 25_000);
    assert_eq!(fresh.db.list_transfers(10, 0).unwrap().len(), 1);

    let _ = std::fs::remove_file(&dest);
}

/// The app keeps working on the same connection afterwards — a restore that left the pool pointing
/// at a detached or half-replaced database would look fine until the next write.
#[test]
fn the_database_is_still_usable_after_a_restore() {
    let t = TestDb::new();
    let w = populate(&t);
    let dest = temp_path("usable");
    t.db.backup_database(dest.to_string_lossy().to_string()).unwrap();
    t.db.restore_backup(dest.to_string_lossy().to_string()).unwrap();

    let added = t.db.create_transaction(w, "after restore".into(), "Groceries".into(), 1_000, false, None, day("2026-03-10")).unwrap();
    assert_eq!(t.db.list_all_transactions(100, 0).unwrap().len(), 3);
    t.db.delete_transaction(added.id).unwrap();
    assert_eq!(t.db.list_all_transactions(100, 0).unwrap().len(), 2);

    let _ = std::fs::remove_file(&dest);
}
