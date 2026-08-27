package dev.gaphunter.logformatstringcompanion.inspection

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import dev.gaphunter.logformatstringcompanion.detect.LogFormatMismatchKind
import dev.gaphunter.logformatstringcompanion.detect.LogFormatScanner
import dev.gaphunter.logformatstringcompanion.review.ReviewPrompt

/**
 * Flags a log message whose placeholder count doesn't match its
 * argument count -- see [LogFormatScanner] for the full detection
 * heuristic (SLF4J brace placeholders in Java/Kotlin, `%`-style
 * placeholders in Python, and the SLF4J trailing-`Throwable` special
 * case).
 *
 * Same shape as `sql-concatenation-companion`'s
 * `SqlConcatenationInspection`: `checkFile` (whole-document regex scan)
 * rather than `buildVisitor`, because detection is plain-text, not a
 * PSI walk of one specific language grammar -- registered in
 * `plugin.xml` without a `language` filter so it runs against Java,
 * Kotlin, and Python files alike without a per-ecosystem PSI
 * dependency (see `build.gradle.kts`).
 */
class LogFormatStringInspection : LocalInspectionTool() {

    companion object {
        /** Files larger than this are skipped -- avoids pathological regex cost on generated/minified files. */
        const val MAX_FILE_LENGTH = 500_000
    }

    override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {
        val text = file.text
        if (text.length > MAX_FILE_LENGTH) return null

        val matches = LogFormatScanner.scan(text)
        if (matches.isEmpty()) return null

        val problems = mutableListOf<ProblemDescriptor>()
        for (match in matches) {
            val anchor = leafElementAt(file, match.startOffset) ?: continue
            val anchorStart = anchor.textRange.startOffset
            val anchorEnd = anchorStart + anchor.textLength

            val clampedEnd = match.endOffset.coerceAtMost(anchorEnd)
            if (clampedEnd <= anchorStart) continue
            val relativeStart = (match.startOffset - anchorStart).coerceAtLeast(0)
            val relativeRange = TextRange(relativeStart, clampedEnd - anchorStart)
            if (relativeRange.startOffset >= relativeRange.endOffset) continue

            val message = when (match.mismatchKind) {
                LogFormatMismatchKind.TOO_FEW_PLACEHOLDERS ->
                    "Log message has ${match.placeholderCount} placeholder(s) but ${match.argumentCount}" +
                        " argument(s) -- the extra argument(s) will be silently ignored"
                LogFormatMismatchKind.TOO_MANY_PLACEHOLDERS ->
                    "Log message has ${match.placeholderCount} placeholder(s) but only ${match.argumentCount}" +
                        " argument(s) -- the extra placeholder(s) will stay literal in the log output"
            }

            problems += manager.createProblemDescriptor(
                anchor,
                relativeRange,
                message,
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                isOnTheFly,
            )

            val path = file.virtualFile?.path
            if (path != null) {
                val lineNumber = file.viewProvider.document?.getLineNumber(match.startOffset) ?: -1
                ReviewPrompt.recordHit(file.project, "$path:$lineNumber")
            }
        }

        return if (problems.isEmpty()) null else problems.toTypedArray()
    }

    /**
     * Resolves a leaf PSI element covering [startOffset] -- never a
     * composite node (a `LineMarkerInfo`/problem anchor on a composite
     * node causes real platform issues). Walks down to `firstChild`
     * until a true leaf is reached.
     */
    private fun leafElementAt(file: PsiFile, startOffset: Int): PsiElement? {
        if (startOffset < 0 || startOffset >= file.textLength) return null
        var element = file.findElementAt(startOffset) ?: return file
        while (element.firstChild != null) {
            element = element.firstChild
        }
        return element
    }
}
