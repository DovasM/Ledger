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
            id         TEXT PRIMARY KEY,
            title      TEXT NOT NULL,
            amount     REAL NOT NULL,
            category   TEXT NOT NULL DEFAULT '',
            wallet_id  TEXT NOT NULL,
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
