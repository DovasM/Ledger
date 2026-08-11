//! A goal's balance and a debt's remaining amount are summed from their history rather than stored.
//! These tests pin that down, including the migration that seeded the history from the old totals.

mod common;
use common::{day, TestDb};

#[test]
fn a_goal_balance_is_the_sum_of_its_contributions() {
    let t = TestDb::new();
    let g = t.db.create_goal("Laptop".into(), 1000.0, None).unwrap();
    assert_eq!(g.current_amount, 0.0);

    t.db.add_contribution(g.id.clone(), 100.0, None, day("2026-03-01")).unwrap();
    let after = t.db.add_contribution(g.id.clone(), 250.0, Some("bonus".into()), day("2026-04-01")).unwrap();
    assert_eq!(after.current_amount, 350.0);

    let history = t.db.list_goal_contributions(g.id.clone()).unwrap();
    assert_eq!(history.len(), 2, "each contribution is its own row, not a number that got bigger");
    assert_eq!(history[0].occurred_at, "2026-04-01T00:00:00Z", "newest first");
    assert_eq!(history[0].note.as_deref(), Some("bonus"));
    assert!(history.iter().all(|c| c.kind == "contribution"));

    // The list view must agree with the single-goal read.
    let listed = t.db.list_goals().unwrap().into_iter().find(|x| x.id == g.id).unwrap();
    assert_eq!(listed.current_amount, 350.0);
}

/// The point of the history: a mistyped contribution can be removed. Before, the only remedy was
/// typing over the total, which destroyed the record of what had really gone in.
#[test]
fn deleting_a_contribution_corrects_the_balance() {
    let t = TestDb::new();
    let g = t.db.create_goal("Laptop".into(), 1000.0, None).unwrap();
    t.db.add_contribution(g.id.clone(), 100.0, None, day("2026-03-01")).unwrap();
    let fat_finger = t.db.list_goal_contributions(g.id.clone()).unwrap()[0].id.clone();
    t.db.add_contribution(g.id.clone(), 50.0, None, day("2026-03-02")).unwrap();

    let corrected = t.db.delete_contribution(fat_finger).unwrap();
    assert_eq!(corrected.current_amount, 50.0);
    assert_eq!(t.db.list_goal_contributions(g.id).unwrap().len(), 1);
}

#[test]
fn deleting_a_goal_takes_its_contributions_with_it() {
    let t = TestDb::new();
    let keep = t.db.create_goal("Keep".into(), 1000.0, None).unwrap();
    let drop = t.db.create_goal("Drop".into(), 1000.0, None).unwrap();
    t.db.add_contribution(keep.id.clone(), 10.0, None, None).unwrap();
    t.db.add_contribution(drop.id.clone(), 20.0, None, None).unwrap();

    t.db.delete_goal(drop.id.clone()).unwrap();
    assert!(t.db.list_goal_contributions(drop.id).unwrap().is_empty(), "no foreign key does this for us");
    assert_eq!(t.db.list_goal_contributions(keep.id).unwrap().len(), 1, "and only that goal's history goes");
}

#[test]
fn a_contribution_must_be_positive_and_the_goal_must_exist() {
    let t = TestDb::new();
    let g = t.db.create_goal("Laptop".into(), 1000.0, None).unwrap();
    assert!(t.db.add_contribution(g.id, 0.0, None, None).is_err());
    assert!(t.db.add_contribution("no-such-goal".into(), 10.0, None, None).is_err());
}

/// Entering a debt part-way through is the normal case: "I owe 3400 of an original 5000". The 1600
/// already paid has to become a row, or the derived remaining would report the full 5000.
#[test]
fn an_existing_debt_records_what_was_already_paid() {
    let t = TestDb::new();
    let d = t.db.create_debt("Car loan".into(), "loan".into(), 5000.0, 3400.0, 4.5, 200.0).unwrap();
    assert_eq!(d.remaining_amount, 3400.0);
    assert_eq!(d.total_amount, 5000.0);

    let history = t.db.list_debt_payments(d.id).unwrap();
    assert_eq!(history.len(), 1);
    assert_eq!(history[0].amount, 1600.0);
    assert_eq!(history[0].kind, "opening");
}

#[test]
fn paying_a_debt_reduces_what_is_left() {
    let t = TestDb::new();
    let d = t.db.create_debt("Car loan".into(), "loan".into(), 5000.0, 5000.0, 4.5, 200.0).unwrap();
    assert!(t.db.list_debt_payments(d.id.clone()).unwrap().is_empty(), "nothing paid yet is no rows, not a zero row");

    t.db.add_debt_payment(d.id.clone(), 200.0, None, day("2026-03-01")).unwrap();
    let after = t.db.add_debt_payment(d.id.clone(), 200.0, Some("extra".into()), day("2026-04-01")).unwrap();
    assert_eq!(after.remaining_amount, 4600.0);

    let history = t.db.list_debt_payments(d.id.clone()).unwrap();
    assert_eq!(history.len(), 2);
    assert_eq!(history[0].occurred_at, "2026-04-01T00:00:00Z", "newest first");

    let undone = t.db.delete_debt_payment(history[0].id.clone()).unwrap();
    assert_eq!(undone.remaining_amount, 4800.0);
}

/// The edit screen still offers a remaining-amount field. It is no longer a column, so honouring it
/// means recording the difference — the user gets the number they typed and the history still adds
/// up to it.
#[test]
fn typing_over_the_remaining_amount_is_recorded_as_an_adjustment() {
    let t = TestDb::new();
    let d = t.db.create_debt("Card".into(), "credit".into(), 1000.0, 1000.0, 20.0, 50.0).unwrap();
    t.db.add_debt_payment(d.id.clone(), 100.0, None, day("2026-03-01")).unwrap();

    let edited = t.db
        .update_debt(d.id.clone(), "Card".into(), "credit".into(), 1000.0, 700.0, 20.0, 50.0)
        .unwrap();
    assert_eq!(edited.remaining_amount, 700.0, "the number the user typed must win");

    let history = t.db.list_debt_payments(d.id.clone()).unwrap();
    assert_eq!(history.len(), 2);
    let adjustment = history.iter().find(|p| p.kind == "adjustment").expect("the correction is on the record");
    assert_eq!(adjustment.amount, 200.0);

    // Editing anything else must not invent a second adjustment.
    t.db.update_debt(d.id.clone(), "Credit card".into(), "credit".into(), 1000.0, 700.0, 20.0, 60.0).unwrap();
    assert_eq!(t.db.list_debt_payments(d.id).unwrap().len(), 2, "an unchanged remaining writes nothing");
}

#[test]
fn deleting_a_debt_takes_its_payments_with_it() {
    let t = TestDb::new();
    let keep = t.db.create_debt("Keep".into(), "loan".into(), 1000.0, 1000.0, 1.0, 10.0).unwrap();
    let drop = t.db.create_debt("Drop".into(), "loan".into(), 1000.0, 1000.0, 1.0, 10.0).unwrap();
    t.db.add_debt_payment(keep.id.clone(), 10.0, None, None).unwrap();
    t.db.add_debt_payment(drop.id.clone(), 20.0, None, None).unwrap();

    t.db.delete_debt(drop.id.clone()).unwrap();
    assert!(t.db.list_debt_payments(drop.id).unwrap().is_empty());
    assert_eq!(t.db.list_debt_payments(keep.id).unwrap().len(), 1);
}
