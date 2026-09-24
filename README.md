# Log Format String Companion

IntelliJ-family plugin. Flags a **log message whose placeholder count
doesn't match its argument count** — an error that today only surfaces
at runtime: a leftover placeholder stays literal in the log output, or
an extra argument is silently dropped, or (worst case) SLF4J
misinterprets the last argument as an exception when it isn't one.
Deliberately narrow (not a full SAST scanner like Qodana): one specific
pattern, near-zero configuration, tuned hard against false positives.

## Why it exists

An original idea, not a port of an existing competitor — validated
against this catalog's own idea-validation discipline before
being built: (1) confirmed no plugin in this catalog does exactly this,
and a search for "log format" only surfaces MyBatis output formatters —
an unrelated tool, not a placeholder/argument mismatch inspector; (2)
confirmed buildable in the ~10-day budget with techniques this catalog
already has proven — `LocalInspectionTool.checkFile` doing a whole-
document regex scan, the exact mechanism `sql-concatenation-companion`
already proved in this catalog (reused directly, not reinvented). Same
"apuesta consciente sin ancla de mercado" treatment as every other
originally-generated idea in this catalog: v0.1 ships free, no
time/marketing investment disproportionate to real demand signal until
there's evidence of adoption.

## Detection heuristic (the actual anti-false-positive design)

**Two conditions, both required — see
[`LogSignalNames.kt`](src/main/kotlin/dev/gaphunter/logformatstringcompanion/detect/LogSignalNames.kt)
and
[`LogFormatScanner.kt`](src/main/kotlin/dev/gaphunter/logformatstringcompanion/detect/LogFormatScanner.kt)
for the real code, same discipline already proven by
`sql-concatenation-companion`'s `SqlSignalNames`:**

1. **Shape**: a call whose simple method name is `trace`/`debug`/`info`/
   `warn`/`warning`/`error` and whose receiver name is `log`/`logger`
   (case-insensitive, exact match — not a substring, deliberately
   stricter than `SqlSignalNames`'s receiver check, since many unrelated
   identifiers contain "log" as a substring, e.g. `catalog`, `dialog`),
   called with a string literal first argument.
2. **Counting**: the number of placeholder markers found *inside* that
   string literal — `{}` for SLF4J-style, or a `%s`/`%d`/`%r`/`%f`/etc.
   conversion specifier for Python `%`-style — compared against the
   number of comma-separated trailing arguments.

### What DOES trigger it

```java
// Java/Kotlin, SLF4J-style: 1 placeholder, 2 arguments -- the second is silently ignored.
log.info("User {} logged in", userId, extraArg);

// 2 placeholders, 1 argument -- the second "{}" stays literal in the log line.
log.info("User {} logged in from {}", userId);
```

```python
# Python, %-style logging: same shape.
logger.info("User %s logged in", user_id, extra_arg)
logger.info("User %s logged in from %s", user_id)
```

### What does NOT trigger it

```java
// Exact match -- correct usage.
log.info("User {} logged in from {}", userId, ipAddress);

// No placeholders, no extra arguments -- correct usage.
log.info("Server started");
```

```java
// A receiver name that isn't "log"/"logger" -- an unrelated .info(...)
// call (a builder, a notification API) is never treated as logging.
builder.info("User {} logged in", userId, extraArg);
```

## The SLF4J trailing-`Throwable` case — handled correctly

**This is the case that matters most, and getting it wrong in either
direction is a real bug in this plugin, not just a missed edge case.**
SLF4J's real API treats a *last* argument as the exception to log, not
a placeholder argument, whenever the call has exactly one more argument
than placeholders:

```java
// 1 placeholder, 2 arguments -- CORRECT, never flagged. SLF4J logs the
// exception's stack trace using its dedicated (String, Object[],
// Throwable) overload; "exception" is never treated as {}'s value.
log.error("Failed for {}", userId, exception);
```

The plugin only ever flags a "too few placeholders" mismatch when the
argument count exceeds the placeholder count by **more than one** — an
excess of exactly one is always treated as the conventional trailing-
exception shape:

```java
// 1 placeholder, 3 arguments -- excess of TWO, unambiguous, flagged
// regardless of what the last argument looks like.
log.error("Failed for {}", userId, extraArg, exception);
```

**Conservative by design when the excess is exactly one:** the plugin
never resolves symbols to real types (same "name-based, not resolved-
symbol-based" principle as `SqlSignalNames`/`HttpSignalNames`), so most
of the time it genuinely cannot tell whether a single trailing argument
is a `Throwable` variable or a `String` variable with the same kind of
identifier name. Rather than risk a false positive on the extremely
common, correct `log.error("...", arg, ex)` idiom, the plugin **stays
silent** in that ambiguous case — a missed real bug is accepted in
trade for never crying wolf on working code. It only overrides that
silence when the last argument's *syntax* makes it unambiguous that it
cannot be a `Throwable` — a string/char/numeric/boolean literal:

```java
// The last argument is a string literal -- syntactically impossible to
// be a Throwable, so this IS flagged as a real mismatch.
log.error("Failed for {}", userId, "not an exception");
```

## Why Python f-strings are out of scope (a real scoping decision, not an oversight)

The brief for this plugin asked explicitly whether Python f-string
logging (`logger.info(f"User {user_id} logged in")`) fits the same
placeholder-count-vs-argument-count shape as SLF4J. **It does not, and
this plugin says so honestly instead of forcing a mismatched check onto
it:**

- In SLF4J and Python `%`-style logging, the placeholders live in the
  message *template* and the values live in *separate call arguments* —
  two independent things that can legitimately drift out of sync,
  which is exactly the bug this plugin exists to catch.
- In an f-string, the "placeholder" and its value are the same token —
  `{user_id}` is interpolated inline, there is no separate argument to
  count against it. There is no way to pass "the wrong number of
  values" to an f-string; the language doesn't allow that shape of bug
  to exist. Scanning f-strings for a placeholder/argument mismatch
  would be checking for something that cannot happen.
- Python's real logging footgun with f-strings is a different, unrelated
  problem (eager string formatting on every call regardless of the
  configured log level, a performance/laziness concern, not a
  correctness mismatch) — out of this plugin's scope, which is
  specifically the placeholder-count bug described in the brief.

Python's `%`-style logging (`logger.info("User %s logged in", user_id)`)
**does** share the exact same shape as SLF4J and is fully supported in
v0.1.

## Known, documented limitations

- **Plain-text scanning, not a real per-language lexer** (same class of
  limitation as `sql-concatenation-companion`'s scanner): a string
  literal whose *text* happens to contain `{}` or `%s` as literal prose
  (not a real placeholder) could in theory be miscounted. In practice
  this is rare for a logging call's own message string.
- **Message literal must be a plain string constant.** A message built
  by concatenation or interpolation instead of a literal is out of v0.1
  scope — reliably counting placeholders requires reading the literal
  text directly, not evaluating an expression.
- **The trailing-`Throwable` exemption is syntax-only, not real type
  resolution** (see above) — deliberately conservative, a missed real
  bug is accepted over a false positive on the common correct case.
  Recognizes string/char/boolean/numeric literals, including a
  Java/Kotlin type suffix (`42L`, `1.5f`, `3.0d`, `100u`) — an
  identifier, method call, or constructor expression is still left
  alone.
- **Receiver name matching is exact (`log`/`logger`), not substring.**
  A logger stored in a differently-named variable (e.g. `LOG`, `logr`)
  is matched case-insensitively for the exact names `log`/`logger`
  only — a project-specific unconventional name won't be picked up.
  Deliberately stricter than `SqlSignalNames`'s substring receiver
  check, since common unrelated identifiers (`catalog`, `dialog`)
  contain "log" as a substring.

## Scope

Free, all of it — no paywall, nothing held back for a future tier, and
**no market anchor** (no confirmed paying competitor with real
complaints in this exact niche). Treated with the same discipline as every other
originally-generated idea in this catalog: no disproportionate time or
marketing investment before real adoption signal. (0.2.0 was a free
precision fix to the existing trailing-argument check, not a scope
expansion — the gaps below are still real.)

Deferred, not started, not promised:
- Support for more logging frameworks/conventions — Log4j2, structlog,
  Kotlin logging (`KotlinLogging`/`mu.KLogger`).
- Detection of placeholder *type* mismatches (not just count) — e.g. a
  `%d` specifier given a string-typed argument.

## Why built this way

- **Plain-text/regex detection, not per-language PSI.** Reuses the
  exact "hand-rolled over plain text" principle already proven by
  `sql-concatenation-companion`: no Python PSI dependency needed (not
  guaranteed present in every IntelliJ Platform edition this catalog
  targets), and the inspection runs against Java/Kotlin/Python alike
  without a per-ecosystem PSI grammar.
- **`LocalInspectionTool.checkFile`, not `buildVisitor`.** Detection is
  a whole-document regex scan, not a PSI-node-by-PSI-node walk of one
  specific language grammar — the right fit for a check that spans
  multiple languages with different grammars, and what lets the
  inspection apply without a `language` filter in `plugin.xml`.
- **Leaf PSI anchoring for each `ProblemDescriptor`.** A
  `ProblemDescriptor` anchored on a composite PSI node instead of a
  real leaf token is a documented platform gotcha — this inspection
  always walks down to a true leaf element before creating a
  descriptor.
- **One shared call-matching regex for both placeholder conventions**,
  deciding SLF4J-vs-Python style from the message literal's own content
  (which marker it actually contains) rather than from the file
  extension — the same call shape (`receiver.level(message, args...)`)
  is genuinely used by both conventions, so matching it once and then
  classifying is simpler and more robust than duplicating the whole
  call pattern per style.

## Enterprise / Team Licensing

Need enterprise features, custom rules, or team licensing? Contact us
at **gaphunterlabs@gmail.com**.

## Development

```
./gradlew test           # unit tests
./gradlew buildPlugin    # generates build/distributions/*.zip
./gradlew verifyPlugin   # checks compatibility against real IDEs
```

## License

Apache-2.0. See `LICENSE`.
