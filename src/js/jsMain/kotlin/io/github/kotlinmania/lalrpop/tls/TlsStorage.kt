// port-lint: source tls/mod.rs (threadLocal! definition, JS target)
package io.github.kotlinmania.lalrpop.tls

/**
 * JS actual for [TlsStorage]. JS is single-threaded by design, so a
 * plain mutable field has the same observable semantics as the upstream
 * `threadLocal!` (which is also "ambient state per execution
 * context"). No expect/actual indirection beyond this.
 */
internal actual object TlsStorage {
    actual var current: TlsFields? = null
}
