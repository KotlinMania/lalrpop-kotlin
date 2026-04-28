// port-lint: source message/builder.rs
package io.github.kotlinmania.lalrpop.message.builder

import io.github.kotlinmania.lalrpop.Style
import io.github.kotlinmania.lalrpop.grammar.parseTree.Span
import io.github.kotlinmania.lalrpop.message.Message
import io.github.kotlinmania.lalrpop.message.horiz.Horiz
import io.github.kotlinmania.lalrpop.message.indent.Indent
import io.github.kotlinmania.lalrpop.message.message.Content
import io.github.kotlinmania.lalrpop.message.styled.Styled
import io.github.kotlinmania.lalrpop.message.text.Text
import io.github.kotlinmania.lalrpop.message.vert.Vert
import io.github.kotlinmania.lalrpop.message.wrap.Wrap

class MessageBuilder(
    private val span: Span,
    private var heading: Content? = null,
    private var body: Content? = null,
) {
    companion object {
        fun new(span: Span): MessageBuilder = MessageBuilder(
            span = span,
            heading = null,
            body = null,
        )
    }

    fun heading(): Builder<MessageBuilder> =
        Builder.new(HeadingCharacter(message = this))

    fun body(): Builder<MessageBuilder> =
        Builder.new(BodyCharacter(message = this))

    fun end(): Message = Message.new(
        this.span,
        checkNotNull(this.heading) { "never defined a heading" },
        checkNotNull(this.body) { "never defined a body" },
    )

    internal fun setHeading(content: Content) {
        check(this.heading == null) { "already defined a heading for this message" }
        this.heading = content
    }

    internal fun setBody(content: Content) {
        check(this.body == null) { "already defined a body for this message" }
        this.body = content
    }
}

class HeadingCharacter(internal val message: MessageBuilder) : Character<MessageBuilder> {
    override fun end(items: MutableList<Content>): MessageBuilder {
        this.message.setHeading(Vert.new(items, 1))
        return this.message
    }
}

class BodyCharacter(internal val message: MessageBuilder) : Character<MessageBuilder> {
    override fun end(items: MutableList<Content>): MessageBuilder {
        this.message.setBody(Vert.new(items, 2))
        return this.message
    }
}

// Inline builder: Useful for constructing little bits of content: for
// example, converting an Example into something renderable. Using an
// inline builder, if you push exactly one item, then when you call
// `end` that is what you get; otherwise, you get items laid out
// adjacent to one another horizontally (no spaces in between).

class InlineBuilder : Character<Content> {
    companion object {
        fun new(): Builder<Content> = Builder.new(InlineBuilder())
    }

    override fun end(items: MutableList<Content>): Content =
        if (items.size == 1) {
            items.removeLast()
        } else {
            Horiz.new(items, 1)
        }
}

// Builder -- generic helper for multi-part items

/**
 * The builder is used to construct multi-part items. It is intended
 * to be used in a "method-call chain" style. The base method is
 * called `push`, and it simply pushes a new child of the current
 * parent.
 *
 * Methods whose name like `beginFoo` are used to create a new
 * multi-part child; they return a fresh builder corresponding to the
 * child. When the child is completely constructed, call `end` to
 * finish the child builder and return to the parent builder.
 *
 * Methods whose name ends in "-ed", such as `styled`, post-process
 * the last item pushed. They will panic if invoked before any items
 * have been pushed.
 *
 * Example:
 *
 * ```noCompile
 * val node = InlineBuilder.new()
 *     .beginLines() // starts a child builder for adjacent lines
 *     .text("foo")   // add a text node "foo" to the child builder
 *     .text("bar")   // add a text node "bar" to the child builder
 *     .end()         // finish the lines builder, return to the parent
 *     .end()         // finish the parent [InlineBuilder], yielding up the
 *                    // `lines` child that was pushed (see [InlineBuilder]
 *                    // for more details)
 * ```
 */
class Builder<End>(
    private val items: MutableList<Content>,
    private val character: Character<End>,
) {
    companion object {
        internal fun <End> new(character: Character<End>): Builder<End> = Builder(
            items = mutableListOf(),
            character = character,
        )
    }

    fun push(item: Content): Builder<End> {
        this.items.add(item)
        return this
    }

    private fun pop(): Content? =
        if (this.items.isNotEmpty()) this.items.removeLast() else null

    fun beginVert(separate: Int): Builder<Builder<End>> =
        Builder.new(VertCharacter(base = this, separate = separate))

    fun beginLines(): Builder<Builder<End>> = beginVert(1)

    fun beginHoriz(separate: Int): Builder<Builder<End>> =
        Builder.new(HorizCharacter(base = this, separate = separate))

    // "item1item2"
    fun beginAdjacent(): Builder<Builder<End>> = beginHoriz(1)

    // "item1 item2"
    fun beginSpaced(): Builder<Builder<End>> = beginHoriz(2)

    fun beginWrap(): Builder<Builder<End>> =
        Builder.new(WrapCharacter(base = this))

    fun styled(style: Style): Builder<End> {
        val content = checkNotNull(pop()) { "bold must be applied to an item" }
        return push(Styled.new(style, content))
    }

    fun indentedBy(amount: Int): Builder<End> {
        val content = checkNotNull(pop()) { "indent must be applied to an item" }
        return push(Indent.new(amount, content))
    }

    fun indented(): Builder<End> = indentedBy(2)

    fun text(text: Any): Builder<End> =
        push(Text.new(text.toString()))

    /**
     * Take the item just pushed and makes some text adjacent to it.
     * E.g. `builder.wrap().text("foo").adjacentText(".").end()`
     * result in `"foo."` being printed without any wrapping in
     * between.
     */
    fun adjacentText(prefix: Any, suffix: Any): Builder<End> {
        val item = checkNotNull(pop()) { "adjacent text must be added to an item" }
        val prefixStr = prefix.toString()
        val suffixStr = suffix.toString()
        return if (prefixStr.isNotEmpty() && suffixStr.isNotEmpty()) {
            beginAdjacent()
                .text(prefixStr)
                .push(item)
                .text(suffixStr)
                .end()
        } else if (suffixStr.isNotEmpty()) {
            beginAdjacent().push(item).text(suffixStr).end()
        } else if (prefixStr.isNotEmpty()) {
            beginAdjacent().text(prefixStr).push(item).end()
        } else {
            push(item)
        }
    }

    fun verbatimed(): Builder<End> = adjacentText("`", "`")

    fun punctuated(text: Any): Builder<End> = adjacentText("", text)

    fun wrapText(text: Any): Builder<End> =
        beginWrap().text(text).end()

    fun end(): End = this.character.end(this.items)
}

interface Character<End> {
    fun end(items: MutableList<Content>): End
}

class HorizCharacter<C>(
    internal val base: Builder<C>,
    internal val separate: Int,
) : Character<Builder<C>> {
    override fun end(items: MutableList<Content>): Builder<C> =
        this.base.push(Horiz.new(items, this.separate))
}

class VertCharacter<C>(
    internal val base: Builder<C>,
    internal val separate: Int,
) : Character<Builder<C>> {
    override fun end(items: MutableList<Content>): Builder<C> =
        this.base.push(Vert.new(items, this.separate))
}

class WrapCharacter<C>(
    internal val base: Builder<C>,
) : Character<Builder<C>> {
    override fun end(items: MutableList<Content>): Builder<C> =
        this.base.push(Wrap.new(items))
}
