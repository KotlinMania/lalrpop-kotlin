// port-lint: source src/tls/mod.rs (threadLocal! definition, wasmJs target)
package io.github.kotlinmania.lalrpop.tls

/**
 * wasmJs actual for [TlsStorage]. wasmJs (browser/node) is
 * single-threaded by design, so a plain mutable field matches the upstream
 * `threadLocal!` semantics for this target.
 */
internal actual object TlsStorage {
    actual var current: TlsFields? = null
}
