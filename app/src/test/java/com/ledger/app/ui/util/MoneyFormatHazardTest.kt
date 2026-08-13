package com.ledger.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `"%,.2f".format(x)` takes `Any?`. Handing it a `Long` compiles cleanly and throws
 * `IllegalFormatConversionException` the moment that screen is drawn — so the compiler cannot help,
 * and neither can a code review, because the call looks exactly like the correct one.
 *
 * It has already shipped three times since money became integer cents: every CSV export, the whole
 * budgets screen, and the streaks screen. This test reads the source and fails the build on the
 * shape that caused all three.
 *
 * It deliberately only flags the unambiguous case — an argument that names a `*Cents` value. Money
 * held in a plainly-named local is not caught here; that is what `formatCents` is for.
 */
class MoneyFormatHazardTest {

    /** A `%f`-style format whose argument mentions cents and does not convert them first. */
    private val floatFormat = Regex("""""" + "\"" + """[^"]*%[-,+ 0-9.#]*f[^"]*""" + "\"" + """\.format\(""")

    private fun firstArgument(line: String, from: Int): String {
        val open = line.indexOf('(', from)
        var depth = 0
        for (i in open until line.length) {
            when (line[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return line.substring(open + 1, i)
                }
            }
        }
        return line.substring(open + 1)
    }

    private fun hazardsIn(line: String): List<String> =
        floatFormat.findAll(line).mapNotNull { match ->
            val arg = firstArgument(line, match.range.first)
            val convertsFirst = arg.contains("asUnits") || arg.contains("toDouble()") || arg.contains("toFloat()")
            if (arg.contains("Cents") && !convertsFirst) arg.trim() else null
        }.toList()

    /** The detector itself, on the exact lines that shipped broken. */
    @Test
    fun `the detector recognises what actually went wrong`() {
        assertEquals(
            listOf("totalSpentCents / totalLimitCents * 100"),
            hazardsIn("""Text("${'$'}{"%.0f".format(totalSpentCents / totalLimitCents * 100)}% used",""")
        )
        assertEquals(
            listOf("dailyAllowanceCents"),
            hazardsIn("""Text("Daily allowance: ${'$'}{"${'$'}%,.2f".format(dailyAllowanceCents)}")""")
        )
    }

    @Test
    fun `the detector leaves the correct forms alone`() {
        assertTrue(hazardsIn("""Text("${'$'}{"%.2f".format(amountCents.asUnits)}")""").isEmpty())
        assertTrue(hazardsIn("""Text("${'$'}{"%.1f".format(spentCents.toDouble() / limitCents * 100)}")""").isEmpty())
        assertTrue(hazardsIn("""Text(formatCents(amountCents, currency, numberFormat))""").isEmpty())
        assertTrue(hazardsIn("""Text("${'$'}{"%.1f".format(savingsRate)}%")""").isEmpty())
        assertTrue(hazardsIn("""Text("${'$'}{"%d".format(amountCents)}")""").isEmpty())
    }

    /**
     * The other direction, and the one that corrupted data rather than crashing: a text field
     * pre-filled with raw cents showed a budget of 1000 as "100000", and saving it multiplied the
     * budget by a hundred. Editing a debt did the same to three fields at once.
     */
    private fun prefillHazardsIn(line: String): List<String> =
        if ("mutableStateOf(" in line && Regex("""Cents[^)]*\.toString\(\)""").containsMatchIn(line))
            listOf(line.trim()) else emptyList()

    @Test
    fun `the prefill detector recognises what actually went wrong`() {
        assertTrue(
            prefillHazardsIn("""var amount by remember { mutableStateOf(existingBudget?.limitAmountCents?.toString() ?: "") }""")
                .isNotEmpty()
        )
        assertTrue(
            prefillHazardsIn("""var amount by remember { mutableStateOf(existingBudget?.limitAmountCents?.asAmountInput() ?: "") }""")
                .isEmpty()
        )
        assertTrue(prefillHazardsIn("""var name by remember { mutableStateOf(wallet?.name ?: "") }""").isEmpty())
    }

    @Test
    fun `no edit field is pre-filled with raw cents`() {
        val sources = File("src/main/java/com/ledger/app")
        val offences = mutableListOf<String>()
        sources.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && "uniffi" !in it.path.replace('\\', '/') }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    prefillHazardsIn(line).forEach { offences += "${file.name}:${index + 1}  ${it.take(90)}" }
                }
            }
        assertEquals(
            "a text field pre-filled with raw cents — use asAmountInput(), or saving multiplies by 100:\n" +
                offences.joinToString("\n"),
            emptyList<String>(),
            offences
        )
    }

    /**
     * A hand-rolled `"$%,.2f"` is wrong twice over: it prints a dollar sign to someone whose wallets
     * are in euros, and it ignores the number-format preference. It is also the shape every money
     * bug in this app has hidden inside. `MoneyFormatter` replaced all 162 of them; this stops the
     * 163rd.
     */
    @Test
    fun `no screen hardcodes a currency symbol in a format string`() {
        val sources = File("src/main/java/com/ledger/app")
        val hardcoded = Regex(""""\$%[-,+ 0-9.#]*f"""")
        val offences = mutableListOf<String>()
        sources.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && "uniffi" !in it.path.replace('\\', '/') }
            // MoneyFormatter's own documentation quotes the pattern it exists to replace.
            .filter { it.name != "MoneyFormatter.kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    if (hardcoded.containsMatchIn(line) && !line.trimStart().startsWith("//")) {
                        offences += "${file.name}:${index + 1}  ${line.trim().take(90)}"
                    }
                }
            }
        assertEquals(
            "a currency symbol written into a format string — use rememberMoneyFormatter():\n" +
                offences.joinToString("\n"),
            emptyList<String>(),
            offences
        )
    }

    @Test
    fun `no screen formats cents as a float`() {
        val sources = File("src/main/java/com/ledger/app")
        assertTrue("source tree not found at ${sources.absolutePath}", sources.isDirectory)

        val offences = mutableListOf<String>()
        sources.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && "uniffi" !in it.path.replace('\\', '/') }
            .forEach { file ->
                file.readLines().forEachIndexed { index, line ->
                    hazardsIn(line).forEach { arg ->
                        offences += "${file.name}:${index + 1}  $arg"
                    }
                }
            }

        assertEquals(
            "money formatted as a float — this throws at runtime; use formatCents, or .asUnits for a ratio:\n" +
                offences.joinToString("\n"),
            emptyList<String>(),
            offences
        )
    }
}
