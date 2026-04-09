uniffi::include_scaffolding!("ledger");

mod db;

use db::open_pool;
use db::models::{
    TransactionRow, WalletRow, SavingsGoalRow,
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
    pub category_id: String,
    pub limit_amount: f64,
    pub period: String,
    pub alert_threshold: f64,
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
                "SELECT id, wallet_id, title, category, amount, is_income, note, created_at
                 FROM transactions WHERE wallet_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?"
            )
            .bind(&wallet_id).bind(limit as i64).bind(offset as i64)
            .fetch_all(&self.pool).await?;
            Ok(rows.into_iter().map(row_to_transaction).collect())
        })
    }

    pub fn list_all_transactions(&self, limit: u32, offset: u32) -> Result<Vec<Transaction>, LedgerError> {
        self.rt.block_on(async {
            let rows = sqlx::query_as::<_, TransactionRow>(
                "SELECT id, wallet_id, title, category, amount, is_income, note, created_at
                 FROM transactions ORDER BY created_at DESC LIMIT ? OFFSET ?"
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

            sqlx::query(
                "INSERT INTO transactions (id, wallet_id, title, category, amount, is_income, note, created_at) VALUES (?,?,?,?,?,?,?,?)"
            )
            .bind(&id).bind(&wallet_id).bind(&title).bind(&category)
            .bind(amount).bind(is_income).bind(&note).bind(&date)
            .execute(&self.pool).await?;

            sqlx::query("UPDATE wallets SET balance = balance + ? WHERE id = ?")
                .bind(sign).bind(&wallet_id)
                .execute(&self.pool).await?;

            let row = sqlx::query_as::<_, TransactionRow>(
                "SELECT id, wallet_id, title, category, amount, is_income, note, created_at FROM transactions WHERE id = ?"
            )
            .bind(&id).fetch_one(&self.pool).await?;
            Ok(row_to_transaction(row))
        })
    }

    pub fn update_transaction(&self, id: String, title: String, category: String, amount: f64, is_income: bool, note: Option<String>, created_at: Option<String>) -> Result<Transaction, LedgerError> {
        self.rt.block_on(async {
            sqlx::query("UPDATE transactions SET title=?, category=?, amount=?, is_income=?, note=?, created_at=COALESCE(?,created_at) WHERE id=?")
                .bind(&title).bind(&category).bind(amount).bind(is_income).bind(&note).bind(&created_at).bind(&id)
                .execute(&self.pool).await?;

            let row = sqlx::query_as::<_, TransactionRow>(
                "SELECT id, wallet_id, title, category, amount, is_income, note, created_at FROM transactions WHERE id = ?"
            )
            .bind(&id).fetch_optional(&self.pool).await?
            .ok_or(LedgerError::NotFound)?;
            Ok(row_to_transaction(row))
        })
    }

    pub fn delete_transaction(&self, id: String) -> Result<(), LedgerError> {
        self.rt.block_on(async {
            sqlx::query("DELETE FROM transactions WHERE id=?").bind(&id).execute(&self.pool).await?;
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
            .bind(&id).fetch_one(&self.pool).await?;
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
            .bind(&id).fetch_optional(&self.pool).await?
            .ok_or(LedgerError::NotFound)?;
            Ok(row_to_wallet(row))
        })
    }

    pub fn delete_wallet(&self, id: String) -> Result<(), LedgerError> {
        self.rt.block_on(async {
            sqlx::query("DELETE FROM wallets WHERE id=?").bind(&id).execute(&self.pool).await?;
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
            sqlx::query("DELETE FROM categories WHERE id=?").bind(&id).execute(&self.pool).await?;
            Ok(())
        })
    }

    // ── Budgets ──────────────────────────────────────────────────────────────

    pub fn list_budgets(&self) -> Result<Vec<Budget>, LedgerError> {
        self.rt.block_on(async {
            let rows = sqlx::query_as::<_, BudgetRow>(
                "SELECT id, category_id, limit_amount, period, alert_threshold, created_at FROM budgets ORDER BY created_at ASC"
            )
            .fetch_all(&self.pool).await?;
            Ok(rows.into_iter().map(row_to_budget).collect())
        })
    }

    pub fn create_budget(&self, category_id: String, limit_amount: f64, period: String, alert_threshold: f64) -> Result<Budget, LedgerError> {
        if limit_amount <= 0.0 { return Err(LedgerError::InvalidInput("limit must be positive".into())); }
        self.rt.block_on(async {
            let id = Uuid::new_v4().to_string();
            let now = Utc::now().to_rfc3339();
            sqlx::query(
                "INSERT INTO budgets (id, category_id, limit_amount, period, alert_threshold, created_at) VALUES (?,?,?,?,?,?)"
            )
            .bind(&id).bind(&category_id).bind(limit_amount).bind(&period).bind(alert_threshold).bind(&now)
            .execute(&self.pool).await?;

            let row = sqlx::query_as::<_, BudgetRow>(
                "SELECT id, category_id, limit_amount, period, alert_threshold, created_at FROM budgets WHERE id=?"
            )
            .bind(&id).fetch_one(&self.pool).await?;
            Ok(row_to_budget(row))
        })
    }

    pub fn update_budget(&self, id: String, limit_amount: f64, period: String, alert_threshold: f64) -> Result<Budget, LedgerError> {
        if limit_amount <= 0.0 { return Err(LedgerError::InvalidInput("limit must be positive".into())); }
        self.rt.block_on(async {
            sqlx::query("UPDATE budgets SET limit_amount=?, period=?, alert_threshold=? WHERE id=?")
                .bind(limit_amount).bind(&period).bind(alert_threshold).bind(&id)
                .execute(&self.pool).await?;

            let row = sqlx::query_as::<_, BudgetRow>(
                "SELECT id, category_id, limit_amount, period, alert_threshold, created_at FROM budgets WHERE id=?"
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
                "SELECT id, title, amount, category, wallet_id, is_income, frequency, next_date, created_at FROM recurring_transactions ORDER BY next_date ASC"
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
            sqlx::query(
                "INSERT INTO recurring_transactions (id, title, amount, category, wallet_id, is_income, frequency, next_date, created_at) VALUES (?,?,?,?,?,?,?,?,?)"
            )
            .bind(&id).bind(&title).bind(amount).bind(&category).bind(&wallet_id).bind(is_income).bind(&frequency).bind(&next_date).bind(&now)
            .execute(&self.pool).await?;

            let row = sqlx::query_as::<_, RecurringTransactionRow>(
                "SELECT id, title, amount, category, wallet_id, is_income, frequency, next_date, created_at FROM recurring_transactions WHERE id=?"
            )
            .bind(&id).fetch_one(&self.pool).await?;
            Ok(row_to_recurring(row))
        })
    }

    pub fn update_recurring(&self, id: String, title: String, amount: f64, category: String, frequency: String, next_date: String) -> Result<RecurringTransaction, LedgerError> {
        if title.is_empty() { return Err(LedgerError::InvalidInput("title is required".into())); }
        self.rt.block_on(async {
            sqlx::query("UPDATE recurring_transactions SET title=?, amount=?, category=?, frequency=?, next_date=? WHERE id=?")
                .bind(&title).bind(amount).bind(&category).bind(&frequency).bind(&next_date).bind(&id)
                .execute(&self.pool).await?;

            let row = sqlx::query_as::<_, RecurringTransactionRow>(
                "SELECT id, title, amount, category, wallet_id, is_income, frequency, next_date, created_at FROM recurring_transactions WHERE id=?"
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

// ── Row converters ────────────────────────────────────────────────────────────

fn row_to_transaction(r: TransactionRow) -> Transaction {
    Transaction { id: r.id, wallet_id: r.wallet_id, title: r.title, category: r.category, amount: r.amount, is_income: r.is_income, note: r.note, created_at: r.created_at }
}

fn row_to_wallet(r: WalletRow) -> Wallet {
    Wallet { id: r.id, name: r.name, description: r.description, balance: r.balance, created_at: r.created_at }
}

fn row_to_goal(r: SavingsGoalRow) -> SavingsGoal {
    SavingsGoal { id: r.id, name: r.name, current_amount: r.current_amount, target_amount: r.target_amount, deadline: r.deadline, created_at: r.created_at }
}

fn row_to_category(r: CategoryRow) -> Category {
    Category { id: r.id, name: r.name, icon_name: r.icon_name, color_hex: r.color_hex, is_expense: r.is_expense, created_at: r.created_at }
}

fn row_to_budget(r: BudgetRow) -> Budget {
    Budget { id: r.id, category_id: r.category_id, limit_amount: r.limit_amount, period: r.period, alert_threshold: r.alert_threshold, created_at: r.created_at }
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
