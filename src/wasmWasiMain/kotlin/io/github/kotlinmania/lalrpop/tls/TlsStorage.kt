// port-lint: source tls/mod.rs (threadLocal! definition, wasmWasi target)
package io.github.kotlinmania.lalrpop.tls

/**
 * wasmWasi actual for [TlsStorage]. wasmWasi is single-threaded,
 * so a plain mutable field matches the upstream `threadLocal!` semantics.
 */
internal actual object TlsStorage {
    actual var current: TlsFields? = null
}
