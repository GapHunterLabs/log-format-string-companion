<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# Log Format String Companion Changelog

## [Unreleased]

## [0.2.2]

### Fixed

- Review/star CTA now links to this plugin's own Marketplace
  reviews page instead of the vendor's generic plugin list.

## [0.2.1]

### Fixed

- Marketplace listing and README still labeled the documented scope
  gaps (Log4j2/structlog/Kotlin logging support, placeholder-type-
  mismatch detection) as "v0.1 scope" / "deferred to v0.2 Pro" --
  stale since 0.2.0 shipped as a free precision fix, not the described
  tier. The gaps themselves are still real and still free/unstarted;
  only the stale version framing is corrected.

## [0.2.0]

### Fixed

- The trailing-argument "definitely not a Throwable" check now
  recognizes numeric literals with a Java/Kotlin type suffix (`42L`,
  `1.5f`, `3.0d`, `100u`) -- previously only unsuffixed numbers were
  recognized, so `log.error("Failed for {}", userId, 42L)` was silently
  treated as "can't tell" instead of being flagged.

## [0.1.1]

### Added

- Review/star CTA: after 10 distinct real findings, a one-time
  notification asks whether to rate the plugin on Marketplace, with a
  permanent "Don't ask again" option. Standard mechanism used
  catalog-wide since 2026-08-24, rolled out
  to this plugin now.

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

[Unreleased]: https://github.com/GapHunterLabs/log-format-string-companion/compare/0.2.2...HEAD
[0.2.2]: https://github.com/GapHunterLabs/log-format-string-companion/compare/0.2.1...0.2.2
[0.2.1]: https://github.com/GapHunterLabs/log-format-string-companion/compare/0.2.0...0.2.1
[0.2.0]: https://github.com/GapHunterLabs/log-format-string-companion/compare/0.1.1...0.2.0
[0.1.1]: https://github.com/GapHunterLabs/log-format-string-companion/compare/0.1.0...0.1.1
[0.1.0]: https://github.com/GapHunterLabs/log-format-string-companion/commits/0.1.0
