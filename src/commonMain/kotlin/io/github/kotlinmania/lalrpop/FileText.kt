// port-lint: source file_text.rs
package io.github.kotlinmania.lalrpop

/*
 * Copyright 2015-2025 The LALRPOP Project Developers.
 * Copyright (c) 2026 Sydney Renee, The Solace Project (Kotlin port).
 *
 * Licensed under either of
 *   - Apache License, Version 2.0
 *     (https://www.apache.org/licenses/LICENSE-2.0)
 *   - MIT license
 *     (https://opensource.org/licenses/MIT)
 * at your option.
 */

import io.github.kotlinmania.lalrpop.build.apiBuildReadFileToString
import io.github.kotlinmania.lalrpop.grammar.parsetree.Span

class FileText(
    private val path: String,
    private val inputStr: String,
    private val newlines: List<Int>,
) {
    companion object {
        fun fromPath(path: String): Result<FileText> = runCatching {
            val inputStr = apiBuildReadFileToString(path)
            new(path, inputStr)
        }

        fun new(path: String, inputStr: String): FileText {
            val newlineIndices: List<Int> = buildList {
                add(0)
                for ((i, ch) in inputStr.withIndex()) {
                    if (ch == '\n') {
                        add(i + 1) // index of first char in the line
                    }
                }
            }

            return FileText(
                path = path,
                inputStr = inputStr,
                newlines = newlineIndices,
            )
        }

        fun test(): FileText = new("test.lalrpop", "")
    }

    fun text(): String = inputStr

    fun spanStr(span: Span): String {
        val (startLine, startCol) = lineCol(span.start)
        val (endLine, endCol) = lineCol(span.end)
        return "$path:${startLine + 1}:${startCol + 1}: ${endLine + 1}:$endCol"
    }

    fun lineCol(pos: Int): Pair<Int, Int> {
        val numLines = newlines.size
        val nextLine = newlines.indexOfFirst { it > pos }
        val line = if (nextLine >= 0) nextLine - 1 else numLines - 1

        // offset of the first character in `line`
        val lineOffset = newlines[line]

        // find the column; use `saturatingSub` in case `pos` is the
        // newline itself, which we will call column 0
        val col = pos - lineOffset

        return Pair(line, col)
    }

    private fun lineText(lineNum: Int): String {
        val startOffset = newlines[lineNum]
        return if (lineNum == newlines.size - 1) {
            inputStr.substring(startOffset)
        } else {
            val endOffset = newlines[lineNum + 1]
            inputStr.substring(startOffset, endOffset - 1)
        }
    }

    fun spanText(span: Span): String = inputStr.substring(span.start, span.end)

    fun highlight(span: Span, out: StringBuilder) {
        val (startLine, startCol) = lineCol(span.start)
        val (endLine, endCol) = lineCol(span.end)

        // (*) use `saturatingSub` since the start line could be the newline
        // itself, in which case we will call it column zero

        // span is within one line:
        if (startLine == endLine) {
            val text = lineText(startLine)
            out.appendLine("  $text")

            if (endCol - startCol <= 1) {
                out.appendLine("  " + " ".repeat(startCol) + "^")
            } else {
                val width = endCol - startCol
                out.appendLine("  " + " ".repeat(startCol) + "~" + "~".repeat((width - 2).coerceAtLeast(0)) + "~")
            }
        } else {
            // span is across many lines, find the maximal width of any of those
            val lineStrs: List<String> = (startLine..endLine).map { i -> lineText(i) }
            val maxLen = lineStrs.maxOf { it.length }
            out.appendLine("  " + " ".repeat(startCol) + "~".repeat((maxLen - startCol).coerceAtLeast(0)) + "~+")
            for (line in lineStrs.subList(0, lineStrs.size - 1)) {
                out.appendLine("| ${line.padEnd(maxLen)} |")
            }
            out.appendLine("| ${lineStrs[lineStrs.size - 1]}")
            out.appendLine("+~" + "~".repeat(endCol))
        }
    }
}
