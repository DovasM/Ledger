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

/// Deleting a wallet has to take its transactions with it explicitly. Only `transactions.wallet_id`
/// declares `ON DELETE CASCADE` at all, and the delete path cannot depend on that: it has to behave
/// the same for the tables that declare nothing.
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

/// The three shapes a budget can take, read back exactly as they were written.
#[test]
fn a_budget_keeps_its_scope_and_settings() {
    let t = TestDb::new();
    let c = t.db.create_category("Groceries".into(), "cart".into(), "#00FF00".into(), true).unwrap();
    let w = t.db.create_wallet("Checking".into(), String::new(), "EUR".into(), 0, false).unwrap();

    let overall = t.db.create_budget(None, None, 31_000, "monthly".into(), 75.0, true).unwrap();
    assert_eq!(overall.category_id, None);
    assert_eq!(overall.wallet_id, None);
    assert_eq!(overall.limit_amount_cents, 31_000);
    assert!(overall.carry_over);
    assert_eq!(overall.alert_threshold, 75.0, "a percentage, not money");

    let per_category = t.db.create_budget(Some(c.id.clone()), None, 15_000, "weekly".into(), 80.0, false).unwrap();
    assert_eq!(per_category.category_id.as_deref(), Some(c.id.as_str()));
    assert_eq!(per_category.period, "weekly");
    assert!(!per_category.carry_over);

    let per_wallet = t.db.create_budget(None, Some(w.id.clone()), 80_000, "monthly".into(), 80.0, false).unwrap();
    assert_eq!(per_wallet.wallet_id.as_deref(), Some(w.id.as_str()));
    assert_eq!(per_wallet.category_id, None);

    assert_eq!(t.db.list_budgets().unwrap().len(), 3);
}

/// The same category budgeted twice for the same period is two answers to one question, and
/// CategoryPace would sum them into a limit nobody set.
#[test]
fn one_category_cannot_have_two_budgets_for_the_same_period() {
    let t = TestDb::new();
    let c = t.db.create_category("Groceries".into(), "cart".into(), "#00FF00".into(), true).unwrap();

    t.db.create_budget(Some(c.id.clone()), None, 15_000, "monthly".into(), 80.0, false).unwrap();
    let duplicate = t.db.create_budget(Some(c.id.clone()), None, 20_000, "monthly".into(), 80.0, false);
    assert!(duplicate.is_err(), "a second monthly budget for the same category must be refused");

    // A different period is a different question and stays allowed.
    t.db.create_budget(Some(c.id), None, 5_000, "weekly".into(), 80.0, false).unwrap();
    assert_eq!(t.db.list_budgets().unwrap().len(), 2);
}

#[test]
fn editing_a_budget_persists_every_field() {
    let t = TestDb::new();
    let c = t.db.create_category("Groceries".into(), "cart".into(), "#00FF00".into(), true).unwrap();
    let b = t.db.create_budget(Some(c.id.clone()), None, 15_000, "monthly".into(), 80.0, false).unwrap();

    let edited = t.db
        .update_budget(b.id.clone(), Some(c.id), None, 22_500, "weekly".into(), 65.0, true)
        .unwrap();
    assert_eq!(edited.limit_amount_cents, 22_500);
    assert_eq!(edited.period, "weekly");
    assert_eq!(edited.alert_threshold, 65.0);
    assert!(edited.carry_over);

    let reloaded = t.db.list_budgets().unwrap().into_iter().find(|x| x.id == b.id).unwrap();
    assert_eq!(reloaded.limit_amount_cents, 22_500);
    assert!(reloaded.carry_over, "the switch has silently saved nothing before");
}

#[test]
fn deleting_a_budget_removes_only_that_budget() {
    let t = TestDb::new();
    let c1 = t.db.create_category("Groceries".into(), "cart".into(), "#00FF00".into(), true).unwrap();
    let c2 = t.db.create_category("Smoking".into(), "smoke".into(), "#FF0000".into(), true).unwrap();
    let keep = t.db.create_budget(Some(c1.id), None, 15_000, "monthly".into(), 80.0, false).unwrap();
    let drop = t.db.create_budget(Some(c2.id), None, 1_000, "monthly".into(), 80.0, false).unwrap();

    t.db.delete_budget(drop.id).unwrap();
    let left = t.db.list_budgets().unwrap();
    assert_eq!(left.len(), 1);
    assert_eq!(left[0].id, keep.id);
}

/// A limit of zero or less is not a budget, it is a mistake.
#[test]
fn a_budget_limit_must_be_positive() {
    let t = TestDb::new();
    assert!(t.db.create_budget(None, None, 0, "monthly".into(), 80.0, false).is_err());
    assert!(t.db.create_budget(None, None, -100, "monthly".into(), 80.0, false).is_err());
}

/// Deleting the overall budget must free the slot, or the user can never replace it.
#[test]
fn the_overall_budget_slot_frees_up_when_it_is_deleted() {
    let t = TestDb::new();
    let first = t.db.create_budget(None, None, 25_000, "monthly".into(), 80.0, false).unwrap();
    assert!(t.db.create_budget(None, None, 31_000, "monthly".into(), 80.0, false).is_err());

    t.db.delete_budget(first.id).unwrap();
    let replacement = t.db.create_budget(None, None, 31_000, "monthly".into(), 80.0, false).unwrap();
    assert_eq!(replacement.limit_amount_cents, 31_000);
}
