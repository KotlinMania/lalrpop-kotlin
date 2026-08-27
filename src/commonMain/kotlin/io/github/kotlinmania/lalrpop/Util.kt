// port-lint: source util.rs
package io.github.kotlinmania.lalrpop

internal class Sep<S>(val sep: String, val vec: Iterable<S>) {
    fun fmt(fmt: StringBuilder) {
        val elems = vec.iterator()
        if (elems.hasNext()) {
            val elem = elems.next()
            fmt.append(elem.toString())
            for (next in elems) {
                fmt.append(sep)
                fmt.append(next.toString())
            }
        }
    }

    override fun toString(): String = buildString { fmt(this) }
}

internal class Escape<S : Any>(val value: S) {
    fun fmt(fmt: StringBuilder) {
        val tmp = value.toString()
        for (c in tmp) {
            when (c) {
                in 'a'..'z', in '0'..'9', in 'A'..'Z' -> fmt.append(c)
                '_' -> fmt.append("__")
                else -> {
                    fmt.append('_')
                    fmt.append(c.code.toString(16))
                }
            }
        }
    }

    override fun toString(): String = buildString { fmt(this) }
}

internal class Prefix<S>(val prefix: String, val vec: Iterable<S>) {
    fun fmt(fmt: StringBuilder) {
        for (elem in vec) {
            fmt.append(prefix)
            fmt.append(elem.toString())
        }
    }

    override fun toString(): String = buildString { fmt(this) }
}

/** Strip leading and trailing whitespace. */
internal fun strip(s: String): String {
    return s.trim { it.isWhitespace() }
}
