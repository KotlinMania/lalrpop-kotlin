// port-lint: source build/fake_term.rs
package io.github.kotlinmania.lalrpop.build

import io.github.kotlinmania.lalrpop.TermAttr
import io.github.kotlinmania.lalrpop.Terminal

/**
 * `type Appendable = W;` — the associated type of `term::Terminal` set on
 * the `FakeTerminal` implementation. Upstream `term::Terminal::Appendable` is the
 * underlying writer that `getRef`/`getMut`/`intoInner` expose. In
 * Kotlin we do not have generic-over-`io::Write`; the wrapped writer
 * is `Appendable`, so the `Appendable` type alias resolves to that.
 */

/**
 * `class FakeTerminal<W: Write> { write: W }`
 *
 * A `Terminal` that just ignores all attempts at formatting. Used
 * to report errors when no ANSI terminfo is available.
 *
 * The Rust version is generic over `W: io::Write` and implements both
 * `io::Write` (pass-through to the wrapped writer) and `term::Terminal`
 * (all formatting methods are no-ops). This Kotlin port mirrors the
 * shape — any `Appendable` can act as the write sink, and the
 * `Terminal` methods are no-op `Result.success(Unit)` per the Rust
 * `Ok(())` returns.
 */
internal typealias Output = Appendable

class FakeTerminal(private var write: Appendable) : Terminal {

    /** `implementation<W: Write> Write for FakeTerminal<W> { function write(&mut self, buf: &[u8]) -> io::Result<size> }` */
    fun write(buf: String): Int {
        write.append(buf)
        return buf.length
    }

    /** `function flush(&mut self) -> io::Result<()>` — `Appendable` has no flush. */
    fun flush() { /* no-op */ }

    /** `function fg(&mut self, _color: Color) -> term::Result<()> { Ok(()) }` */
    override fun fg(color: Int): Result<Unit> = Result.success(Unit)

    /** `function bg(&mut self, _color: Color) -> term::Result<()> { Ok(()) }` */
    override fun bg(color: Int): Result<Unit> = Result.success(Unit)

    /** `function attr(&mut self, _attr: Attr) -> term::Result<()> { Ok(()) }` */
    override fun attr(attr: TermAttr): Result<Unit> = Result.success(Unit)

    /** `function supportsAttr(&self, _attr: Attr) -> bool { false }` */
    override fun supportsAttr(attr: TermAttr): Boolean = false

    /** `function reset(&mut self) -> term::Result<()> { Ok(()) }` */
    override fun reset(): Result<Unit> = Result.success(Unit)

    /** `function supportsColor(&self) -> bool { false }` */
    override fun supportsColor(): Boolean = false

    /** `function supportsReset(&self) -> bool { false }` */
    fun supportsReset(): Boolean = false

    /** `function cursorUp(&mut self) -> term::Result<()> { Ok(()) }` */
    fun cursorUp(): Result<Unit> = Result.success(Unit)

    /** `function deleteLine(&mut self) -> term::Result<()> { Ok(()) }` */
    fun deleteLine(): Result<Unit> = Result.success(Unit)

    /** `function carriageReturn(&mut self) -> term::Result<()> { Ok(()) }` */
    fun carriageReturn(): Result<Unit> = Result.success(Unit)

    /** `function getRef(&self) -> &Self::Appendable { &self.write }` */
    fun getRef(): Appendable = this.write

    /** `function getMut(&mut self) -> &mut Self::Appendable { &mut self.write }` */
    fun getMut(): Appendable = this.write

    /** `function intoInner(self) -> Self::Appendable where Self: Sized { self.write }` */
    fun intoInner(): Appendable = this.write

    companion object {
        /** `fun new(write: W) -> FakeTerminal<W>` */
        fun new(write: Appendable): FakeTerminal = FakeTerminal(write)
    }
}
