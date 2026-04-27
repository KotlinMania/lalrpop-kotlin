// port-lint: source src/tls/mod.rs (thread_local! definition, JVM target)
package io.github.kotlinmania.lalrpop.tls

/**
 * Android (JVM) actual for [TlsStorage]. Backed by a
 * [java.lang.ThreadLocal] so each JVM thread has its own slot, matching
 * the semantics of Rust's
 * `thread_local! { static THE_TLS_FIELDS: ... }`.
 */
internal actual object TlsStorage {
    private val tl: ThreadLocal<TlsFields?> = ThreadLocal()

    actual var current: TlsFields?
        get() = tl.get()
        set(value) {
            if (value == null) tl.remove() else tl.set(value)
        }
}
