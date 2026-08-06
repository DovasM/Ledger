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

async fn run_migrations(pool: &SqlitePool) -> Result<(), sqlx::Error> {
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

    migrate_transaction_categories(pool).await?;
    clean_orphans(pool).await?;
    Ok(())
}

// Every ON DELETE CASCADE in this schema is inert: SQLite honours foreign keys only under
// PRAGMA foreign_keys=ON and this pool does not set it. The delete_* functions now clean up
// explicitly, which is deterministic and needs no pragma — this sweeps whatever earlier builds
// already stranded. Enabling the pragma instead was considered and rejected: it would turn loose
// historical rows into hard write failures at runtime.
async fn clean_orphans(pool: &SqlitePool) -> Result<(), sqlx::Error> {
    for stmt in [
        "DELETE FROM transaction_tags WHERE transaction_id NOT IN (SELECT id FROM transactions)",
        "DELETE FROM transaction_tags WHERE tag_id NOT IN (SELECT id FROM tags)",
        "DELETE FROM transactions WHERE wallet_id NOT IN (SELECT id FROM wallets)",
        "DELETE FROM recurring_transactions WHERE wallet_id NOT IN (SELECT id FROM wallets)",
        "DELETE FROM budgets WHERE category_id NOT IN (SELECT id FROM categories)",
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
