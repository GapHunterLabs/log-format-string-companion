package dev.gaphunter.logformatstringcompanion.inspection

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * End-to-end: real PSI + real inspection registration via `myFixture` --
 * the matching logic itself is already covered exhaustively by
 * [dev.gaphunter.logformatstringcompanion.detect.LogFormatScannerTest].
 * This confirms the inspection actually fires real warnings against a
 * real highlighting pass, not just "doesn't crash".
 */
class LogFormatStringInspectionTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(LogFormatStringInspection::class.java)
    }

    fun `test slf4j fewer placeholders than arguments warns`() {
        myFixture.configureByText(
            "Demo.java",
            """
            class Demo {
                void run(org.slf4j.Logger log, String userId, String extraArg, String anotherExtra) {
                    log.info("User {} logged in", userId, extraArg, anotherExtra);
                }
            }
            """.trimIndent(),
        )

        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.any { it.description?.contains("placeholder") == true })
    }

    fun `test slf4j exact placeholder and argument match produces no warning`() {
        myFixture.configureByText(
            "Demo.java",
            """
            class Demo {
                void run(org.slf4j.Logger log, String userId, String ipAddress) {
                    log.info("User {} logged in from {}", userId, ipAddress);
                }
            }
            """.trimIndent(),
        )

        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("placeholder") == true })
    }

    fun `test slf4j trailing throwable argument produces no warning`() {
        myFixture.configureByText(
            "Demo.java",
            """
            class Demo {
                void run(org.slf4j.Logger log, String userId, Exception exception) {
                    log.error("Failed for {}", userId, exception);
                }
            }
            """.trimIndent(),
        )

        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("placeholder") == true })
    }

    fun `test log message with no placeholders and no arguments produces no warning`() {
        myFixture.configureByText(
            "Demo.java",
            """
            class Demo {
                void run(org.slf4j.Logger log) {
                    log.info("Server started");
                }
            }
            """.trimIndent(),
        )

        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("placeholder") == true })
    }

    fun `test unrelated receiver named builder is not treated as a logger`() {
        myFixture.configureByText(
            "Demo.java",
            """
            class Demo {
                void run(NotificationBuilder builder, String userId, String extraArg) {
                    builder.info("User {} logged in", userId, extraArg);
                }
            }
            interface NotificationBuilder {
                void info(String msg, Object... args);
            }
            """.trimIndent(),
        )

        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.none { it.description?.contains("placeholder") == true })
    }

    fun `test python percent style fewer placeholders than arguments warns`() {
        myFixture.configureByText(
            "demo.py",
            """
            logger.info("User %s logged in", user_id, extra_arg)
            """.trimIndent(),
        )

        val highlights = myFixture.doHighlighting()
        assertTrue(highlights.any { it.description?.contains("placeholder") == true })
    }
}
