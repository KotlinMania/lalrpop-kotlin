// port-lint: source src/tls/mod.rs (thread_local! definition, JS target)
package io.github.kotlinmania.lalrpop.tls

/**
 * JS actual for [TlsStorage]. JS is single-threaded by design, so a
 * plain mutable field has the same observable semantics as Rust's
 * `thread_local!` (which is also "ambient state per execution
 * context"). No expect/actual indirection beyond this.
 */
internal actual object TlsStorage {
    actual var current: TlsFields? = null
}
