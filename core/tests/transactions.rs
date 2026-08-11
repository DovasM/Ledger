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

/// A wallet's balance is the opening amount plus income minus expenses, however many rows there are.
#[test]
fn many_transactions_add_up_to_the_wallet_balance() {
    let t = TestDb::new();
    let w = t.db.create_wallet("Checking".into(), String::new(), "EUR".into(), 100_000, false).unwrap().id;

    for i in 0..10 {
        t.db.create_transaction(w.clone(), format!("expense {i}"), "Groceries".into(), 1_234, false, None, day("2026-03-05")).unwrap();
    }
    for i in 0..3 {
        t.db.create_transaction(w.clone(), format!("income {i}"), "Salary".into(), 5_000, true, None, day("2026-03-06")).unwrap();
    }

    let balance = t.db.list_wallets().unwrap().into_iter().find(|x| x.id == w).unwrap().balance_cents;
    assert_eq!(balance, 100_000 - 10 * 1_234 + 3 * 5_000, "integer cents make this exact, not approximate");
    assert_eq!(t.db.list_transactions(w, 100, 0).unwrap().len(), 13);
}

/// The monthly summary sums integers, so it is exact — and it only sees its own month.
#[test]
fn the_month_summary_covers_its_own_month_exactly() {
    let t = TestDb::new();
    let w = t.with_wallet();

    t.db.create_transaction(w.clone(), "march a".into(), "Groceries".into(), 1_999, false, None, day("2026-03-01")).unwrap();
    t.db.create_transaction(w.clone(), "march b".into(), "Groceries".into(), 2_001, false, None, day("2026-03-31")).unwrap();
    t.db.create_transaction(w.clone(), "march pay".into(), "Salary".into(), 300_000, true, None, day("2026-03-15")).unwrap();
    // Neighbouring months must not leak in.
    t.db.create_transaction(w.clone(), "february".into(), "Groceries".into(), 9_999, false, None, day("2026-02-28")).unwrap();
    t.db.create_transaction(w, "april".into(), "Groceries".into(), 8_888, false, None, day("2026-04-01")).unwrap();

    let m = t.db.get_month_summary(2026, 3).unwrap();
    assert_eq!(m.total_expenses_cents, 4_000);
    assert_eq!(m.total_income_cents, 300_000);
    assert_eq!(m.net_savings_cents, 296_000);
    assert_eq!(m.transaction_count, 3);

    let empty = t.db.get_month_summary(2026, 5).unwrap();
    assert_eq!(empty.total_expenses_cents, 0);
    assert_eq!(empty.transaction_count, 0);
}

/// The per-wallet list is scoped to its wallet; the all-wallets list is not.
#[test]
fn listing_by_wallet_returns_only_that_wallets_rows() {
    let t = TestDb::new();
    let a = t.db.create_wallet("A".into(), String::new(), "EUR".into(), 0, false).unwrap().id;
    let b = t.db.create_wallet("B".into(), String::new(), "EUR".into(), 0, false).unwrap().id;

    t.db.create_transaction(a.clone(), "in a".into(), "Groceries".into(), 100, false, None, day("2026-03-05")).unwrap();
    t.db.create_transaction(b.clone(), "in b".into(), "Groceries".into(), 200, false, None, day("2026-03-05")).unwrap();

    let only_a: Vec<_> = t.db.list_transactions(a, 100, 0).unwrap().into_iter().map(|x| x.title).collect();
    assert_eq!(only_a, vec!["in a"]);
    assert_eq!(t.db.list_all_transactions(100, 0).unwrap().len(), 2);
}

/// Rejected at the boundary rather than stored as a row nobody can interpret.
#[test]
fn a_transaction_needs_a_title_and_a_positive_amount() {
    let t = TestDb::new();
    let w = t.with_wallet();
    assert!(t.db.create_transaction(w.clone(), String::new(), "Groceries".into(), 100, false, None, None).is_err());
    assert!(t.db.create_transaction(w.clone(), "x".into(), "Groceries".into(), 0, false, None, None).is_err());
    assert!(t.db.create_transaction(w, "x".into(), "Groceries".into(), -100, false, None, None).is_err());
}

/// Deleting one of several transactions leaves the rest and the balance consistent.
#[test]
fn deleting_one_transaction_leaves_the_others_alone() {
    let t = TestDb::new();
    let w = t.db.create_wallet("Checking".into(), String::new(), "EUR".into(), 10_000, false).unwrap().id;
    let keep = t.db.create_transaction(w.clone(), "keep".into(), "Groceries".into(), 1_000, false, None, day("2026-03-05")).unwrap();
    let drop = t.db.create_transaction(w.clone(), "drop".into(), "Groceries".into(), 2_500, false, None, day("2026-03-06")).unwrap();

    t.db.delete_transaction(drop.id).unwrap();

    let left: Vec<_> = t.db.list_all_transactions(100, 0).unwrap();
    assert_eq!(left.len(), 1);
    assert_eq!(left[0].id, keep.id);
    let balance = t.db.list_wallets().unwrap()[0].balance_cents;
    assert_eq!(balance, 10_000 - 1_000);
}

/// Editing must move the transaction to its new category, not leave it filed under the old one.
#[test]
fn editing_a_transaction_changes_its_category_and_direction() {
    let t = TestDb::new();
    let w = t.db.create_wallet("Checking".into(), String::new(), "EUR".into(), 10_000, false).unwrap().id;
    t.db.create_category("Groceries".into(), "cart".into(), "#00FF00".into(), true).unwrap();
    t.db.create_category("Transport".into(), "car".into(), "#0000FF".into(), true).unwrap();

    let tx = t.db
        .create_transaction(w.clone(), "shopping".into(), "Groceries".into(), 2_000, false, Some("first note".into()), day("2026-03-05"))
        .unwrap();
    assert_eq!(tx.category, "Groceries");
    assert_eq!(tx.note.as_deref(), Some("first note"));

    let moved = t.db
        .update_transaction(tx.id.clone(), "taxi".into(), "Transport".into(), 3_000, false, Some("second note".into()), None)
        .unwrap();
    assert_eq!(moved.category, "Transport");
    assert_eq!(moved.title, "taxi");
    assert_eq!(moved.note.as_deref(), Some("second note"));
    assert_eq!(moved.amount_cents, 3_000);

    // The category link, not just the stored label: renaming Transport must now reach this row.
    let transport = t.db.list_categories().unwrap().into_iter().find(|c| c.name == "Transport").unwrap();
    t.db.update_category(transport.id, "Travel".into(), "car".into(), "#0000FF".into(), true).unwrap();
    assert_eq!(t.db.list_all_transactions(10, 0).unwrap()[0].category, "Travel");

    let flipped = t.db
        .update_transaction(tx.id, "refund".into(), "Transport".into(), 3_000, true, None, None)
        .unwrap();
    assert!(flipped.is_income);
    assert_eq!(t.db.list_wallets().unwrap()[0].balance_cents, 10_000 + 3_000);
}

/// Tags are many-to-many and the link rows are reachable only through their two owners, so both
/// delete paths have to leave nothing behind.
///
/// Note what this does *not* prove: `transaction_tags` is one of the three columns that actually
/// declares `ON DELETE CASCADE`, and foreign keys are enforced here, so the links go whether or not
/// `delete_transaction` clears them itself. Removing that explicit cleanup does not fail this test.
/// The tables without a cascade are a different matter — deleting a goal with its contributions
/// left behind fails outright with SQLITE_CONSTRAINT_FOREIGNKEY, which
/// `deleting_a_goal_takes_its_contributions_with_it` does catch.
#[test]
fn tags_attach_to_transactions_and_are_cleaned_up() {
    let t = TestDb::new();
    let w = t.with_wallet();
    let tx = t.db.create_transaction(w.clone(), "lunch".into(), "Groceries".into(), 1_500, false, None, day("2026-03-05")).unwrap();
    let other = t.db.create_transaction(w, "dinner".into(), "Groceries".into(), 2_500, false, None, day("2026-03-06")).unwrap();

    let work = t.db.create_tag("work".into()).unwrap();
    let split = t.db.create_tag("split".into()).unwrap();

    // The same name twice is the same tag, not a duplicate.
    assert_eq!(t.db.create_tag("work".into()).unwrap().id, work.id);
    assert_eq!(t.db.list_tags().unwrap().len(), 2);

    t.db.add_tag_to_transaction(tx.id.clone(), work.id.clone()).unwrap();
    t.db.add_tag_to_transaction(tx.id.clone(), split.id.clone()).unwrap();
    t.db.add_tag_to_transaction(other.id.clone(), work.id.clone()).unwrap();

    let mut names: Vec<_> = t.db.list_transaction_tags(tx.id.clone()).unwrap().into_iter().map(|x| x.name).collect();
    names.sort();
    assert_eq!(names, vec!["split", "work"]);

    t.db.remove_tag_from_transaction(tx.id.clone(), split.id).unwrap();
    assert_eq!(t.db.list_transaction_tags(tx.id.clone()).unwrap().len(), 1);

    // Deleting the transaction takes its links, and leaves the other transaction's alone.
    t.db.delete_transaction(tx.id.clone()).unwrap();
    assert!(t.db.list_transaction_tags(tx.id).unwrap().is_empty());
    assert_eq!(t.db.list_transaction_tags(other.id.clone()).unwrap().len(), 1);

    // Deleting the tag takes the remaining link with it; the transaction stays.
    t.db.delete_tag(work.id).unwrap();
    assert!(t.db.list_transaction_tags(other.id).unwrap().is_empty());
    assert_eq!(t.db.list_all_transactions(10, 0).unwrap().len(), 1);
}

/// A receipt split into several lines is several transactions sharing a date and a note.
#[test]
fn a_split_receipt_becomes_several_transactions_on_one_date() {
    let t = TestDb::new();
    let w = t.db.create_wallet("Checking".into(), String::new(), "EUR".into(), 10_000, false).unwrap().id;

    for (title, cat, cents) in [("cigarettes", "Smoking", 780), ("banana", "Groceries", 120), ("coffee", "Cafe", 250)] {
        t.db.create_transaction(w.clone(), title.into(), cat.into(), cents, false, Some("Lidl receipt".into()), day("2026-03-05")).unwrap();
    }

    let rows = t.db.list_all_transactions(10, 0).unwrap();
    assert_eq!(rows.len(), 3);
    assert!(rows.iter().all(|r| r.occurred_at.starts_with("2026-03-05")));
    assert!(rows.iter().all(|r| r.note.as_deref() == Some("Lidl receipt")));
    assert_eq!(rows.iter().map(|r| r.amount_cents).sum::<i64>(), 1_150);
    assert_eq!(t.db.list_wallets().unwrap()[0].balance_cents, 10_000 - 1_150);

    // Each line keeps its own category rather than collapsing into one.
    let mut cats: Vec<_> = rows.iter().map(|r| r.category.clone()).collect();
    cats.sort();
    assert_eq!(cats, vec!["Cafe", "Groceries", "Smoking"]);
}

/// Editing one transaction must not disturb its neighbours' balances or rows.
#[test]
fn editing_one_of_many_transactions_leaves_the_rest_untouched() {
    let t = TestDb::new();
    let w = t.db.create_wallet("Checking".into(), String::new(), "EUR".into(), 100_000, false).unwrap().id;
    let a = t.db.create_transaction(w.clone(), "a".into(), "Groceries".into(), 1_000, false, None, day("2026-03-01")).unwrap();
    t.db.create_transaction(w.clone(), "b".into(), "Groceries".into(), 2_000, false, None, day("2026-03-02")).unwrap();
    t.db.create_transaction(w, "c".into(), "Groceries".into(), 3_000, false, None, day("2026-03-03")).unwrap();

    t.db.update_transaction(a.id, "a".into(), "Groceries".into(), 5_000, false, None, None).unwrap();

    assert_eq!(t.db.list_wallets().unwrap()[0].balance_cents, 100_000 - 5_000 - 2_000 - 3_000);
    let rows = t.db.list_all_transactions(10, 0).unwrap();
    assert_eq!(rows.len(), 3);
    assert_eq!(rows.iter().find(|r| r.title == "b").unwrap().amount_cents, 2_000);
}
