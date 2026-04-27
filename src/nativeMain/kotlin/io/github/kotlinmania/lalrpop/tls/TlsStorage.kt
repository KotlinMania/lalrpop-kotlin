// port-lint: source src/tls/mod.rs (thread_local! definition, native target)
package io.github.kotlinmania.lalrpop.tls

import kotlin.native.concurrent.ThreadLocal

/**
 * Kotlin/Native actual for [TlsStorage]. The
 * [`@ThreadLocal`][ThreadLocal] annotation gives this `object` its own
 * mutable state per worker (i.e. per OS thread under the new memory
 * model), matching the semantics of Rust's
 * `thread_local! { static THE_TLS_FIELDS: ... }`.
 */
@ThreadLocal
internal actual object TlsStorage {
    actual var current: TlsFields? = null
}
