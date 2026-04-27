// port-lint: source src/tls/mod.rs (thread_local! definition, wasmJs target)
package io.github.kotlinmania.lalrpop.tls

/**
 * wasmJs actual for [TlsStorage]. wasmJs (browser/node) is
 * single-threaded by design, so a plain mutable field matches Rust's
 * `thread_local!` semantics for this target.
 */
internal actual object TlsStorage {
    actual var current: TlsFields? = null
}
