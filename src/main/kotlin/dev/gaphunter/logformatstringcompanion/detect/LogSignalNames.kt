package dev.gaphunter.logformatstringcompanion.detect

/**
 * The plugin's actual anti-false-positive design, documented here (not
 * just in the README) because it's the part most likely to need tuning
 * later -- same role as `sql-concatenation-companion`'s `SqlSignalNames`
 * and `http-status-inline-companion`'s `HttpSignalNames`.
 *
 * A candidate call is only ever treated as a logging call when its
 * simple method name AND its receiver name both look like logging --
 * see [LogFormatScanner] for exactly how the two combine. Without the
 * receiver check, any unrelated `.info(...)`/`.warn(...)` call (a
 * builder, a notification API) would be treated as SLF4J-style logging.
 */
object LogSignalNames {

    /**
     * Method *simple* names (case-insensitive) that, when called on a
     * receiver that also looks like a logger (see
     * [looksLikeLoggerReceiverName]), are treated as an SLF4J-style
     * logging call. Deliberately name-based, not resolved-symbol-based
     * (same principle as `SqlSignalNames`/`HttpSignalNames`): this
     * plugin works the same whether the receiver is a real
     * `org.slf4j.Logger`, a facade wrapper, or a test double, because no
     * call is ever resolved to a specific type.
     *
     * Deliberately excludes `trace`/`fatal` framework variance beyond
     * the 4 SLF4J core levels plus `trace` -- kept to the handful of
     * names that are unambiguous across virtually every JVM logging
     * facade (SLF4J, java.util.logging wrappers, common home-grown
     * `Logger` classes).
     */
    private val SIGNAL_METHOD_NAMES = setOf(
        "trace",
        "debug",
        "info",
        "warn",
        "error",
    )

    /**
     * Exact receiver identifier names (case-insensitive) that make a
     * nearby `.info(...)`/`.warn(...)`/etc. call look like real logging,
     * not an unrelated method that happens to share a name (e.g. a
     * builder's `.info(...)`, a notification service's `.warn(...)`).
     * Matched as the *whole* identifier, not a substring -- unlike
     * `SqlSignalNames`'s receiver check, a substring match here would be
     * too permissive (many unrelated identifiers contain "log", e.g.
     * `catalog`, `dialog`).
     */
    private val SIGNAL_RECEIVER_NAMES = setOf(
        "log",
        "logger",
    )

    fun isSignalMethodName(simpleName: String): Boolean =
        simpleName.lowercase() in SIGNAL_METHOD_NAMES

    fun looksLikeLoggerReceiverName(identifier: String): Boolean =
        identifier.lowercase() in SIGNAL_RECEIVER_NAMES
}
