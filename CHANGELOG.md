<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Log Format String Companion Changelog

## [Unreleased]

## [0.1.0]

### Added

- **Inspection that flags a log message whose placeholder count doesn't
  match its argument count** -- an error that today only surfaces at
  runtime: a leftover placeholder stays literal in the log output, or an
  extra argument is silently dropped.
- **Patterns detected**: SLF4J-style brace placeholders in Java/Kotlin
  (`log.info("User {} logged in", userId)`, matched against `log`/
  `logger`-named receivers and `trace`/`debug`/`info`/`warn`/`error`
  method names) and Python `%`-style logging
  (`logger.info("User %s logged in", user_id)`).
- **SLF4J trailing-`Throwable` case handled correctly**: SLF4J treats a
  last argument as the exception to log, not a placeholder argument,
  when the call has exactly one more argument than placeholders --
  `log.error("Failed for {}", userId, exception)` (1 placeholder, 2
  arguments) is correct usage and is never flagged.
- **Conservative by design**: when the last argument's type can't be
  determined from its syntax alone (a bare identifier, a method call --
  no symbol resolution is performed), the plugin does not flag it, even
  when that hides a real one-argument-too-many bug -- a missed warning
  beats a false alarm here.
- **Python f-strings deliberately out of scope**: an f-string's
  interpolations are inline expressions, not separate call arguments, so
  there's no placeholder-count-vs-argument-count mismatch to detect in
  the same shape as SLF4J/percent-style logging -- documented honestly
  in the README rather than silently mismatched.
- 100% static text analysis of files already open in the project -- no
  network call, no external process spawned.

[Unreleased]: https://github.com/GapHunterLabs/log-format-string-companion/compare/0.1.0...HEAD
[0.1.0]: https://github.com/GapHunterLabs/log-format-string-companion/commits/0.1.0
