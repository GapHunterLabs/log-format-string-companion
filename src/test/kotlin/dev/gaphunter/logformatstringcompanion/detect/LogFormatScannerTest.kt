package dev.gaphunter.logformatstringcompanion.detect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogFormatScannerTest {

    // --- SLF4J-style, real mismatches ------------------------------------

    @Test
    fun `slf4j fewer placeholders than arguments triggers`() {
        // Two excess arguments beyond the placeholder count -- unambiguous
        // regardless of the trailing-Throwable exemption below, which only
        // ever exempts an excess of exactly one.
        val text = """log.info("User {} logged in", userId, extraArg, anotherExtra);"""
        val matches = LogFormatScanner.scan(text)
        assertEquals(1, matches.size)
        assertEquals(LogFormatKind.SLF4J_BRACE_PLACEHOLDER, matches.single().kind)
        assertEquals(LogFormatMismatchKind.TOO_FEW_PLACEHOLDERS, matches.single().mismatchKind)
        assertEquals(1, matches.single().placeholderCount)
        assertEquals(3, matches.single().argumentCount)
    }

    @Test
    fun `slf4j more placeholders than arguments triggers`() {
        val text = """log.info("User {} logged in from {}", userId);"""
        val matches = LogFormatScanner.scan(text)
        assertEquals(1, matches.size)
        assertEquals(LogFormatMismatchKind.TOO_MANY_PLACEHOLDERS, matches.single().mismatchKind)
        assertEquals(2, matches.single().placeholderCount)
        assertEquals(1, matches.single().argumentCount)
    }

    // --- SLF4J-style, correct usage: no warning ---------------------------

    @Test
    fun `slf4j exact placeholder and argument count does not trigger`() {
        val text = """log.info("User {} logged in from {}", userId, ipAddress);"""
        val matches = LogFormatScanner.scan(text)
        assertTrue(matches.isEmpty())
    }

    @Test
    fun `slf4j no placeholders and no arguments does not trigger`() {
        val text = """log.info("Server started");"""
        val matches = LogFormatScanner.scan(text)
        assertTrue(matches.isEmpty())
    }

    // --- SLF4J trailing-Throwable special case ----------------------------

    @Test
    fun `slf4j trailing exception argument with exactly one excess does not trigger`() {
        val text = """log.error("Failed for {}", userId, exception);"""
        val matches = LogFormatScanner.scan(text)
        assertTrue(matches.isEmpty())
    }

    @Test
    fun `slf4j trailing new RuntimeException argument with exactly one excess does not trigger`() {
        val text = """log.error("Failed for {}", userId, new RuntimeException("boom"));"""
        val matches = LogFormatScanner.scan(text)
        assertTrue(matches.isEmpty())
    }

    @Test
    fun `slf4j trailing argument whose type is not determinable is conservative and does not trigger`() {
        // `lastArg` is a bare identifier -- could be a Throwable variable
        // or a String variable, and this scanner never resolves symbols.
        // Conservative choice: no false positive, even at the cost of a
        // possible missed real one-argument-too-many bug.
        val text = """log.error("Failed for {}", userId, lastArg);"""
        val matches = LogFormatScanner.scan(text)
        assertTrue(matches.isEmpty())
    }

    @Test
    fun `slf4j trailing string literal argument with one excess IS flagged, not a Throwable shape`() {
        val text = """log.error("Failed for {}", userId, "not an exception");"""
        val matches = LogFormatScanner.scan(text)
        assertEquals(1, matches.size)
        assertEquals(LogFormatMismatchKind.TOO_FEW_PLACEHOLDERS, matches.single().mismatchKind)
    }

    @Test
    fun `slf4j trailing long-suffixed numeric literal argument with one excess IS flagged, not a Throwable shape`() {
        val text = """log.error("Failed for {}", userId, 42L);"""
        val matches = LogFormatScanner.scan(text)
        assertEquals(1, matches.size)
        assertEquals(LogFormatMismatchKind.TOO_FEW_PLACEHOLDERS, matches.single().mismatchKind)
    }

    @Test
    fun `slf4j trailing float-suffixed numeric literal argument with one excess IS flagged, not a Throwable shape`() {
        val text = """log.error("Failed for {}", userId, 1.5f);"""
        val matches = LogFormatScanner.scan(text)
        assertEquals(1, matches.size)
        assertEquals(LogFormatMismatchKind.TOO_FEW_PLACEHOLDERS, matches.single().mismatchKind)
    }

    @Test
    fun `slf4j excess of two arguments beyond placeholders still triggers even with a trailing exception-like name`() {
        val text = """log.error("Failed for {}", userId, extraArg, exception);"""
        val matches = LogFormatScanner.scan(text)
        assertEquals(1, matches.size)
        assertEquals(LogFormatMismatchKind.TOO_FEW_PLACEHOLDERS, matches.single().mismatchKind)
        assertEquals(1, matches.single().placeholderCount)
        assertEquals(3, matches.single().argumentCount)
    }

    // --- Receiver name heuristic -------------------------------------------

    @Test
    fun `logger receiver name also matches`() {
        // 2 placeholders, 3 arguments -- excess of one beyond placeholder
        // count, but with a real second placeholder already accounted
        // for, so the total excess (1) still exempts under the
        // trailing-Throwable rule only when it's exactly 1 -- use an
        // unambiguous excess of two extra arguments instead.
        val text = """logger.warn("Retrying {} of {}", attempt, maxAttempts, extra, moreExtra);"""
        val matches = LogFormatScanner.scan(text)
        assertEquals(1, matches.size)
    }

    @Test
    fun `unrelated receiver name with info method is not treated as logging`() {
        val text = """builder.info("User {} logged in", userId, extraArg);"""
        val matches = LogFormatScanner.scan(text)
        assertTrue(matches.isEmpty())
    }

    // --- Python %-style logging --------------------------------------------

    @Test
    fun `python percent style fewer placeholders than arguments triggers`() {
        val text = """logger.info("User %s logged in", user_id, extra_arg)"""
        val matches = LogFormatScanner.scan(text)
        assertEquals(1, matches.size)
        assertEquals(LogFormatKind.PYTHON_PERCENT_STYLE, matches.single().kind)
        assertEquals(LogFormatMismatchKind.TOO_FEW_PLACEHOLDERS, matches.single().mismatchKind)
    }

    @Test
    fun `python percent style more placeholders than arguments triggers`() {
        val text = """logger.info("User %s logged in from %s", user_id)"""
        val matches = LogFormatScanner.scan(text)
        assertEquals(1, matches.size)
        assertEquals(LogFormatMismatchKind.TOO_MANY_PLACEHOLDERS, matches.single().mismatchKind)
    }

    @Test
    fun `python percent style exact match does not trigger`() {
        val text = """logger.info("User %s logged in from %s", user_id, ip_address)"""
        val matches = LogFormatScanner.scan(text)
        assertTrue(matches.isEmpty())
    }

    @Test
    fun `python percent style has no Throwable exemption -- one excess argument still triggers`() {
        // Unlike SLF4J, Python logging has no "trailing exception
        // argument" convention (exceptions go through `exc_info=True`/
        // `logger.exception(...)`, not an extra positional argument).
        val text = """logger.error("Failed for %s", user_id, exc)"""
        val matches = LogFormatScanner.scan(text)
        assertEquals(1, matches.size)
        assertEquals(LogFormatMismatchKind.TOO_FEW_PLACEHOLDERS, matches.single().mismatchKind)
    }

    @Test
    fun `python fstring is out of scope and never scanned`() {
        // f-string interpolations are inline expressions, not separate
        // call arguments -- there is no placeholder-count-vs-argument
        // -count mismatch to detect in the same shape as percent-style.
        val text = """logger.info(f"User {user_id} logged in")"""
        val matches = LogFormatScanner.scan(text)
        assertTrue(matches.isEmpty())
    }
}
