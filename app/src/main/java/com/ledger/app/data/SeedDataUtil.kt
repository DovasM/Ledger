package com.ledger.app.data

import java.time.LocalDate

/**
 * Seeds realistic demo data for a 28-year-old software developer named Alex.
 * Runs once on first launch — skipped if wallets already exist.
 */
object SeedDataUtil {

    fun seed(bridge: LedgerBridge) {
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
        val checking = bridge.createWallet("Checking Account", "Day-to-day spending",    0.0)
        val savings  = bridge.createWallet("Savings Account",  "Emergency fund & goals", 10000.0)
        val cash     = bridge.createWallet("Cash",             "Physical cash on hand",  180.0)

        // ── Savings goals ─────────────────────────────────────────────────────
        val emergency = bridge.createGoal("Emergency Fund",   15000.0, "2027-01-01")
        val vacation  = bridge.createGoal("Vacation — Japan",  3000.0, "2026-12-15")
        val laptop    = bridge.createGoal("New Laptop",        2500.0, null)
        bridge.addContribution(emergency.id, 8500.0)
        bridge.addContribution(vacation.id,  1200.0)
        bridge.addContribution(laptop.id,     800.0)

        // ── Budgets ───────────────────────────────────────────────────────────
        bridge.createBudget(catDining.id,    350.0, "monthly", 80.0)
        bridge.createBudget(catGroceries.id, 400.0, "monthly", 85.0)
        bridge.createBudget(catTransport.id, 150.0, "monthly", 80.0)
        bridge.createBudget(catEntertain.id,  80.0, "monthly", 80.0)
        bridge.createBudget(catShopping.id,  200.0, "monthly", 80.0)

        // ── Debts ─────────────────────────────────────────────────────────────
        bridge.createDebt("Student Loan", "loan", 24000.0, 18500.0, 4.5, 280.0)
        bridge.createDebt("Car Loan",     "loan", 12000.0,  8200.0, 6.9, 320.0)

        // ── Recurring transactions ────────────────────────────────────────────
        // Next dates all start in the future so auto-apply doesn't trigger on seed
        val nextMonth = today.plusMonths(1).withDayOfMonth(1)
        bridge.createRecurring("Monthly Salary",  4200.00, catSalary.name,    checking.id, true,  "monthly", nextMonth.toString())
        bridge.createRecurring("Rent",            1450.00, catRent.name,      checking.id, false, "monthly", nextMonth.toString())
        bridge.createRecurring("Student Loan",     280.00, catLoans.name,     checking.id, false, "monthly", nextMonth.toString())
        bridge.createRecurring("Car Payment",      320.00, catLoans.name,     checking.id, false, "monthly", nextMonth.withDayOfMonth(3).toString())
        bridge.createRecurring("Internet Bill",     59.99, catUtilities.name, checking.id, false, "monthly", nextMonth.withDayOfMonth(5).toString())
        bridge.createRecurring("Gym Membership",    35.00, catFitness.name,   checking.id, false, "monthly", nextMonth.withDayOfMonth(10).toString())
        bridge.createRecurring("Netflix",           15.99, catEntertain.name, checking.id, false, "monthly", today.withDayOfMonth(20).let { if (it.isBefore(today)) it.plusMonths(1) else it }.toString())
        bridge.createRecurring("Spotify",           10.99, catEntertain.name, checking.id, false, "monthly", today.withDayOfMonth(22).let { if (it.isBefore(today)) it.plusMonths(1) else it }.toString())

        // ── Historical transactions — 6 months ───────────────────────────────
        for (offset in 5 downTo 0) {
            val month = today.minusMonths(offset.toLong())
            val y  = month.year
            val mo = month.monthValue
            val daysInMonth = month.lengthOfMonth()

            // Helper: only insert if the date isn't in the future
            fun tx(
                walletId: String, title: String, category: String,
                amount: Double, isIncome: Boolean, day: Int, note: String? = null
            ) {
                val d = LocalDate.of(y, mo, day.coerceAtMost(daysInMonth))
                if (!d.isAfter(today)) bridge.createTransaction(walletId, title, category, amount, isIncome, note, d.toString())
            }

            // ── Fixed monthly ────────────────────────────────────────────────
            tx(checking.id, "Monthly Salary",   catSalary.name,    4200.00, true,  1)
            tx(checking.id, "Rent",             catRent.name,      1450.00, false, 1)
            tx(checking.id, "Student Loan",     catLoans.name,      280.00, false, 1)
            tx(checking.id, "Car Payment",      catLoans.name,      320.00, false, 3)
            tx(checking.id, "Internet Bill",    catUtilities.name,   59.99, false, 5)
            tx(checking.id, "Electricity",      catUtilities.name,  (80.0 + offset * 4.5), false, 14)
            tx(checking.id, "Gym Membership",   catFitness.name,     35.00, false, 10)
            tx(checking.id, "Netflix",          catEntertain.name,   15.99, false, 15)
            if (offset < 5) // Spotify added 5 months ago
                tx(checking.id, "Spotify",      catEntertain.name,   10.99, false, 22)

            // ── Groceries — roughly weekly ────────────────────────────────────
            tx(checking.id, "Grocery Store",   catGroceries.name,  94.20, false,  3)
            tx(checking.id, "Grocery Store",   catGroceries.name,  87.50, false, 10)
            tx(checking.id, "Grocery Store",   catGroceries.name, 112.30, false, 17)
            tx(checking.id, "Grocery Store",   catGroceries.name,  98.80, false, 24)

            // ── Dining out ────────────────────────────────────────────────────
            tx(checking.id, "Lunch with colleagues", catDining.name, 28.50, false,  2)
            tx(checking.id, "Italian Restaurant",    catDining.name, 62.00, false,  7)
            tx(checking.id, "Quick Bite",            catDining.name, 14.80, false, 12)
            tx(checking.id, "Sushi Place",           catDining.name, 52.00, false, 16)
            tx(checking.id, "Burger Bar",            catDining.name, 24.60, false, 21)
            tx(checking.id, "Pizza Delivery",        catDining.name, 31.50, false, 27)

            // ── Coffee shop ───────────────────────────────────────────────────
            tx(checking.id, "Coffee Shop", catCoffee.name,  6.80, false,  4)
            tx(checking.id, "Coffee Shop", catCoffee.name,  5.40, false,  9)
            tx(checking.id, "Coffee Shop", catCoffee.name,  7.20, false, 13)
            tx(checking.id, "Coffee Shop", catCoffee.name,  6.50, false, 18)
            tx(checking.id, "Coffee Shop", catCoffee.name,  8.10, false, 23)

            // ── Transport ─────────────────────────────────────────────────────
            tx(checking.id, "Gas Station",  catTransport.name, 58.0 + (offset % 4) * 8.0, false, 6)
            if (offset % 2 == 0) tx(checking.id, "Uber Ride",   catTransport.name, 16.40, false, 19)
            if (offset % 3 == 1) tx(checking.id, "Parking Fee", catTransport.name, 12.00, false, 22)

            // ── Variable monthly — shopping, entertainment, healthcare ─────────
            when (offset % 3) {
                0 -> {
                    tx(checking.id, "Clothing Store",  catShopping.name,  94.99, false,  8)
                    tx(checking.id, "Cinema Tickets",  catEntertain.name, 28.00, false, 23)
                    tx(checking.id, "Pharmacy",        catHealth.name,    18.50, false, 17)
                }
                1 -> {
                    tx(checking.id, "Amazon Order",    catShopping.name, 143.20, false, 11)
                    tx(checking.id, "Doctor Copay",    catHealth.name,    30.00, false, 15)
                    tx(checking.id, "Steam Game",      catEntertain.name, 19.99, false, 25)
                }
                2 -> {
                    tx(checking.id, "Sports Store",    catShopping.name,  67.50, false,  9)
                    tx(checking.id, "Concert Tickets", catEntertain.name, 58.00, false, 20)
                    tx(checking.id, "Supplements",     catFitness.name,   34.99, false, 16)
                }
            }

            // ── Freelance income — 3 months out of 6 ─────────────────────────
            val freelance = mapOf(5 to 450.0, 3 to 720.0, 1 to 380.0)
            freelance[offset]?.let { amt ->
                tx(checking.id, "Freelance Payment", catFreelance.name, amt, true, 20, "Side project")
            }

            // ── Monthly savings transfer ──────────────────────────────────────
            tx(savings.id, "Savings Transfer", catSalary.name, 300.0, true, 28, "Monthly contribution")
        }
    }
}
