pub mod models;

use sqlx::{SqlitePool, sqlite::SqliteConnectOptions};
use std::str::FromStr;

pub async fn open_pool(db_path: &str) -> Result<SqlitePool, sqlx::Error> {
    let options = SqliteConnectOptions::from_str(&format!("sqlite:{}", db_path))?
        .create_if_missing(true);
    let pool = SqlitePool::connect_with(options).await?;
    run_migrations(&pool).await?;
    Ok(pool)
}

// Schema changes are numbered and recorded, rather than the previous mix of
// CREATE TABLE IF NOT EXISTS and ALTER statements whose errors were swallowed. That worked while
// there were two of them; it cannot be reasoned about once there are ten.
//
// Databases that predate this table bootstrap at version 0 and re-run m1 and m2, so **every
// migration must stay idempotent** — no exceptions, even for future ones. It costs little and it
// is the property that makes a mid-life migration table safe to introduce at all.
async fn run_migrations(pool: &SqlitePool) -> Result<(), sqlx::Error> {
    sqlx::query(
        "CREATE TABLE IF NOT EXISTS schema_version (
            version    INTEGER PRIMARY KEY,
            applied_at TEXT NOT NULL
        )",
    )
    .execute(pool)
    .await?;

    let (applied,): (i64,) =
        sqlx::query_as("SELECT COALESCE(MAX(version), 0) FROM schema_version")
            .fetch_one(pool)
            .await?;

    if applied < 1 {
        m1_baseline_tables(pool).await?;
        record_version(pool, 1).await?;
    }
    if applied < 2 {
        m2_category_links(pool).await?;
        record_version(pool, 2).await?;
    }
    if applied < 3 {
        m3_indexes(pool).await?;
        record_version(pool, 3).await?;
    }
    if applied < 4 {
        m4_transfers_currency_budgets(pool).await?;
        record_version(pool, 4).await?;
    }
    if applied < 5 {
        m5_off_budget_wallets(pool).await?;
        record_version(pool, 5).await?;
    }
    if applied < 6 {
        m6_transaction_occurred_at(pool).await?;
        record_version(pool, 6).await?;
    }
    if applied < 7 {
        m7_goal_and_debt_history(pool).await?;
        record_version(pool, 7).await?;
    }

    Ok(())
}

async fn column_exists(pool: &SqlitePool, table: &str, column: &str) -> Result<bool, sqlx::Error> {
    let cols: Vec<(i64, String, String, i64, Option<String>, i64)> =
        sqlx::query_as(&format!("PRAGMA table_info({table})"))
            .fetch_all(pool)
            .await?;
    Ok(cols.iter().any(|c| c.1 == column))
}

// A savings goal recorded only how much was in it, and a debt only how much was left. Both were
// mutated in place, so "I put 50 in last Tuesday" and "I paid 200 in March" existed nowhere: there
// was no history to show, nothing to correct, and a mistyped contribution could only be fixed by
// typing over the total.
//
// Contributions and payments now get their own tables and the two totals become derived, the same
// answer this project already needed for wallets.balance — a stored running total that drifted the
// moment one code path forgot to update it. The columns are dropped rather than kept in sync,
// because a second source of truth is the thing that goes wrong.
async fn m7_goal_and_debt_history(pool: &SqlitePool) -> Result<(), sqlx::Error> {
    sqlx::query(
        r#"
        CREATE TABLE IF NOT EXISTS goal_contributions (
            id          TEXT PRIMARY KEY,
            goal_id     TEXT NOT NULL REFERENCES savings_goals(id),
            amount      REAL NOT NULL,
            note        TEXT,
            kind        TEXT NOT NULL DEFAULT 'contribution',
            occurred_at TEXT NOT NULL,
            created_at  TEXT NOT NULL
        );
        CREATE INDEX IF NOT EXISTS idx_contrib_goal ON goal_contributions(goal_id);
        CREATE TABLE IF NOT EXISTS debt_payments (
            id          TEXT PRIMARY KEY,
            debt_id     TEXT NOT NULL REFERENCES debts(id),
            amount      REAL NOT NULL,
            note        TEXT,
            kind        TEXT NOT NULL DEFAULT 'payment',
            occurred_at TEXT NOT NULL,
            created_at  TEXT NOT NULL
        );
        CREATE INDEX IF NOT EXISTS idx_payment_debt ON debt_payments(debt_id);
        "#,
    )
    .execute(pool)
    .await?;

    let now = chrono::Utc::now().to_rfc3339();

    // The existing totals are real money and the only record of it, so each becomes one opening
    // entry before the column carrying it goes away. Both halves are guarded on the column still
    // existing, which is what makes re-running this on a bootstrapped database a no-op.
    if column_exists(pool, "savings_goals", "current_amount").await? {
        sqlx::query(
            "INSERT INTO goal_contributions (id, goal_id, amount, note, kind, occurred_at, created_at)
             SELECT lower(hex(randomblob(16))), id, current_amount, 'Balance before contributions were itemised', 'opening', created_at, ?
             FROM savings_goals WHERE current_amount > 0",
        )
        .bind(&now)
        .execute(pool)
        .await?;

        rebuild_table(
            pool,
            "savings_goals",
            "CREATE TABLE savings_goals_new (
                id            TEXT PRIMARY KEY,
                name          TEXT NOT NULL,
                target_amount REAL NOT NULL,
                deadline      TEXT,
                created_at    TEXT NOT NULL
            )",
            "INSERT INTO savings_goals_new (id, name, target_amount, deadline, created_at)
             SELECT id, name, target_amount, deadline, created_at FROM savings_goals",
        )
        .await?;
    }

    if column_exists(pool, "debts", "remaining_amount").await? {
        sqlx::query(
            "INSERT INTO debt_payments (id, debt_id, amount, note, kind, occurred_at, created_at)
             SELECT lower(hex(randomblob(16))), id, total_amount - remaining_amount, 'Paid off before payments were itemised', 'opening', created_at, ?
             FROM debts WHERE total_amount - remaining_amount > 0",
        )
        .bind(&now)
        .execute(pool)
        .await?;

        rebuild_table(
            pool,
            "debts",
            "CREATE TABLE debts_new (
                id              TEXT PRIMARY KEY,
                name            TEXT NOT NULL,
                debt_type       TEXT NOT NULL,
                total_amount    REAL NOT NULL,
                apr             REAL NOT NULL,
                monthly_payment REAL NOT NULL,
                created_at      TEXT NOT NULL
            )",
            "INSERT INTO debts_new (id, name, debt_type, total_amount, apr, monthly_payment, created_at)
             SELECT id, name, debt_type, total_amount, apr, monthly_payment, created_at FROM debts",
        )
        .await?;
    }

    Ok(())
}

// SQLite cannot drop a column on the old versions this app has to run against, so the table is
// rebuilt: create the new shape, copy, drop, rename. The caller guards the call on the current
// shape, which is what keeps it idempotent.
// Every statement goes in **one** query on purpose. Split across separate `execute` calls they are
// handed out to different pool connections, and the rename then runs against a connection whose
// cached schema still lists the dropped table — it fails with "there is already another table or
// index with this name". One string, one connection, one consistent view of the schema.
//
// Foreign keys are switched off across the rebuild, which is SQLite's own prescribed procedure for
// altering a table: rows already seeded into goal_contributions reference savings_goals, so dropping
// it with enforcement on fails with "FOREIGN KEY constraint failed" — and a migration that fails is
// an app that cannot open its database at all. The pragma is a no-op inside a transaction, which is
// why these statements are not wrapped in one.
async fn rebuild_table(
    pool: &SqlitePool,
    table: &str,
    create_new: &str,
    copy_rows: &str,
) -> Result<(), sqlx::Error> {
    sqlx::query(&format!(
        "PRAGMA foreign_keys = OFF;
         {create_new};
         {copy_rows};
         DROP TABLE {table};
         ALTER TABLE {table}_new RENAME TO {table};
         PRAGMA foreign_keys = ON;"
    ))
    .execute(pool)
    .await?;
    Ok(())
}

// created_at meant two things at once: the row's creation time *and* the date the transaction
// happened — the import wrote the Money Manager date into it, and the date picker overwrote it.
// occurred_at now carries the event date; created_at goes back to meaning what it says.
async fn m6_transaction_occurred_at(pool: &SqlitePool) -> Result<(), sqlx::Error> {
    let _ = sqlx::query("ALTER TABLE transactions ADD COLUMN occurred_at TEXT")
        .execute(pool)
        .await;
    // Existing rows held the event date in created_at, so that is the honest source to seed from.
    sqlx::query("UPDATE transactions SET occurred_at = created_at WHERE occurred_at IS NULL")
        .execute(pool)
        .await?;
    sqlx::query("CREATE INDEX IF NOT EXISTS idx_tx_occurred ON transactions(occurred_at)")
        .execute(pool)
        .await?;
    Ok(())
}

// "This account should not count toward my budget" is a property of the account, not of each
// budget — a work account stays off-budget no matter how many budgets exist, and a new personal
// wallet joins automatically. A per-budget wallet list would need a join table and re-editing every
// budget whenever an account is added.
async fn m5_off_budget_wallets(pool: &SqlitePool) -> Result<(), sqlx::Error> {
    let _ = sqlx::query("ALTER TABLE wallets ADD COLUMN off_budget INTEGER NOT NULL DEFAULT 0")
        .execute(pool)
        .await;
    Ok(())
}

async fn record_version(pool: &SqlitePool, version: i64) -> Result<(), sqlx::Error> {
    sqlx::query("INSERT OR REPLACE INTO schema_version (version, applied_at) VALUES (?, ?)")
        .bind(version)
        .bind(chrono::Utc::now().to_rfc3339())
        .execute(pool)
        .await?;
    Ok(())
}

// Nothing here is indexed by default beyond the primary keys, so every category breakdown and
// wallet filter was a full table scan.
async fn m3_indexes(pool: &SqlitePool) -> Result<(), sqlx::Error> {
    for stmt in [
        "CREATE INDEX IF NOT EXISTS idx_tx_wallet    ON transactions(wallet_id)",
        "CREATE INDEX IF NOT EXISTS idx_tx_category  ON transactions(category_id)",
        "CREATE INDEX IF NOT EXISTS idx_tx_created   ON transactions(created_at)",
        "CREATE INDEX IF NOT EXISTS idx_rec_wallet   ON recurring_transactions(wallet_id)",
        "CREATE INDEX IF NOT EXISTS idx_rec_category ON recurring_transactions(category_id)",
        "CREATE INDEX IF NOT EXISTS idx_budget_cat   ON budgets(category_id)",
        "CREATE INDEX IF NOT EXISTS idx_taglink_tag  ON transaction_tags(tag_id)",
    ] {
        sqlx::query(stmt).execute(pool).await?;
    }
    Ok(())
}

async fn m4_transfers_currency_budgets(pool: &SqlitePool) -> Result<(), sqlx::Error> {
    // A transfer is neither income nor expense. Folded into `transactions` it would appear as an
    // expense in one wallet and income in the other, and every report would count it twice.
    sqlx::query(
        r#"
        CREATE TABLE IF NOT EXISTS transfers (
            id             TEXT PRIMARY KEY,
            from_wallet_id TEXT NOT NULL REFERENCES wallets(id),
            to_wallet_id   TEXT NOT NULL REFERENCES wallets(id),
            amount         REAL NOT NULL,
            note           TEXT,
            created_at     TEXT NOT NULL
        );
        CREATE INDEX IF NOT EXISTS idx_transfer_from ON transfers(from_wallet_id);
        CREATE INDEX IF NOT EXISTS idx_transfer_to   ON transfers(to_wallet_id);
        "#,
    )
    .execute(pool)
    .await?;

    let _ = sqlx::query("ALTER TABLE wallets ADD COLUMN currency TEXT NOT NULL DEFAULT ''")
        .execute(pool)
        .await;

    // The Money Manager import passed the account's currency code as the wallet *description*,
    // so every imported wallet is described as "EUR". Move it to the column it belongs in.
    sqlx::query(
        "UPDATE wallets SET currency = description, description = ''
         WHERE currency = '' AND description GLOB '[A-Z][A-Z][A-Z]'",
    )
    .execute(pool)
    .await?;

    rebuild_budgets_if_needed(pool).await?;
    dedupe_before_unique_indexes(pool).await?;

    sqlx::query(
        "CREATE UNIQUE INDEX IF NOT EXISTS idx_categories_unique
         ON categories(name COLLATE NOCASE, is_expense)",
    )
    .execute(pool)
    .await?;
    // NULLs compare distinct in SQLite, so this does not stop several wallet-only budgets sharing
    // a period. It catches the case that actually bit: repeated budgets for one category, which
    // CategoryPace sums.
    sqlx::query(
        "CREATE UNIQUE INDEX IF NOT EXISTS idx_budgets_unique
         ON budgets(category_id, wallet_id, period)",
    )
    .execute(pool)
    .await?;

    Ok(())
}

// budgets.category_id was TEXT NOT NULL, but a wallet-level budget ("800 from Checking, any
// category") has no category. SQLite cannot relax NOT NULL in place, so the table is rebuilt —
// guarded by the current shape, which makes it idempotent.
async fn rebuild_budgets_if_needed(pool: &SqlitePool) -> Result<(), sqlx::Error> {
    let cols: Vec<(i64, String, String, i64, Option<String>, i64)> =
        sqlx::query_as("PRAGMA table_info(budgets)").fetch_all(pool).await?;
    let category_is_not_null = cols.iter().any(|c| c.1 == "category_id" && c.3 == 1);
    if !category_is_not_null {
        return Ok(());
    }

    sqlx::query(
        r#"
        CREATE TABLE budgets_rebuilt (
            id              TEXT PRIMARY KEY,
            category_id     TEXT REFERENCES categories(id),
            wallet_id       TEXT REFERENCES wallets(id),
            limit_amount    REAL NOT NULL,
            period          TEXT NOT NULL DEFAULT 'monthly',
            alert_threshold REAL NOT NULL DEFAULT 80,
            carry_over      INTEGER NOT NULL DEFAULT 0,
            created_at      TEXT NOT NULL
        );
        INSERT INTO budgets_rebuilt (id, category_id, wallet_id, limit_amount, period, alert_threshold, carry_over, created_at)
            SELECT id, category_id, NULL, limit_amount, period, alert_threshold, 0, created_at FROM budgets;
        DROP TABLE budgets;
        ALTER TABLE budgets_rebuilt RENAME TO budgets;
        CREATE INDEX IF NOT EXISTS idx_budget_cat ON budgets(category_id);
        "#,
    )
    .execute(pool)
    .await?;

    Ok(())
}

// The unique indexes below cannot be created while duplicates exist. Keep the oldest row of each
// group and re-point everything that referenced the others.
async fn dedupe_before_unique_indexes(pool: &SqlitePool) -> Result<(), sqlx::Error> {
    let dupes: Vec<(String, String)> = sqlx::query_as(
        "SELECT c.id, keep.id FROM categories c
         JOIN (SELECT MIN(created_at) AS ca, name, is_expense FROM categories
               GROUP BY name COLLATE NOCASE, is_expense) g
           ON g.name = c.name COLLATE NOCASE AND g.is_expense = c.is_expense
         JOIN categories keep ON keep.name = c.name COLLATE NOCASE
                             AND keep.is_expense = c.is_expense AND keep.created_at = g.ca
         WHERE c.id <> keep.id",
    )
    .fetch_all(pool)
    .await?;

    for (dead, keep) in dupes {
        for table in ["transactions", "recurring_transactions", "budgets"] {
            sqlx::query(&format!("UPDATE {table} SET category_id = ? WHERE category_id = ?"))
                .bind(&keep).bind(&dead).execute(pool).await?;
        }
        sqlx::query("DELETE FROM categories WHERE id = ?").bind(&dead).execute(pool).await?;
    }

    sqlx::query(
        "DELETE FROM budgets WHERE id NOT IN (
            SELECT MIN(id) FROM budgets GROUP BY category_id, wallet_id, period
         )",
    )
    .execute(pool)
    .await?;

    Ok(())
}

async fn m1_baseline_tables(pool: &SqlitePool) -> Result<(), sqlx::Error> {
    sqlx::query(
        r#"
        CREATE TABLE IF NOT EXISTS wallets (
            id          TEXT PRIMARY KEY,
            name        TEXT NOT NULL,
            description TEXT NOT NULL DEFAULT '',
            balance     REAL NOT NULL DEFAULT 0.0,
            created_at  TEXT NOT NULL
        );

        CREATE TABLE IF NOT EXISTS transactions (
            id          TEXT PRIMARY KEY,
            wallet_id   TEXT NOT NULL REFERENCES wallets(id) ON DELETE CASCADE,
            title       TEXT NOT NULL,
            -- category_id is the real link. `category` is kept as the label the transaction was
            -- filed under: it survives deleting the category, and reads fall back to it when
            -- category_id is NULL. Renaming a category updates both.
            category_id TEXT REFERENCES categories(id),
            category    TEXT NOT NULL DEFAULT '',
            amount      REAL NOT NULL,
            is_income   INTEGER NOT NULL DEFAULT 0,
            note        TEXT,
            created_at  TEXT NOT NULL
        );

        CREATE TABLE IF NOT EXISTS savings_goals (
            id             TEXT PRIMARY KEY,
            name           TEXT NOT NULL,
            current_amount REAL NOT NULL DEFAULT 0.0,
            target_amount  REAL NOT NULL,
            deadline       TEXT,
            created_at     TEXT NOT NULL
        );

        CREATE TABLE IF NOT EXISTS categories (
            id         TEXT PRIMARY KEY,
            name       TEXT NOT NULL,
            icon_name  TEXT NOT NULL DEFAULT 'label',
            color_hex  TEXT NOT NULL DEFAULT '#00513F',
            is_expense INTEGER NOT NULL DEFAULT 1,
            created_at TEXT NOT NULL
        );

        CREATE TABLE IF NOT EXISTS budgets (
            id                TEXT PRIMARY KEY,
            category_id       TEXT NOT NULL,
            limit_amount      REAL NOT NULL,
            period            TEXT NOT NULL DEFAULT 'monthly',
            alert_threshold   REAL NOT NULL DEFAULT 80,
            created_at        TEXT NOT NULL
        );

        CREATE TABLE IF NOT EXISTS debts (
            id               TEXT PRIMARY KEY,
            name             TEXT NOT NULL,
            debt_type        TEXT NOT NULL DEFAULT 'Other',
            total_amount     REAL NOT NULL,
            remaining_amount REAL NOT NULL,
            apr              REAL NOT NULL DEFAULT 0.0,
            monthly_payment  REAL NOT NULL,
            created_at       TEXT NOT NULL
        );

        CREATE TABLE IF NOT EXISTS recurring_transactions (
            id          TEXT PRIMARY KEY,
            title       TEXT NOT NULL,
            amount      REAL NOT NULL,
            category_id TEXT REFERENCES categories(id),
            category    TEXT NOT NULL DEFAULT '',
            wallet_id   TEXT NOT NULL REFERENCES wallets(id),
            is_income  INTEGER NOT NULL DEFAULT 0,
            frequency  TEXT NOT NULL DEFAULT 'monthly',
            next_date  TEXT NOT NULL,
            created_at TEXT NOT NULL
        );

        CREATE TABLE IF NOT EXISTS tags (
            id         TEXT PRIMARY KEY,
            name       TEXT NOT NULL UNIQUE,
            created_at TEXT NOT NULL
        );

        CREATE TABLE IF NOT EXISTS transaction_tags (
            transaction_id TEXT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
            tag_id         TEXT NOT NULL REFERENCES tags(id) ON DELETE CASCADE,
            PRIMARY KEY (transaction_id, tag_id)
        );

        CREATE TABLE IF NOT EXISTS price_alerts (
            id           TEXT PRIMARY KEY,
            symbol       TEXT NOT NULL,
            asset_name   TEXT NOT NULL DEFAULT '',
            target_price REAL NOT NULL,
            direction    TEXT NOT NULL DEFAULT 'above',
            active       INTEGER NOT NULL DEFAULT 1,
            created_at   TEXT NOT NULL
        );
        "#,
    )
    .execute(pool)
    .await?;
    Ok(())
}

async fn m2_category_links(pool: &SqlitePool) -> Result<(), sqlx::Error> {
    migrate_transaction_categories(pool).await?;
    clean_orphans(pool).await?;
    Ok(())
}

// Correction, established while writing m7: foreign keys ARE enforced here. `DROP TABLE
// savings_goals` with child rows present failed with SQLITE_CONSTRAINT_FOREIGNKEY (787), and
// DROP TABLE only runs that check under PRAGMA foreign_keys=ON — sqlx's SqliteConnectOptions
// turns it on by default, which nothing in this file asks for or disables. An earlier comment
// here claimed the opposite and concluded every ON DELETE CASCADE was inert; that reasoning was
// wrong, and any new code relying on it would be too.
//
// The explicit cleanup in each delete_* stays, and matters more rather than less: only
// transactions.wallet_id and the two transaction_tags columns declare ON DELETE CASCADE, so
// without it deleting a goal, debt, wallet or category would be a hard runtime failure on the
// tables that do not. This sweep clears rows stranded before those deletes existed.
async fn clean_orphans(pool: &SqlitePool) -> Result<(), sqlx::Error> {
    for stmt in [
        "DELETE FROM transaction_tags WHERE transaction_id NOT IN (SELECT id FROM transactions)",
        "DELETE FROM transaction_tags WHERE tag_id NOT IN (SELECT id FROM tags)",
        "DELETE FROM transactions WHERE wallet_id NOT IN (SELECT id FROM wallets)",
        "DELETE FROM recurring_transactions WHERE wallet_id NOT IN (SELECT id FROM wallets)",
        "DELETE FROM budgets WHERE category_id NOT IN (SELECT id FROM categories)",
        // goal_contributions and debt_payments are deliberately not swept here: this runs inside
        // m2, before m7 creates them, so a fresh install would fail on "no such table". Their
        // cleanup lives in delete_goal and delete_debt, which is where it belongs anyway.
    ] {
        sqlx::query(stmt).execute(pool).await?;
    }
    Ok(())
}

// Transactions used to store only the category *name*, so renaming a category silently orphaned
// every transaction filed under the old name (and a budget on it then matched nothing). This adds
// the id link and backfills it, creating a category for any name that no longer has one.
async fn migrate_transaction_categories(pool: &SqlitePool) -> Result<(), sqlx::Error> {
    // ALTER fails once the column exists; there is no IF NOT EXISTS for columns in SQLite.
    let _ = sqlx::query("ALTER TABLE transactions ADD COLUMN category_id TEXT REFERENCES categories(id)")
        .execute(pool)
        .await;
    let _ = sqlx::query("ALTER TABLE recurring_transactions ADD COLUMN category_id TEXT REFERENCES categories(id)")
        .execute(pool)
        .await;

    // is_expense must match too: the same name legitimately exists in both directions after a
    // Money Manager import (Gifts, Other), and matching on name alone links expense rows to the
    // income category.
    for table in ["transactions", "recurring_transactions"] {
        sqlx::query(&format!(
            "UPDATE {table}
             SET category_id = (SELECT c.id FROM categories c
                                WHERE c.name = {table}.category COLLATE NOCASE
                                  AND c.is_expense <> {table}.is_income)
             WHERE category_id IS NULL AND category <> ''"
        ))
        .execute(pool)
        .await?;

        // Repair rows an earlier build linked to the wrong-typed twin. Idempotent: once the link
        // points at the right type the first EXISTS stops matching.
        sqlx::query(&format!(
            "UPDATE {table}
             SET category_id = (SELECT c.id FROM categories c
                                WHERE c.name = {table}.category COLLATE NOCASE
                                  AND c.is_expense <> {table}.is_income)
             WHERE EXISTS (SELECT 1 FROM categories bad
                           WHERE bad.id = {table}.category_id AND bad.is_expense = {table}.is_income)
               AND EXISTS (SELECT 1 FROM categories good
                           WHERE good.name = {table}.category COLLATE NOCASE
                             AND good.is_expense <> {table}.is_income)"
        ))
        .execute(pool)
        .await?;
    }

    // Names with no category row left — typically a category that was renamed or deleted before
    // this migration existed. Recreate it so the history stays addressable instead of dangling.
    let orphans: Vec<(String, bool)> = sqlx::query_as(
        "SELECT category, MIN(is_income) FROM transactions
         WHERE category_id IS NULL AND category <> '' GROUP BY category COLLATE NOCASE",
    )
    .fetch_all(pool)
    .await?;

    for (name, is_income) in orphans {
        let id = uuid::Uuid::new_v4().to_string();
        sqlx::query(
            "INSERT INTO categories (id, name, icon_name, color_hex, is_expense, created_at)
             VALUES (?,?,?,?,?,?)",
        )
        .bind(&id)
        .bind(&name)
        .bind("shopping_bag")
        .bind("#00838F")
        .bind(!is_income)
        .bind(chrono::Utc::now().to_rfc3339())
        .execute(pool)
        .await?;

        for table in ["transactions", "recurring_transactions"] {
            sqlx::query(&format!(
                "UPDATE {table} SET category_id = ? WHERE category_id IS NULL AND category = ? COLLATE NOCASE"
            ))
            .bind(&id)
            .bind(&name)
            .execute(pool)
            .await?;
        }
    }

    Ok(())
}
