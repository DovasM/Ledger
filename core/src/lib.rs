uniffi::include_scaffolding!("ledger");

mod db;
mod llama;

pub use llama::{LlamaEngine, LlamaError};

pub fn llama_create(model_path: String, n_ctx: u32) -> Result<Arc<LlamaEngine>, LlamaError> {
    LlamaEngine::new(model_path, n_ctx).map(Arc::new)
}

use db::open_pool;
use db::models::{
    TransactionRow, WalletRow, TransferRow, SavingsGoalRow,
    CategoryRow, BudgetRow, DebtRow, RecurringTransactionRow, TagRow, PriceAlertRow,
};
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
    pub currency: String,
    pub balance: f64,
    pub off_budget: bool,
    pub created_at: String,
}

pub struct Transfer {
    pub id: String,
    pub from_wallet_id: String,
    pub to_wallet_id: String,
    pub amount: f64,
    pub note: Option<String>,
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

pub struct Category {
    pub id: String,
    pub name: String,
    pub icon_name: String,
    pub color_hex: String,
    pub is_expense: bool,
    pub created_at: String,
}

pub struct Budget {
    pub id: String,
    pub category_id: Option<String>,
    pub wallet_id: Option<String>,
    pub limit_amount: f64,
    pub period: String,
    pub alert_threshold: f64,
    pub carry_over: bool,
    pub created_at: String,
}

pub struct Debt {
    pub id: String,
    pub name: String,
    pub debt_type: String,
    pub total_amount: f64,
    pub remaining_amount: f64,
    pub apr: f64,
    pub monthly_payment: f64,
    pub created_at: String,
}

pub struct RecurringTransaction {
    pub id: String,
    pub title: String,
    pub amount: f64,
    pub category: String,
    pub wallet_id: String,
    pub is_income: bool,
    pub frequency: String,
    pub next_date: String,
    pub created_at: String,
}

pub struct Tag {
    pub id: String,
    pub name: String,
    pub created_at: String,
}

pub struct PriceAlert {
    pub id: String,
    pub symbol: String,
    pub asset_name: String,
    pub target_price: f64,
    pub direction: String,
    pub active: bool,
    pub created_at: String,
}

// ── LedgerDb ─────────────────────────────────────────────────────────────────

pub struct LedgerDb {
    pool: SqlitePool,
    rt: tokio::runtime::Runtime,
}

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
                &format!("{TX_SELECT} WHERE t.wallet_id = ? ORDER BY t.created_at DESC LIMIT ? OFFSET ?")
            )
            .bind(&wallet_id).bind(limit as i64).bind(offset as i64)
            .fetch_all(&self.pool).await?;
            Ok(rows.into_iter().map(row_to_transaction).collect())
        })
    }

    pub fn list_all_transactions(&self, limit: u32, offset: u32) -> Result<Vec<Transaction>, LedgerError> {
        self.rt.block_on(async {
            let rows = sqlx::query_as::<_, TransactionRow>(
                &format!("{TX_SELECT} ORDER BY t.created_at DESC LIMIT ? OFFSET ?")
            )
            .bind(limit as i64).bind(offset as i64)
            .fetch_all(&self.pool).await?;
            Ok(rows.into_iter().map(row_to_transaction).collect())
        })
    }

    pub fn create_transaction(&self, wallet_id: String, title: String, category: String, amount: f64, is_income: bool, note: Option<String>, created_at: Option<String>) -> Result<Transaction, LedgerError> {
        if title.is_empty() { return Err(LedgerError::InvalidInput("title is required".into())); }
        if amount <= 0.0 { return Err(LedgerError::InvalidInput("amount must be positive".into())); }

        self.rt.block_on(async {
            let id = Uuid::new_v4().to_string();
            let date = created_at.unwrap_or_else(|| Utc::now().to_rfc3339());
            let sign: f64 = if is_income { amount } else { -amount };

            // Resolved before the transaction opens: a stray category left behind by a failed
            // insert is harmless, a half-applied balance is not.
            let category_id = resolve_category_id(&self.pool, &category, is_income).await?;

            // The row and the balance must land together or neither does.
            let mut tx = self.pool.begin().await?;
            sqlx::query(
                "INSERT INTO transactions (id, wallet_id, title, category_id, category, amount, is_income, note, created_at) VALUES (?,?,?,?,?,?,?,?,?)"
            )
            .bind(&id).bind(&wallet_id).bind(&title).bind(&category_id).bind(&category)
            .bind(amount).bind(is_income).bind(&note).bind(&date)
            .execute(&mut *tx).await?;

            sqlx::query("UPDATE wallets SET balance = balance + ? WHERE id = ?")
                .bind(sign).bind(&wallet_id)
                .execute(&mut *tx).await?;
            tx.commit().await?;

            let row = sqlx::query_as::<_, TransactionRow>(&format!("{TX_SELECT} WHERE t.id = ?"))
                .bind(&id).fetch_one(&self.pool).await?;
            Ok(row_to_transaction(row))
        })
    }

    pub fn update_transaction(&self, id: String, title: String, category: String, amount: f64, is_income: bool, note: Option<String>, created_at: Option<String>) -> Result<Transaction, LedgerError> {
        self.rt.block_on(async {
            let category_id = resolve_category_id(&self.pool, &category, is_income).await?;

            // Editing an amount or flipping income/expense used to leave the wallet balance on the
            // old figure. Reverse the previous effect, then apply the new one.
            let previous: Option<(String, f64, bool)> = sqlx::query_as(
                "SELECT wallet_id, amount, is_income FROM transactions WHERE id=?"
            ).bind(&id).fetch_optional(&self.pool).await?;
            let (prev_wallet, prev_amount, prev_is_income) = previous.ok_or(LedgerError::NotFound)?;

            let mut tx = self.pool.begin().await?;
            sqlx::query("UPDATE wallets SET balance = balance - ? WHERE id = ?")
                .bind(if prev_is_income { prev_amount } else { -prev_amount })
                .bind(&prev_wallet)
                .execute(&mut *tx).await?;

            sqlx::query("UPDATE transactions SET title=?, category_id=?, category=?, amount=?, is_income=?, note=?, created_at=COALESCE(?,created_at) WHERE id=?")
                .bind(&title).bind(&category_id).bind(&category).bind(amount).bind(is_income).bind(&note).bind(&created_at).bind(&id)
                .execute(&mut *tx).await?;

            sqlx::query("UPDATE wallets SET balance = balance + ? WHERE id = ?")
                .bind(if is_income { amount } else { -amount })
                .bind(&prev_wallet)
                .execute(&mut *tx).await?;
            tx.commit().await?;

            let row = sqlx::query_as::<_, TransactionRow>(&format!("{TX_SELECT} WHERE t.id = ?"))
                .bind(&id).fetch_optional(&self.pool).await?
                .ok_or(LedgerError::NotFound)?;
            Ok(row_to_transaction(row))
        })
    }

    pub fn delete_transaction(&self, id: String) -> Result<(), LedgerError> {
        self.rt.block_on(async {
            // Deleting used to leave the wallet balance carrying the removed amount forever.
            let row: Option<(String, f64, bool)> = sqlx::query_as(
                "SELECT wallet_id, amount, is_income FROM transactions WHERE id=?"
            ).bind(&id).fetch_optional(&self.pool).await?;
            let Some((wallet_id, amount, is_income)) = row else { return Ok(()) };

            let mut tx = self.pool.begin().await?;
            // transaction_tags declares ON DELETE CASCADE, but SQLite ignores it without
            // PRAGMA foreign_keys=ON. Clear the links here so they cannot pile up.
            sqlx::query("DELETE FROM transaction_tags WHERE transaction_id=?")
                .bind(&id).execute(&mut *tx).await?;
            sqlx::query("DELETE FROM transactions WHERE id=?")
                .bind(&id).execute(&mut *tx).await?;
            sqlx::query("UPDATE wallets SET balance = balance - ? WHERE id = ?")
                .bind(if is_income { amount } else { -amount })
                .bind(&wallet_id)
                .execute(&mut *tx).await?;
            tx.commit().await?;
            Ok(())
        })
    }

    // ── Wallets ──────────────────────────────────────────────────────────────

    pub fn list_wallets(&self) -> Result<Vec<Wallet>, LedgerError> {
        self.rt.block_on(async {
            let rows = sqlx::query_as::<_, WalletRow>(
                "SELECT id, name, description, currency, balance, off_budget, created_at FROM wallets ORDER BY created_at ASC"
            )
            .fetch_all(&self.pool).await?;
            Ok(rows.into_iter().map(row_to_wallet).collect())
        })
    }

    pub fn create_wallet(&self, name: String, description: String, currency: String, initial_balance: f64, off_budget: bool) -> Result<Wallet, LedgerError> {
        if name.is_empty() { return Err(LedgerError::InvalidInput("name is required".into())); }
        self.rt.block_on(async {
            let id = Uuid::new_v4().to_string();
            let now = Utc::now().to_rfc3339();
            sqlx::query(
                "INSERT INTO wallets (id, name, description, currency, balance, off_budget, created_at) VALUES (?,?,?,?,?,?,?)"
            )
            .bind(&id).bind(&name).bind(&description).bind(&currency).bind(initial_balance).bind(off_budget).bind(&now)
            .execute(&self.pool).await?;

            let row = sqlx::query_as::<_, WalletRow>(
                "SELECT id, name, description, currency, balance, off_budget, created_at FROM wallets WHERE id=?"
            )
            .bind(&id).fetch_one(&self.pool).await?;
            Ok(row_to_wallet(row))
        })
    }

    pub fn update_wallet(&self, id: String, name: String, description: String, currency: String, off_budget: bool) -> Result<Wallet, LedgerError> {
        self.rt.block_on(async {
            sqlx::query("UPDATE wallets SET name=?, description=?, currency=?, off_budget=? WHERE id=?")
                .bind(&name).bind(&description).bind(&currency).bind(off_budget).bind(&id)
                .execute(&self.pool).await?;

            let row = sqlx::query_as::<_, WalletRow>(
                "SELECT id, name, description, currency, balance, off_budget, created_at FROM wallets WHERE id=?"
            )
            .bind(&id).fetch_optional(&self.pool).await?
            .ok_or(LedgerError::NotFound)?;
            Ok(row_to_wallet(row))
        })
    }

    pub fn delete_wallet(&self, id: String) -> Result<(), LedgerError> {
        self.rt.block_on(async {
            // The ON DELETE CASCADE on transactions.wallet_id never fires (no PRAGMA
            // foreign_keys), so deleting a wallet used to leave its transactions behind —
            // counted by every report while belonging to an account that no longer exists.
            let mut tx = self.pool.begin().await?;
            sqlx::query(
                "DELETE FROM transaction_tags WHERE transaction_id IN (SELECT id FROM transactions WHERE wallet_id=?)"
            ).bind(&id).execute(&mut *tx).await?;
            sqlx::query("DELETE FROM transactions WHERE wallet_id=?")
                .bind(&id).execute(&mut *tx).await?;
            sqlx::query("DELETE FROM recurring_transactions WHERE wallet_id=?")
                .bind(&id).execute(&mut *tx).await?;
            // Transfers on the other side must go too, and reversing their balance would be
            // meaningless once one end no longer exists.
            sqlx::query("DELETE FROM transfers WHERE from_wallet_id=? OR to_wallet_id=?")
                .bind(&id).bind(&id).execute(&mut *tx).await?;
            sqlx::query("DELETE FROM wallets WHERE id=?").bind(&id).execute(&mut *tx).await?;
            tx.commit().await?;
            Ok(())
        })
    }

    pub fn count_transactions_for_wallet(&self, id: String) -> Result<u32, LedgerError> {
        self.rt.block_on(async {
            let (n,): (i64,) = sqlx::query_as("SELECT COUNT(*) FROM transactions WHERE wallet_id=?")
                .bind(&id).fetch_one(&self.pool).await?;
            Ok(n as u32)
        })
    }

    // ── Transfers ────────────────────────────────────────────────────────────

    pub fn list_transfers(&self, limit: u32, offset: u32) -> Result<Vec<Transfer>, LedgerError> {
        self.rt.block_on(async {
            let rows = sqlx::query_as::<_, TransferRow>(
                "SELECT id, from_wallet_id, to_wallet_id, amount, note, created_at
                 FROM transfers ORDER BY created_at DESC LIMIT ? OFFSET ?"
            )
            .bind(limit as i64).bind(offset as i64)
            .fetch_all(&self.pool).await?;
            Ok(rows.into_iter().map(row_to_transfer).collect())
        })
    }

    pub fn create_transfer(&self, from_wallet_id: String, to_wallet_id: String, amount: f64, note: Option<String>, created_at: Option<String>) -> Result<Transfer, LedgerError> {
        if amount <= 0.0 { return Err(LedgerError::InvalidInput("amount must be positive".into())); }
        if from_wallet_id == to_wallet_id {
            return Err(LedgerError::InvalidInput("cannot transfer to the same wallet".into()));
        }
        self.rt.block_on(async {
            let id = Uuid::new_v4().to_string();
            let date = created_at.unwrap_or_else(|| Utc::now().to_rfc3339());

            // Both wallets must exist, or the transfer would move money to or from nowhere —
            // nothing enforces the foreign keys at runtime.
            let (known,): (i64,) = sqlx::query_as("SELECT COUNT(*) FROM wallets WHERE id IN (?, ?)")
                .bind(&from_wallet_id).bind(&to_wallet_id)
                .fetch_one(&self.pool).await?;
            if known < 2 { return Err(LedgerError::NotFound); }

            // Two balance updates and an insert: all three land together or none do.
            let mut tx = self.pool.begin().await?;
            sqlx::query(
                "INSERT INTO transfers (id, from_wallet_id, to_wallet_id, amount, note, created_at) VALUES (?,?,?,?,?,?)"
            )
            .bind(&id).bind(&from_wallet_id).bind(&to_wallet_id).bind(amount).bind(&note).bind(&date)
            .execute(&mut *tx).await?;

            // A transfer only moves balance; it must never touch income or expense totals.
            sqlx::query("UPDATE wallets SET balance = balance - ? WHERE id = ?")
                .bind(amount).bind(&from_wallet_id).execute(&mut *tx).await?;
            sqlx::query("UPDATE wallets SET balance = balance + ? WHERE id = ?")
                .bind(amount).bind(&to_wallet_id).execute(&mut *tx).await?;
            tx.commit().await?;

            let row = sqlx::query_as::<_, TransferRow>(
                "SELECT id, from_wallet_id, to_wallet_id, amount, note, created_at FROM transfers WHERE id=?"
            )
            .bind(&id).fetch_one(&self.pool).await?;
            Ok(row_to_transfer(row))
        })
    }

    pub fn delete_transfer(&self, id: String) -> Result<(), LedgerError> {
        self.rt.block_on(async {
            let row = sqlx::query_as::<_, TransferRow>(
                "SELECT id, from_wallet_id, to_wallet_id, amount, note, created_at FROM transfers WHERE id=?"
            )
            .bind(&id).fetch_optional(&self.pool).await?
            .ok_or(LedgerError::NotFound)?;

            // Put the money back before dropping the record, or the balances drift.
            let mut tx = self.pool.begin().await?;
            sqlx::query("UPDATE wallets SET balance = balance + ? WHERE id = ?")
                .bind(row.amount).bind(&row.from_wallet_id).execute(&mut *tx).await?;
            sqlx::query("UPDATE wallets SET balance = balance - ? WHERE id = ?")
                .bind(row.amount).bind(&row.to_wallet_id).execute(&mut *tx).await?;

            sqlx::query("DELETE FROM transfers WHERE id=?").bind(&id).execute(&mut *tx).await?;
            tx.commit().await?;
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
            .bind(&id).fetch_one(&self.pool).await?;
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
            .bind(&goal_id).fetch_optional(&self.pool).await?
            .ok_or(LedgerError::NotFound)?;
            Ok(row_to_goal(row))
        })
    }

    pub fn update_goal(&self, id: String, name: String, target_amount: f64, deadline: Option<String>) -> Result<SavingsGoal, LedgerError> {
        if name.is_empty() { return Err(LedgerError::InvalidInput("name is required".into())); }
        if target_amount <= 0.0 { return Err(LedgerError::InvalidInput("target must be positive".into())); }
        self.rt.block_on(async {
            sqlx::query("UPDATE savings_goals SET name=?, target_amount=?, deadline=? WHERE id=?")
                .bind(&name).bind(target_amount).bind(&deadline).bind(&id)
                .execute(&self.pool).await?;
            let row = sqlx::query_as::<_, SavingsGoalRow>(
                "SELECT id, name, current_amount, target_amount, deadline, created_at FROM savings_goals WHERE id=?"
            ).bind(&id).fetch_one(&self.pool).await?;
            Ok(row_to_goal(row))
        })
    }

    pub fn delete_goal(&self, id: String) -> Result<(), LedgerError> {
        self.rt.block_on(async {
            sqlx::query("DELETE FROM savings_goals WHERE id=?").bind(&id).execute(&self.pool).await?;
            Ok(())
        })
    }

    // ── Statistics ───────────────────────────────────────────────────────────

    pub fn get_month_summary(&self, year: i32, month: i32) -> Result<MonthSummary, LedgerError> {
        self.rt.block_on(async {
            let prefix = format!("{}-{:02}%", year, month);

            let income: f64 = sqlx::query_scalar::<_, Option<f64>>(
                "SELECT SUM(amount) FROM transactions WHERE is_income=1 AND created_at LIKE ?"
            )
            .bind(&prefix).fetch_one(&self.pool).await?.unwrap_or(0.0);

            let expenses: f64 = sqlx::query_scalar::<_, Option<f64>>(
                "SELECT SUM(amount) FROM transactions WHERE is_income=0 AND created_at LIKE ?"
            )
            .bind(&prefix).fetch_one(&self.pool).await?.unwrap_or(0.0);

            let count: i64 = sqlx::query_scalar::<_, i64>(
                "SELECT COUNT(*) FROM transactions WHERE created_at LIKE ?"
            )
            .bind(&prefix).fetch_one(&self.pool).await?;

            Ok(MonthSummary {
                total_income: income,
                total_expenses: expenses,
                net_savings: income - expenses,
                transaction_count: count as i32,
            })
        })
    }

    // ── Categories ───────────────────────────────────────────────────────────

    pub fn list_categories(&self) -> Result<Vec<Category>, LedgerError> {
        self.rt.block_on(async {
            let rows = sqlx::query_as::<_, CategoryRow>(
                "SELECT id, name, icon_name, color_hex, is_expense, created_at FROM categories ORDER BY name ASC"
            )
            .fetch_all(&self.pool).await?;
            Ok(rows.into_iter().map(row_to_category).collect())
        })
    }

    pub fn create_category(&self, name: String, icon_name: String, color_hex: String, is_expense: bool) -> Result<Category, LedgerError> {
        if name.is_empty() { return Err(LedgerError::InvalidInput("name is required".into())); }
        self.rt.block_on(async {
            let id = Uuid::new_v4().to_string();
            let now = Utc::now().to_rfc3339();
            sqlx::query(
                "INSERT INTO categories (id, name, icon_name, color_hex, is_expense, created_at) VALUES (?,?,?,?,?,?)"
            )
            .bind(&id).bind(&name).bind(&icon_name).bind(&color_hex).bind(is_expense).bind(&now)
            .execute(&self.pool).await?;

            let row = sqlx::query_as::<_, CategoryRow>(
                "SELECT id, name, icon_name, color_hex, is_expense, created_at FROM categories WHERE id=?"
            )
            .bind(&id).fetch_one(&self.pool).await?;
            Ok(row_to_category(row))
        })
    }

    pub fn update_category(&self, id: String, name: String, icon_name: String, color_hex: String, is_expense: bool) -> Result<Category, LedgerError> {
        if name.is_empty() { return Err(LedgerError::InvalidInput("name is required".into())); }
        self.rt.block_on(async {
            sqlx::query("UPDATE categories SET name=?, icon_name=?, color_hex=?, is_expense=? WHERE id=?")
                .bind(&name).bind(&icon_name).bind(&color_hex).bind(is_expense).bind(&id)
                .execute(&self.pool).await?;

            // Reads resolve the name through category_id, so this is only keeping the fallback
            // label current — it is what a row shows once its category is deleted.
            for table in ["transactions", "recurring_transactions"] {
                sqlx::query(&format!("UPDATE {table} SET category=? WHERE category_id=?"))
                    .bind(&name).bind(&id)
                    .execute(&self.pool).await?;
            }

            let row = sqlx::query_as::<_, CategoryRow>(
                "SELECT id, name, icon_name, color_hex, is_expense, created_at FROM categories WHERE id=?"
            )
            .bind(&id).fetch_optional(&self.pool).await?
            .ok_or(LedgerError::NotFound)?;
            Ok(row_to_category(row))
        })
    }

    pub fn delete_category(&self, id: String) -> Result<(), LedgerError> {
        self.rt.block_on(async {
            // SQLite enforces foreign keys only with PRAGMA foreign_keys=ON, which is off by
            // default, so clear the link explicitly rather than trusting ON DELETE. The `category`
            // label stays, so historical transactions still read as what they were filed under.
            for table in ["transactions", "recurring_transactions"] {
                sqlx::query(&format!("UPDATE {table} SET category_id=NULL WHERE category_id=?"))
                    .bind(&id).execute(&self.pool).await?;
            }
            // A budget whose category is gone can never be seen or edited again — the screens all
            // resolve it through the category — so it would linger invisibly forever.
            sqlx::query("DELETE FROM budgets WHERE category_id=?")
                .bind(&id).execute(&self.pool).await?;
            sqlx::query("DELETE FROM categories WHERE id=?").bind(&id).execute(&self.pool).await?;
            Ok(())
        })
    }

    pub fn count_transactions_for_category(&self, id: String) -> Result<u32, LedgerError> {
        self.rt.block_on(async {
            let (n,): (i64,) = sqlx::query_as("SELECT COUNT(*) FROM transactions WHERE category_id=?")
                .bind(&id).fetch_one(&self.pool).await?;
            Ok(n as u32)
        })
    }

    // ── Budgets ──────────────────────────────────────────────────────────────

    pub fn list_budgets(&self) -> Result<Vec<Budget>, LedgerError> {
        self.rt.block_on(async {
            let rows = sqlx::query_as::<_, BudgetRow>(
                "SELECT id, category_id, wallet_id, limit_amount, period, alert_threshold, carry_over, created_at FROM budgets ORDER BY created_at ASC"
            )
            .fetch_all(&self.pool).await?;
            Ok(rows.into_iter().map(row_to_budget).collect())
        })
    }

    pub fn create_budget(&self, category_id: Option<String>, wallet_id: Option<String>, limit_amount: f64, period: String, alert_threshold: f64, carry_over: bool) -> Result<Budget, LedgerError> {
        if limit_amount <= 0.0 { return Err(LedgerError::InvalidInput("limit must be positive".into())); }
        // Both null is the *overall* budget — a cap on everything you spend. It is the only way to
        // state "at most X a month in total"; summing whatever category budgets happen to exist
        // produces an arbitrary number rather than an intention.
        self.rt.block_on(async {
            // "At most X in total" can only be one number. A second one is not a second budget,
            // it is a contradiction — and it used to be accepted and then silently ignored.
            if category_id.is_none() && wallet_id.is_none() {
                let (existing,): (i64,) = sqlx::query_as(
                    "SELECT COUNT(*) FROM budgets WHERE category_id IS NULL AND wallet_id IS NULL"
                ).fetch_one(&self.pool).await?;
                if existing > 0 {
                    return Err(LedgerError::InvalidInput(
                        "an overall budget already exists — edit it instead".into()
                    ));
                }
            }

            let id = Uuid::new_v4().to_string();
            let now = Utc::now().to_rfc3339();
            sqlx::query(
                "INSERT INTO budgets (id, category_id, wallet_id, limit_amount, period, alert_threshold, carry_over, created_at) VALUES (?,?,?,?,?,?,?,?)"
            )
            .bind(&id).bind(&category_id).bind(&wallet_id).bind(limit_amount).bind(&period).bind(alert_threshold).bind(carry_over).bind(&now)
            .execute(&self.pool).await?;

            let row = sqlx::query_as::<_, BudgetRow>(
                "SELECT id, category_id, wallet_id, limit_amount, period, alert_threshold, carry_over, created_at FROM budgets WHERE id=?"
            )
            .bind(&id).fetch_one(&self.pool).await?;
            Ok(row_to_budget(row))
        })
    }

    pub fn update_budget(&self, id: String, category_id: Option<String>, wallet_id: Option<String>, limit_amount: f64, period: String, alert_threshold: f64, carry_over: bool) -> Result<Budget, LedgerError> {
        if limit_amount <= 0.0 { return Err(LedgerError::InvalidInput("limit must be positive".into())); }
        self.rt.block_on(async {
            sqlx::query("UPDATE budgets SET category_id=?, wallet_id=?, limit_amount=?, period=?, alert_threshold=?, carry_over=? WHERE id=?")
                .bind(&category_id).bind(&wallet_id).bind(limit_amount).bind(&period).bind(alert_threshold).bind(carry_over).bind(&id)
                .execute(&self.pool).await?;

            let row = sqlx::query_as::<_, BudgetRow>(
                "SELECT id, category_id, wallet_id, limit_amount, period, alert_threshold, carry_over, created_at FROM budgets WHERE id=?"
            )
            .bind(&id).fetch_optional(&self.pool).await?
            .ok_or(LedgerError::NotFound)?;
            Ok(row_to_budget(row))
        })
    }

    pub fn delete_budget(&self, id: String) -> Result<(), LedgerError> {
        self.rt.block_on(async {
            sqlx::query("DELETE FROM budgets WHERE id=?").bind(&id).execute(&self.pool).await?;
            Ok(())
        })
    }

    // ── Debts ─────────────────────────────────────────────────────────────────

    pub fn list_debts(&self) -> Result<Vec<Debt>, LedgerError> {
        self.rt.block_on(async {
            let rows = sqlx::query_as::<_, DebtRow>(
                "SELECT id, name, debt_type, total_amount, remaining_amount, apr, monthly_payment, created_at FROM debts ORDER BY created_at ASC"
            )
            .fetch_all(&self.pool).await?;
            Ok(rows.into_iter().map(row_to_debt).collect())
        })
    }

    pub fn create_debt(&self, name: String, debt_type: String, total_amount: f64, remaining_amount: f64, apr: f64, monthly_payment: f64) -> Result<Debt, LedgerError> {
        if name.is_empty() { return Err(LedgerError::InvalidInput("name is required".into())); }
        if total_amount <= 0.0 { return Err(LedgerError::InvalidInput("total amount must be positive".into())); }
        if monthly_payment <= 0.0 { return Err(LedgerError::InvalidInput("monthly payment must be positive".into())); }
        self.rt.block_on(async {
            let id = Uuid::new_v4().to_string();
            let now = Utc::now().to_rfc3339();
            sqlx::query(
                "INSERT INTO debts (id, name, debt_type, total_amount, remaining_amount, apr, monthly_payment, created_at) VALUES (?,?,?,?,?,?,?,?)"
            )
            .bind(&id).bind(&name).bind(&debt_type).bind(total_amount).bind(remaining_amount).bind(apr).bind(monthly_payment).bind(&now)
            .execute(&self.pool).await?;

            let row = sqlx::query_as::<_, DebtRow>(
                "SELECT id, name, debt_type, total_amount, remaining_amount, apr, monthly_payment, created_at FROM debts WHERE id=?"
            )
            .bind(&id).fetch_one(&self.pool).await?;
            Ok(row_to_debt(row))
        })
    }

    pub fn update_debt(&self, id: String, name: String, debt_type: String, total_amount: f64, remaining_amount: f64, apr: f64, monthly_payment: f64) -> Result<Debt, LedgerError> {
        if name.is_empty() { return Err(LedgerError::InvalidInput("name is required".into())); }
        self.rt.block_on(async {
            sqlx::query("UPDATE debts SET name=?, debt_type=?, total_amount=?, remaining_amount=?, apr=?, monthly_payment=? WHERE id=?")
                .bind(&name).bind(&debt_type).bind(total_amount).bind(remaining_amount).bind(apr).bind(monthly_payment).bind(&id)
                .execute(&self.pool).await?;

            let row = sqlx::query_as::<_, DebtRow>(
                "SELECT id, name, debt_type, total_amount, remaining_amount, apr, monthly_payment, created_at FROM debts WHERE id=?"
            )
            .bind(&id).fetch_optional(&self.pool).await?
            .ok_or(LedgerError::NotFound)?;
            Ok(row_to_debt(row))
        })
    }

    pub fn delete_debt(&self, id: String) -> Result<(), LedgerError> {
        self.rt.block_on(async {
            sqlx::query("DELETE FROM debts WHERE id=?").bind(&id).execute(&self.pool).await?;
            Ok(())
        })
    }

    // ── Recurring Transactions ───────────────────────────────────────────────

    pub fn list_recurring(&self) -> Result<Vec<RecurringTransaction>, LedgerError> {
        self.rt.block_on(async {
            let rows = sqlx::query_as::<_, RecurringTransactionRow>(
                &format!("{RECURRING_SELECT} ORDER BY r.next_date ASC")
            )
            .fetch_all(&self.pool).await?;
            Ok(rows.into_iter().map(row_to_recurring).collect())
        })
    }

    pub fn create_recurring(&self, title: String, amount: f64, category: String, wallet_id: String, is_income: bool, frequency: String, next_date: String) -> Result<RecurringTransaction, LedgerError> {
        if title.is_empty() { return Err(LedgerError::InvalidInput("title is required".into())); }
        if amount <= 0.0 { return Err(LedgerError::InvalidInput("amount must be positive".into())); }
        self.rt.block_on(async {
            let id = Uuid::new_v4().to_string();
            let now = Utc::now().to_rfc3339();
            let category_id = resolve_category_id(&self.pool, &category, is_income).await?;
            sqlx::query(
                "INSERT INTO recurring_transactions (id, title, amount, category_id, category, wallet_id, is_income, frequency, next_date, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)"
            )
            .bind(&id).bind(&title).bind(amount).bind(&category_id).bind(&category).bind(&wallet_id).bind(is_income).bind(&frequency).bind(&next_date).bind(&now)
            .execute(&self.pool).await?;

            let row = sqlx::query_as::<_, RecurringTransactionRow>(
                &format!("{RECURRING_SELECT} WHERE r.id=?")
            )
            .bind(&id).fetch_one(&self.pool).await?;
            Ok(row_to_recurring(row))
        })
    }

    pub fn update_recurring(&self, id: String, title: String, amount: f64, category: String, frequency: String, next_date: String) -> Result<RecurringTransaction, LedgerError> {
        if title.is_empty() { return Err(LedgerError::InvalidInput("title is required".into())); }
        self.rt.block_on(async {
            let is_income: bool = sqlx::query_as::<_, (bool,)>("SELECT is_income FROM recurring_transactions WHERE id=?")
                .bind(&id).fetch_optional(&self.pool).await?
                .map(|(v,)| v).unwrap_or(false);
            let category_id = resolve_category_id(&self.pool, &category, is_income).await?;

            sqlx::query("UPDATE recurring_transactions SET title=?, amount=?, category_id=?, category=?, frequency=?, next_date=? WHERE id=?")
                .bind(&title).bind(amount).bind(&category_id).bind(&category).bind(&frequency).bind(&next_date).bind(&id)
                .execute(&self.pool).await?;

            let row = sqlx::query_as::<_, RecurringTransactionRow>(
                &format!("{RECURRING_SELECT} WHERE r.id=?")
            )
            .bind(&id).fetch_optional(&self.pool).await?
            .ok_or(LedgerError::NotFound)?;
            Ok(row_to_recurring(row))
        })
    }

    pub fn delete_recurring(&self, id: String) -> Result<(), LedgerError> {
        self.rt.block_on(async {
            sqlx::query("DELETE FROM recurring_transactions WHERE id=?").bind(&id).execute(&self.pool).await?;
            Ok(())
        })
    }

    // ── Tags ─────────────────────────────────────────────────────────────────

    pub fn list_tags(&self) -> Result<Vec<Tag>, LedgerError> {
        self.rt.block_on(async {
            let rows = sqlx::query_as::<_, TagRow>(
                "SELECT id, name, created_at FROM tags ORDER BY name ASC"
            )
            .fetch_all(&self.pool).await?;
            Ok(rows.into_iter().map(row_to_tag).collect())
        })
    }

    pub fn create_tag(&self, name: String) -> Result<Tag, LedgerError> {
        if name.is_empty() { return Err(LedgerError::InvalidInput("tag name is required".into())); }
        self.rt.block_on(async {
            let id = Uuid::new_v4().to_string();
            let now = Utc::now().to_rfc3339();
            sqlx::query("INSERT OR IGNORE INTO tags (id, name, created_at) VALUES (?,?,?)")
                .bind(&id).bind(&name).bind(&now)
                .execute(&self.pool).await?;

            let row = sqlx::query_as::<_, TagRow>(
                "SELECT id, name, created_at FROM tags WHERE name=?"
            )
            .bind(&name).fetch_one(&self.pool).await?;
            Ok(row_to_tag(row))
        })
    }

    pub fn delete_tag(&self, id: String) -> Result<(), LedgerError> {
        self.rt.block_on(async {
            sqlx::query("DELETE FROM transaction_tags WHERE tag_id=?")
                .bind(&id).execute(&self.pool).await?;
            sqlx::query("DELETE FROM tags WHERE id=?").bind(&id).execute(&self.pool).await?;
            Ok(())
        })
    }

    pub fn add_tag_to_transaction(&self, transaction_id: String, tag_id: String) -> Result<(), LedgerError> {
        self.rt.block_on(async {
            sqlx::query("INSERT OR IGNORE INTO transaction_tags (transaction_id, tag_id) VALUES (?,?)")
                .bind(&transaction_id).bind(&tag_id)
                .execute(&self.pool).await?;
            Ok(())
        })
    }

    pub fn remove_tag_from_transaction(&self, transaction_id: String, tag_id: String) -> Result<(), LedgerError> {
        self.rt.block_on(async {
            sqlx::query("DELETE FROM transaction_tags WHERE transaction_id=? AND tag_id=?")
                .bind(&transaction_id).bind(&tag_id)
                .execute(&self.pool).await?;
            Ok(())
        })
    }

    pub fn list_transaction_tags(&self, transaction_id: String) -> Result<Vec<Tag>, LedgerError> {
        self.rt.block_on(async {
            let rows = sqlx::query_as::<_, TagRow>(
                "SELECT t.id, t.name, t.created_at FROM tags t
                 INNER JOIN transaction_tags tt ON tt.tag_id = t.id
                 WHERE tt.transaction_id = ? ORDER BY t.name ASC"
            )
            .bind(&transaction_id).fetch_all(&self.pool).await?;
            Ok(rows.into_iter().map(row_to_tag).collect())
        })
    }

    // ── Price Alerts ─────────────────────────────────────────────────────────

    pub fn list_price_alerts(&self) -> Result<Vec<PriceAlert>, LedgerError> {
        self.rt.block_on(async {
            let rows = sqlx::query_as::<_, PriceAlertRow>(
                "SELECT id, symbol, asset_name, target_price, direction, active, created_at FROM price_alerts ORDER BY created_at DESC"
            )
            .fetch_all(&self.pool).await?;
            Ok(rows.into_iter().map(row_to_alert).collect())
        })
    }

    pub fn create_price_alert(&self, symbol: String, asset_name: String, target_price: f64, direction: String) -> Result<PriceAlert, LedgerError> {
        if symbol.is_empty() { return Err(LedgerError::InvalidInput("symbol is required".into())); }
        if target_price <= 0.0 { return Err(LedgerError::InvalidInput("target price must be positive".into())); }
        self.rt.block_on(async {
            let id = Uuid::new_v4().to_string();
            let now = Utc::now().to_rfc3339();
            sqlx::query(
                "INSERT INTO price_alerts (id, symbol, asset_name, target_price, direction, active, created_at) VALUES (?,?,?,?,?,1,?)"
            )
            .bind(&id).bind(&symbol).bind(&asset_name).bind(target_price).bind(&direction).bind(&now)
            .execute(&self.pool).await?;

            let row = sqlx::query_as::<_, PriceAlertRow>(
                "SELECT id, symbol, asset_name, target_price, direction, active, created_at FROM price_alerts WHERE id=?"
            )
            .bind(&id).fetch_one(&self.pool).await?;
            Ok(row_to_alert(row))
        })
    }

    pub fn set_price_alert_active(&self, id: String, active: bool) -> Result<PriceAlert, LedgerError> {
        self.rt.block_on(async {
            sqlx::query("UPDATE price_alerts SET active=? WHERE id=?")
                .bind(active).bind(&id)
                .execute(&self.pool).await?;

            let row = sqlx::query_as::<_, PriceAlertRow>(
                "SELECT id, symbol, asset_name, target_price, direction, active, created_at FROM price_alerts WHERE id=?"
            )
            .bind(&id).fetch_optional(&self.pool).await?
            .ok_or(LedgerError::NotFound)?;
            Ok(row_to_alert(row))
        })
    }

    pub fn delete_price_alert(&self, id: String) -> Result<(), LedgerError> {
        self.rt.block_on(async {
            sqlx::query("DELETE FROM price_alerts WHERE id=?").bind(&id).execute(&self.pool).await?;
            Ok(())
        })
    }
}

// ── Category linkage ──────────────────────────────────────────────────────────

// Every transaction read resolves its category name through category_id, so a rename is picked up
// automatically. The stored `category` text is only a fallback for transactions whose category has
// since been deleted.
const TX_SELECT: &str = "SELECT t.id, t.wallet_id, t.title, \
     COALESCE(c.name, t.category) AS category, \
     t.amount, t.is_income, t.note, t.created_at \
     FROM transactions t LEFT JOIN categories c ON c.id = t.category_id";

// Recurring transactions had the same name-only storage, so a rename skipped them too.
const RECURRING_SELECT: &str = "SELECT r.id, r.title, r.amount, \
     COALESCE(c.name, r.category) AS category, \
     r.wallet_id, r.is_income, r.frequency, r.next_date, r.created_at \
     FROM recurring_transactions r LEFT JOIN categories c ON c.id = r.category_id";

// Writes still take a category *name* (the whole app works that way), so the name is matched
// case-insensitively against existing categories and created when it is genuinely new. Without
// this, a transaction could name a category that does not exist and rename would miss it again.
async fn resolve_category_id(
    pool: &sqlx::SqlitePool,
    name: &str,
    is_income: bool,
) -> Result<Option<String>, sqlx::Error> {
    let trimmed = name.trim();
    if trimmed.is_empty() {
        return Ok(None);
    }

    // Match the type as well as the name. A Money Manager import legitimately produces the same
    // name in both directions (Gifts, Other), and matching on name alone linked expense rows to
    // the income category — invisible until that category was renamed or deleted.
    if let Some((id,)) = sqlx::query_as::<_, (String,)>(
        "SELECT id FROM categories WHERE name = ? COLLATE NOCASE AND is_expense = ?",
    )
    .bind(trimmed)
    .bind(!is_income)
    .fetch_optional(pool)
    .await?
    {
        return Ok(Some(id));
    }

    let id = Uuid::new_v4().to_string();
    sqlx::query(
        "INSERT INTO categories (id, name, icon_name, color_hex, is_expense, created_at) VALUES (?,?,?,?,?,?)",
    )
    .bind(&id)
    .bind(trimmed)
    .bind("shopping_bag")
    .bind("#00838F")
    .bind(!is_income)
    .bind(Utc::now().to_rfc3339())
    .execute(pool)
    .await?;

    Ok(Some(id))
}

// ── Row converters ────────────────────────────────────────────────────────────

fn row_to_transaction(r: TransactionRow) -> Transaction {
    Transaction { id: r.id, wallet_id: r.wallet_id, title: r.title, category: r.category, amount: r.amount, is_income: r.is_income, note: r.note, created_at: r.created_at }
}

fn row_to_wallet(r: WalletRow) -> Wallet {
    Wallet { id: r.id, name: r.name, description: r.description, currency: r.currency, balance: r.balance, off_budget: r.off_budget, created_at: r.created_at }
}

fn row_to_transfer(r: TransferRow) -> Transfer {
    Transfer { id: r.id, from_wallet_id: r.from_wallet_id, to_wallet_id: r.to_wallet_id, amount: r.amount, note: r.note, created_at: r.created_at }
}

fn row_to_goal(r: SavingsGoalRow) -> SavingsGoal {
    SavingsGoal { id: r.id, name: r.name, current_amount: r.current_amount, target_amount: r.target_amount, deadline: r.deadline, created_at: r.created_at }
}

fn row_to_category(r: CategoryRow) -> Category {
    Category { id: r.id, name: r.name, icon_name: r.icon_name, color_hex: r.color_hex, is_expense: r.is_expense, created_at: r.created_at }
}

fn row_to_budget(r: BudgetRow) -> Budget {
    Budget { id: r.id, category_id: r.category_id, wallet_id: r.wallet_id, limit_amount: r.limit_amount, period: r.period, alert_threshold: r.alert_threshold, carry_over: r.carry_over, created_at: r.created_at }
}

fn row_to_debt(r: DebtRow) -> Debt {
    Debt { id: r.id, name: r.name, debt_type: r.debt_type, total_amount: r.total_amount, remaining_amount: r.remaining_amount, apr: r.apr, monthly_payment: r.monthly_payment, created_at: r.created_at }
}

fn row_to_recurring(r: RecurringTransactionRow) -> RecurringTransaction {
    RecurringTransaction { id: r.id, title: r.title, amount: r.amount, category: r.category, wallet_id: r.wallet_id, is_income: r.is_income, frequency: r.frequency, next_date: r.next_date, created_at: r.created_at }
}

fn row_to_tag(r: TagRow) -> Tag {
    Tag { id: r.id, name: r.name, created_at: r.created_at }
}

fn row_to_alert(r: PriceAlertRow) -> PriceAlert {
    PriceAlert { id: r.id, symbol: r.symbol, asset_name: r.asset_name, target_price: r.target_price, direction: r.direction, active: r.active, created_at: r.created_at }
}
