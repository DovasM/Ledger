//! Splitting a bill between people.
//!
//! Written before the implementation. The thing that has to hold is that a group always adds up: the
//! shares of an expense sum to the expense, and the members' balances sum to zero. Money split three
//! ways rarely divides evenly, and a lost cent is exactly how a group stops reconciling.

mod common;
use common::{day, TestDb};

use uniffi_ledger::ShareInput;

/// Three people, and the app's user is one of them.
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

// ── Groups ───────────────────────────────────────────────────────────────────

#[test]
fn a_new_group_has_you_in_it_and_nothing_else() {
    let t = TestDb::new();
    let group = t.db
        .create_expense_group("Trip".into(), "✈️".into(), "#1565C0".into(), vec!["Sarah".into(), "Mike".into()])
        .unwrap();

    assert_eq!(group.name, "Trip");
    assert_eq!(group.total_cents, 0);
    assert_eq!(group.your_share_cents, 0);
    assert_eq!(group.net_balance_cents, 0);

    let members = t.db.list_group_members(group.id).unwrap();
    assert_eq!(members.len(), 3);
    assert_eq!(members.iter().filter(|m| m.is_you).count(), 1, "exactly one member is the app's user");
}

#[test]
fn a_group_needs_a_name() {
    let t = TestDb::new();
    assert!(t.db.create_expense_group(String::new(), String::new(), "#1565C0".into(), vec![]).is_err());
}

#[test]
fn deleting_a_group_takes_its_members_expenses_and_shares() {
    let t = TestDb::new();
    let (group, you, sarah, mike) = trip(&t);
    t.db.add_shared_expense(
        group.clone(), "Hotel".into(), 36_000, you.clone(), None,
        vec![share(&you, 12_000), share(&sarah, 12_000), share(&mike, 12_000)],
        day("2026-03-01"),
    ).unwrap();

    t.db.delete_expense_group(group.clone()).unwrap();
    assert!(t.db.list_expense_groups().unwrap().is_empty());
    assert!(t.db.list_group_members(group.clone()).unwrap().is_empty());
    assert!(t.db.list_shared_expenses(group).unwrap().is_empty());
}

// ── Expenses and their shares ────────────────────────────────────────────────

#[test]
fn an_expense_records_who_paid_and_what_each_person_owes() {
    let t = TestDb::new();
    let (group, you, sarah, mike) = trip(&t);

    let expense = t.db.add_shared_expense(
        group.clone(), "Hotel".into(), 36_000, you.clone(), None,
        vec![share(&you, 12_000), share(&sarah, 12_000), share(&mike, 12_000)],
        day("2026-03-01"),
    ).unwrap();

    assert_eq!(expense.amount_cents, 36_000);
    assert_eq!(expense.your_share_cents, 12_000);
    assert_eq!(expense.paid_by_name, "You");

    let listed = t.db.list_expense_groups().unwrap();
    assert_eq!(listed[0].total_cents, 36_000);
    assert_eq!(listed[0].your_share_cents, 12_000);

    let shares = t.db.list_expense_shares(expense.id).unwrap();
    assert_eq!(shares.len(), 3);
    assert_eq!(shares.iter().map(|s| s.share_cents).sum::<i64>(), 36_000);
}

/// The invariant the whole feature rests on: if the shares do not add up to the expense, somebody's
/// money has gone missing and every balance after it is wrong.
#[test]
fn shares_that_do_not_add_up_are_refused() {
    let t = TestDb::new();
    let (group, you, sarah, mike) = trip(&t);

    let short = t.db.add_shared_expense(
        group.clone(), "Hotel".into(), 36_000, you.clone(),
        None, vec![share(&you, 10_000), share(&sarah, 10_000), share(&mike, 10_000)],
        day("2026-03-01"),
    );
    assert!(short.is_err(), "30000 of shares against a 36000 expense must not be stored");

    let over = t.db.add_shared_expense(
        group.clone(), "Hotel".into(), 36_000, you.clone(),
        None, vec![share(&you, 20_000), share(&sarah, 20_000)],
        day("2026-03-01"),
    );
    assert!(over.is_err());

    assert!(t.db.list_shared_expenses(group).unwrap().is_empty(), "a refusal must leave nothing behind");
}

#[test]
fn an_expense_needs_a_positive_amount() {
    let t = TestDb::new();
    let (group, you, _, _) = trip(&t);
    assert!(t.db.add_shared_expense(
        group.clone(), "Nothing".into(), 0, you.clone(), None, vec![share(&you, 0)], day("2026-03-01"),
    ).is_err());
    assert!(t.db.add_shared_expense(
        group, "Negative".into(), -100, you.clone(), None, vec![share(&you, -100)], day("2026-03-01"),
    ).is_err());
}

#[test]
fn deleting_an_expense_takes_its_shares_and_the_totals_follow() {
    let t = TestDb::new();
    let (group, you, sarah, mike) = trip(&t);
    let keep = t.db.add_shared_expense(
        group.clone(), "Hotel".into(), 36_000, you.clone(), None,
        vec![share(&you, 12_000), share(&sarah, 12_000), share(&mike, 12_000)], day("2026-03-01"),
    ).unwrap();
    let drop = t.db.add_shared_expense(
        group.clone(), "Taxi".into(), 7_800, you.clone(), None,
        vec![share(&you, 2_600), share(&sarah, 2_600), share(&mike, 2_600)], day("2026-03-02"),
    ).unwrap();

    t.db.delete_shared_expense(drop.id.clone()).unwrap();

    assert_eq!(t.db.list_shared_expenses(group).unwrap().len(), 1);
    assert!(t.db.list_expense_shares(drop.id).unwrap().is_empty());
    assert_eq!(t.db.list_expense_groups().unwrap()[0].total_cents, 36_000);
    assert_eq!(t.db.list_expense_shares(keep.id).unwrap().len(), 3);
}

// ── Splitting evenly, which is where the cents go missing ────────────────────

/// 100.00 three ways is 33.333…, and the remainder has to land somewhere deliberate rather than
/// being rounded away. Whatever is left over goes to the first share.
#[test]
fn an_equal_split_never_loses_a_cent() {
    assert_eq!(uniffi_ledger::split_equally(10_000, 3), vec![3_334, 3_333, 3_333]);
    assert_eq!(uniffi_ledger::split_equally(10_000, 3).iter().sum::<i64>(), 10_000);

    assert_eq!(uniffi_ledger::split_equally(10_000, 1), vec![10_000]);
    assert_eq!(uniffi_ledger::split_equally(10_000, 4), vec![2_500, 2_500, 2_500, 2_500]);

    // A single cent between three people still adds up to a single cent.
    assert_eq!(uniffi_ledger::split_equally(1, 3), vec![1, 0, 0]);

    for amount in [1_i64, 2, 7, 99, 100, 12_345, 999_999] {
        for people in 1..=7_i32 {
            let parts = uniffi_ledger::split_equally(amount, people);
            assert_eq!(parts.len(), people as usize);
            assert_eq!(parts.iter().sum::<i64>(), amount, "{amount} split {people} ways");
            assert!(parts.iter().max().unwrap() - parts.iter().min().unwrap() <= 1, "shares stay within a cent");
        }
    }
}

#[test]
fn splitting_between_nobody_is_refused_rather_than_dividing_by_zero() {
    assert!(uniffi_ledger::split_equally(10_000, 0).is_empty());
}

// ── Balances ─────────────────────────────────────────────────────────────────

/// You pay 360 split three ways, Sarah pays 210 split three ways. You are owed 170, Sarah 20, and
/// Mike owes 190.
#[test]
fn balances_are_what_you_paid_minus_what_you_owe() {
    let t = TestDb::new();
    let (group, you, sarah, mike) = trip(&t);

    t.db.add_shared_expense(
        group.clone(), "Hotel".into(), 36_000, you.clone(), None,
        vec![share(&you, 12_000), share(&sarah, 12_000), share(&mike, 12_000)], day("2026-03-01"),
    ).unwrap();
    t.db.add_shared_expense(
        group.clone(), "Dinner".into(), 21_000, sarah.clone(), None,
        vec![share(&you, 7_000), share(&sarah, 7_000), share(&mike, 7_000)], day("2026-03-02"),
    ).unwrap();

    let members = t.db.list_group_members(group.clone()).unwrap();
    let of = |id: &str| members.iter().find(|m| m.id == id).unwrap();

    assert_eq!(of(&you).paid_cents, 36_000);
    assert_eq!(of(&you).owes_cents, 19_000);
    assert_eq!(of(&you).balance_cents, 17_000);

    assert_eq!(of(&sarah).balance_cents, 2_000);
    assert_eq!(of(&mike).balance_cents, -19_000);

    let group_row = t.db.list_expense_groups().unwrap();
    assert_eq!(group_row[0].net_balance_cents, 17_000, "the group list shows your side of it");
    assert_eq!(group_row[0].total_cents, 57_000);
    assert_eq!(group_row[0].your_share_cents, 19_000);
}

/// Every euro someone put in is owed to them by somebody, so the balances cancel out. If they ever
/// do not, a share has gone missing.
#[test]
fn the_balances_of_a_group_always_sum_to_zero() {
    let t = TestDb::new();
    let (group, you, sarah, mike) = trip(&t);

    // Deliberately awkward: an amount that does not divide evenly, paid by different people.
    for (i, (desc, amount, payer)) in [
        ("Hotel", 36_000_i64, &you), ("Dinner", 21_001, &sarah), ("Museum", 4_499, &mike),
        ("Taxi", 7_777, &you), ("Coffee", 1, &sarah),
    ].iter().enumerate() {
        let parts = uniffi_ledger::split_equally(*amount, 3);
        t.db.add_shared_expense(
            group.clone(), desc.to_string(), *amount, (*payer).clone(), None,
            vec![share(&you, parts[0]), share(&sarah, parts[1]), share(&mike, parts[2])],
            day(&format!("2026-03-0{}", i + 1)),
        ).unwrap();
    }

    let members = t.db.list_group_members(group).unwrap();
    assert_eq!(members.iter().map(|m| m.balance_cents).sum::<i64>(), 0);
    assert_eq!(members.iter().map(|m| m.paid_cents).sum::<i64>(), members.iter().map(|m| m.owes_cents).sum::<i64>());
}

/// An expense someone else paid is money you owe, not money that left your wallet — so there is no
/// transaction behind it.
#[test]
fn an_expense_someone_else_paid_has_no_transaction_of_yours() {
    let t = TestDb::new();
    let (group, you, sarah, mike) = trip(&t);

    let theirs = t.db.add_shared_expense(
        group.clone(), "Dinner".into(), 21_000, sarah.clone(), None,
        vec![share(&you, 7_000), share(&sarah, 7_000), share(&mike, 7_000)], day("2026-03-02"),
    ).unwrap();
    assert_eq!(theirs.transaction_id, None);
    assert!(t.db.list_all_transactions(10, 0).unwrap().is_empty(), "nothing left your wallet");

    // One you paid can carry the transaction it came from.
    let w = t.with_wallet();
    let tx = t.db.create_transaction(w, "Hotel".into(), "Travel".into(), 36_000, false, None, day("2026-03-01")).unwrap();
    let yours = t.db.add_shared_expense(
        group, "Hotel".into(), 36_000, you.clone(), Some(tx.id.clone()),
        vec![share(&you, 12_000), share(&sarah, 12_000), share(&mike, 12_000)], day("2026-03-01"),
    ).unwrap();
    assert_eq!(yours.transaction_id.as_deref(), Some(tx.id.as_str()));
}

/// The decision recorded when this was designed: the transaction stays whole. The reports keep
/// showing what actually left the wallet, and the split is tracked beside it.
#[test]
fn a_split_does_not_change_the_transaction_or_the_wallet_balance() {
    let t = TestDb::new();
    let (group, you, sarah, mike) = trip(&t);
    let w = t.db.create_wallet("Checking".into(), String::new(), "EUR".into(), 100_000, false).unwrap().id;
    let tx = t.db.create_transaction(w.clone(), "Hotel".into(), "Travel".into(), 36_000, false, None, day("2026-03-01")).unwrap();

    t.db.add_shared_expense(
        group, "Hotel".into(), 36_000, you.clone(), Some(tx.id.clone()),
        vec![share(&you, 12_000), share(&sarah, 12_000), share(&mike, 12_000)], day("2026-03-01"),
    ).unwrap();

    assert_eq!(t.db.list_wallets().unwrap()[0].balance_cents, 100_000 - 36_000);
    assert_eq!(t.db.list_all_transactions(10, 0).unwrap()[0].amount_cents, 36_000);
    assert_eq!(t.db.get_month_summary(2026, 3).unwrap().total_expenses_cents, 36_000);
}

// ── Correcting a mistake ─────────────────────────────────────────────────────

/// Deleting and retyping is a poor way to fix a typo, and it loses the entry's place in the list.
#[test]
fn an_expense_can_be_corrected() {
    let t = TestDb::new();
    let (group, you, sarah, mike) = trip(&t);

    let expense = t.db.add_shared_expense(
        group.clone(), "Hotal".into(), 36_000, you.clone(), None,
        vec![share(&you, 12_000), share(&sarah, 12_000), share(&mike, 12_000)], day("2026-03-01"),
    ).unwrap();

    let fixed = t.db.update_shared_expense(
        expense.id.clone(), "Hotel".into(), 30_000, sarah.clone(),
        vec![share(&you, 10_000), share(&sarah, 10_000), share(&mike, 10_000)], day("2026-03-02"),
    ).unwrap();

    assert_eq!(fixed.description, "Hotel");
    assert_eq!(fixed.amount_cents, 30_000);
    assert_eq!(fixed.paid_by_name, "Sarah");
    assert_eq!(fixed.occurred_at, "2026-03-02T00:00:00Z");
    assert_eq!(fixed.your_share_cents, 10_000);

    assert_eq!(t.db.list_shared_expenses(group.clone()).unwrap().len(), 1, "correcting must not add a second entry");
    assert_eq!(t.db.list_expense_groups().unwrap()[0].total_cents, 30_000);

    // The balances follow the correction: Sarah paid it now, so she is owed her two-thirds.
    let members = t.db.list_group_members(group).unwrap();
    let of = |id: &str| members.iter().find(|m| m.id == id).unwrap();
    assert_eq!(of(&sarah).balance_cents, 20_000);
    assert_eq!(of(&you).balance_cents, -10_000);
    assert_eq!(members.iter().map(|m| m.balance_cents).sum::<i64>(), 0);
}

/// The shares are replaced, not added to — otherwise every correction would double them and the
/// group would quietly stop adding up.
#[test]
fn correcting_replaces_the_shares_rather_than_piling_them_up() {
    let t = TestDb::new();
    let (group, you, sarah, mike) = trip(&t);
    let expense = t.db.add_shared_expense(
        group, "Hotel".into(), 36_000, you.clone(), None,
        vec![share(&you, 12_000), share(&sarah, 12_000), share(&mike, 12_000)], day("2026-03-01"),
    ).unwrap();

    t.db.update_shared_expense(
        expense.id.clone(), "Hotel".into(), 36_000, you.clone(),
        vec![share(&you, 20_000), share(&sarah, 16_000)], day("2026-03-01"),
    ).unwrap();

    let shares = t.db.list_expense_shares(expense.id).unwrap();
    assert_eq!(shares.len(), 2, "the old three shares must be gone, not kept alongside the new two");
    assert_eq!(shares.iter().map(|s| s.share_cents).sum::<i64>(), 36_000);
    assert!(shares.iter().all(|s| s.member_id != mike), "Mike was taken off this expense");
}

/// A correction that does not add up is refused, and — the part that matters — the entry it was
/// meant to correct is left exactly as it was.
#[test]
fn a_correction_that_does_not_add_up_leaves_the_original_alone() {
    let t = TestDb::new();
    let (group, you, sarah, mike) = trip(&t);
    let expense = t.db.add_shared_expense(
        group.clone(), "Hotel".into(), 36_000, you.clone(), None,
        vec![share(&you, 12_000), share(&sarah, 12_000), share(&mike, 12_000)], day("2026-03-01"),
    ).unwrap();

    let refused = t.db.update_shared_expense(
        expense.id.clone(), "Hotel".into(), 36_000, you.clone(),
        vec![share(&you, 1_000), share(&sarah, 1_000)], day("2026-03-01"),
    );
    assert!(refused.is_err());

    let still = &t.db.list_shared_expenses(group).unwrap()[0];
    assert_eq!(still.amount_cents, 36_000);
    assert_eq!(still.description, "Hotel");
    assert_eq!(t.db.list_expense_shares(expense.id).unwrap().len(), 3, "the original shares survive a refused edit");
}

#[test]
fn correcting_an_expense_that_is_not_there_fails() {
    let t = TestDb::new();
    let (_, you, _, _) = trip(&t);
    assert!(t.db.update_shared_expense(
        "no-such-expense".into(), "Hotel".into(), 1_000, you.clone(), vec![share(&you, 1_000)], day("2026-03-01"),
    ).is_err());
}

#[test]
fn a_group_can_be_renamed() {
    let t = TestDb::new();
    let (group, _, _, _) = trip(&t);
    let renamed = t.db.update_expense_group(group, "Trip to Riga".into(), "🚗".into(), "#6A1B9A".into()).unwrap();
    assert_eq!(renamed.name, "Trip to Riga");
    assert_eq!(renamed.emoji, "🚗");
    assert!(t.db.update_expense_group("nope".into(), "x".into(), String::new(), "#000000".into()).is_err());
}
