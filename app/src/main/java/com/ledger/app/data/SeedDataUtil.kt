package com.ledger.app.data

import java.time.LocalDate

/**
 * Seeds realistic demo data for a 28-year-old software developer named Alex.
 * Runs once on first launch — skipped if wallets already exist.
 */
object SeedDataUtil {

    fun seed(bridge: ILedgerBridge) {
        if (bridge.listWallets().isNotEmpty()) return

        val today = LocalDate.now()

        // ── Categories ────────────────────────────────────────────────────────
        // Icons must match categoryIconNames in CategoryIcons.kt
        val catSalary    = bridge.createCategory("Salary",        "payments",            "#558B2F", false)
        val catFreelance = bridge.createCategory("Freelance",     "work",                "#1565C0", false)
        val catRent      = bridge.createCategory("Rent & Housing","home",                "#6A1B9A", true)
        val catGroceries = bridge.createCategory("Groceries",     "local_grocery_store", "#00513F", true)
        val catDining    = bridge.createCategory("Dining Out",    "restaurant",          "#920009", true)
        val catTransport = bridge.createCategory("Transport",     "directions_car",      "#1565C0", true)
        val catUtilities = bridge.createCategory("Utilities",     "payments",            "#F9A825", true)
        val catEntertain = bridge.createCategory("Entertainment", "movie",               "#E65100", true)
        val catHealth    = bridge.createCategory("Healthcare",    "health_and_safety",   "#920009", true)
        val catShopping  = bridge.createCategory("Shopping",      "shopping_bag",        "#00838F", true)
        val catFitness   = bridge.createCategory("Fitness",       "fitness_center",      "#00513F", true)
        val catCoffee    = bridge.createCategory("Coffee",        "local_cafe",          "#4E342E", true)
        val catLoans     = bridge.createCategory("Loan Payments", "school",              "#4E342E", true)

        // ── Wallets ───────────────────────────────────────────────────────────
        val checking = bridge.createWallet("Checking Account", "Day-to-day spending",    "EUR", 0)
        val savings  = bridge.createWallet("Savings Account",  "Emergency fund & goals", "EUR", 1000000)
        val cash     = bridge.createWallet("Cash",             "Physical cash on hand",  "EUR",   18000)

        // ── Savings goals ─────────────────────────────────────────────────────
        val emergency = bridge.createGoal("Emergency Fund",   1500000, "2027-01-01")
        val vacation  = bridge.createGoal("Vacation — Japan",  300000, "2026-12-15")
        val laptop    = bridge.createGoal("New Laptop",        250000, null)
        bridge.addContribution(emergency.id, 850000)
        bridge.addContribution(vacation.id,  120000)
        bridge.addContribution(laptop.id,     80000)

        // ── Budgets ───────────────────────────────────────────────────────────
        bridge.createBudget(catDining.id, null,    35000, "monthly", 80.0)
        bridge.createBudget(catGroceries.id, null, 40000, "monthly", 85.0)
        bridge.createBudget(catTransport.id, null, 15000, "monthly", 80.0)
        bridge.createBudget(catEntertain.id, null,   8000, "monthly", 80.0)
        bridge.createBudget(catShopping.id, null,  20000, "monthly", 80.0)

        // ── Debts ─────────────────────────────────────────────────────────────
        bridge.createDebt("Student Loan", "loan", 2400000, 1850000, 4.5, 28000)
        bridge.createDebt("Car Loan",     "loan", 1200000,  820000, 6.9, 32000)

        // ── Recurring transactions ────────────────────────────────────────────
        // Next dates all start in the future so auto-apply doesn't trigger on seed
        val nextMonth = today.plusMonths(1).withDayOfMonth(1)
        bridge.createRecurring("Monthly Salary",  420000, catSalary.name,    checking.id, true,  "monthly", nextMonth.toString())
        bridge.createRecurring("Rent",            145000, catRent.name,      checking.id, false, "monthly", nextMonth.toString())
        bridge.createRecurring("Student Loan",     28000, catLoans.name,     checking.id, false, "monthly", nextMonth.toString())
        bridge.createRecurring("Car Payment",      32000, catLoans.name,     checking.id, false, "monthly", nextMonth.withDayOfMonth(3).toString())
        bridge.createRecurring("Internet Bill",     5999, catUtilities.name, checking.id, false, "monthly", nextMonth.withDayOfMonth(5).toString())
        bridge.createRecurring("Gym Membership",    3500, catFitness.name,   checking.id, false, "monthly", nextMonth.withDayOfMonth(10).toString())
        bridge.createRecurring("Netflix",           1599, catEntertain.name, checking.id, false, "monthly", today.withDayOfMonth(20).let { if (it.isBefore(today)) it.plusMonths(1) else it }.toString())
        bridge.createRecurring("Spotify",           1099, catEntertain.name, checking.id, false, "monthly", today.withDayOfMonth(22).let { if (it.isBefore(today)) it.plusMonths(1) else it }.toString())

        // ── Historical transactions — 6 months ───────────────────────────────
        for (offset in 5 downTo 0) {
            val month = today.minusMonths(offset.toLong())
            val y  = month.year
            val mo = month.monthValue
            val daysInMonth = month.lengthOfMonth()

            // Helper: only insert if the date isn't in the future
            fun tx(
                walletId: String, title: String, category: String,
                amountCents: Long, isIncome: Boolean, day: Int, note: String? = null
            ) {
                val d = LocalDate.of(y, mo, day.coerceAtMost(daysInMonth))
                if (!d.isAfter(today)) bridge.createTransaction(walletId, title, category, amountCents, isIncome, note, d.toString())
            }

            // ── Fixed monthly ────────────────────────────────────────────────
            tx(checking.id, "Monthly Salary",   catSalary.name,    420000, true,  1)
            tx(checking.id, "Rent",             catRent.name,      145000, false, 1)
            tx(checking.id, "Student Loan",     catLoans.name,      28000, false, 1)
            tx(checking.id, "Car Payment",      catLoans.name,      32000, false, 3)
            tx(checking.id, "Internet Bill",    catUtilities.name,   5999, false, 5)
            tx(checking.id, "Electricity",      catUtilities.name,  (8000L + offset * 450), false, 14)
            tx(checking.id, "Gym Membership",   catFitness.name,     3500, false, 10)
            tx(checking.id, "Netflix",          catEntertain.name,   1599, false, 15)
            if (offset < 5) // Spotify added 5 months ago
                tx(checking.id, "Spotify",      catEntertain.name,   1099, false, 22)

            // ── Groceries — roughly weekly ────────────────────────────────────
            tx(checking.id, "Grocery Store",   catGroceries.name,  9420, false,  3)
            tx(checking.id, "Grocery Store",   catGroceries.name,  8750, false, 10)
            tx(checking.id, "Grocery Store",   catGroceries.name, 11230, false, 17)
            tx(checking.id, "Grocery Store",   catGroceries.name,  9880, false, 24)

            // ── Dining out ────────────────────────────────────────────────────
            tx(checking.id, "Lunch with colleagues", catDining.name, 2850, false,  2)
            tx(checking.id, "Italian Restaurant",    catDining.name, 6200, false,  7)
            tx(checking.id, "Quick Bite",            catDining.name, 1480, false, 12)
            tx(checking.id, "Sushi Place",           catDining.name, 5200, false, 16)
            tx(checking.id, "Burger Bar",            catDining.name, 2460, false, 21)
            tx(checking.id, "Pizza Delivery",        catDining.name, 3150, false, 27)

            // ── Coffee shop ───────────────────────────────────────────────────
            tx(checking.id, "Coffee Shop", catCoffee.name,  680, false,  4)
            tx(checking.id, "Coffee Shop", catCoffee.name,  540, false,  9)
            tx(checking.id, "Coffee Shop", catCoffee.name,  720, false, 13)
            tx(checking.id, "Coffee Shop", catCoffee.name,  650, false, 18)
            tx(checking.id, "Coffee Shop", catCoffee.name,  810, false, 23)

            // ── Transport ─────────────────────────────────────────────────────
            tx(checking.id, "Gas Station",  catTransport.name, 5800L + (offset % 4) * 800, false, 6)
            if (offset % 2 == 0) tx(checking.id, "Uber Ride",   catTransport.name, 1640, false, 19)
            if (offset % 3 == 1) tx(checking.id, "Parking Fee", catTransport.name, 1200, false, 22)

            // ── Variable monthly — shopping, entertainment, healthcare ─────────
            when (offset % 3) {
                0 -> {
                    tx(checking.id, "Clothing Store",  catShopping.name,  9499, false,  8)
                    tx(checking.id, "Cinema Tickets",  catEntertain.name, 2800, false, 23)
                    tx(checking.id, "Pharmacy",        catHealth.name,    1850, false, 17)
                }
                1 -> {
                    tx(checking.id, "Amazon Order",    catShopping.name, 14320, false, 11)
                    tx(checking.id, "Doctor Copay",    catHealth.name,    3000, false, 15)
                    tx(checking.id, "Steam Game",      catEntertain.name, 1999, false, 25)
                }
                2 -> {
                    tx(checking.id, "Sports Store",    catShopping.name,  6750, false,  9)
                    tx(checking.id, "Concert Tickets", catEntertain.name, 5800, false, 20)
                    tx(checking.id, "Supplements",     catFitness.name,   3499, false, 16)
                }
            }

            // ── Freelance income — 3 months out of 6 ─────────────────────────
            val freelance = mapOf(5 to 45000L, 3 to 72000L, 1 to 38000L)
            freelance[offset]?.let { amt ->
                tx(checking.id, "Freelance Payment", catFreelance.name, amt, true, 20, "Side project")
            }

            // ── Monthly savings transfer ──────────────────────────────────────
            tx(savings.id, "Savings Transfer", catSalary.name, 30000, true, 28, "Monthly contribution")
        }
    }
}
