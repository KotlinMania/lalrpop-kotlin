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

internal class FileText private constructor(
    val path: String,
    val inputStr: String,
    val newlines: List<Int>,
    private val inputBytes: ByteArray,
) {
    constructor(
        path: String,
        inputStr: String,
        newlines: List<Int>,
    ) : this(
        path = path,
        inputStr = inputStr,
        newlines = newlines,
        inputBytes = inputStr.encodeToByteArray(),
    )

    fun text(): String = this.inputStr

    fun spanStr(span: Span): String {
        val (startLine, startCol) = this.lineCol(span.start)
        val (endLine, endCol) = this.lineCol(span.end)
        return "${this.path}:${startLine + 1}:${startCol + 1}: ${endLine + 1}:$endCol"
    }

    fun lineCol(pos: Int): Pair<Int, Int> {
        val numLines = this.newlines.size
        val line =
            (0 until numLines)
                .asSequence()
                .filter { i -> this.newlines[i] > pos }
                .map { i -> i - 1 }
                .firstOrNull() ?: (numLines - 1)
        // offset of the first character in `line`
        val lineOffset = this.newlines[line]
        // find the column; use `saturatingSub` in case `pos` is the
        // newline itself, which we'll call column 0
        val col = pos - lineOffset
        return Pair(line, col)
    }

    fun lineText(lineNum: Int): String {
        val startOffset = this.newlines[lineNum]
        return if (lineNum == this.newlines.size - 1) {
            this.utf8Slice(startOffset, this.inputBytes.size)
        } else {
            val endOffset = this.newlines[lineNum + 1]
            this.utf8Slice(startOffset, endOffset - 1)
        }
    }

    fun spanText(span: Span): String = this.utf8Slice(span.start, span.end)

    fun highlight(span: Span, out: Appendable) {
        val (startLine, startCol) = this.lineCol(span.start)
        val (endLine, endCol) = this.lineCol(span.end)
        // (*) use `saturatingSub` since the start line could be the newline
        // itself, in which case we'll call it column zero
        // span is within one line:
        if (startLine == endLine) {
            val text = this.lineText(startLine)
            out.appendLine("  $text")
            if (endCol - startCol <= 1) {
                out.appendLine("  ${Repeat(' ', startCol)}^")
            } else {
                val width = endCol - startCol
                out.appendLine("  ${Repeat(' ', startCol)}~${Repeat('~', (width - 2).coerceAtLeast(0))}~")
            }
        } else {
            // span is across many lines, find the maximal width of any of those
            val lineStrs: List<String> = (startLine..endLine).map { i -> this.lineText(i) }
            val maxLen = lineStrs.maxOf { l -> l.encodeToByteArray().size }
            out.appendLine("  ${Repeat(' ', startCol)}${Repeat('~', maxLen - startCol)}~+")
            for (line in lineStrs.subList(0, lineStrs.size - 1)) {
                out.appendLine("| ${padRightScalars(line, maxLen)} |")
            }
            out.appendLine("| ${lineStrs[lineStrs.size - 1]}")
            out.appendLine("+~${Repeat('~', endCol)}")
        }
    }

    private fun utf8Slice(start: Int, end: Int): String {
        if (start < 0 || end < start || end > this.inputBytes.size) {
            throw IndexOutOfBoundsException(
                "UTF-8 byte span $start..$end out of bounds for length ${this.inputBytes.size}",
            )
        }
        val validStart =
            start == 0 ||
                start == this.inputBytes.size ||
                (this.inputBytes[start].toInt() and 0xC0) != 0x80
        val validEnd =
            end == 0 ||
                end == this.inputBytes.size ||
                (this.inputBytes[end].toInt() and 0xC0) != 0x80
        if (!validStart || !validEnd) {
            throw IllegalArgumentException("UTF-8 byte span $start..$end is not on a char boundary")
        }
        return this.inputBytes.decodeToString(startIndex = start, endIndex = end, throwOnInvalidSequence = true)
    }

    companion object {
        fun fromPath(path: String): Result<FileText> =
            runCatching {
                val inputStr = apiBuildReadFileToString(path)
                FileText.new(path, inputStr)
            }

        fun new(path: String, inputStr: String): FileText {
            val inputBytes = inputStr.encodeToByteArray()
            val newlineIndices: List<Int> =
                run {
                    val inputIndices =
                        inputBytes
                            .asSequence()
                            .withIndex()
                            .filter { (_, b) -> b == '\n'.code.toByte() }
                            .map { (i, _) -> i + 1 }
                    // index of first char in the line
                    (sequenceOf(0) + inputIndices).toList()
                }
            return FileText(
                path = path,
                inputStr = inputStr,
                newlines = newlineIndices,
                inputBytes = inputBytes,
            )
        }

        fun test(): FileText = FileText.new("test.lalrpop", "")
    }
}

internal class Repeat(
    var field0: Char,
    var field1: Int,
) {
    fun fmt(fmt: Appendable) {
        repeat(this.field1) {
            fmt.append(this.field0)
        }
    }

    override fun toString(): String = buildString { fmt(this) }
}

private fun padRightScalars(s: String, width: Int): String {
    var scalarCount = 0
    var i = 0
    while (i < s.length) {
        i += if (s[i].isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate()) 2 else 1
        scalarCount += 1
    }
    val padCount = (width - scalarCount).coerceAtLeast(0)
    return if (padCount == 0) s else "$s${Repeat(' ', padCount)}"
}
