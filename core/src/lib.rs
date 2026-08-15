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
    ExpenseGroupRow, GroupMemberRow, SharedExpenseRow, ExpenseShareRow,
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

/// What a backup file contains. Shown to the user before a restore, because "replace everything"
/// is not a question anyone should answer blind.
pub struct BackupInfo {
    pub path: String,
    /// The schema the file was written at. Older is fine — it is migrated forward on restore.
    pub schema_version: i64,
    pub wallets: i64,
    pub transactions: i64,
    pub categories: i64,
    pub budgets: i64,
    pub goals: i64,
    pub debts: i64,
    pub transfers: i64,
    pub recurring: i64,
}

/// A group of people splitting costs. Every figure on it is read from your side of the group.
pub struct ExpenseGroup {
    pub id: String,
    pub name: String,
    pub emoji: String,
    pub color_hex: String,
    /// Everything the group has spent between them.
    pub total_cents: i64,
    /// The part of that which is yours.
    pub your_share_cents: i64,
    /// What you paid minus what you owe. Positive means the group owes you.
    pub net_balance_cents: i64,
    pub member_count: i32,
    pub expense_count: i32,
    pub created_at: String,
}

pub struct GroupMember {
    pub id: String,
    pub group_id: String,
    pub name: String,
    /// Exactly one member of a group is the person using the app.
    pub is_you: bool,
    pub paid_cents: i64,
    pub owes_cents: i64,
    /// paid − owes. Positive means they are owed money.
    pub balance_cents: i64,
}

/// Money handed over to square up, as opposed to money spent on something.
pub struct Settlement {
    pub id: String,
    pub group_id: String,
    pub from_member_id: String,
    pub from_name: String,
    pub to_member_id: String,
    pub to_name: String,
    pub amount_cents: i64,
    /// Set when the money moved through one of your wallets. Null when two other people settled
    /// between themselves.
    pub transaction_id: Option<String>,
    pub occurred_at: String,
}

/// A payment the app thinks should happen, worked out from the balances. Nothing is recorded until
/// it is confirmed.
pub struct SettlementSuggestion {
    pub from_member_id: String,
    pub from_name: String,
    pub to_member_id: String,
    pub to_name: String,
    pub amount_cents: i64,
}

pub struct SharedExpense {
    pub id: String,
    pub group_id: String,
    /// Set when you paid it, so the money really left your wallet. Null when somebody else did.
    pub transaction_id: Option<String>,
    pub description: String,
    pub amount_cents: i64,
    pub paid_by_member_id: String,
    pub paid_by_name: String,
    pub your_share_cents: i64,
    pub occurred_at: String,
}

pub struct ExpenseShare {
    pub id: String,
    pub shared_expense_id: String,
    pub member_id: String,
    pub member_name: String,
    pub share_cents: i64,
}

/// One person's slice of an expense, as handed in when it is recorded.
pub struct ShareInput {
    pub member_id: String,
    pub share_cents: i64,
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

/// The backup format this build writes and can read up to. Exposed so neither a test nor a screen
/// has to repeat the number — both did, and both went stale the moment a migration was added.
pub fn current_schema_version() -> i64 {
    db::CURRENT_SCHEMA_VERSION
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

    // ── Backup & restore ──────────────────────────────────────────────────────

    /// Writes a consistent snapshot of the whole database to `dest_path`.
    ///
    /// `VACUUM INTO` is SQLite's own answer to this: it produces a single, complete, non-torn file
    /// without stopping the app. Copying `ledger.db` by hand would race the write-ahead log and can
    /// hand back a file that is subtly incomplete — which is the worst possible outcome for a
    /// backup, because it looks fine until the day it is needed.
    pub fn backup_database(&self, dest_path: String) -> Result<BackupInfo, LedgerError> {
        self.rt.block_on(async {
            // VACUUM INTO refuses to overwrite, and the caller is often writing over yesterday's.
            let _ = std::fs::remove_file(&dest_path);

            sqlx::query("VACUUM INTO ?")
                .bind(&dest_path)
                .execute(&self.pool)
                .await?;

            let mut info = count_contents(&self.pool).await?;
            info.schema_version = current_version(&self.pool).await?;
            info.path = dest_path;
            Ok(info)
        })
    }

    /// What a backup file holds, without changing it or the live database. This is what the user is
    /// shown before they agree to overwrite everything they have.
    pub fn inspect_backup(&self, path: String) -> Result<BackupInfo, LedgerError> {
        if !std::path::Path::new(&path).exists() {
            return Err(LedgerError::InvalidInput("backup file not found".into()));
        }
        self.rt.block_on(async {
            // Read-only on purpose: opening it through open_pool would migrate a file the user is
            // only looking at.
            let pool = open_readonly(&path).await
                .map_err(|_| LedgerError::InvalidInput("this file is not a Ledger backup".into()))?;
            let version = current_version(&pool).await
                .map_err(|_| LedgerError::InvalidInput("this file is not a Ledger backup".into()))?;
            if version > db::CURRENT_SCHEMA_VERSION {
                pool.close().await;
                return Err(LedgerError::InvalidInput(format!(
                    "this backup came from a newer version of the app (format {}, this build reads up to {})",
                    version, db::CURRENT_SCHEMA_VERSION
                )));
            }
            let mut info = count_contents(&pool).await?;
            pool.close().await;
            info.schema_version = version;
            info.path = path;
            Ok(info)
        })
    }

    /// Replaces everything in the live database with the contents of `path`.
    ///
    /// **The backup is migrated up to this build's schema first.** The app is still changing shape,
    /// so a file taken two versions ago is the normal case, not the exotic one — restoring it must
    /// not fail on columns that did not exist when it was written. The staging copy is run through
    /// the same `open_pool` the app uses, so it goes through exactly the same migrations, and the
    /// user's own file is never modified.
    ///
    /// The replacement itself is one transaction: either all of it lands or none of it does. A
    /// restore that failed half-way would leave a mixture of two databases, which is worse than
    /// either of them.
    pub fn restore_backup(&self, path: String) -> Result<BackupInfo, LedgerError> {
        let source = self.inspect_backup(path.clone())?;

        self.rt.block_on(async {
            let staged = format!("{}.restore-staging", path);
            let _ = std::fs::remove_file(&staged);
            std::fs::copy(&path, &staged)
                .map_err(|e| LedgerError::InvalidInput(format!("could not stage the backup: {}", e)))?;

            // Brings the staged copy from whatever version it was written at up to this one.
            let staged_pool = open_pool(&staged).await?;
            staged_pool.close().await;

            let attached = staged.replace('\'', "''");
            let mut sql = String::from("PRAGMA foreign_keys = OFF;\n");
            sql.push_str(&format!("ATTACH DATABASE '{}' AS backup;\n", attached));
            sql.push_str("BEGIN;\n");
            // Children first so the deletes never trip over a parent that is still referenced.
            for table in db::USER_TABLES.iter().rev() {
                sql.push_str(&format!("DELETE FROM {};\n", table));
            }
            for table in db::USER_TABLES {
                sql.push_str(&format!("INSERT INTO {} SELECT * FROM backup.{};\n", table, table));
            }
            sql.push_str("COMMIT;\n");
            sql.push_str("DETACH DATABASE backup;\n");
            sql.push_str("PRAGMA foreign_keys = ON;");

            let result = sqlx::query(&sql).execute(&self.pool).await;
            let _ = std::fs::remove_file(&staged);
            let _ = std::fs::remove_file(format!("{}-wal", staged));
            let _ = std::fs::remove_file(format!("{}-shm", staged));
            result?;

            let mut restored = count_contents(&self.pool).await?;
            restored.schema_version = source.schema_version;
            restored.path = path;
            Ok(restored)
        })
    }

    // ── Shared expenses ───────────────────────────────────────────────────────

    pub fn list_expense_groups(&self) -> Result<Vec<ExpenseGroup>, LedgerError> {
        self.rt.block_on(async {
            let rows = sqlx::query_as::<_, ExpenseGroupRow>(&format!("{GROUP_SELECT} ORDER BY g.created_at DESC"))
                .fetch_all(&self.pool).await?;
            Ok(rows.into_iter().map(row_to_group).collect())
        })
    }

    /// Creates the group with you already in it — every balance is read from your side, so a group
    /// without you in it could not answer the only question it exists to answer.
    pub fn create_expense_group(&self, name: String, emoji: String, color_hex: String, member_names: Vec<String>) -> Result<ExpenseGroup, LedgerError> {
        if name.trim().is_empty() { return Err(LedgerError::InvalidInput("name is required".into())); }
        self.rt.block_on(async {
            let id = Uuid::new_v4().to_string();
            let now = Utc::now().to_rfc3339();

            let mut tx = self.pool.begin().await?;
            sqlx::query("INSERT INTO expense_groups (id, name, emoji, color_hex, created_at) VALUES (?,?,?,?,?)")
                .bind(&id).bind(name.trim()).bind(&emoji).bind(&color_hex).bind(&now)
                .execute(&mut *tx).await?;

            sqlx::query("INSERT INTO group_members (id, group_id, name, is_you, created_at) VALUES (?,?,?,1,?)")
                .bind(Uuid::new_v4().to_string()).bind(&id).bind("You").bind(&now)
                .execute(&mut *tx).await?;
            for member in member_names.iter().filter(|n| !n.trim().is_empty()) {
                sqlx::query("INSERT INTO group_members (id, group_id, name, is_you, created_at) VALUES (?,?,?,0,?)")
                    .bind(Uuid::new_v4().to_string()).bind(&id).bind(member.trim()).bind(&now)
                    .execute(&mut *tx).await?;
            }
            tx.commit().await?;

            self.group_by_id(&id).await
        })
    }

    pub fn update_expense_group(&self, id: String, name: String, emoji: String, color_hex: String) -> Result<ExpenseGroup, LedgerError> {
        if name.trim().is_empty() { return Err(LedgerError::InvalidInput("name is required".into())); }
        self.rt.block_on(async {
            let changed = sqlx::query("UPDATE expense_groups SET name=?, emoji=?, color_hex=? WHERE id=?")
                .bind(name.trim()).bind(&emoji).bind(&color_hex).bind(&id)
                .execute(&self.pool).await?;
            if changed.rows_affected() == 0 { return Err(LedgerError::NotFound); }
            self.group_by_id(&id).await
        })
    }

    pub fn delete_expense_group(&self, id: String) -> Result<(), LedgerError> {
        self.rt.block_on(async {
            // Children first, and by hand: nothing here declares ON DELETE CASCADE.
            let mut tx = self.pool.begin().await?;
            sqlx::query(
                "DELETE FROM shared_expense_shares WHERE shared_expense_id IN (SELECT id FROM shared_expenses WHERE group_id=?)"
            ).bind(&id).execute(&mut *tx).await?;
            sqlx::query("DELETE FROM settlements WHERE group_id=?").bind(&id).execute(&mut *tx).await?;
            sqlx::query("DELETE FROM shared_expenses WHERE group_id=?").bind(&id).execute(&mut *tx).await?;
            sqlx::query("DELETE FROM group_members WHERE group_id=?").bind(&id).execute(&mut *tx).await?;
            sqlx::query("DELETE FROM expense_groups WHERE id=?").bind(&id).execute(&mut *tx).await?;
            tx.commit().await?;
            Ok(())
        })
    }

    pub fn list_group_members(&self, group_id: String) -> Result<Vec<GroupMember>, LedgerError> {
        self.rt.block_on(async {
            let rows = sqlx::query_as::<_, GroupMemberRow>(&format!("{MEMBER_SELECT} WHERE m.group_id = ? ORDER BY m.is_you DESC, m.created_at ASC"))
                .bind(&group_id).fetch_all(&self.pool).await?;
            Ok(rows.into_iter().map(row_to_member).collect())
        })
    }

    pub fn add_group_member(&self, group_id: String, name: String) -> Result<GroupMember, LedgerError> {
        if name.trim().is_empty() { return Err(LedgerError::InvalidInput("name is required".into())); }
        self.rt.block_on(async {
            let id = Uuid::new_v4().to_string();
            sqlx::query("INSERT INTO group_members (id, group_id, name, is_you, created_at) VALUES (?,?,?,0,?)")
                .bind(&id).bind(&group_id).bind(name.trim()).bind(Utc::now().to_rfc3339())
                .execute(&self.pool).await?;
            let row = sqlx::query_as::<_, GroupMemberRow>(&format!("{MEMBER_SELECT} WHERE m.id = ?"))
                .bind(&id).fetch_one(&self.pool).await?;
            Ok(row_to_member(row))
        })
    }

    /// Refused while the member still appears in an expense — removing them would leave shares
    /// pointing at nobody and the group would stop adding up.
    pub fn remove_group_member(&self, id: String) -> Result<(), LedgerError> {
        self.rt.block_on(async {
            let (used,): (i64,) = sqlx::query_as(
                "SELECT (SELECT COUNT(*) FROM shared_expense_shares WHERE member_id = ?1)
                      + (SELECT COUNT(*) FROM shared_expenses WHERE paid_by_member_id = ?1)
                      + (SELECT COUNT(*) FROM settlements WHERE from_member_id = ?1 OR to_member_id = ?1)"
            ).bind(&id).fetch_one(&self.pool).await?;
            if used > 0 {
                return Err(LedgerError::InvalidInput(
                    "this person appears in an expense or a payment — remove those first".into()
                ));
            }
            sqlx::query("DELETE FROM group_members WHERE id=? AND is_you = 0")
                .bind(&id).execute(&self.pool).await?;
            Ok(())
        })
    }

    pub fn list_shared_expenses(&self, group_id: String) -> Result<Vec<SharedExpense>, LedgerError> {
        self.rt.block_on(async {
            let rows = sqlx::query_as::<_, SharedExpenseRow>(
                &format!("{EXPENSE_SELECT} WHERE e.group_id = ? ORDER BY e.occurred_at DESC, e.created_at DESC, e.id DESC")
            ).bind(&group_id).fetch_all(&self.pool).await?;
            Ok(rows.into_iter().map(row_to_shared_expense).collect())
        })
    }

    /// `transaction_id` is set when you paid and the money really left your wallet; it is null when
    /// somebody else paid, because then nothing of yours moved.
    ///
    /// The shares must add up to the amount exactly. Anything else means a share has been lost, and
    /// every balance computed afterwards would be wrong in a way nobody could trace.
    pub fn add_shared_expense(
        &self,
        group_id: String,
        description: String,
        amount_cents: i64,
        paid_by_member_id: String,
        transaction_id: Option<String>,
        shares: Vec<ShareInput>,
        occurred_at: Option<String>,
    ) -> Result<SharedExpense, LedgerError> {
        if description.trim().is_empty() { return Err(LedgerError::InvalidInput("description is required".into())); }
        if amount_cents <= 0 { return Err(LedgerError::InvalidInput("amount must be positive".into())); }

        let total: i64 = shares.iter().map(|s| s.share_cents).sum();
        if total != amount_cents {
            return Err(LedgerError::InvalidInput(format!(
                "the shares add up to {} but the expense is {} — every cent has to belong to somebody",
                total, amount_cents
            )));
        }
        if shares.is_empty() { return Err(LedgerError::InvalidInput("split it between someone".into())); }

        self.rt.block_on(async {
            let id = Uuid::new_v4().to_string();
            let now = Utc::now().to_rfc3339();
            let occurred = occurred_at.unwrap_or_else(|| now.clone());

            let mut tx = self.pool.begin().await?;
            sqlx::query(
                "INSERT INTO shared_expenses (id, group_id, transaction_id, description, amount_cents, paid_by_member_id, occurred_at, created_at)
                 VALUES (?,?,?,?,?,?,?,?)"
            )
            .bind(&id).bind(&group_id).bind(&transaction_id).bind(description.trim())
            .bind(amount_cents).bind(&paid_by_member_id).bind(&occurred).bind(&now)
            .execute(&mut *tx).await?;

            for s in &shares {
                sqlx::query("INSERT INTO shared_expense_shares (id, shared_expense_id, member_id, share_cents) VALUES (?,?,?,?)")
                    .bind(Uuid::new_v4().to_string()).bind(&id).bind(&s.member_id).bind(s.share_cents)
                    .execute(&mut *tx).await?;
            }
            tx.commit().await?;

            let row = sqlx::query_as::<_, SharedExpenseRow>(&format!("{EXPENSE_SELECT} WHERE e.id = ?"))
                .bind(&id).fetch_one(&self.pool).await?;
            Ok(row_to_shared_expense(row))
        })
    }

    /// Correcting an entry that was typed wrong. Deleting and retyping would work, but it loses the
    /// entry and is a poor answer to a typo.
    ///
    /// The shares are replaced wholesale rather than adjusted: working out which of them changed is
    /// harder than writing the set that is now correct, and doubling them is the obvious way to get
    /// this wrong. The same adding-up rule applies as when the expense was first written, and it is
    /// checked before anything is touched, so a correction that does not balance leaves the original
    /// exactly as it was.
    ///
    /// `transaction_id` is deliberately not in the SET — nothing here should quietly unlink an
    /// expense from the transaction it was paid by.
    pub fn update_shared_expense(
        &self,
        id: String,
        description: String,
        amount_cents: i64,
        paid_by_member_id: String,
        shares: Vec<ShareInput>,
        occurred_at: Option<String>,
    ) -> Result<SharedExpense, LedgerError> {
        if description.trim().is_empty() { return Err(LedgerError::InvalidInput("description is required".into())); }
        if amount_cents <= 0 { return Err(LedgerError::InvalidInput("amount must be positive".into())); }
        if shares.is_empty() { return Err(LedgerError::InvalidInput("split it between someone".into())); }

        let total: i64 = shares.iter().map(|s| s.share_cents).sum();
        if total != amount_cents {
            return Err(LedgerError::InvalidInput(format!(
                "the shares add up to {} but the expense is {} — every cent has to belong to somebody",
                total, amount_cents
            )));
        }

        self.rt.block_on(async {
            let mut tx = self.pool.begin().await?;

            let mut sql = String::from("UPDATE shared_expenses SET description=?, amount_cents=?, paid_by_member_id=?");
            if occurred_at.is_some() { sql.push_str(", occurred_at=?"); }
            sql.push_str(" WHERE id=?");

            let mut q = sqlx::query(&sql)
                .bind(description.trim()).bind(amount_cents).bind(&paid_by_member_id);
            if let Some(when) = &occurred_at { q = q.bind(when); }
            let changed = q.bind(&id).execute(&mut *tx).await?;
            if changed.rows_affected() == 0 { return Err(LedgerError::NotFound); }

            sqlx::query("DELETE FROM shared_expense_shares WHERE shared_expense_id=?")
                .bind(&id).execute(&mut *tx).await?;
            for s in &shares {
                sqlx::query("INSERT INTO shared_expense_shares (id, shared_expense_id, member_id, share_cents) VALUES (?,?,?,?)")
                    .bind(Uuid::new_v4().to_string()).bind(&id).bind(&s.member_id).bind(s.share_cents)
                    .execute(&mut *tx).await?;
            }
            tx.commit().await?;

            let row = sqlx::query_as::<_, SharedExpenseRow>(&format!("{EXPENSE_SELECT} WHERE e.id = ?"))
                .bind(&id).fetch_one(&self.pool).await?;
            Ok(row_to_shared_expense(row))
        })
    }

    pub fn delete_shared_expense(&self, id: String) -> Result<(), LedgerError> {
        self.rt.block_on(async {
            let mut tx = self.pool.begin().await?;
            sqlx::query("DELETE FROM shared_expense_shares WHERE shared_expense_id=?").bind(&id).execute(&mut *tx).await?;
            sqlx::query("DELETE FROM shared_expenses WHERE id=?").bind(&id).execute(&mut *tx).await?;
            tx.commit().await?;
            Ok(())
        })
    }

    // ── Settling up ──────────────────────────────────────────────────────────

    /// `transaction_id` is set when the money moved through one of your wallets. It is null when two
    /// other people squared up between themselves: the group balance changes, but nothing of yours
    /// did, so there is no transaction to point at.
    pub fn record_settlement(
        &self,
        group_id: String,
        from_member_id: String,
        to_member_id: String,
        amount_cents: i64,
        transaction_id: Option<String>,
        occurred_at: Option<String>,
    ) -> Result<Settlement, LedgerError> {
        if amount_cents <= 0 { return Err(LedgerError::InvalidInput("amount must be positive".into())); }
        if from_member_id == to_member_id {
            return Err(LedgerError::InvalidInput("paying yourself back is not a payment".into()));
        }

        self.rt.block_on(async {
            // Both sides have to belong to this group, or the balances would never come back to zero
            // and nothing would say why.
            let (belong,): (i64,) = sqlx::query_as(
                "SELECT COUNT(*) FROM group_members WHERE group_id = ?1 AND id IN (?2, ?3)"
            ).bind(&group_id).bind(&from_member_id).bind(&to_member_id).fetch_one(&self.pool).await?;
            if belong != 2 {
                return Err(LedgerError::InvalidInput("both people have to be in this group".into()));
            }

            let id = Uuid::new_v4().to_string();
            let now = Utc::now().to_rfc3339();
            let occurred = occurred_at.unwrap_or_else(|| now.clone());
            sqlx::query(
                "INSERT INTO settlements (id, group_id, from_member_id, to_member_id, amount_cents, transaction_id, occurred_at, created_at)
                 VALUES (?,?,?,?,?,?,?,?)"
            )
            .bind(&id).bind(&group_id).bind(&from_member_id).bind(&to_member_id)
            .bind(amount_cents).bind(&transaction_id).bind(&occurred).bind(&now)
            .execute(&self.pool).await?;

            let row = sqlx::query_as::<_, SettlementRow>(&format!("{SETTLEMENT_SELECT} WHERE s.id = ?"))
                .bind(&id).fetch_one(&self.pool).await?;
            Ok(row_to_settlement(row))
        })
    }

    pub fn list_settlements(&self, group_id: String) -> Result<Vec<Settlement>, LedgerError> {
        self.rt.block_on(async {
            let rows = sqlx::query_as::<_, SettlementRow>(
                &format!("{SETTLEMENT_SELECT} WHERE s.group_id = ? ORDER BY s.occurred_at DESC, s.created_at DESC, s.id DESC")
            ).bind(&group_id).fetch_all(&self.pool).await?;
            Ok(rows.into_iter().map(row_to_settlement).collect())
        })
    }

    pub fn delete_settlement(&self, id: String) -> Result<(), LedgerError> {
        self.rt.block_on(async {
            sqlx::query("DELETE FROM settlements WHERE id=?").bind(&id).execute(&self.pool).await?;
            Ok(())
        })
    }

    /// Who should pay whom to close the group.
    ///
    /// Everybody paying everybody they owe is up to one payment per pair; this gives one per person
    /// less one, by repeatedly sending the largest debt to the largest credit. Nobody is asked for
    /// more than they owe, and because each step takes the smaller of the two sides, every balance
    /// lands exactly on zero rather than near it.
    pub fn suggest_settlements(&self, group_id: String) -> Result<Vec<SettlementSuggestion>, LedgerError> {
        let members = self.list_group_members(group_id)?;
        let named: Vec<(String, String, i64)> = members.into_iter()
            .map(|m| (m.id, m.name, m.balance_cents))
            .collect();

        let mut owe: Vec<(String, String, i64)> = named.iter()
            .filter(|(_, _, b)| *b < 0)
            .map(|(id, name, b)| (id.clone(), name.clone(), -b))
            .collect();
        let mut owed: Vec<(String, String, i64)> = named.iter()
            .filter(|(_, _, b)| *b > 0)
            .map(|(id, name, b)| (id.clone(), name.clone(), *b))
            .collect();

        // Largest first, so the biggest debt is cleared against the biggest credit and each step
        // retires at least one of the two.
        owe.sort_by(|a, b| b.2.cmp(&a.2).then(a.0.cmp(&b.0)));
        owed.sort_by(|a, b| b.2.cmp(&a.2).then(a.0.cmp(&b.0)));

        let mut plan = Vec::new();
        let (mut i, mut j) = (0usize, 0usize);
        while i < owe.len() && j < owed.len() {
            let amount = owe[i].2.min(owed[j].2);
            if amount > 0 {
                plan.push(SettlementSuggestion {
                    from_member_id: owe[i].0.clone(), from_name: owe[i].1.clone(),
                    to_member_id: owed[j].0.clone(), to_name: owed[j].1.clone(),
                    amount_cents: amount,
                });
            }
            owe[i].2 -= amount;
            owed[j].2 -= amount;
            if owe[i].2 == 0 { i += 1; }
            if owed[j].2 == 0 { j += 1; }
        }
        Ok(plan)
    }

    pub fn list_expense_shares(&self, shared_expense_id: String) -> Result<Vec<ExpenseShare>, LedgerError> {
        self.rt.block_on(async {
            let rows = sqlx::query_as::<_, ExpenseShareRow>(
                "SELECT s.id, s.shared_expense_id, s.member_id, m.name AS member_name, s.share_cents
                 FROM shared_expense_shares s JOIN group_members m ON m.id = s.member_id
                 WHERE s.shared_expense_id = ? ORDER BY m.is_you DESC, m.created_at ASC"
            ).bind(&shared_expense_id).fetch_all(&self.pool).await?;
            Ok(rows.into_iter().map(row_to_expense_share).collect())
        })
    }

    async fn group_by_id(&self, id: &str) -> Result<ExpenseGroup, LedgerError> {
        let row = sqlx::query_as::<_, ExpenseGroupRow>(&format!("{GROUP_SELECT} WHERE g.id = ?"))
            .bind(id).fetch_optional(&self.pool).await?
            .ok_or(LedgerError::NotFound)?;
        Ok(row_to_group(row))
    }

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


// ── Backup helpers ───────────────────────────────────────────────────────────

/// Opens a database file without running migrations on it. Inspecting a backup must not alter it.
async fn open_readonly(path: &str) -> Result<SqlitePool, sqlx::Error> {
    use std::str::FromStr;
    let options = sqlx::sqlite::SqliteConnectOptions::from_str(&format!("sqlite:{}", path))?
        .read_only(true)
        .create_if_missing(false);
    SqlitePool::connect_with(options).await
}

async fn current_version(pool: &SqlitePool) -> Result<i64, sqlx::Error> {
    let (v,): (i64,) = sqlx::query_as("SELECT COALESCE(MAX(version), 0) FROM schema_version")
        .fetch_one(pool)
        .await?;
    Ok(v)
}

async fn count_of(pool: &SqlitePool, table: &str) -> Result<i64, sqlx::Error> {
    // A table can legitimately be missing from an old backup taken before it existed.
    let row: Result<(i64,), _> = sqlx::query_as(&format!("SELECT COUNT(*) FROM {}", table))
        .fetch_one(pool)
        .await;
    Ok(row.map(|r| r.0).unwrap_or(0))
}

async fn count_contents(pool: &SqlitePool) -> Result<BackupInfo, LedgerError> {
    Ok(BackupInfo {
        path: String::new(),
        schema_version: 0,
        wallets: count_of(pool, "wallets").await?,
        transactions: count_of(pool, "transactions").await?,
        categories: count_of(pool, "categories").await?,
        budgets: count_of(pool, "budgets").await?,
        goals: count_of(pool, "savings_goals").await?,
        debts: count_of(pool, "debts").await?,
        transfers: count_of(pool, "transfers").await?,
        recurring: count_of(pool, "recurring_transactions").await?,
    })
}


// ── Shared expenses ──────────────────────────────────────────────────────────

// Totals are summed on read, like every other total in this schema. A group's numbers are only ever
// as right as the rows underneath them, which is the point.
const GROUP_SELECT: &str = "SELECT g.id, g.name, g.emoji, g.color_hex, \
     COALESCE((SELECT SUM(amount_cents) FROM shared_expenses WHERE group_id = g.id), 0) AS total_cents, \
     COALESCE((SELECT SUM(s.share_cents) FROM shared_expense_shares s \
               JOIN group_members m ON m.id = s.member_id \
               JOIN shared_expenses e ON e.id = s.shared_expense_id \
               WHERE e.group_id = g.id AND m.is_you = 1), 0) AS your_share_cents, \
     COALESCE((SELECT SUM(amount_cents) FROM shared_expenses e \
               JOIN group_members m ON m.id = e.paid_by_member_id \
               WHERE e.group_id = g.id AND m.is_you = 1), 0) \
       - COALESCE((SELECT SUM(s.share_cents) FROM shared_expense_shares s \
                   JOIN group_members m ON m.id = s.member_id \
                   JOIN shared_expenses e ON e.id = s.shared_expense_id \
                   WHERE e.group_id = g.id AND m.is_you = 1), 0) AS net_balance_cents, \
     (SELECT COUNT(*) FROM group_members WHERE group_id = g.id) AS member_count, \
     (SELECT COUNT(*) FROM shared_expenses WHERE group_id = g.id) AS expense_count, \
     g.created_at FROM expense_groups g";

#[derive(sqlx::FromRow)]
struct SettlementRow {
    id: String,
    group_id: String,
    from_member_id: String,
    from_name: String,
    to_member_id: String,
    to_name: String,
    amount_cents: i64,
    transaction_id: Option<String>,
    occurred_at: String,
}

fn row_to_settlement(r: SettlementRow) -> Settlement {
    Settlement {
        id: r.id, group_id: r.group_id,
        from_member_id: r.from_member_id, from_name: r.from_name,
        to_member_id: r.to_member_id, to_name: r.to_name,
        amount_cents: r.amount_cents, transaction_id: r.transaction_id,
        occurred_at: r.occurred_at,
    }
}

const SETTLEMENT_SELECT: &str = "SELECT s.id, s.group_id, s.from_member_id, f.name AS from_name, \
     s.to_member_id, t.name AS to_name, s.amount_cents, s.transaction_id, s.occurred_at \
     FROM settlements s \
     JOIN group_members f ON f.id = s.from_member_id \
     JOIN group_members t ON t.id = s.to_member_id";

// Paying somebody back counts as putting that much more into the group, and being paid back counts
// as putting that much less in — which is what keeps the balances summing to zero across a
// settlement instead of quietly leaking the amount that changed hands.
const MEMBER_SELECT: &str = "SELECT m.id, m.group_id, m.name, m.is_you, \
     COALESCE((SELECT SUM(amount_cents) FROM shared_expenses WHERE paid_by_member_id = m.id), 0) \
       + COALESCE((SELECT SUM(amount_cents) FROM settlements WHERE from_member_id = m.id), 0) AS paid_cents, \
     COALESCE((SELECT SUM(share_cents) FROM shared_expense_shares WHERE member_id = m.id), 0) \
       + COALESCE((SELECT SUM(amount_cents) FROM settlements WHERE to_member_id = m.id), 0) AS owes_cents \
     FROM group_members m";

const EXPENSE_SELECT: &str = "SELECT e.id, e.group_id, e.transaction_id, e.description, e.amount_cents, \
     e.paid_by_member_id, p.name AS paid_by_name, \
     COALESCE((SELECT SUM(s.share_cents) FROM shared_expense_shares s \
               JOIN group_members m ON m.id = s.member_id \
               WHERE s.shared_expense_id = e.id AND m.is_you = 1), 0) AS your_share_cents, \
     e.occurred_at \
     FROM shared_expenses e JOIN group_members p ON p.id = e.paid_by_member_id";

/// Splits `amount_cents` between `people`, giving the remainder to the first shares.
///
/// 100.00 three ways is 33.333…, which has no exact answer in cents. Rounding each share
/// independently loses the odd cent and the group silently stops adding up, so the remainder is
/// handed out deliberately instead: the first person pays the extra penny.
pub fn split_equally(amount_cents: i64, people: i32) -> Vec<i64> {
    if people <= 0 { return Vec::new(); }
    let n = people as i64;
    let base = amount_cents / n;
    let remainder = amount_cents - base * n;
    (0..n).map(|i| if i < remainder { base + 1 } else { base }).collect()
}

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

fn row_to_group(r: ExpenseGroupRow) -> ExpenseGroup {
    ExpenseGroup {
        id: r.id, name: r.name, emoji: r.emoji, color_hex: r.color_hex,
        total_cents: r.total_cents, your_share_cents: r.your_share_cents,
        net_balance_cents: r.net_balance_cents,
        member_count: r.member_count as i32, expense_count: r.expense_count as i32,
        created_at: r.created_at,
    }
}

fn row_to_member(r: GroupMemberRow) -> GroupMember {
    GroupMember {
        id: r.id, group_id: r.group_id, name: r.name, is_you: r.is_you,
        paid_cents: r.paid_cents, owes_cents: r.owes_cents,
        balance_cents: r.paid_cents - r.owes_cents,
    }
}

fn row_to_shared_expense(r: SharedExpenseRow) -> SharedExpense {
    SharedExpense {
        id: r.id, group_id: r.group_id, transaction_id: r.transaction_id,
        description: r.description, amount_cents: r.amount_cents,
        paid_by_member_id: r.paid_by_member_id, paid_by_name: r.paid_by_name,
        your_share_cents: r.your_share_cents, occurred_at: r.occurred_at,
    }
}

fn row_to_expense_share(r: ExpenseShareRow) -> ExpenseShare {
    ExpenseShare {
        id: r.id, shared_expense_id: r.shared_expense_id, member_id: r.member_id,
        member_name: r.member_name, share_cents: r.share_cents,
    }
}
