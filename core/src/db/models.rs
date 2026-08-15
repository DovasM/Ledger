use serde::{Deserialize, Serialize};
use sqlx::FromRow;

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct TransactionRow {
    pub id: String,
    pub wallet_id: String,
    pub title: String,
    pub category: String,
    pub amount_cents: i64,
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
    pub balance_cents: i64,
    pub off_budget: bool,
    pub created_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct TransferRow {
    pub id: String,
    pub from_wallet_id: String,
    pub to_wallet_id: String,
    pub amount_cents: i64,
    pub note: Option<String>,
    pub created_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct SavingsGoalRow {
    pub id: String,
    pub name: String,
    pub current_amount_cents: i64,
    pub target_amount_cents: i64,
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
    pub limit_amount_cents: i64,
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
    pub total_amount_cents: i64,
    pub remaining_amount_cents: i64,
    pub apr: f64,
    pub monthly_payment_cents: i64,
    pub created_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct RecurringTransactionRow {
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
    pub target_price_cents: i64,
    pub direction: String,
    pub active: bool,
    pub created_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct GoalContributionRow {
    pub id: String,
    pub goal_id: String,
    pub amount_cents: i64,
    pub note: Option<String>,
    pub kind: String,
    pub occurred_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct DebtPaymentRow {
    pub id: String,
    pub debt_id: String,
    pub amount_cents: i64,
    pub note: Option<String>,
    pub kind: String,
    pub occurred_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct ExpenseGroupRow {
    pub id: String,
    pub name: String,
    pub emoji: String,
    pub color_hex: String,
    pub total_cents: i64,
    pub your_share_cents: i64,
    pub net_balance_cents: i64,
    pub member_count: i64,
    pub expense_count: i64,
    pub created_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct GroupMemberRow {
    pub id: String,
    pub group_id: String,
    pub name: String,
    pub is_you: bool,
    pub paid_cents: i64,
    pub owes_cents: i64,
}

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct SharedExpenseRow {
    pub id: String,
    pub group_id: String,
    pub transaction_id: Option<String>,
    pub description: String,
    pub amount_cents: i64,
    pub paid_by_member_id: String,
    pub paid_by_name: String,
    pub your_share_cents: i64,
    pub occurred_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct ExpenseShareRow {
    pub id: String,
    pub shared_expense_id: String,
    pub member_id: String,
    pub member_name: String,
    pub share_cents: i64,
}
