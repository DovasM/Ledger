uniffi::include_scaffolding!("ledger");

mod db;

use db::open_pool;
use db::models::{TransactionRow, WalletRow, SavingsGoalRow};
use sqlx::SqlitePool;
use std::sync::Arc;
use uuid::Uuid;
use chrono::Utc;

// ── Error ────────────────────────────────────────────────────────────────────

#[derive(Debug, thiserror::Error)]
pub enum LedgerError {
    #[error("Database error: {0}")]
    DatabaseError(String),
    #[error("Not found")]
    NotFound,
    #[error("Invalid input: {0}")]
    InvalidInput(String),
}

impl From<sqlx::Error> for LedgerError {
    fn from(e: sqlx::Error) -> Self {
        LedgerError::DatabaseError(e.to_string())
    }
}

// ── DTOs (match UDL) ─────────────────────────────────────────────────────────

pub struct Transaction {
    pub id: String,
    pub wallet_id: String,
    pub title: String,
    pub category: String,
    pub amount: f64,
    pub is_income: bool,
    pub note: Option<String>,
    pub created_at: String,
}

pub struct Wallet {
    pub id: String,
    pub name: String,
    pub description: String,
    pub balance: f64,
    pub created_at: String,
}

pub struct SavingsGoal {
    pub id: String,
    pub name: String,
    pub current_amount: f64,
    pub target_amount: f64,
    pub deadline: Option<String>,
    pub created_at: String,
}

pub struct MonthSummary {
    pub total_income: f64,
    pub total_expenses: f64,
    pub net_savings: f64,
    pub transaction_count: i32,
}

// ── LedgerDb ─────────────────────────────────────────────────────────────────

pub struct LedgerDb {
    pool: SqlitePool,
    rt: tokio::runtime::Runtime,
}

// UniFFI requires the constructor to return Arc<Self>
pub fn open_database(db_path: String) -> Arc<LedgerDb> {
    let rt = tokio::runtime::Runtime::new().expect("failed to create tokio runtime");
    let pool = rt.block_on(open_pool(&db_path)).expect("failed to open database");
    Arc::new(LedgerDb { pool, rt })
}

impl LedgerDb {
    // ── Transactions ─────────────────────────────────────────────────────────

    pub fn list_transactions(&self, wallet_id: String, limit: u32, offset: u32) -> Result<Vec<Transaction>, LedgerError> {
        self.rt.block_on(async {
            let rows = sqlx::query_as::<_, TransactionRow>(
                "SELECT id, wallet_id, title, category, amount, is_income, note, created_at
                 FROM transactions WHERE wallet_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?"
            )
            .bind(&wallet_id)
            .bind(limit as i64)
            .bind(offset as i64)
            .fetch_all(&self.pool)
            .await?;
            Ok(rows.into_iter().map(row_to_transaction).collect())
        })
    }

    pub fn create_transaction(&self, wallet_id: String, title: String, category: String, amount: f64, is_income: bool, note: Option<String>) -> Result<Transaction, LedgerError> {
        if title.is_empty() { return Err(LedgerError::InvalidInput("title is required".into())); }
        if amount <= 0.0 { return Err(LedgerError::InvalidInput("amount must be positive".into())); }

        self.rt.block_on(async {
            let id = Uuid::new_v4().to_string();
            let now = Utc::now().to_rfc3339();
            let sign: f64 = if is_income { amount } else { -amount };

            sqlx::query(
                "INSERT INTO transactions (id, wallet_id, title, category, amount, is_income, note, created_at) VALUES (?,?,?,?,?,?,?,?)"
            )
            .bind(&id).bind(&wallet_id).bind(&title).bind(&category)
            .bind(amount).bind(is_income).bind(&note).bind(&now)
            .execute(&self.pool).await?;

            sqlx::query("UPDATE wallets SET balance = balance + ? WHERE id = ?")
                .bind(sign).bind(&wallet_id)
                .execute(&self.pool).await?;

            let row = sqlx::query_as::<_, TransactionRow>(
                "SELECT id, wallet_id, title, category, amount, is_income, note, created_at FROM transactions WHERE id = ?"
            )
            .bind(&id)
            .fetch_one(&self.pool).await?;

            Ok(row_to_transaction(row))
        })
    }

    pub fn update_transaction(&self, id: String, title: String, category: String, amount: f64, note: Option<String>) -> Result<Transaction, LedgerError> {
        self.rt.block_on(async {
            sqlx::query("UPDATE transactions SET title=?, category=?, amount=?, note=? WHERE id=?")
                .bind(&title).bind(&category).bind(amount).bind(&note).bind(&id)
                .execute(&self.pool).await?;

            let row = sqlx::query_as::<_, TransactionRow>(
                "SELECT id, wallet_id, title, category, amount, is_income, note, created_at FROM transactions WHERE id = ?"
            )
            .bind(&id)
            .fetch_optional(&self.pool).await?
            .ok_or(LedgerError::NotFound)?;

            Ok(row_to_transaction(row))
        })
    }

    pub fn delete_transaction(&self, id: String) -> Result<(), LedgerError> {
        self.rt.block_on(async {
            sqlx::query("DELETE FROM transactions WHERE id=?")
                .bind(&id)
                .execute(&self.pool).await?;
            Ok(())
        })
    }

    // ── Wallets ──────────────────────────────────────────────────────────────

    pub fn list_wallets(&self) -> Result<Vec<Wallet>, LedgerError> {
        self.rt.block_on(async {
            let rows = sqlx::query_as::<_, WalletRow>(
                "SELECT id, name, description, balance, created_at FROM wallets ORDER BY created_at ASC"
            )
            .fetch_all(&self.pool).await?;
            Ok(rows.into_iter().map(row_to_wallet).collect())
        })
    }

    pub fn create_wallet(&self, name: String, description: String, initial_balance: f64) -> Result<Wallet, LedgerError> {
        if name.is_empty() { return Err(LedgerError::InvalidInput("name is required".into())); }
        self.rt.block_on(async {
            let id = Uuid::new_v4().to_string();
            let now = Utc::now().to_rfc3339();
            sqlx::query(
                "INSERT INTO wallets (id, name, description, balance, created_at) VALUES (?,?,?,?,?)"
            )
            .bind(&id).bind(&name).bind(&description).bind(initial_balance).bind(&now)
            .execute(&self.pool).await?;

            let row = sqlx::query_as::<_, WalletRow>(
                "SELECT id, name, description, balance, created_at FROM wallets WHERE id=?"
            )
            .bind(&id)
            .fetch_one(&self.pool).await?;

            Ok(row_to_wallet(row))
        })
    }

    pub fn update_wallet(&self, id: String, name: String, description: String) -> Result<Wallet, LedgerError> {
        self.rt.block_on(async {
            sqlx::query("UPDATE wallets SET name=?, description=? WHERE id=?")
                .bind(&name).bind(&description).bind(&id)
                .execute(&self.pool).await?;

            let row = sqlx::query_as::<_, WalletRow>(
                "SELECT id, name, description, balance, created_at FROM wallets WHERE id=?"
            )
            .bind(&id)
            .fetch_optional(&self.pool).await?
            .ok_or(LedgerError::NotFound)?;

            Ok(row_to_wallet(row))
        })
    }

    pub fn delete_wallet(&self, id: String) -> Result<(), LedgerError> {
        self.rt.block_on(async {
            sqlx::query("DELETE FROM wallets WHERE id=?")
                .bind(&id)
                .execute(&self.pool).await?;
            Ok(())
        })
    }

    // ── Savings Goals ────────────────────────────────────────────────────────

    pub fn list_goals(&self) -> Result<Vec<SavingsGoal>, LedgerError> {
        self.rt.block_on(async {
            let rows = sqlx::query_as::<_, SavingsGoalRow>(
                "SELECT id, name, current_amount, target_amount, deadline, created_at FROM savings_goals ORDER BY created_at ASC"
            )
            .fetch_all(&self.pool).await?;
            Ok(rows.into_iter().map(row_to_goal).collect())
        })
    }

    pub fn create_goal(&self, name: String, target_amount: f64, deadline: Option<String>) -> Result<SavingsGoal, LedgerError> {
        if name.is_empty() { return Err(LedgerError::InvalidInput("name is required".into())); }
        if target_amount <= 0.0 { return Err(LedgerError::InvalidInput("target must be positive".into())); }
        self.rt.block_on(async {
            let id = Uuid::new_v4().to_string();
            let now = Utc::now().to_rfc3339();
            sqlx::query(
                "INSERT INTO savings_goals (id, name, current_amount, target_amount, deadline, created_at) VALUES (?,?,0.0,?,?,?)"
            )
            .bind(&id).bind(&name).bind(target_amount).bind(&deadline).bind(&now)
            .execute(&self.pool).await?;

            let row = sqlx::query_as::<_, SavingsGoalRow>(
                "SELECT id, name, current_amount, target_amount, deadline, created_at FROM savings_goals WHERE id=?"
            )
            .bind(&id)
            .fetch_one(&self.pool).await?;

            Ok(row_to_goal(row))
        })
    }

    pub fn add_contribution(&self, goal_id: String, amount: f64) -> Result<SavingsGoal, LedgerError> {
        if amount <= 0.0 { return Err(LedgerError::InvalidInput("amount must be positive".into())); }
        self.rt.block_on(async {
            sqlx::query("UPDATE savings_goals SET current_amount = current_amount + ? WHERE id=?")
                .bind(amount).bind(&goal_id)
                .execute(&self.pool).await?;

            let row = sqlx::query_as::<_, SavingsGoalRow>(
                "SELECT id, name, current_amount, target_amount, deadline, created_at FROM savings_goals WHERE id=?"
            )
            .bind(&goal_id)
            .fetch_optional(&self.pool).await?
            .ok_or(LedgerError::NotFound)?;

            Ok(row_to_goal(row))
        })
    }

    // ── Statistics ───────────────────────────────────────────────────────────

    pub fn get_month_summary(&self, year: i32, month: i32) -> Result<MonthSummary, LedgerError> {
        self.rt.block_on(async {
            let prefix = format!("{}-{:02}%", year, month);

            let income: f64 = sqlx::query_scalar::<_, Option<f64>>(
                "SELECT SUM(amount) FROM transactions WHERE is_income=1 AND created_at LIKE ?"
            )
            .bind(&prefix)
            .fetch_one(&self.pool).await?
            .unwrap_or(0.0);

            let expenses: f64 = sqlx::query_scalar::<_, Option<f64>>(
                "SELECT SUM(amount) FROM transactions WHERE is_income=0 AND created_at LIKE ?"
            )
            .bind(&prefix)
            .fetch_one(&self.pool).await?
            .unwrap_or(0.0);

            let count: i64 = sqlx::query_scalar::<_, i64>(
                "SELECT COUNT(*) FROM transactions WHERE created_at LIKE ?"
            )
            .bind(&prefix)
            .fetch_one(&self.pool).await?;

            Ok(MonthSummary {
                total_income: income,
                total_expenses: expenses,
                net_savings: income - expenses,
                transaction_count: count as i32,
            })
        })
    }
}

// ── Row converters ────────────────────────────────────────────────────────────

fn row_to_transaction(r: TransactionRow) -> Transaction {
    Transaction {
        id: r.id,
        wallet_id: r.wallet_id,
        title: r.title,
        category: r.category,
        amount: r.amount,
        is_income: r.is_income,
        note: r.note,
        created_at: r.created_at,
    }
}

fn row_to_wallet(r: WalletRow) -> Wallet {
    Wallet {
        id: r.id,
        name: r.name,
        description: r.description,
        balance: r.balance,
        created_at: r.created_at,
    }
}

fn row_to_goal(r: SavingsGoalRow) -> SavingsGoal {
    SavingsGoal {
        id: r.id,
        name: r.name,
        current_amount: r.current_amount,
        target_amount: r.target_amount,
        deadline: r.deadline,
        created_at: r.created_at,
    }
}
