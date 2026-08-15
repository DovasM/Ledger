//! Splitting a transaction you are already writing.
//!
//! The other direction of the wallet link. Starting from the group is right when the split is the
//! point — a trip, a flat. It is the wrong way round when you are simply entering what you spent and
//! it happens to have been shared: you would have to abandon the half-typed transaction, go to
//! another screen, and enter the amount a second time.
//!
//! What makes this safe is that the transaction already exists and already moved the wallet. The
//! split takes its amount and its date and adds nothing to the balance — the one thing that must not
//! happen here is the money counting twice.

mod common;
use common::{day, TestDb};

use uniffi_ledger::ShareInput;

fn trip(t: &TestDb) -> (String, String, String, String) {
    let group = t.db
        .create_expense_group("Trip".into(), "✈️".into(), "#1565C0".into(), vec!["Sarah".into(), "Mike".into()])
        .unwrap();
    let members = t.db.list_group_members(group.id.clone()).unwrap();
    let you = members.iter().find(|m| m.is_you).unwrap().id.clone();
    let sarah = members.iter().find(|m| m.name == "Sarah").unwrap().id.clone();
    let mike = members.iter().find(|m| m.name == "Mike").unwrap().id.clone();
    (group.id, you, sarah, mike)
}

fn share(member_id: &str, cents: i64) -> ShareInput {
    ShareInput { member_id: member_id.to_string(), share_cents: cents }
}

fn thirds(you: &str, sarah: &str, mike: &str, amount: i64) -> Vec<ShareInput> {
    let parts = uniffi_ledger::split_equally(amount, 3);
    vec![share(you, parts[0]), share(sarah, parts[1]), share(mike, parts[2])]
}

fn wallet_balance(t: &TestDb, wallet: &str) -> i64 {
    t.db.list_wallets().unwrap().iter().find(|w| w.id == wallet).unwrap().balance_cents
}

#[test]
fn splitting_a_transaction_takes_its_amount_and_its_date_and_moves_nothing() {
    let t = TestDb::new();
    let wallet = t.with_wallet();
    let (group, you, sarah, mike) = trip(&t);

    let paid = t.db.create_transaction(
        wallet.clone(), "Dinner".into(), "Food".into(), 9_000, false, None, day("2026-03-01"),
    ).unwrap();
    assert_eq!(wallet_balance(&t, &wallet), -9_000);

    let expense = t.db.split_transaction(paid.id.clone(), group.clone(), thirds(&you, &sarah, &mike, 9_000)).unwrap();

    assert_eq!(expense.amount_cents, 9_000);
    assert_eq!(expense.description, "Dinner");
    assert_eq!(expense.occurred_at, "2026-03-01T00:00:00Z");
    assert_eq!(expense.paid_by_name, "You", "it is your transaction, so you paid it");
    assert_eq!(expense.transaction_id, Some(paid.id.clone()));
    assert_eq!(expense.your_share_cents, 3_000);

    // The one thing that must not happen: the money counting twice.
    assert_eq!(wallet_balance(&t, &wallet), -9_000, "the wallet already moved when the transaction was written");
    assert_eq!(t.db.list_all_transactions(100, 0).unwrap().len(), 1, "no second transaction");

    // Sarah and Mike each owe you a third.
    let members = t.db.list_group_members(group).unwrap();
    let of = |id: &str| members.iter().find(|m| m.id == id).unwrap().balance_cents;
    assert_eq!(of(&you), 6_000);
    assert_eq!(of(&sarah), -3_000);
    assert_eq!(of(&mike), -3_000);
}

/// Splitting the same transaction again would put its whole amount into the group a second time and
/// every balance after it would be wrong.
#[test]
fn a_transaction_can_only_be_split_once() {
    let t = TestDb::new();
    let wallet = t.with_wallet();
    let (group, you, sarah, mike) = trip(&t);
    let paid = t.db.create_transaction(
        wallet, "Dinner".into(), "Food".into(), 9_000, false, None, day("2026-03-01"),
    ).unwrap();

    t.db.split_transaction(paid.id.clone(), group.clone(), thirds(&you, &sarah, &mike, 9_000)).unwrap();
    let again = t.db.split_transaction(paid.id.clone(), group.clone(), thirds(&you, &sarah, &mike, 9_000));
    assert!(again.is_err(), "a second split of one transaction doubles it");

    assert_eq!(t.db.list_shared_expenses(group.clone()).unwrap().len(), 1);
    assert_eq!(t.db.list_expense_groups().unwrap()[0].total_cents, 9_000);
    let _ = group;
}

/// Taking a split off a transaction leaves the transaction alone — the money still left the wallet.
/// And having done that, it can be split again, into a different group if that was the mistake.
#[test]
fn a_split_can_be_taken_off_and_the_transaction_split_again() {
    let t = TestDb::new();
    let wallet = t.with_wallet();
    let (group, you, sarah, mike) = trip(&t);
    let paid = t.db.create_transaction(
        wallet.clone(), "Dinner".into(), "Food".into(), 9_000, false, None, day("2026-03-01"),
    ).unwrap();

    let wrong = t.db.split_transaction(paid.id.clone(), group.clone(), thirds(&you, &sarah, &mike, 9_000)).unwrap();
    t.db.delete_shared_expense_keeping_transaction(wrong.id).unwrap();

    assert_eq!(wallet_balance(&t, &wallet), -9_000, "the transaction is untouched");
    assert!(t.db.list_shared_expenses(group.clone()).unwrap().is_empty());

    let redone = t.db.split_transaction(paid.id.clone(), group.clone(), thirds(&you, &sarah, &mike, 9_000));
    assert!(redone.is_ok(), "once the split is off, the transaction is free to be split again");
    assert_eq!(t.db.list_expense_groups().unwrap()[0].total_cents, 9_000);
}

#[test]
fn the_shares_still_have_to_add_up_to_the_transaction() {
    let t = TestDb::new();
    let wallet = t.with_wallet();
    let (group, you, sarah, mike) = trip(&t);
    let paid = t.db.create_transaction(
        wallet, "Dinner".into(), "Food".into(), 9_000, false, None, day("2026-03-01"),
    ).unwrap();

    let short = t.db.split_transaction(paid.id.clone(), group.clone(),
        vec![share(&you, 1_000), share(&sarah, 1_000), share(&mike, 1_000)]);
    assert!(short.is_err());
    let over = t.db.split_transaction(paid.id.clone(), group.clone(),
        vec![share(&you, 5_000), share(&sarah, 5_000)]);
    assert!(over.is_err());

    assert!(t.db.list_shared_expenses(group).unwrap().is_empty(), "a refusal leaves nothing behind");
}

/// Money coming in is not a cost to share out. Splitting it would record everyone as owing you a
/// piece of your own salary.
#[test]
fn income_is_not_something_to_split() {
    let t = TestDb::new();
    let wallet = t.with_wallet();
    let (group, you, sarah, mike) = trip(&t);
    let salary = t.db.create_transaction(
        wallet, "Salary".into(), "Income".into(), 9_000, true, None, day("2026-03-01"),
    ).unwrap();

    assert!(t.db.split_transaction(salary.id, group.clone(), thirds(&you, &sarah, &mike, 9_000)).is_err());
    assert!(t.db.list_shared_expenses(group).unwrap().is_empty());
}

#[test]
fn splitting_a_transaction_that_is_not_there_fails() {
    let t = TestDb::new();
    let (group, you, sarah, mike) = trip(&t);
    assert!(t.db.split_transaction("no-such-transaction".into(), group, thirds(&you, &sarah, &mike, 9_000)).is_err());
}

/// The shares belong to the group being split into, not to whichever group they came from.
#[test]
fn the_shares_have_to_belong_to_the_group_being_split_into() {
    let t = TestDb::new();
    let wallet = t.with_wallet();
    let (group, you, sarah, mike) = trip(&t);
    let other = t.db.create_expense_group("Flat".into(), "🏠".into(), "#1565C0".into(), vec!["Ann".into()]).unwrap();
    let ann = t.db.list_group_members(other.id).unwrap().iter().find(|m| m.name == "Ann").unwrap().id.clone();

    let paid = t.db.create_transaction(
        wallet, "Dinner".into(), "Food".into(), 9_000, false, None, day("2026-03-01"),
    ).unwrap();

    let strangers = t.db.split_transaction(paid.id, group.clone(),
        vec![share(&you, 3_000), share(&sarah, 3_000), share(&ann, 3_000)]);
    assert!(strangers.is_err(), "Ann is not on this trip");
    assert!(t.db.list_shared_expenses(group).unwrap().is_empty());
    let _ = mike;
}

/// Correcting the transaction afterwards is the same event changing, so the split follows it — the
/// mirror of a corrected split following through to the transaction.
#[test]
fn correcting_the_transaction_afterwards_keeps_the_split_in_step() {
    let t = TestDb::new();
    let wallet = t.with_wallet();
    let (group, you, sarah, mike) = trip(&t);
    let paid = t.db.create_transaction(
        wallet.clone(), "Dinner".into(), "Food".into(), 9_000, false, None, day("2026-03-01"),
    ).unwrap();
    let expense = t.db.split_transaction(paid.id.clone(), group.clone(), thirds(&you, &sarah, &mike, 9_000)).unwrap();

    // The bill was actually 120, split the same three ways.
    t.db.update_transaction(paid.id.clone(), "Dinner".into(), "Food".into(), 12_000, false, None, day("2026-03-01")).unwrap();

    let after = &t.db.list_shared_expenses(group.clone()).unwrap()[0];
    assert_eq!(after.amount_cents, 12_000, "the split followed the correction");
    assert_eq!(after.id, expense.id, "corrected, not replaced");
    assert_eq!(wallet_balance(&t, &wallet), -12_000);

    // The shares were re-cut to the new amount rather than left summing to the old one.
    let shares = t.db.list_expense_shares(after.id.clone()).unwrap();
    assert_eq!(shares.iter().map(|s| s.share_cents).sum::<i64>(), 12_000, "every cent still belongs to somebody");
    assert_eq!(t.db.list_expense_groups().unwrap()[0].total_cents, 12_000);
}
