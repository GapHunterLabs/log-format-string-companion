package dev.gaphunter.logformatstringcompanion.detect

/**
 * Finds real "log message placeholder count does not match argument
 * count" occurrences in Java, Kotlin, and Python source text --
 * plain-text/regex analysis, same "hand-rolled over plain text"
 * principle already proven catalog-wide (see `build.gradle.kts` for why
 * no per-language PSI dependency is taken here).
 *
 * **One call shape, two placeholder conventions -- the style is decided
 * per-match from the message literal's own content, never from file
 * extension** (see README "Detection heuristic" for worked examples):
 *
 * 1. **SLF4J-style brace placeholders** (Java/Kotlin): `log.info("User
 *    {} logged in from {}", userId, ipAddress)` -- count the `{}`
 *    occurrences in the message literal (the call's first argument),
 *    compare against the number of trailing arguments.
 * 2. **Python `%`-style logging**: `logger.info("User %s logged in",
 *    user_id)` -- count `%s`/`%d`/`%r`/etc. conversion specifiers,
 *    compare against the number of trailing arguments. Python f-strings
 *    are deliberately NOT scanned by this plugin -- see the README's
 *    "Why f-strings are out of scope" section: an f-string's
 *    interpolations are inline expressions, not separate arguments, so
 *    there is no placeholder-count-vs-argument-count mismatch to detect
 *    in the same shape as SLF4J/percent-style. A literal containing
 *    neither marker (including an f-string, whose interpolations use
 *    neither `{}` nor `%x`) only ever falls back to the SLF4J
 *    zero-placeholder path, so it's still correctly silent unless there
 *    are unexplained extra arguments.
 *
 * **The SLF4J `Throwable`-last-argument special case (critical, see
 * README):** SLF4J's API treats a trailing `Throwable` argument as the
 * exception to log, not a placeholder argument -- so
 * `log.error("Failed for {}", userId, exception)` (1 placeholder, 2
 * arguments) is CORRECT, not a mismatch. This scanner only flags a
 * "too few placeholders" case when the argument count exceeds the
 * placeholder count by more than 1 -- an excess of exactly 1 is treated
 * as the conventional trailing-`Throwable` shape and never reported.
 * This is deliberately conservative: it also silently accepts the rarer
 * case where the last argument is NOT actually a `Throwable` (a real
 * one-too-many-arguments bug) rather than risk flagging the common
 * correct case -- see README "Known, documented limitation" for why a
 * false negative was chosen over a false positive here, and
 * [isDefinitelyNotThrowable] for the (best-effort, syntax-only) check
 * applied when a fuller signal is available.
 *
 * **What does NOT trigger** (see README "Detection heuristic" for the
 * full worked list):
 * - No placeholders and no arguments (`log.info("Server started")`) --
 *   zero vs. zero, never a mismatch.
 * - Exact match (placeholder count == argument count).
 * - Exactly one more argument than placeholders (the conventional SLF4J
 *   trailing-`Throwable` shape, see above).
 * - A message literal built by concatenation/interpolation instead of a
 *   plain string constant -- out of v0.1 scope, same reasoning as
 *   `sql-concatenation-companion`'s documented scope limits: reliably
 *   counting placeholders requires the message to be a literal the
 *   scanner can read directly, not an expression it would need to
 *   evaluate.
 */
object LogFormatScanner {

    // --- Any logging call: log.info("...", arg1, arg2, ...) ---------------
    // One call shape covers both conventions -- SLF4J (Java/Kotlin) and
    // Python's `%`-style all use "receiver.level(message, args...)".
    // Which *style* of placeholder a given call uses is decided per-match
    // from the message literal's own content (see [classifyStyle]), never
    // from the file extension -- same "hand-rolled over plain text,
    // language-agnostic" principle as the rest of this catalog.
    private val STRING_LITERAL = """"(?:[^"\\]|\\.)*""""
    private val LOG_CALL = Regex(
        """\b([A-Za-z_][A-Za-z0-9_]*)\s*\.\s*(trace|debug|info|warn|warning|error)\s*\(\s*($STRING_LITERAL)\s*(,\s*(?:[^()]|\([^()]*\))*)?\)"""
    )
    private val BRACE_PLACEHOLDER = Regex("""\{}""")

    // %s, %d, %r, %f, etc. -- excludes a literal "%%" escape.
    private val PERCENT_PLACEHOLDER = Regex("""(?<!%)%[sdrfxXoeEgG]""")

    fun scan(text: String): List<LogFormatMatch> {
        val results = mutableListOf<LogFormatMatch>()
        for (match in LOG_CALL.findAll(text)) {
            val receiver = match.groups[1]!!.value
            if (!LogSignalNames.looksLikeLoggerReceiverName(receiver)) continue

            val literal = match.groups[3]!!
            val argsGroup = match.groups[4]
            val args = splitArguments(argsGroup?.value?.removePrefix(",") ?: "")
            val argumentCount = args.size

            val bracePlaceholders = BRACE_PLACEHOLDER.findAll(literal.value).count()
            val percentPlaceholders = PERCENT_PLACEHOLDER.findAll(literal.value).count()

            // A literal using both placeholder styles at once (or an
            // f-string with no `{}`/`%x` markers of either kind, e.g.
            // `f"User {user_id}"`) isn't reliably one style or the
            // other -- skip rather than guess, same conservative
            // principle as the Throwable check below.
            val callMatch = when {
                bracePlaceholders > 0 && percentPlaceholders == 0 ->
                    scoreSlf4j(literal, bracePlaceholders, argumentCount, args.lastOrNull())
                percentPlaceholders > 0 && bracePlaceholders == 0 ->
                    scorePythonPercent(literal, percentPlaceholders, argumentCount)
                bracePlaceholders == 0 && percentPlaceholders == 0 ->
                    // No placeholders at all -- only a real mismatch if
                    // there are extra arguments; can't tell SLF4J from
                    // percent-style with zero markers, but the SLF4J
                    // trailing-Throwable exemption still applies (a
                    // logger call with exactly one trailing argument and
                    // zero placeholders is the plain `log.error(msg, ex)`
                    // idiom, valid in both conventions).
                    scoreSlf4j(literal, 0, argumentCount, args.lastOrNull())
                else -> null
            }
            if (callMatch != null) results += callMatch
        }
        return results.sortedBy { it.startOffset }
    }

    private fun scoreSlf4j(
        literal: MatchGroup,
        placeholderCount: Int,
        argumentCount: Int,
        lastArgumentText: String?,
    ): LogFormatMatch? {
        val mismatch = classifyMismatch(
            placeholderCount = placeholderCount,
            argumentCount = argumentCount,
            lastArgumentText = lastArgumentText,
        ) ?: return null

        return LogFormatMatch(
            kind = LogFormatKind.SLF4J_BRACE_PLACEHOLDER,
            mismatchKind = mismatch,
            startOffset = literal.range.first,
            endOffset = literal.range.last + 1,
            placeholderCount = placeholderCount,
            argumentCount = argumentCount,
        )
    }

    private fun scorePythonPercent(
        literal: MatchGroup,
        placeholderCount: Int,
        argumentCount: Int,
    ): LogFormatMatch? {
        // Python logging has no Throwable-as-trailing-argument convention
        // (exceptions go through `exc_info=True` or `logger.exception(...)`,
        // both keyword/method-choice based, never an extra positional
        // argument) -- so, unlike SLF4J, an excess of exactly 1 argument
        // here IS a real mismatch, not a special case to exempt.
        val mismatch = when {
            placeholderCount == argumentCount -> null
            argumentCount > placeholderCount -> LogFormatMismatchKind.TOO_FEW_PLACEHOLDERS
            else -> LogFormatMismatchKind.TOO_MANY_PLACEHOLDERS
        } ?: return null

        return LogFormatMatch(
            kind = LogFormatKind.PYTHON_PERCENT_STYLE,
            mismatchKind = mismatch,
            startOffset = literal.range.first,
            endOffset = literal.range.last + 1,
            placeholderCount = placeholderCount,
            argumentCount = argumentCount,
        )
    }

    /**
     * Applies the SLF4J counting rules and returns the mismatch kind, or
     * null when this is not a real mismatch (including the conservative
     * "don't know for sure" cases -- see class doc).
     */
    private fun classifyMismatch(
        placeholderCount: Int,
        argumentCount: Int,
        lastArgumentText: String?,
    ): LogFormatMismatchKind? {
        if (placeholderCount == 0 && argumentCount == 0) return null
        if (placeholderCount == argumentCount) return null

        if (argumentCount > placeholderCount) {
            val excess = argumentCount - placeholderCount
            // Exactly one excess argument is the conventional trailing-
            // Throwable shape -- never flagged, per SLF4J's real runtime
            // behavior, unless we have positive evidence the last
            // argument is clearly NOT an exception (a bare string/number
            // literal can't be a Throwable) -- still conservative: an
            // unresolvable variable reference is left alone.
            if (excess == 1) {
                return if (lastArgumentText != null && isDefinitelyNotThrowable(lastArgumentText)) {
                    LogFormatMismatchKind.TOO_FEW_PLACEHOLDERS
                } else {
                    null
                }
            }
            return LogFormatMismatchKind.TOO_FEW_PLACEHOLDERS
        }

        return LogFormatMismatchKind.TOO_MANY_PLACEHOLDERS
    }

    /**
     * Best-effort, syntax-only check for the rare case where the last
     * argument's text makes it unambiguous that it cannot be a
     * `Throwable` -- a string/char/numeric/boolean literal. Anything
     * else (an identifier, a method call, a `new`/constructor
     * expression) is left alone: without real type resolution there's no
     * reliable way to tell a `String` variable from an `Exception`
     * variable by name alone, and the conservative choice (see class
     * doc) is a false negative, never a false positive, in that case.
     */
    private fun isDefinitelyNotThrowable(argumentText: String): Boolean {
        val trimmed = argumentText.trim()
        if (trimmed.isEmpty()) return false
        if (trimmed.startsWith("\"") || trimmed.startsWith("'")) return true
        if (trimmed == "true" || trimmed == "false" || trimmed == "null") return true
        return trimmed.toDoubleOrNull() != null
    }

    /**
     * Splits a raw trailing-arguments string on top-level commas only --
     * a comma inside a nested call's parentheses (`foo(a, b)`) does not
     * split. Deliberately simple: this whole scanner works on
     * expression *shape*, not resolved types, same principle as
     * `sql-concatenation-companion`.
     */
    private fun splitArguments(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()

        val parts = mutableListOf<String>()
        var depth = 0
        var start = 0
        for (i in trimmed.indices) {
            when (trimmed[i]) {
                '(' -> depth++
                ')' -> depth--
                ',' -> if (depth == 0) {
                    parts += trimmed.substring(start, i)
                    start = i + 1
                }
            }
        }
        parts += trimmed.substring(start)
        return parts.map { it.trim() }.filter { it.isNotEmpty() }
    }
}
