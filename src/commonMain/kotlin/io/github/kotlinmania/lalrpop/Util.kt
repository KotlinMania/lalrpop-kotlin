// port-lint: source src/util.rs
package io.github.kotlinmania.lalrpop

class Sep<S>(val sep: String, val vec: Iterable<S>) {
    override fun toString(): String = buildString {
        val elems = vec.iterator()
        if (elems.hasNext()) {
            val elem = elems.next()
            append(elem.toString())
            for (next in elems) {
                append(sep)
                append(next.toString())
            }
        }
    }
}

class Escape<S : Any>(val value: S) {
    override fun toString(): String = buildString {
        val tmp = value.toString()
        for (c in tmp) {
            when (c) {
                in 'a'..'z', in '0'..'9', in 'A'..'Z' -> append(c)
                '_' -> append("__")
                else -> {
                    append('_')
                    append(c.code.toString(16))
                }
            }
        }
    }
}

class Prefix<S>(val prefix: String, val vec: Iterable<S>) {
    override fun toString(): String = buildString {
        for (elem in vec) {
            append(prefix)
            append(elem.toString())
        }
    }
}

/** Strip leading and trailing whitespace. */
fun strip(s: String): String = s.trim { it.isWhitespace() }
