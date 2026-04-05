use serde::{Deserialize, Serialize};
use sqlx::FromRow;

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct TransactionRow {
    pub id: String,
    pub wallet_id: String,
    pub title: String,
    pub category: String,
    pub amount: f64,
    // SQLite stores booleans as INTEGER (0/1)
    pub is_income: bool,
    pub note: Option<String>,
    pub created_at: String,
}

#[derive(Debug, Clone, Serialize, Deserialize, FromRow)]
pub struct WalletRow {
    pub id: String,
    pub name: String,
    pub description: String,
    pub balance: f64,
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
