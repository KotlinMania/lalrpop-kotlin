// port-lint: source util.rs
package io.github.kotlinmania.lalrpop

internal class Sep<S>(
    var field0: String,
    var field1: Iterable<S>,
) {
    operator fun component1(): String = field0

    operator fun component2(): Iterable<S> = field1

    fun fmt(fmt: Appendable) {
        val (sep, vec) = this
        val elems = vec.iterator()
        if (elems.hasNext()) {
            val elem = elems.next()
            fmt.append("$elem")
            for (elem1 in elems) {
                fmt.append("$sep$elem1")
            }
        }
    }

    override fun toString(): String = buildString { fmt(this) }
}

internal class Escape<S>(
    var value: S,
) {
    var field0: S
        get() = value
        set(v) {
            value = v
        }

    fun fmt(fmt: Appendable) {
        val tmp = "${this.value}"
        var i = 0
        while (i < tmp.length) {
            val c = tmp[i]
            if (c.isHighSurrogate() && i + 1 < tmp.length && tmp[i + 1].isLowSurrogate()) {
                val scalar = 0x10000 + ((c.code - 0xD800) shl 10) + (tmp[i + 1].code - 0xDC00)
                fmt.append("_${scalar.toString(16)}")
                i += 2
            } else {
                when (c) {
                    in 'a'..'z', in '0'..'9', in 'A'..'Z' -> fmt.append("$c")
                    '_' -> fmt.append("__")
                    else -> fmt.append("_${c.code.toString(16)}")
                }
                i += 1
            }
        }
    }

    override fun toString(): String = buildString { fmt(this) }
}

internal class Prefix<S>(
    var field0: String,
    var field1: Iterable<S>,
) {
    operator fun component1(): String = field0

    operator fun component2(): Iterable<S> = field1

    fun fmt(fmt: Appendable) {
        val (prefix, vec) = this
        for (elem in vec) {
            fmt.append("$prefix$elem")
        }
    }

    override fun toString(): String = buildString { fmt(this) }
}

/**
 * Strip leading and trailing whitespace.
 */
internal fun strip(s: String): String = s.trim(Char::isWhitespace)
