package io.github.kotlinmania.lalrpop.tls

/**
 * Android actual for [TlsStorage]. Backed by a [ThreadLocal] so each thread
 * owns its own slot, matching the upstream thread-local semantics.
 */
internal actual object TlsStorage {
    private val tl: ThreadLocal<TlsFields?> = ThreadLocal()

    actual var current: TlsFields?
        get() = tl.get()
        set(value) {
            if (value == null) tl.remove() else tl.set(value)
        }
}
