//! Regression tests for transaction dates, ordering and balances.
//!
//! Every test here corresponds to a bug that actually shipped. They are written against the same
//! `open_database` entry point the app uses, so a schema or migration mistake fails them too.

mod common;
use common::{day, TestDb};

/// The bug: `occurred_at` is a date, so every transaction entered today ties. Ordering by it alone
/// left the tiebreak to SQLite, and a just-saved transaction landed somewhere arbitrary among the
/// day's other rows — below the fold, reading as "the app didn't save it".
#[test]
fn newest_transaction_is_first_among_the_same_day() {
    let t = TestDb::new();
    let w = t.with_wallet();

    for title in ["imported one", "imported two", "imported three"] {
        t.db.create_transaction(w.clone(), title.into(), "Groceries".into(), 500, false, None, day("2026-08-10")).unwrap();
    }
    let just_added = t.db
        .create_transaction(w.clone(), "typed by hand".into(), "Groceries".into(), 1400, false, None, day("2026-08-10"))
        .unwrap();

    let listed = t.db.list_all_transactions(100, 0).unwrap();
    assert_eq!(listed[0].id, just_added.id, "the transaction just saved must be at the top of its day");

    let per_wallet = t.db.list_transactions(w, 100, 0).unwrap();
    assert_eq!(per_wallet[0].id, just_added.id, "the per-wallet list must order the same way");
}

/// Sorting is by when the money was spent, not by when the row was written — otherwise importing a
/// March statement in August files the whole thing under August.
#[test]
fn back_dated_transaction_sorts_by_when_it_happened() {
    let t = TestDb::new();
    let w = t.with_wallet();

    t.db.create_transaction(w.clone(), "today".into(), "Groceries".into(), 500, false, None, day("2026-08-10")).unwrap();
    // Written second, happened first.
    t.db.create_transaction(w.clone(), "back in March".into(), "Groceries".into(), 500, false, None, day("2026-03-01")).unwrap();

    let titles: Vec<_> = t.db.list_all_transactions(100, 0).unwrap().into_iter().map(|x| x.title).collect();
    assert_eq!(titles, vec!["today", "back in March"]);
}

/// A stable total order is what makes paging safe: without one, a row can appear on both pages or
/// on neither.
#[test]
fn paging_never_repeats_or_drops_a_row() {
    let t = TestDb::new();
    let w = t.with_wallet();
    for i in 0..25 {
        t.db.create_transaction(w.clone(), format!("tx {i}"), "Groceries".into(), 100, false, None, day("2026-08-10")).unwrap();
    }

    let mut seen = Vec::new();
    for page in 0..5 {
        seen.extend(t.db.list_all_transactions(5, page * 5).unwrap().into_iter().map(|x| x.id));
    }
    let unique: std::collections::HashSet<_> = seen.iter().collect();
    assert_eq!(seen.len(), 25, "paging returned the wrong number of rows");
    assert_eq!(unique.len(), 25, "a row was returned on two different pages");
}

#[test]
fn occurred_at_is_stored_as_given_and_defaults_to_now() {
    let t = TestDb::new();
    let w = t.with_wallet();

    let back_dated = t.db
        .create_transaction(w.clone(), "March".into(), "Groceries".into(), 500, false, None, day("2026-03-01"))
        .unwrap();
    assert_eq!(back_dated.occurred_at, "2026-03-01T00:00:00Z");

    let defaulted = t.db
        .create_transaction(w, "no date given".into(), "Groceries".into(), 500, false, None, None)
        .unwrap();
    assert!(!defaulted.occurred_at.is_empty(), "a missing date must fall back to now, not to empty");
}

/// `update_transaction` uses `COALESCE(?, occurred_at, created_at)`. Passing no date must leave the
/// transaction where it was rather than quietly moving it to today.
#[test]
fn editing_without_a_date_keeps_the_original_date() {
    let t = TestDb::new();
    let w = t.with_wallet();
    let tx = t.db
        .create_transaction(w, "March".into(), "Groceries".into(), 500, false, None, day("2026-03-01"))
        .unwrap();

    let edited = t.db
        .update_transaction(tx.id.clone(), "March, corrected".into(), "Groceries".into(), 700, false, None, None)
        .unwrap();
    assert_eq!(edited.occurred_at, "2026-03-01T00:00:00Z");
    assert_eq!(edited.amount_cents, 700);

    let moved = t.db
        .update_transaction(tx.id, "March, moved".into(), "Groceries".into(), 700, false, None, day("2026-04-02"))
        .unwrap();
    assert_eq!(moved.occurred_at, "2026-04-02T00:00:00Z", "an explicit date must still move it");
}

/// The balance is a stored running total, so an edit or a delete that forgets to reverse the
/// previous effect leaves it permanently wrong. Both did, once.
#[test]
fn wallet_balance_survives_edit_and_delete() {
    let t = TestDb::new();
    let w = t.db.create_wallet("Checking".into(), String::new(), "EUR".into(), 10000, false).unwrap().id;
    let balance = |id: &str| t.db.list_wallets().unwrap().into_iter().find(|x| x.id == id).unwrap().balance_cents;

    let tx = t.db
        .create_transaction(w.clone(), "groceries".into(), "Groceries".into(), 3000, false, None, day("2026-08-10"))
        .unwrap();
    assert_eq!(balance(&w), 7000);

    t.db.update_transaction(tx.id.clone(), "groceries".into(), "Groceries".into(), 1000, false, None, None).unwrap();
    assert_eq!(balance(&w), 9000, "an edited amount must replace the old one, not stack on it");

    t.db.update_transaction(tx.id.clone(), "refund".into(), "Groceries".into(), 1000, true, None, None).unwrap();
    assert_eq!(balance(&w), 11000, "flipping expense to income must reverse the expense first");

    t.db.delete_transaction(tx.id).unwrap();
    assert_eq!(balance(&w), 10000, "deleting must put the money back");
}

/// The bug: `resolve_category_id` matched on name alone, so 175 expense transactions linked to the
/// income-side category of the same name.
#[test]
fn category_links_respect_income_versus_expense() {
    let t = TestDb::new();
    let w = t.with_wallet();
    t.db.create_category("Gifts".into(), "gift".into(), "#FF0000".into(), true).unwrap();
    t.db.create_category("Gifts".into(), "gift".into(), "#00FF00".into(), false).unwrap();

    let spent = t.db.create_transaction(w.clone(), "present".into(), "Gifts".into(), 2000, false, None, day("2026-08-10")).unwrap();
    let received = t.db.create_transaction(w, "birthday".into(), "Gifts".into(), 5000, true, None, day("2026-08-10")).unwrap();

    assert_eq!(spent.category, "Gifts");
    assert_eq!(received.category, "Gifts");

    // Renaming the expense-side category must move the expense and leave the income alone.
    let expense_cat = t.db.list_categories().unwrap().into_iter().find(|c| c.name == "Gifts" && c.is_expense).unwrap();
    t.db.update_category(expense_cat.id, "Presents".into(), "gift".into(), "#FF0000".into(), true).unwrap();

    let after: Vec<_> = t.db.list_all_transactions(100, 0).unwrap();
    let spent_now = after.iter().find(|x| x.id == spent.id).unwrap();
    let received_now = after.iter().find(|x| x.id == received.id).unwrap();
    assert_eq!(spent_now.category, "Presents", "the rename must reach the expense transaction");
    assert_eq!(received_now.category, "Gifts", "and must not touch the income one");
}
