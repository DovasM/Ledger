use serde::{Deserialize, Serialize};
use sqlx::FromRow;

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct TransactionRow {
    pub id: String,
    pub wallet_id: String,
    pub title: String,
    pub category: String,
    pub amount: f64,
    pub is_income: bool,
    pub note: Option<String>,
    pub occurred_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct WalletRow {
    pub id: String,
    pub name: String,
    pub description: String,
    pub currency: String,
    pub balance: f64,
    pub off_budget: bool,
    pub created_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct TransferRow {
    pub id: String,
    pub from_wallet_id: String,
    pub to_wallet_id: String,
    pub amount: f64,
    pub note: Option<String>,
    pub created_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct SavingsGoalRow {
    pub id: String,
    pub name: String,
    pub current_amount: f64,
    pub target_amount: f64,
    pub deadline: Option<String>,
    pub created_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct CategoryRow {
    pub id: String,
    pub name: String,
    pub icon_name: String,
    pub color_hex: String,
    pub is_expense: bool,
    pub created_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct BudgetRow {
    pub id: String,
    pub category_id: Option<String>,
    pub wallet_id: Option<String>,
    pub limit_amount: f64,
    pub period: String,
    pub alert_threshold: f64,
    pub carry_over: bool,
    pub created_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct DebtRow {
    pub id: String,
    pub name: String,
    pub debt_type: String,
    pub total_amount: f64,
    pub remaining_amount: f64,
    pub apr: f64,
    pub monthly_payment: f64,
    pub created_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct RecurringTransactionRow {
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

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct TagRow {
    pub id: String,
    pub name: String,
    pub created_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct PriceAlertRow {
    pub id: String,
    pub symbol: String,
    pub asset_name: String,
    pub target_price: f64,
    pub direction: String,
    pub active: bool,
    pub created_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct GoalContributionRow {
    pub id: String,
    pub goal_id: String,
    pub amount: f64,
    pub note: Option<String>,
    pub kind: String,
    pub occurred_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct DebtPaymentRow {
    pub id: String,
    pub debt_id: String,
    pub amount: f64,
    pub note: Option<String>,
    pub kind: String,
    pub occurred_at: String,
}
