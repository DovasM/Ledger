//! Regression tests for the wallet and budget rules that have silently failed before.

mod common;
use common::{day, TestDb};

/// The bug: `update_wallet` accepted `off_budget` and never wrote it, so the toggle saved nothing.
/// The compiler had said `unused variable: off_budget`; the build log was filtered to errors only.
#[test]
fn off_budget_survives_an_edit() {
    let t = TestDb::new();
    let w = t.db.create_wallet("Work".into(), String::new(), "EUR".into(), 0, true).unwrap();
    assert!(w.off_budget, "the flag must persist through create");

    let edited = t.db.update_wallet(w.id.clone(), "Work account".into(), String::new(), "EUR".into(), true).unwrap();
    assert!(edited.off_budget, "the flag must survive an edit that keeps it on");

    let reloaded = t.db.list_wallets().unwrap().into_iter().find(|x| x.id == w.id).unwrap();
    assert!(reloaded.off_budget, "and must still be set when read back from the database");

    let cleared = t.db.update_wallet(w.id, "Work account".into(), String::new(), "EUR".into(), false).unwrap();
    assert!(!cleared.off_budget, "turning it off must work too");
}

/// "At most X in total" can only be one number. A second overall budget used to be accepted and
/// then silently ignored.
#[test]
fn a_second_overall_budget_is_refused() {
    let t = TestDb::new();
    t.db.create_budget(None, None, 25000, "monthly".into(), 0.8, true).unwrap();

    let second = t.db.create_budget(None, None, 40000, "monthly".into(), 0.8, false);
    assert!(second.is_err(), "a second overall budget must be rejected, not ignored");

    // A category budget and a wallet budget are different things and stay allowed.
    let c = t.db.create_category("Groceries".into(), "cart".into(), "#00FF00".into(), true).unwrap();
    t.db.create_budget(Some(c.id), None, 10000, "monthly".into(), 0.8, false).unwrap();
    let wallet = t.db.create_wallet("Checking".into(), String::new(), "EUR".into(), 0, false).unwrap();
    t.db.create_budget(None, Some(wallet.id), 80000, "monthly".into(), 0.8, false).unwrap();
    assert_eq!(t.db.list_budgets().unwrap().len(), 3);
}

/// A transfer is neither income nor expense. Folded into `transactions` it would show as an expense
/// in one wallet and income in the other, and every report would count it twice.
#[test]
fn a_transfer_moves_money_without_touching_income_or_expenses() {
    let t = TestDb::new();
    let from = t.db.create_wallet("Checking".into(), String::new(), "EUR".into(), 10000, false).unwrap();
    let to = t.db.create_wallet("Savings".into(), String::new(), "EUR".into(), 0, false).unwrap();
    let balance = |id: &str| t.db.list_wallets().unwrap().into_iter().find(|x| x.id == id).unwrap().balance_cents;

    let tr = t.db.create_transfer(from.id.clone(), to.id.clone(), 4000, None, None).unwrap();
    assert_eq!(balance(&from.id), 6000);
    assert_eq!(balance(&to.id), 4000);
    assert!(t.db.list_all_transactions(100, 0).unwrap().is_empty(), "a transfer must not appear as a transaction");

    t.db.delete_transfer(tr.id).unwrap();
    assert_eq!(balance(&from.id), 10000, "deleting a transfer must put the money back");
    assert_eq!(balance(&to.id), 0);

    assert!(t.db.create_transfer(from.id.clone(), from.id, 1000, None, None).is_err(), "same wallet on both sides");
}

/// SQLite ignores foreign keys unless `PRAGMA foreign_keys=ON`, which this pool never sets, so
/// every declared `ON DELETE CASCADE` did nothing and deleted wallets left their rows behind.
#[test]
fn deleting_a_wallet_takes_its_transactions_with_it() {
    let t = TestDb::new();
    let keep = t.db.create_wallet("Checking".into(), String::new(), "EUR".into(), 0, false).unwrap();
    let drop = t.db.create_wallet("Old".into(), String::new(), "EUR".into(), 0, false).unwrap();

    t.db.create_transaction(keep.id.clone(), "stays".into(), "Groceries".into(), 500, false, None, day("2026-08-10")).unwrap();
    t.db.create_transaction(drop.id.clone(), "goes".into(), "Groceries".into(), 500, false, None, day("2026-08-10")).unwrap();

    assert_eq!(t.db.count_transactions_for_wallet(drop.id.clone()).unwrap(), 1, "the UI warns with this count");

    t.db.delete_wallet(drop.id).unwrap();
    let left: Vec<_> = t.db.list_all_transactions(100, 0).unwrap().into_iter().map(|x| x.title).collect();
    assert_eq!(left, vec!["stays"], "the deleted wallet's transactions must be gone, and only those");
}

/// Deleting a category used to orphan its budget — invisible forever, because every screen resolves
/// a budget through its category.
#[test]
fn deleting_a_category_takes_its_budget_and_leaves_the_transactions_labelled() {
    let t = TestDb::new();
    let w = t.with_wallet();
    let c = t.db.create_category("Groceries".into(), "cart".into(), "#00FF00".into(), true).unwrap();
    t.db.create_budget(Some(c.id.clone()), None, 10000, "monthly".into(), 0.8, false).unwrap();
    t.db.create_transaction(w, "shopping".into(), "Groceries".into(), 500, false, None, day("2026-08-10")).unwrap();

    assert_eq!(t.db.count_transactions_for_category(c.id.clone()).unwrap(), 1);

    t.db.delete_category(c.id).unwrap();
    assert!(t.db.list_budgets().unwrap().is_empty(), "the budget must go with its category");

    let tx = &t.db.list_all_transactions(100, 0).unwrap()[0];
    assert_eq!(tx.category, "Groceries", "the transaction keeps its label after losing the link");
}
