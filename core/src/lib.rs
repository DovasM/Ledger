uniffi::include_scaffolding!("ledger");

mod db;
mod llama;

pub use llama::{LlamaEngine, LlamaError};

pub fn llama_create(model_path: String, n_ctx: u32) -> Result<Arc<LlamaEngine>, LlamaError> {
    LlamaEngine::new(model_path, n_ctx).map(Arc::new)
}

use db::open_pool;
use db::models::{
    TransactionRow, WalletRow, TransferRow, SavingsGoalRow, GoalContributionRow, DebtPaymentRow,
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
    pub amount_cents: i64,
    pub is_income: bool,
    pub note: Option<String>,
    pub occurred_at: String,
}

pub struct Wallet {
    pub id: String,
    pub name: String,
    pub description: String,
    pub currency: String,
    pub balance_cents: i64,
    pub off_budget: bool,
    pub created_at: String,
}

pub struct Transfer {
    pub id: String,
    pub from_wallet_id: String,
    pub to_wallet_id: String,
    pub amount_cents: i64,
    pub note: Option<String>,
    pub created_at: String,
}

pub struct SavingsGoal {
    pub id: String,
    pub name: String,
    pub current_amount_cents: i64,
    pub target_amount_cents: i64,
    pub deadline: Option<String>,
    pub created_at: String,
}

pub struct GoalContribution {
    pub id: String,
    pub goal_id: String,
    pub amount_cents: i64,
    pub note: Option<String>,
    // "contribution" for money the user put in, "opening" for the single row m7 wrote to carry the
    // balance that existed before contributions were itemised.
    pub kind: String,
    pub occurred_at: String,
}

pub struct DebtPayment {
    pub id: String,
    pub debt_id: String,
    pub amount_cents: i64,
    pub note: Option<String>,
    // "payment", "opening", or "adjustment" — the last one is written when the remaining amount is
    // typed over directly, so the total still reconciles against the history.
    pub kind: String,
    pub occurred_at: String,
}

pub struct MonthSummary {
    pub total_income_cents: i64,
    pub total_expenses_cents: i64,
    pub net_savings_cents: i64,
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
    pub limit_amount_cents: i64,
    pub period: String,
    pub alert_threshold: f64,
    pub carry_over: bool,
    pub created_at: String,
}

pub struct Debt {
    pub id: String,
    pub name: String,
    pub debt_type: String,
    pub total_amount_cents: i64,
    pub remaining_amount_cents: i64,
    pub apr: f64,
    pub monthly_payment_cents: i64,
    pub created_at: String,
}

pub struct RecurringTransaction {
    pub id: String,
    pub title: String,
    pub amount_cents: i64,
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
    pub target_price_cents: i64,
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
                &format!("{TX_SELECT} WHERE t.wallet_id = ? {TX_ORDER} LIMIT ? OFFSET ?")
            )
            .bind(&wallet_id).bind(limit as i64).bind(offset as i64)
            .fetch_all(&self.pool).await?;
            Ok(rows.into_iter().map(row_to_transaction).collect())
        })
    }

    pub fn list_all_transactions(&self, limit: u32, offset: u32) -> Result<Vec<Transaction>, LedgerError> {
        self.rt.block_on(async {
            let rows = sqlx::query_as::<_, TransactionRow>(
                &format!("{TX_SELECT} {TX_ORDER} LIMIT ? OFFSET ?")
            )
            .bind(limit as i64).bind(offset as i64)
            .fetch_all(&self.pool).await?;
            Ok(rows.into_iter().map(row_to_transaction).collect())
        })
    }

    pub fn create_transaction(&self, wallet_id: String, title: String, category: String, amount_cents: i64, is_income: bool, note: Option<String>, occurred_at: Option<String>) -> Result<Transaction, LedgerError> {
        if title.is_empty() { return Err(LedgerError::InvalidInput("title is required".into())); }
        if amount_cents <= 0 { return Err(LedgerError::InvalidInput("amount must be positive".into())); }

        self.rt.block_on(async {
            let id = Uuid::new_v4().to_string();
            let now = Utc::now().to_rfc3339();
            // Two different times: when it happened (back-datable) and when the row was written.
            let occurred = occurred_at.unwrap_or_else(|| now.clone());

            // Resolved before the transaction opens: a stray category left behind by a failed
            // insert is harmless, a half-written transaction is not.
            let category_id = resolve_category_id(&self.pool, &category, is_income).await?;

            // The row and the balance must land together or neither does.
            let mut tx = self.pool.begin().await?;
            sqlx::query(
                "INSERT INTO transactions (id, wallet_id, title, category_id, category, amount_cents, is_income, note, occurred_at, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)"
            )
            .bind(&id).bind(&wallet_id).bind(&title).bind(&category_id).bind(&category)
            .bind(amount_cents).bind(is_income).bind(&note).bind(&occurred).bind(&now)
            .execute(&mut *tx).await?;

            tx.commit().await?;

            let row = sqlx::query_as::<_, TransactionRow>(&format!("{TX_SELECT} WHERE t.id = ?"))
                .bind(&id).fetch_one(&self.pool).await?;
            Ok(row_to_transaction(row))
        })
    }

    pub fn update_transaction(&self, id: String, title: String, category: String, amount_cents: i64, is_income: bool, note: Option<String>, occurred_at: Option<String>) -> Result<Transaction, LedgerError> {
        self.rt.block_on(async {
            let category_id = resolve_category_id(&self.pool, &category, is_income).await?;

            // Editing an amount or flipping income/expense used to need the old values read back so
            // the wallet balance could be corrected. The balance follows from the rows now, so the
            // update is the whole operation.
            //
            // Editing changes when it happened, never when the row was written.
            let changed = sqlx::query("UPDATE transactions SET title=?, category_id=?, category=?, amount_cents=?, is_income=?, note=?, occurred_at=COALESCE(?,occurred_at,created_at) WHERE id=?")
                .bind(&title).bind(&category_id).bind(&category).bind(amount_cents).bind(is_income).bind(&note).bind(&occurred_at).bind(&id)
                .execute(&self.pool).await?;
            if changed.rows_affected() == 0 { return Err(LedgerError::NotFound); }

            let row = sqlx::query_as::<_, TransactionRow>(&format!("{TX_SELECT} WHERE t.id = ?"))
                .bind(&id).fetch_optional(&self.pool).await?
                .ok_or(LedgerError::NotFound)?;
            Ok(row_to_transaction(row))
        })
    }

    pub fn delete_transaction(&self, id: String) -> Result<(), LedgerError> {
        self.rt.block_on(async {
            let mut tx = self.pool.begin().await?;
            // transaction_tags declares ON DELETE CASCADE, but SQLite ignores it without
            // PRAGMA foreign_keys=ON. Clear the links here so they cannot pile up.
            sqlx::query("DELETE FROM transaction_tags WHERE transaction_id=?")
                .bind(&id).execute(&mut *tx).await?;
            sqlx::query("DELETE FROM transactions WHERE id=?")
                .bind(&id).execute(&mut *tx).await?;
            tx.commit().await?;
            Ok(())
        })
    }

    // ── Wallets ──────────────────────────────────────────────────────────────

    pub fn list_wallets(&self) -> Result<Vec<Wallet>, LedgerError> {
        self.rt.block_on(async {
            let rows = sqlx::query_as::<_, WalletRow>(
                &format!("{WALLET_SELECT} ORDER BY w.created_at ASC")
            )
            .fetch_all(&self.pool).await?;
            Ok(rows.into_iter().map(row_to_wallet).collect())
        })
    }

    pub fn create_wallet(&self, name: String, description: String, currency: String, initial_balance_cents: i64, off_budget: bool) -> Result<Wallet, LedgerError> {
        if name.is_empty() { return Err(LedgerError::InvalidInput("name is required".into())); }
        self.rt.block_on(async {
            let id = Uuid::new_v4().to_string();
            let now = Utc::now().to_rfc3339();
            sqlx::query(
                "INSERT INTO wallets (id, name, description, currency, opening_balance_cents, off_budget, created_at) VALUES (?,?,?,?,?,?,?)"
            )
            .bind(&id).bind(&name).bind(&description).bind(&currency).bind(initial_balance_cents).bind(off_budget).bind(&now)
            .execute(&self.pool).await?;

            let row = sqlx::query_as::<_, WalletRow>(
                &format!("{WALLET_SELECT} WHERE w.id = ?")
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
                &format!("{WALLET_SELECT} WHERE w.id = ?")
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
                "SELECT id, from_wallet_id, to_wallet_id, amount_cents, note, created_at
                 FROM transfers ORDER BY created_at DESC LIMIT ? OFFSET ?"
            )
            .bind(limit as i64).bind(offset as i64)
            .fetch_all(&self.pool).await?;
            Ok(rows.into_iter().map(row_to_transfer).collect())
        })
    }

    pub fn create_transfer(&self, from_wallet_id: String, to_wallet_id: String, amount_cents: i64, note: Option<String>, created_at: Option<String>) -> Result<Transfer, LedgerError> {
        if amount_cents <= 0 { return Err(LedgerError::InvalidInput("amount must be positive".into())); }
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
                "INSERT INTO transfers (id, from_wallet_id, to_wallet_id, amount_cents, note, created_at) VALUES (?,?,?,?,?,?)"
            )
            .bind(&id).bind(&from_wallet_id).bind(&to_wallet_id).bind(amount_cents).bind(&note).bind(&date)
            .execute(&mut *tx).await?;

            // A transfer only moves balance; it must never touch income or expense totals. Both
            // balances follow from the row itself now, so there is nothing to update.
            tx.commit().await?;

            let row = sqlx::query_as::<_, TransferRow>(
                "SELECT id, from_wallet_id, to_wallet_id, amount_cents, note, created_at FROM transfers WHERE id=?"
            )
            .bind(&id).fetch_one(&self.pool).await?;
            Ok(row_to_transfer(row))
        })
    }

    pub fn delete_transfer(&self, id: String) -> Result<(), LedgerError> {
        self.rt.block_on(async {
            // Dropping the row is enough: both balances are summed from the transfers that exist,
            // so there is no money to put back by hand.
            let removed = sqlx::query("DELETE FROM transfers WHERE id=?")
                .bind(&id).execute(&self.pool).await?;
            if removed.rows_affected() == 0 { return Err(LedgerError::NotFound); }
            Ok(())
        })
    }

    // ── Savings Goals ────────────────────────────────────────────────────────

    pub fn list_goals(&self) -> Result<Vec<SavingsGoal>, LedgerError> {
        self.rt.block_on(async {
            let rows = sqlx::query_as::<_, SavingsGoalRow>(
                &format!("{GOAL_SELECT} ORDER BY g.created_at ASC")
            )
            .fetch_all(&self.pool).await?;
            Ok(rows.into_iter().map(row_to_goal).collect())
        })
    }

    pub fn create_goal(&self, name: String, target_amount_cents: i64, deadline: Option<String>) -> Result<SavingsGoal, LedgerError> {
        if name.is_empty() { return Err(LedgerError::InvalidInput("name is required".into())); }
        if target_amount_cents <= 0 { return Err(LedgerError::InvalidInput("target must be positive".into())); }
        self.rt.block_on(async {
            let id = Uuid::new_v4().to_string();
            let now = Utc::now().to_rfc3339();
            sqlx::query(
                "INSERT INTO savings_goals (id, name, target_amount_cents, deadline, created_at) VALUES (?,?,?,?,?)"
            )
            .bind(&id).bind(&name).bind(target_amount_cents).bind(&deadline).bind(&now)
            .execute(&self.pool).await?;

            self.goal_by_id(&id).await
        })
    }

    pub fn list_goal_contributions(&self, goal_id: String) -> Result<Vec<GoalContribution>, LedgerError> {
        self.rt.block_on(async {
            let rows = sqlx::query_as::<_, GoalContributionRow>(
                &format!("SELECT id, goal_id, amount_cents, note, kind, occurred_at FROM goal_contributions WHERE goal_id = ? {HISTORY_ORDER}")
            )
            .bind(&goal_id).fetch_all(&self.pool).await?;
            Ok(rows.into_iter().map(row_to_contribution).collect())
        })
    }

    pub fn add_contribution(&self, goal_id: String, amount_cents: i64, note: Option<String>, occurred_at: Option<String>) -> Result<SavingsGoal, LedgerError> {
        if amount_cents <= 0 { return Err(LedgerError::InvalidInput("amount must be positive".into())); }
        self.rt.block_on(async {
            let (exists,): (i64,) = sqlx::query_as("SELECT COUNT(*) FROM savings_goals WHERE id=?")
                .bind(&goal_id).fetch_one(&self.pool).await?;
            if exists == 0 { return Err(LedgerError::NotFound); }

            let now = Utc::now().to_rfc3339();
            sqlx::query(
                "INSERT INTO goal_contributions (id, goal_id, amount_cents, note, kind, occurred_at, created_at) VALUES (?,?,?,?,'contribution',?,?)"
            )
            .bind(Uuid::new_v4().to_string()).bind(&goal_id).bind(amount_cents).bind(&note)
            .bind(occurred_at.unwrap_or_else(|| now.clone())).bind(&now)
            .execute(&self.pool).await?;

            self.goal_by_id(&goal_id).await
        })
    }

    /// Removing a contribution is how a mistyped one gets fixed. Before the history existed the
    /// only remedy was to type over the total, which lost the record of what really went in.
    pub fn delete_contribution(&self, id: String) -> Result<SavingsGoal, LedgerError> {
        self.rt.block_on(async {
            let goal_id: Option<(String,)> = sqlx::query_as("SELECT goal_id FROM goal_contributions WHERE id=?")
                .bind(&id).fetch_optional(&self.pool).await?;
            let (goal_id,) = goal_id.ok_or(LedgerError::NotFound)?;

            sqlx::query("DELETE FROM goal_contributions WHERE id=?")
                .bind(&id).execute(&self.pool).await?;

            self.goal_by_id(&goal_id).await
        })
    }

    pub fn update_goal(&self, id: String, name: String, target_amount_cents: i64, deadline: Option<String>) -> Result<SavingsGoal, LedgerError> {
        if name.is_empty() { return Err(LedgerError::InvalidInput("name is required".into())); }
        if target_amount_cents <= 0 { return Err(LedgerError::InvalidInput("target must be positive".into())); }
        self.rt.block_on(async {
            sqlx::query("UPDATE savings_goals SET name=?, target_amount_cents=?, deadline=? WHERE id=?")
                .bind(&name).bind(target_amount_cents).bind(&deadline).bind(&id)
                .execute(&self.pool).await?;
            self.goal_by_id(&id).await
        })
    }

    async fn goal_by_id(&self, id: &str) -> Result<SavingsGoal, LedgerError> {
        let row = sqlx::query_as::<_, SavingsGoalRow>(&format!("{GOAL_SELECT} WHERE g.id = ?"))
            .bind(id).fetch_optional(&self.pool).await?
            .ok_or(LedgerError::NotFound)?;
        Ok(row_to_goal(row))
    }

    pub fn delete_goal(&self, id: String) -> Result<(), LedgerError> {
        self.rt.block_on(async {
            // The foreign key declaration does nothing without PRAGMA foreign_keys=ON, which this
            // pool does not set, so the contributions have to be removed here or they outlive the
            // goal and are unreachable forever.
            let mut tx = self.pool.begin().await?;
            sqlx::query("DELETE FROM goal_contributions WHERE goal_id=?").bind(&id).execute(&mut *tx).await?;
            sqlx::query("DELETE FROM savings_goals WHERE id=?").bind(&id).execute(&mut *tx).await?;
            tx.commit().await?;
            Ok(())
        })
    }

    // ── Statistics ───────────────────────────────────────────────────────────

    pub fn get_month_summary(&self, year: i32, month: i32) -> Result<MonthSummary, LedgerError> {
        self.rt.block_on(async {
            let prefix = format!("{}-{:02}%", year, month);

            // SUM over an INTEGER column is exact, so the monthly totals no longer depend on how
            // many rows happened to be added up or in what order.
            let income: i64 = sqlx::query_scalar::<_, Option<i64>>(
                "SELECT SUM(amount_cents) FROM transactions WHERE is_income=1 AND occurred_at LIKE ?"
            )
            .bind(&prefix).fetch_one(&self.pool).await?.unwrap_or(0);

            let expenses: i64 = sqlx::query_scalar::<_, Option<i64>>(
                "SELECT SUM(amount_cents) FROM transactions WHERE is_income=0 AND occurred_at LIKE ?"
            )
            .bind(&prefix).fetch_one(&self.pool).await?.unwrap_or(0);

            let count: i64 = sqlx::query_scalar::<_, i64>(
                "SELECT COUNT(*) FROM transactions WHERE occurred_at LIKE ?"
            )
            .bind(&prefix).fetch_one(&self.pool).await?;

            Ok(MonthSummary {
                total_income_cents: income,
                total_expenses_cents: expenses,
                net_savings_cents: income - expenses,
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
                "SELECT id, category_id, wallet_id, limit_amount_cents, period, alert_threshold, carry_over, created_at FROM budgets ORDER BY created_at ASC"
            )
            .fetch_all(&self.pool).await?;
            Ok(rows.into_iter().map(row_to_budget).collect())
        })
    }

    pub fn create_budget(&self, category_id: Option<String>, wallet_id: Option<String>, limit_amount_cents: i64, period: String, alert_threshold: f64, carry_over: bool) -> Result<Budget, LedgerError> {
        if limit_amount_cents <= 0 { return Err(LedgerError::InvalidInput("limit must be positive".into())); }
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

            // The same category capped twice for the same period is two answers to one question,
            // and StreakCalculator produces a pace per budget — so the screen would show the
            // category twice and "tightest category" could pick either one.
            //
            // idx_budgets_unique cannot enforce this: its other column is NULL for a category
            // budget, and SQLite treats NULLs as distinct in a unique index, so every row looks
            // unique to it. The check has to live here.
            if let Some(ref cat) = category_id {
                let (existing,): (i64,) = sqlx::query_as(
                    "SELECT COUNT(*) FROM budgets WHERE category_id = ? AND period = ?"
                ).bind(cat).bind(&period).fetch_one(&self.pool).await?;
                if existing > 0 {
                    return Err(LedgerError::InvalidInput(
                        "this category already has a budget for that period — edit it instead".into()
                    ));
                }
            } else if let Some(ref wal) = wallet_id {
                let (existing,): (i64,) = sqlx::query_as(
                    "SELECT COUNT(*) FROM budgets WHERE wallet_id = ? AND category_id IS NULL AND period = ?"
                ).bind(wal).bind(&period).fetch_one(&self.pool).await?;
                if existing > 0 {
                    return Err(LedgerError::InvalidInput(
                        "this wallet already has a budget for that period — edit it instead".into()
                    ));
                }
            }

            let id = Uuid::new_v4().to_string();
            let now = Utc::now().to_rfc3339();
            sqlx::query(
                "INSERT INTO budgets (id, category_id, wallet_id, limit_amount_cents, period, alert_threshold, carry_over, created_at) VALUES (?,?,?,?,?,?,?,?)"
            )
            .bind(&id).bind(&category_id).bind(&wallet_id).bind(limit_amount_cents).bind(&period).bind(alert_threshold).bind(carry_over).bind(&now)
            .execute(&self.pool).await?;

            let row = sqlx::query_as::<_, BudgetRow>(
                "SELECT id, category_id, wallet_id, limit_amount_cents, period, alert_threshold, carry_over, created_at FROM budgets WHERE id=?"
            )
            .bind(&id).fetch_one(&self.pool).await?;
            Ok(row_to_budget(row))
        })
    }

    pub fn update_budget(&self, id: String, category_id: Option<String>, wallet_id: Option<String>, limit_amount_cents: i64, period: String, alert_threshold: f64, carry_over: bool) -> Result<Budget, LedgerError> {
        if limit_amount_cents <= 0 { return Err(LedgerError::InvalidInput("limit must be positive".into())); }
        self.rt.block_on(async {
            sqlx::query("UPDATE budgets SET category_id=?, wallet_id=?, limit_amount_cents=?, period=?, alert_threshold=?, carry_over=? WHERE id=?")
                .bind(&category_id).bind(&wallet_id).bind(limit_amount_cents).bind(&period).bind(alert_threshold).bind(carry_over).bind(&id)
                .execute(&self.pool).await?;

            let row = sqlx::query_as::<_, BudgetRow>(
                "SELECT id, category_id, wallet_id, limit_amount_cents, period, alert_threshold, carry_over, created_at FROM budgets WHERE id=?"
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
            let rows = sqlx::query_as::<_, DebtRow>(&format!("{DEBT_SELECT} ORDER BY d.created_at ASC"))
                .fetch_all(&self.pool).await?;
            Ok(rows.into_iter().map(row_to_debt).collect())
        })
    }

    pub fn create_debt(&self, name: String, debt_type: String, total_amount_cents: i64, remaining_amount_cents: i64, apr: f64, monthly_payment_cents: i64) -> Result<Debt, LedgerError> {
        if name.is_empty() { return Err(LedgerError::InvalidInput("name is required".into())); }
        if total_amount_cents <= 0 { return Err(LedgerError::InvalidInput("total amount_cents must be positive".into())); }
        if monthly_payment_cents <= 0 { return Err(LedgerError::InvalidInput("monthly payment must be positive".into())); }
        self.rt.block_on(async {
            let id = Uuid::new_v4().to_string();
            let now = Utc::now().to_rfc3339();
            let mut tx = self.pool.begin().await?;
            sqlx::query(
                "INSERT INTO debts (id, name, debt_type, total_amount_cents, apr, monthly_payment_cents, created_at) VALUES (?,?,?,?,?,?,?)"
            )
            .bind(&id).bind(&name).bind(&debt_type).bind(total_amount_cents).bind(apr).bind(monthly_payment_cents).bind(&now)
            .execute(&mut *tx).await?;

            // "I owe 3400 of an original 5000" is the normal way to enter an existing debt. The
            // 1600 already paid is real and has to be recorded, or the derived remaining would
            // report the full 5000.
            let already_paid = total_amount_cents - remaining_amount_cents;
            if already_paid > 0 {
                sqlx::query(
                    "INSERT INTO debt_payments (id, debt_id, amount_cents, note, kind, occurred_at, created_at) VALUES (?,?,?,?,'opening',?,?)"
                )
                .bind(Uuid::new_v4().to_string()).bind(&id).bind(already_paid)
                .bind("Paid before this debt was tracked").bind(&now).bind(&now)
                .execute(&mut *tx).await?;
            }
            tx.commit().await?;

            self.debt_by_id(&id).await
        })
    }

    pub fn list_debt_payments(&self, debt_id: String) -> Result<Vec<DebtPayment>, LedgerError> {
        self.rt.block_on(async {
            let rows = sqlx::query_as::<_, DebtPaymentRow>(
                &format!("SELECT id, debt_id, amount_cents, note, kind, occurred_at FROM debt_payments WHERE debt_id = ? {HISTORY_ORDER}")
            )
            .bind(&debt_id).fetch_all(&self.pool).await?;
            Ok(rows.into_iter().map(row_to_payment).collect())
        })
    }

    pub fn add_debt_payment(&self, debt_id: String, amount_cents: i64, note: Option<String>, occurred_at: Option<String>) -> Result<Debt, LedgerError> {
        if amount_cents <= 0 { return Err(LedgerError::InvalidInput("amount must be positive".into())); }
        self.rt.block_on(async {
            let (exists,): (i64,) = sqlx::query_as("SELECT COUNT(*) FROM debts WHERE id=?")
                .bind(&debt_id).fetch_one(&self.pool).await?;
            if exists == 0 { return Err(LedgerError::NotFound); }

            let now = Utc::now().to_rfc3339();
            sqlx::query(
                "INSERT INTO debt_payments (id, debt_id, amount_cents, note, kind, occurred_at, created_at) VALUES (?,?,?,?,'payment',?,?)"
            )
            .bind(Uuid::new_v4().to_string()).bind(&debt_id).bind(amount_cents).bind(&note)
            .bind(occurred_at.unwrap_or_else(|| now.clone())).bind(&now)
            .execute(&self.pool).await?;

            self.debt_by_id(&debt_id).await
        })
    }

    pub fn delete_debt_payment(&self, id: String) -> Result<Debt, LedgerError> {
        self.rt.block_on(async {
            let debt_id: Option<(String,)> = sqlx::query_as("SELECT debt_id FROM debt_payments WHERE id=?")
                .bind(&id).fetch_optional(&self.pool).await?;
            let (debt_id,) = debt_id.ok_or(LedgerError::NotFound)?;

            sqlx::query("DELETE FROM debt_payments WHERE id=?").bind(&id).execute(&self.pool).await?;

            self.debt_by_id(&debt_id).await
        })
    }

    pub fn update_debt(&self, id: String, name: String, debt_type: String, total_amount_cents: i64, remaining_amount_cents: i64, apr: f64, monthly_payment_cents: i64) -> Result<Debt, LedgerError> {
        if name.is_empty() { return Err(LedgerError::InvalidInput("name is required".into())); }
        self.rt.block_on(async {
            let mut tx = self.pool.begin().await?;
            let changed = sqlx::query("UPDATE debts SET name=?, debt_type=?, total_amount_cents=?, apr=?, monthly_payment_cents=? WHERE id=?")
                .bind(&name).bind(&debt_type).bind(total_amount_cents).bind(apr).bind(monthly_payment_cents).bind(&id)
                .execute(&mut *tx).await?;
            if changed.rows_affected() == 0 { return Err(LedgerError::NotFound); }

            // The edit screen still lets the remaining amount be typed over directly. It is no
            // longer a column, so honouring that means writing the difference as an adjustment —
            // the number the user asked for, with the history still adding up to it.
            let (paid,): (i64,) = sqlx::query_as(
                "SELECT COALESCE(SUM(amount_cents), 0) FROM debt_payments WHERE debt_id=?"
            ).bind(&id).fetch_one(&mut *tx).await?;
            let delta = (total_amount_cents - remaining_amount_cents) - paid;
            if delta != 0 {
                let now = Utc::now().to_rfc3339();
                sqlx::query(
                    "INSERT INTO debt_payments (id, debt_id, amount_cents, note, kind, occurred_at, created_at) VALUES (?,?,?,?,'adjustment',?,?)"
                )
                .bind(Uuid::new_v4().to_string()).bind(&id).bind(delta)
                .bind("Remaining amount_cents corrected by hand").bind(&now).bind(&now)
                .execute(&mut *tx).await?;
            }
            tx.commit().await?;

            self.debt_by_id(&id).await
        })
    }

    async fn debt_by_id(&self, id: &str) -> Result<Debt, LedgerError> {
        let row = sqlx::query_as::<_, DebtRow>(&format!("{DEBT_SELECT} WHERE d.id = ?"))
            .bind(id).fetch_optional(&self.pool).await?
            .ok_or(LedgerError::NotFound)?;
        Ok(row_to_debt(row))
    }

    pub fn delete_debt(&self, id: String) -> Result<(), LedgerError> {
        self.rt.block_on(async {
            // Same inert foreign key as everywhere else in this schema.
            let mut tx = self.pool.begin().await?;
            sqlx::query("DELETE FROM debt_payments WHERE debt_id=?").bind(&id).execute(&mut *tx).await?;
            sqlx::query("DELETE FROM debts WHERE id=?").bind(&id).execute(&mut *tx).await?;
            tx.commit().await?;
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

    pub fn create_recurring(&self, title: String, amount_cents: i64, category: String, wallet_id: String, is_income: bool, frequency: String, next_date: String) -> Result<RecurringTransaction, LedgerError> {
        if title.is_empty() { return Err(LedgerError::InvalidInput("title is required".into())); }
        if amount_cents <= 0 { return Err(LedgerError::InvalidInput("amount must be positive".into())); }
        self.rt.block_on(async {
            let id = Uuid::new_v4().to_string();
            let now = Utc::now().to_rfc3339();
            let category_id = resolve_category_id(&self.pool, &category, is_income).await?;
            sqlx::query(
                "INSERT INTO recurring_transactions (id, title, amount_cents, category_id, category, wallet_id, is_income, frequency, next_date, created_at) VALUES (?,?,?,?,?,?,?,?,?,?)"
            )
            .bind(&id).bind(&title).bind(amount_cents).bind(&category_id).bind(&category).bind(&wallet_id).bind(is_income).bind(&frequency).bind(&next_date).bind(&now)
            .execute(&self.pool).await?;

            let row = sqlx::query_as::<_, RecurringTransactionRow>(
                &format!("{RECURRING_SELECT} WHERE r.id=?")
            )
            .bind(&id).fetch_one(&self.pool).await?;
            Ok(row_to_recurring(row))
        })
    }

    pub fn update_recurring(&self, id: String, title: String, amount_cents: i64, category: String, frequency: String, next_date: String) -> Result<RecurringTransaction, LedgerError> {
        if title.is_empty() { return Err(LedgerError::InvalidInput("title is required".into())); }
        self.rt.block_on(async {
            let is_income: bool = sqlx::query_as::<_, (bool,)>("SELECT is_income FROM recurring_transactions WHERE id=?")
                .bind(&id).fetch_optional(&self.pool).await?
                .map(|(v,)| v).unwrap_or(false);
            let category_id = resolve_category_id(&self.pool, &category, is_income).await?;

            sqlx::query("UPDATE recurring_transactions SET title=?, amount_cents=?, category_id=?, category=?, frequency=?, next_date=? WHERE id=?")
                .bind(&title).bind(amount_cents).bind(&category_id).bind(&category).bind(&frequency).bind(&next_date).bind(&id)
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
                "SELECT id, symbol, asset_name, target_price_cents, direction, active, created_at FROM price_alerts ORDER BY created_at DESC"
            )
            .fetch_all(&self.pool).await?;
            Ok(rows.into_iter().map(row_to_alert).collect())
        })
    }

    pub fn create_price_alert(&self, symbol: String, asset_name: String, target_price_cents: i64, direction: String) -> Result<PriceAlert, LedgerError> {
        if symbol.is_empty() { return Err(LedgerError::InvalidInput("symbol is required".into())); }
        if target_price_cents <= 0 { return Err(LedgerError::InvalidInput("target price must be positive".into())); }
        self.rt.block_on(async {
            let id = Uuid::new_v4().to_string();
            let now = Utc::now().to_rfc3339();
            sqlx::query(
                "INSERT INTO price_alerts (id, symbol, asset_name, target_price_cents, direction, active, created_at) VALUES (?,?,?,?,?,1,?)"
            )
            .bind(&id).bind(&symbol).bind(&asset_name).bind(target_price_cents).bind(&direction).bind(&now)
            .execute(&self.pool).await?;

            let row = sqlx::query_as::<_, PriceAlertRow>(
                "SELECT id, symbol, asset_name, target_price_cents, direction, active, created_at FROM price_alerts WHERE id=?"
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
                "SELECT id, symbol, asset_name, target_price_cents, direction, active, created_at FROM price_alerts WHERE id=?"
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
// occurred_at needed a COALESCE onto created_at while m6 had it nullable; m8 rebuilt the table with
// it NOT NULL, so reads can finally just say what they mean.
const TX_SELECT: &str = "SELECT t.id, t.wallet_id, t.title, \
     COALESCE(c.name, t.category) AS category, \
     t.amount_cents, t.is_income, t.note, t.occurred_at \
     FROM transactions t LEFT JOIN categories c ON c.id = t.category_id";

// `occurred_at` is a date, so today's transactions all tie. `created_at` is a full timestamp and
// unique per row, which makes it the tiebreak that puts a just-added transaction at the top of its
// own day instead of somewhere arbitrary among the day's other rows — where it reads as "the app
// didn't save it". Never order by `occurred_at` alone.
const TX_ORDER: &str = "ORDER BY t.occurred_at DESC, t.created_at DESC, t.id DESC";

// The balance is derived, not stored: opening balance plus every movement in and out. A stored
// running total has to be corrected from nine different places and drifted twice when one of them
// forgot; a sum cannot fall out of step with the rows it is summing.
const WALLET_SELECT: &str = "SELECT w.id, w.name, w.description, w.currency, \
     w.opening_balance_cents \
       + COALESCE((SELECT SUM(amount_cents) FROM transactions WHERE wallet_id = w.id AND is_income = 1), 0) \
       - COALESCE((SELECT SUM(amount_cents) FROM transactions WHERE wallet_id = w.id AND is_income = 0), 0) \
       + COALESCE((SELECT SUM(amount_cents) FROM transfers WHERE to_wallet_id = w.id), 0) \
       - COALESCE((SELECT SUM(amount_cents) FROM transfers WHERE from_wallet_id = w.id), 0) AS balance_cents, \
     w.off_budget, w.created_at \
     FROM wallets w";

// current_amount_cents and remaining_amount_cents are not columns. They are summed from the history on
// every read, which costs nothing at these row counts and removes the second source of truth that
// let wallets.balance drift twice.
const GOAL_SELECT: &str = "SELECT g.id, g.name, \
     COALESCE((SELECT SUM(amount_cents) FROM goal_contributions WHERE goal_id = g.id), 0) AS current_amount_cents, \
     g.target_amount_cents, g.deadline, g.created_at \
     FROM savings_goals g";

const DEBT_SELECT: &str = "SELECT d.id, d.name, d.debt_type, d.total_amount_cents, \
     d.total_amount_cents - COALESCE((SELECT SUM(amount_cents) FROM debt_payments WHERE debt_id = d.id), 0) AS remaining_amount_cents, \
     d.apr, d.monthly_payment_cents, d.created_at \
     FROM debts d";

// Same reasoning as TX_ORDER: occurred_at is a date, so entries made on one day tie and a
// just-added one would surface in an arbitrary position in its own history.
const HISTORY_ORDER: &str = "ORDER BY occurred_at DESC, created_at DESC, id DESC";

// Recurring transactions had the same name-only storage, so a rename skipped them too.
const RECURRING_SELECT: &str = "SELECT r.id, r.title, r.amount_cents, \
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
    Transaction { id: r.id, wallet_id: r.wallet_id, title: r.title, category: r.category, amount_cents: r.amount_cents, is_income: r.is_income, note: r.note, occurred_at: r.occurred_at }
}

fn row_to_wallet(r: WalletRow) -> Wallet {
    Wallet { id: r.id, name: r.name, description: r.description, currency: r.currency, balance_cents: r.balance_cents, off_budget: r.off_budget, created_at: r.created_at }
}

fn row_to_transfer(r: TransferRow) -> Transfer {
    Transfer { id: r.id, from_wallet_id: r.from_wallet_id, to_wallet_id: r.to_wallet_id, amount_cents: r.amount_cents, note: r.note, created_at: r.created_at }
}

fn row_to_goal(r: SavingsGoalRow) -> SavingsGoal {
    SavingsGoal { id: r.id, name: r.name, current_amount_cents: r.current_amount_cents, target_amount_cents: r.target_amount_cents, deadline: r.deadline, created_at: r.created_at }
}

fn row_to_contribution(r: GoalContributionRow) -> GoalContribution {
    GoalContribution { id: r.id, goal_id: r.goal_id, amount_cents: r.amount_cents, note: r.note, kind: r.kind, occurred_at: r.occurred_at }
}

fn row_to_payment(r: DebtPaymentRow) -> DebtPayment {
    DebtPayment { id: r.id, debt_id: r.debt_id, amount_cents: r.amount_cents, note: r.note, kind: r.kind, occurred_at: r.occurred_at }
}

fn row_to_category(r: CategoryRow) -> Category {
    Category { id: r.id, name: r.name, icon_name: r.icon_name, color_hex: r.color_hex, is_expense: r.is_expense, created_at: r.created_at }
}

fn row_to_budget(r: BudgetRow) -> Budget {
    Budget { id: r.id, category_id: r.category_id, wallet_id: r.wallet_id, limit_amount_cents: r.limit_amount_cents, period: r.period, alert_threshold: r.alert_threshold, carry_over: r.carry_over, created_at: r.created_at }
}

fn row_to_debt(r: DebtRow) -> Debt {
    Debt { id: r.id, name: r.name, debt_type: r.debt_type, total_amount_cents: r.total_amount_cents, remaining_amount_cents: r.remaining_amount_cents, apr: r.apr, monthly_payment_cents: r.monthly_payment_cents, created_at: r.created_at }
}

fn row_to_recurring(r: RecurringTransactionRow) -> RecurringTransaction {
    RecurringTransaction { id: r.id, title: r.title, amount_cents: r.amount_cents, category: r.category, wallet_id: r.wallet_id, is_income: r.is_income, frequency: r.frequency, next_date: r.next_date, created_at: r.created_at }
}

fn row_to_tag(r: TagRow) -> Tag {
    Tag { id: r.id, name: r.name, created_at: r.created_at }
}

fn row_to_alert(r: PriceAlertRow) -> PriceAlert {
    PriceAlert { id: r.id, symbol: r.symbol, asset_name: r.asset_name, target_price_cents: r.target_price_cents, direction: r.direction, active: r.active, created_at: r.created_at }
}
