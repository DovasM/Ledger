//! Connecting a split to the money that actually moved.
//!
//! Written before the implementation. Until now a group was a second ledger kept alongside the real
//! one: recording that you paid 360 for a dinner changed nobody's wallet, and recording that Mike
//! paid you back 120 changed nobody's wallet either. The split was true and the balance was wrong.
//!
//! The rule that has to survive all of this is the one the feature was built on: **the transaction
//! stays whole**. Your wallet really did lose 360, not 120, and the reports go on saying so. The
//! split is a separate fact recorded beside it, not a correction to it.

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

// ── An expense you paid for ──────────────────────────────────────────────────

/// The whole point. One action, two records: the wallet loses the full amount and the group learns
/// who owes you what.
#[test]
fn paying_for_the_group_moves_your_wallet_by_the_whole_amount() {
    let t = TestDb::new();
    let wallet = t.with_wallet();
    let (group, you, sarah, mike) = trip(&t);

    let expense = t.db.add_shared_expense_from_wallet(
        group.clone(), "Hotel".into(), 36_000, you.clone(),
        wallet.clone(), "Travel".into(),
        thirds(&you, &sarah, &mike, 36_000), day("2026-03-01"),
    ).unwrap();

    assert_eq!(wallet_balance(&t, &wallet), -36_000, "the wallet lost all of it, not your third");
    assert!(expense.transaction_id.is_some(), "the two records are linked");

    let tx = t.db.list_all_transactions(100, 0).unwrap();
    assert_eq!(tx.len(), 1, "one transaction, not one per person");
    assert_eq!(tx[0].amount_cents, 36_000);
    assert!(!tx[0].is_income);
    assert_eq!(tx[0].id, expense.transaction_id.clone().unwrap());
    assert_eq!(tx[0].occurred_at, "2026-03-01T00:00:00Z", "the transaction happened when the expense did");

    // The split is unchanged by any of this.
    assert_eq!(expense.your_share_cents, 12_000);
    assert_eq!(t.db.list_expense_groups().unwrap()[0].total_cents, 36_000);
}

/// Somebody else paying is not a transaction of yours. You owe them a share, but no money of yours
/// moved, and inventing a transaction would put the wallet out by the amount.
#[test]
fn somebody_else_paying_leaves_your_wallet_alone() {
    let t = TestDb::new();
    let wallet = t.with_wallet();
    let (group, you, sarah, mike) = trip(&t);

    let refused = t.db.add_shared_expense_from_wallet(
        group.clone(), "Taxi".into(), 9_000, sarah.clone(),
        wallet.clone(), "Travel".into(),
        thirds(&you, &sarah, &mike, 9_000), day("2026-03-02"),
    );
    assert!(refused.is_err(), "there is no transaction to record when you did not pay");

    // Recorded the ordinary way it still works, and still touches nothing of yours.
    t.db.add_shared_expense(
        group, "Taxi".into(), 9_000, sarah.clone(), None, thirds(&you, &sarah, &mike, 9_000), day("2026-03-02"),
    ).unwrap();
    assert_eq!(wallet_balance(&t, &wallet), 0);
    assert!(t.db.list_all_transactions(100, 0).unwrap().is_empty());
}

/// A shares list that does not add up must not leave a transaction behind. Both rows land or
/// neither does — otherwise the wallet is short and there is nothing to explain it.
#[test]
fn a_refused_split_writes_no_transaction() {
    let t = TestDb::new();
    let wallet = t.with_wallet();
    let (group, you, sarah, mike) = trip(&t);

    let refused = t.db.add_shared_expense_from_wallet(
        group.clone(), "Hotel".into(), 36_000, you.clone(), wallet.clone(), "Travel".into(),
        vec![share(&you, 10_000), share(&sarah, 10_000), share(&mike, 10_000)], day("2026-03-01"),
    );
    assert!(refused.is_err());

    assert!(t.db.list_all_transactions(100, 0).unwrap().is_empty(), "no orphan transaction");
    assert_eq!(wallet_balance(&t, &wallet), 0);
    assert!(t.db.list_shared_expenses(group).unwrap().is_empty());
}

// ── Being paid back ──────────────────────────────────────────────────────────

#[test]
fn being_paid_back_is_income_and_paying_back_is_an_expense() {
    let t = TestDb::new();
    let wallet = t.with_wallet();
    let (group, you, sarah, mike) = trip(&t);
    t.db.add_shared_expense(group.clone(), "Hotel".into(), 36_000, you.clone(), None,
        thirds(&you, &sarah, &mike, 36_000), day("2026-03-01")).unwrap();

    let paid_to_you = t.db.record_settlement_to_wallet(
        group.clone(), mike.clone(), you.clone(), 12_000, wallet.clone(), "Reimbursement".into(), day("2026-03-05"),
    ).unwrap();
    assert_eq!(wallet_balance(&t, &wallet), 12_000);
    assert!(paid_to_you.transaction_id.is_some());
    let tx = t.db.list_all_transactions(100, 0).unwrap();
    assert_eq!(tx.len(), 1);
    assert!(tx[0].is_income, "money coming back to you is income");

    // And the other direction, in a group where you are the one who owes.
    let (group2, you2, sarah2, mike2) = trip(&t);
    t.db.add_shared_expense(group2.clone(), "Dinner".into(), 9_000, sarah2.clone(), None,
        thirds(&you2, &sarah2, &mike2, 9_000), day("2026-03-06")).unwrap();
    t.db.record_settlement_to_wallet(
        group2, you2, sarah2, 3_000, wallet.clone(), "Reimbursement".into(), day("2026-03-07"),
    ).unwrap();

    assert_eq!(wallet_balance(&t, &wallet), 9_000, "12000 in, 3000 out");
    assert_eq!(t.db.list_all_transactions(100, 0).unwrap().iter().filter(|x| !x.is_income).count(), 1);
    let _ = group;
}

/// Two other people squaring up between themselves changes the group and nothing of yours. Asking
/// for a wallet transaction there is a mistake, not a no-op.
#[test]
fn a_payment_between_two_other_people_has_no_transaction_of_yours() {
    let t = TestDb::new();
    let wallet = t.with_wallet();
    let (group, _, sarah, mike) = trip(&t);

    let refused = t.db.record_settlement_to_wallet(
        group.clone(), mike.clone(), sarah.clone(), 5_000, wallet.clone(), "Reimbursement".into(), day("2026-03-05"),
    );
    assert!(refused.is_err());
    assert_eq!(wallet_balance(&t, &wallet), 0);
    assert!(t.db.list_settlements(group).unwrap().is_empty(), "a refusal leaves nothing behind");
}

// ── Keeping the two sides honest ─────────────────────────────────────────────

/// A transaction can be deleted from the transactions screen by somebody who has forgotten it was
/// part of a split. The link has to go with it, or the expense points at a row that is not there.
#[test]
fn deleting_the_transaction_clears_the_link_rather_than_leaving_it_dangling() {
    let t = TestDb::new();
    let wallet = t.with_wallet();
    let (group, you, sarah, mike) = trip(&t);

    let expense = t.db.add_shared_expense_from_wallet(
        group.clone(), "Hotel".into(), 36_000, you.clone(), wallet.clone(), "Travel".into(),
        thirds(&you, &sarah, &mike, 36_000), day("2026-03-01"),
    ).unwrap();
    let settled = t.db.record_settlement_to_wallet(
        group.clone(), mike.clone(), you.clone(), 12_000, wallet.clone(), "Reimbursement".into(), day("2026-03-05"),
    ).unwrap();

    t.db.delete_transaction(expense.transaction_id.clone().unwrap()).unwrap();
    t.db.delete_transaction(settled.transaction_id.clone().unwrap()).unwrap();

    let still = &t.db.list_shared_expenses(group.clone()).unwrap()[0];
    assert_eq!(still.amount_cents, 36_000, "the split itself survives");
    assert!(still.transaction_id.is_none(), "but it no longer claims a transaction that is gone");
    assert!(t.db.list_settlements(group).unwrap()[0].transaction_id.is_none());
}

/// The two records describe one event, so correcting the amount on one has to correct the other.
/// Otherwise the wallet says 360 and the group says 300 and neither is obviously wrong.
#[test]
fn correcting_a_linked_expense_corrects_the_transaction_with_it() {
    let t = TestDb::new();
    let wallet = t.with_wallet();
    let (group, you, sarah, mike) = trip(&t);

    let expense = t.db.add_shared_expense_from_wallet(
        group.clone(), "Hotal".into(), 36_000, you.clone(), wallet.clone(), "Travel".into(),
        thirds(&you, &sarah, &mike, 36_000), day("2026-03-01"),
    ).unwrap();

    t.db.update_shared_expense(
        expense.id.clone(), "Hotel".into(), 30_000, you.clone(),
        thirds(&you, &sarah, &mike, 30_000), day("2026-03-01"),
    ).unwrap();

    assert_eq!(wallet_balance(&t, &wallet), -30_000, "the wallet followed the correction");
    let tx = t.db.list_all_transactions(100, 0).unwrap();
    assert_eq!(tx.len(), 1, "corrected, not duplicated");
    assert_eq!(tx[0].amount_cents, 30_000);
    assert_eq!(tx[0].title, "Hotel");
    let _ = group;
}

/// Deleting the split is asked about separately, because the two cases are opposite: the dinner
/// never happened, or it happened and you no longer care who owed what.
#[test]
fn deleting_a_split_can_take_the_transaction_or_leave_it() {
    let t = TestDb::new();
    let wallet = t.with_wallet();
    let (group, you, sarah, mike) = trip(&t);

    let kept = t.db.add_shared_expense_from_wallet(
        group.clone(), "Hotel".into(), 36_000, you.clone(), wallet.clone(), "Travel".into(),
        thirds(&you, &sarah, &mike, 36_000), day("2026-03-01"),
    ).unwrap();
    t.db.delete_shared_expense_keeping_transaction(kept.id).unwrap();
    assert_eq!(wallet_balance(&t, &wallet), -36_000, "the money really did leave; the reports keep saying so");
    assert_eq!(t.db.list_all_transactions(100, 0).unwrap().len(), 1);

    let mistake = t.db.add_shared_expense_from_wallet(
        group.clone(), "Never happened".into(), 5_000, you.clone(), wallet.clone(), "Travel".into(),
        thirds(&you, &sarah, &mike, 5_000), day("2026-03-02"),
    ).unwrap();
    t.db.delete_shared_expense_with_transaction(mistake.id).unwrap();
    assert_eq!(wallet_balance(&t, &wallet), -36_000, "only the mistake was undone");
    assert_eq!(t.db.list_all_transactions(100, 0).unwrap().len(), 1);
    assert_eq!(t.db.list_shared_expenses(group).unwrap().len(), 0);
}

/// A split with no transaction behind it must still be deletable by either route, without either
/// one failing or wandering off and deleting somebody else's transaction.
#[test]
fn deleting_an_unlinked_split_works_either_way() {
    let t = TestDb::new();
    let wallet = t.with_wallet();
    let (group, you, sarah, mike) = trip(&t);
    t.db.create_transaction(wallet.clone(), "Groceries".into(), "Food".into(), 4_000, false, None, day("2026-03-01")).unwrap();

    let a = t.db.add_shared_expense(group.clone(), "Taxi".into(), 9_000, sarah.clone(), None,
        thirds(&you, &sarah, &mike, 9_000), day("2026-03-02")).unwrap();
    let b = t.db.add_shared_expense(group.clone(), "Coffee".into(), 600, mike.clone(), None,
        thirds(&you, &sarah, &mike, 600), day("2026-03-03")).unwrap();

    t.db.delete_shared_expense_with_transaction(a.id).unwrap();
    t.db.delete_shared_expense_keeping_transaction(b.id).unwrap();

    assert!(t.db.list_shared_expenses(group).unwrap().is_empty());
    assert_eq!(t.db.list_all_transactions(100, 0).unwrap().len(), 1, "the unrelated transaction is untouched");
    assert_eq!(wallet_balance(&t, &wallet), -4_000);
}

/// The split and the wallet answer different questions and must not be confused for each other: the
/// group total is what the dinner cost, the wallet is what you are out of pocket.
#[test]
fn the_group_total_and_the_wallet_are_not_the_same_number() {
    let t = TestDb::new();
    let wallet = t.with_wallet();
    let (group, you, sarah, mike) = trip(&t);

    t.db.add_shared_expense_from_wallet(
        group.clone(), "Hotel".into(), 36_000, you.clone(), wallet.clone(), "Travel".into(),
        thirds(&you, &sarah, &mike, 36_000), day("2026-03-01"),
    ).unwrap();
    t.db.record_settlement_to_wallet(
        group.clone(), sarah.clone(), you.clone(), 12_000, wallet.clone(), "Reimbursement".into(), day("2026-03-05"),
    ).unwrap();
    t.db.record_settlement_to_wallet(
        group.clone(), mike.clone(), you.clone(), 12_000, wallet.clone(), "Reimbursement".into(), day("2026-03-05"),
    ).unwrap();

    assert_eq!(t.db.list_expense_groups().unwrap()[0].total_cents, 36_000, "the hotel still cost 360");
    assert_eq!(wallet_balance(&t, &wallet), -12_000, "and you are out of pocket by your share alone");
    assert!(t.db.suggest_settlements(group).unwrap().is_empty(), "the group is closed");
}

/// The split is checked before the transaction is written, so most bad input never gets that far.
/// But not all of it does: a description is only refused deeper in, and by then the money has
/// already left the wallet. That is the case this covers — the one where the rollback is the only
/// thing standing between a refusal and a wallet that is quietly short.
#[test]
fn a_split_refused_after_the_transaction_is_written_takes_it_back() {
    let t = TestDb::new();
    let wallet = t.with_wallet();
    let (group, you, sarah, mike) = trip(&t);

    let refused = t.db.add_shared_expense_from_wallet(
        group.clone(), "   ".into(), 36_000, you.clone(), wallet.clone(), "Travel".into(),
        thirds(&you, &sarah, &mike, 36_000), day("2026-03-01"),
    );
    assert!(refused.is_err(), "an expense with no description is refused");

    assert_eq!(wallet_balance(&t, &wallet), 0, "and the wallet is put back");
    assert!(t.db.list_all_transactions(100, 0).unwrap().is_empty(), "no transaction survives a refusal");
    assert!(t.db.list_shared_expenses(group).unwrap().is_empty());
}
