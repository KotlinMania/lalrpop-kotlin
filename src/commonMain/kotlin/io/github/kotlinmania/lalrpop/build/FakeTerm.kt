package io.github.kotlinmania.lalrpop.build

import io.github.kotlinmania.lalrpop.TermAttr
import io.github.kotlinmania.lalrpop.Terminal

/**
 * A `Terminal` that just ignores all attempts at formatting. Used
 * to report errors when no ANSI terminfo is available.
 */
internal interface Output : Appendable {
    fun flush(): Result<Unit>
}

internal class FakeTerminal private constructor(private var write: Output) : Terminal, Appendable {

    override fun append(value: CharSequence?): Appendable {
        write.append(value)
        return this
    }

    override fun append(value: CharSequence?, startIndex: Int, endIndex: Int): Appendable {
        write.append(value, startIndex, endIndex)
        return this
    }

    override fun append(value: Char): Appendable {
        write.append(value)
        return this
    }

    fun write(buf: String): Int {
        append(buf)
        return buf.length
    }

    fun flush(): Result<Unit> = write.flush()

    override fun fg(color: Int): Result<Unit> = Result.success(Unit)

    override fun bg(color: Int): Result<Unit> = Result.success(Unit)

    override fun attr(attr: TermAttr): Result<Unit> = Result.success(Unit)

    override fun supportsAttr(attr: TermAttr): Boolean = false

    override fun reset(): Result<Unit> = Result.success(Unit)

    override fun supportsColor(): Boolean = false

    override fun supportsReset(): Boolean = false

    override fun cursorUp(): Result<Unit> = Result.success(Unit)

    override fun deleteLine(): Result<Unit> = Result.success(Unit)

    override fun carriageReturn(): Result<Unit> = Result.success(Unit)

    fun getRef(): Output = this.write

    fun getMut(): Output = this.write

    fun intoInner(): Output = this.write

    companion object {
        fun new(write: Output): FakeTerminal = FakeTerminal(write)
    }
}
