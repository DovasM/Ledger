//! Paying each other back.
//!
//! Written before the implementation. A group is only useful if it can be closed, and closing it by
//! hand is where the arithmetic goes wrong: with four people there are six possible payments and
//! only three are needed. The app knows every balance, so it should say who pays whom rather than
//! leave that to be worked out over a table.
//!
//! Two things have to hold. The suggested payments, once made, must leave every balance at zero —
//! not near zero, since a stray cent is exactly what makes somebody re-check the whole trip. And
//! there must never be more of them than necessary.

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

fn balance_of(t: &TestDb, group: &str, member: &str) -> i64 {
    t.db.list_group_members(group.to_string()).unwrap()
        .iter().find(|m| m.id == member).unwrap().balance_cents
}

fn balances(t: &TestDb, group: &str) -> Vec<i64> {
    t.db.list_group_members(group.to_string()).unwrap().iter().map(|m| m.balance_cents).collect()
}

/// You pay 360 split three ways: Sarah and Mike each owe you 120.
fn one_expense(t: &TestDb) -> (String, String, String, String) {
    let (group, you, sarah, mike) = trip(t);
    t.db.add_shared_expense(
        group.clone(), "Hotel".into(), 36_000, you.clone(), None,
        vec![share(&you, 12_000), share(&sarah, 12_000), share(&mike, 12_000)], day("2026-03-01"),
    ).unwrap();
    (group, you, sarah, mike)
}

// ── Recording a payment ──────────────────────────────────────────────────────

#[test]
fn paying_someone_back_moves_both_balances_and_nobody_elses() {
    let t = TestDb::new();
    let (group, you, sarah, mike) = one_expense(&t);
    assert_eq!(balance_of(&t, &group, &you), 24_000);

    t.db.record_settlement(group.clone(), mike.clone(), you.clone(), 12_000, None, day("2026-03-05")).unwrap();

    assert_eq!(balance_of(&t, &group, &mike), 0, "Mike has paid what he owed");
    assert_eq!(balance_of(&t, &group, &you), 12_000, "you are still owed Sarah's share");
    assert_eq!(balance_of(&t, &group, &sarah), -12_000, "Sarah is untouched");
    assert_eq!(balances(&t, &group).iter().sum::<i64>(), 0);
}

#[test]
fn settling_the_whole_group_leaves_everyone_at_zero() {
    let t = TestDb::new();
    let (group, you, sarah, mike) = one_expense(&t);
    t.db.record_settlement(group.clone(), mike, you.clone(), 12_000, None, day("2026-03-05")).unwrap();
    t.db.record_settlement(group.clone(), sarah, you, 12_000, None, day("2026-03-05")).unwrap();

    assert!(balances(&t, &group).iter().all(|&b| b == 0), "the group is closed");
    assert_eq!(t.db.list_settlements(group).unwrap().len(), 2);
}

/// Overpaying is allowed — it happens, and it just means the balance swings the other way rather
/// than being clamped and silently losing the difference.
#[test]
fn overpaying_swings_the_balance_rather_than_being_swallowed() {
    let t = TestDb::new();
    let (group, you, _, mike) = one_expense(&t);
    t.db.record_settlement(group.clone(), mike.clone(), you.clone(), 20_000, None, day("2026-03-05")).unwrap();

    assert_eq!(balance_of(&t, &group, &mike), 8_000, "Mike is now owed the 80 he overpaid");
    assert_eq!(balances(&t, &group).iter().sum::<i64>(), 0);
}

#[test]
fn a_payment_needs_two_different_people_and_a_positive_amount() {
    let t = TestDb::new();
    let (group, you, _, mike) = one_expense(&t);

    assert!(t.db.record_settlement(group.clone(), mike.clone(), you.clone(), 0, None, day("2026-03-05")).is_err());
    assert!(t.db.record_settlement(group.clone(), mike.clone(), you.clone(), -500, None, day("2026-03-05")).is_err());
    assert!(t.db.record_settlement(group.clone(), you.clone(), you.clone(), 1_000, None, day("2026-03-05")).is_err(),
        "paying yourself back is not a payment");
    assert!(t.db.list_settlements(group).unwrap().is_empty(), "a refusal leaves nothing behind");
}

/// Somebody from another trip has no balance here, and letting them pay into this group would put
/// the totals permanently out of true.
#[test]
fn somebody_from_another_group_cannot_pay_into_this_one() {
    let t = TestDb::new();
    let (group, you, _, _) = one_expense(&t);
    let other = t.db.create_expense_group("Flat".into(), "🏠".into(), "#1565C0".into(), vec!["Ann".into()]).unwrap();
    let ann = t.db.list_group_members(other.id).unwrap().iter().find(|m| m.name == "Ann").unwrap().id.clone();

    assert!(t.db.record_settlement(group.clone(), ann, you, 1_000, None, day("2026-03-05")).is_err());
    assert_eq!(balances(&t, &group).iter().sum::<i64>(), 0);
}

#[test]
fn deleting_a_payment_puts_the_balances_back() {
    let t = TestDb::new();
    let (group, you, _, mike) = one_expense(&t);
    let paid = t.db.record_settlement(group.clone(), mike.clone(), you.clone(), 12_000, None, day("2026-03-05")).unwrap();
    assert_eq!(balance_of(&t, &group, &mike), 0);

    t.db.delete_settlement(paid.id).unwrap();

    assert_eq!(balance_of(&t, &group, &mike), -12_000, "the debt is back");
    assert_eq!(balance_of(&t, &group, &you), 24_000);
    assert!(t.db.list_settlements(group).unwrap().is_empty());
}

#[test]
fn deleting_a_group_takes_its_payments_too() {
    let t = TestDb::new();
    let (group, you, _, mike) = one_expense(&t);
    t.db.record_settlement(group.clone(), mike, you, 12_000, None, day("2026-03-05")).unwrap();

    t.db.delete_expense_group(group.clone()).unwrap();
    assert!(t.db.list_settlements(group).unwrap().is_empty());
}

/// Removing somebody is already refused while they appear in an expense; a payment has to count for
/// the same reason, or their side of it would point at nobody.
#[test]
fn somebody_who_has_paid_up_cannot_be_removed_from_the_group() {
    let t = TestDb::new();
    let (group, you, sarah, _) = trip(&t);
    t.db.record_settlement(group, sarah.clone(), you, 5_000, None, day("2026-03-05")).unwrap();
    assert!(t.db.remove_group_member(sarah).is_err());
}

// ── Working out who should pay whom ──────────────────────────────────────────

#[test]
fn a_settled_group_has_nothing_to_suggest() {
    let t = TestDb::new();
    let (group, _, _, _) = trip(&t);
    assert!(t.db.suggest_settlements(group.clone()).unwrap().is_empty(), "nothing spent, nothing owed");

    let (group2, you, sarah, mike) = one_expense(&t);
    t.db.record_settlement(group2.clone(), mike, you.clone(), 12_000, None, day("2026-03-05")).unwrap();
    t.db.record_settlement(group2.clone(), sarah, you, 12_000, None, day("2026-03-05")).unwrap();
    assert!(t.db.suggest_settlements(group2).unwrap().is_empty());
    let _ = group;
}

#[test]
fn one_person_owed_by_two_is_two_payments_to_that_person() {
    let t = TestDb::new();
    let (group, you, sarah, mike) = one_expense(&t);

    let plan = t.db.suggest_settlements(group).unwrap();
    assert_eq!(plan.len(), 2);
    assert!(plan.iter().all(|s| s.to_member_id == you), "everything is owed to you");
    assert!(plan.iter().all(|s| s.amount_cents == 12_000));
    assert!(plan.iter().any(|s| s.from_member_id == sarah));
    assert!(plan.iter().any(|s| s.from_member_id == mike));
    assert!(plan.iter().all(|s| !s.from_name.is_empty() && !s.to_name.is_empty()), "the screen needs names");
}

/// The point of suggesting at all. Four people each owed by the others is six possible payments;
/// three is enough, and no plan can do it in fewer than one payment per person left holding a
/// balance minus one.
#[test]
fn the_plan_never_uses_more_payments_than_it_has_to() {
    let t = TestDb::new();
    let group = t.db.create_expense_group(
        "Flat".into(), "🏠".into(), "#1565C0".into(),
        vec!["Sarah".into(), "Mike".into(), "Ann".into()],
    ).unwrap();
    let m = t.db.list_group_members(group.id.clone()).unwrap();
    let id = |name: &str| m.iter().find(|x| x.name == name).unwrap().id.clone();
    let (you, sarah, mike, ann) = (id("You"), id("Sarah"), id("Mike"), id("Ann"));

    // Everybody pays for something, so all four end up with a balance.
    t.db.add_shared_expense(group.id.clone(), "Rent".into(), 80_000, you.clone(), None,
        vec![share(&you, 20_000), share(&sarah, 20_000), share(&mike, 20_000), share(&ann, 20_000)], day("2026-03-01")).unwrap();
    t.db.add_shared_expense(group.id.clone(), "Power".into(), 12_000, sarah.clone(), None,
        vec![share(&you, 3_000), share(&sarah, 3_000), share(&mike, 3_000), share(&ann, 3_000)], day("2026-03-02")).unwrap();
    t.db.add_shared_expense(group.id.clone(), "Internet".into(), 4_000, mike.clone(), None,
        vec![share(&you, 1_000), share(&sarah, 1_000), share(&mike, 1_000), share(&ann, 1_000)], day("2026-03-03")).unwrap();

    let plan = t.db.suggest_settlements(group.id.clone()).unwrap();
    let unsettled = t.db.list_group_members(group.id.clone()).unwrap().iter().filter(|x| x.balance_cents != 0).count();
    assert!(plan.len() <= unsettled - 1, "{} payments for {} unsettled people", plan.len(), unsettled);
    assert!(plan.iter().all(|s| s.amount_cents > 0), "a suggested payment of nothing is noise");
    assert!(plan.iter().all(|s| s.from_member_id != s.to_member_id));
}

/// The property that matters more than the count: make every payment the app suggested and the
/// group is closed. Run over a set of splits chosen because they do not divide evenly.
#[test]
fn making_every_suggested_payment_closes_the_group() {
    for amount in [10_000_i64, 1, 7, 99, 100_001, 333] {
        let t = TestDb::new();
        let (group, you, sarah, mike) = trip(&t);

        let parts = uniffi_ledger::split_equally(amount, 3);
        t.db.add_shared_expense(group.clone(), "Dinner".into(), amount, you.clone(), None,
            vec![share(&you, parts[0]), share(&sarah, parts[1]), share(&mike, parts[2])], day("2026-03-01")).unwrap();
        // A second payer, so it is not just everyone owing one person.
        let parts2 = uniffi_ledger::split_equally(amount, 3);
        t.db.add_shared_expense(group.clone(), "Taxi".into(), amount, sarah.clone(), None,
            vec![share(&you, parts2[0]), share(&sarah, parts2[1]), share(&mike, parts2[2])], day("2026-03-02")).unwrap();

        for s in t.db.suggest_settlements(group.clone()).unwrap() {
            t.db.record_settlement(group.clone(), s.from_member_id, s.to_member_id, s.amount_cents, None, day("2026-03-09")).unwrap();
        }

        let left = balances(&t, &group);
        assert!(left.iter().all(|&b| b == 0), "{amount} left {left:?} behind");
        assert!(t.db.suggest_settlements(group).unwrap().is_empty(), "nothing more to suggest once it is closed");
    }
}

/// Nobody should be told to pay more than they owe, even when the plan routes around a third person.
#[test]
fn nobody_is_asked_to_pay_more_than_they_owe() {
    let t = TestDb::new();
    let (group, you, sarah, mike) = trip(&t);
    t.db.add_shared_expense(group.clone(), "Hotel".into(), 30_000, you.clone(), None,
        vec![share(&you, 5_000), share(&sarah, 20_000), share(&mike, 5_000)], day("2026-03-01")).unwrap();

    let plan = t.db.suggest_settlements(group.clone()).unwrap();
    let members = t.db.list_group_members(group).unwrap();
    for m in members.iter().filter(|m| m.balance_cents < 0) {
        let asked: i64 = plan.iter().filter(|s| s.from_member_id == m.id).map(|s| s.amount_cents).sum();
        assert_eq!(asked, -m.balance_cents, "{} owes {} but is asked for {}", m.name, -m.balance_cents, asked);
    }
    let _ = (sarah, mike);
}
