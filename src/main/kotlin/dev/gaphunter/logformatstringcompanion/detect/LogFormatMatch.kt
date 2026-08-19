package dev.gaphunter.logformatstringcompanion.detect

/** Which source-language convention produced this match -- drives the message wording, nothing else. */
enum class LogFormatKind {
    /** SLF4J-style `log.info("User {} logged in", userId)` -- Java or Kotlin. */
    SLF4J_BRACE_PLACEHOLDER,

    /** Python `%`-style logging -- `logger.info("User %s logged in", user_id)`. */
    PYTHON_PERCENT_STYLE,
}

/** Which direction the mismatch goes -- drives the message wording. */
enum class LogFormatMismatchKind {
    /** Fewer placeholders than arguments -- one or more arguments are silently ignored. */
    TOO_FEW_PLACEHOLDERS,

    /** More placeholders than arguments -- one or more placeholders stay literal in the log output. */
    TOO_MANY_PLACEHOLDERS,
}

data class LogFormatMatch(
    val kind: LogFormatKind,
    val mismatchKind: LogFormatMismatchKind,
    /** Offset of the message string literal itself (the anchor for the highlighted range). */
    val startOffset: Int,
    val endOffset: Int,
    val placeholderCount: Int,
    val argumentCount: Int,
)
