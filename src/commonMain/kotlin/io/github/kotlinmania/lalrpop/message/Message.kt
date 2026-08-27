// port-lint: source message/message.rs
package io.github.kotlinmania.lalrpop.message

import io.github.kotlinmania.lalrpop.Style
import io.github.kotlinmania.lalrpop.grammar.parsetree.Span
import io.github.kotlinmania.lalrpop.tls.Tls
import io.github.kotlinmania.lalrpop.message.horiz.Horiz
import io.github.kotlinmania.lalrpop.message.message.Content
import io.github.kotlinmania.lalrpop.message.text.Text
import io.github.kotlinmania.lalrpop.message.vert.Vert

/**
 * The top-level message display like this:
 *
 * ```text
 * <span>: <heading>
 *
 * <body>
 * ```
 *
 * This is equivalent to a
 *
 * ```text
 * Vert[separate=2] {
 * Horiz[separate=1] {
 * Horiz[separate=0] {
 * Citation { span },
 * Text { ":" },
 * },
 * <heading>,
 * },
 * <body>
 * }
 * ```
 */
internal class Message private constructor(
    private val span: Span,
    private val heading: Content,
    private val body: Content,
) : Content {
    companion object {
        fun new(span: Span, heading: Content, body: Content): Message =
            Message(span = span, heading = heading, body = body)
    }

    override fun minWidth(): Int {
        val fileText = Tls.fileText()
        val span = fileText.spanStr(this.span).length
        val heading = this.heading.minWidth()
        val body = this.body.minWidth()
        return maxOf(span + heading + 2, body + 2)
    }

    override fun emit(view: AsciiView) {
        val session = Tls.session()
        val fileText = Tls.fileText()

        val span = fileText.spanStr(this.span)
        view.writeChars(0, 0, span.iterator(), Style.new())
        val count = span.length
        view.writeChars(0, count, ":".iterator(), Style.new())

        val (row, _) = this.heading
            .emitAt(view.styled(session.heading), 0, count + 2)

        this.body.emitAt(view, row + 2, 2)
    }

    override fun intoWrapItems(wrapItems: MutableList<Content>) {
        wrapItems.add(this)
    }

    fun fmt(fmt: StringBuilder) {
        fmt.append("Message { span: ").append(span)
            .append(", heading: ").append(heading.toString())
            .append(", body: ").append(body.toString())
            .append(" }")
    }

    override fun toString(): String = buildString { fmt(this) }
}
