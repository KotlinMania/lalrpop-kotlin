// port-lint: source src/tls/mod.rs
package io.github.kotlinmania.lalrpop.tls

/*
 * Copyright 2015-2025 The LALRPOP Project Developers.
 * Copyright (c) 2026 Sydney Renee, The Solace Project (Kotlin port).
 *
 * Licensed under either of
 *   - Apache License, Version 2.0
 *     (https://www.apache.org/licenses/LICENSE-2.0)
 *   - MIT license
 *     (https://opensource.org/licenses/MIT)
 * at your option.
 */

//! Certain bits of environmental state are too annoying to thread
//! around everywhere, so pack them into TLS.

import io.github.kotlinmania.lalrpop.FileText
import io.github.kotlinmania.lalrpop.Session

/**
 * Mirrors the upstream `struct TlsFields { session: Rc<Session>, fileText: Rc<FileText> }`.
 *
 * Visible at `internal` so the per-platform [TlsStorage] actual files
 * can hold a reference; the type itself is otherwise private to this
 * module.
 */
internal data class TlsFields(
    val session: Session,
    val fileText: FileText,
)

/**
 * Per-thread backing store for [Tls]. Direct port of the upstream
 * `threadLocal! { static THE_TLS_FIELDS: RefCell<Option<TlsFields>> = ... }`.
 *
 * `expect` so each platform supplies a thread-local implementation:
 *  * Native: `@kotlin.native.concurrent.ThreadLocal` annotation on the
 *    actual `object`, giving each Kotlin/Native worker its own copy.
 *  * Android (JVM): `java.lang.ThreadLocal` accessor.
 *  * JS / wasmJs: a plain field — those targets are single-threaded so
 *    a process-wide cell has the same observable semantics as a
 *    thread-local.
 *
 * Replaces the previous JVM-static `private object THE_TLS_FIELDS`
 * which had wrong semantics on every platform: on JVM it leaked state
 * across threads, and on Native (under the new memory model) writes
 * from one worker would have been illegal-frozen-mutation errors at
 * runtime.
 */
internal expect object TlsStorage {
    var current: TlsFields?
}

/**
 * Direct port of upstream `class Tls { _dummy: () }` plus the
 * `implementation Drop for Tls { ... THE_TLS_FIELDS.with(... = None) }` block.
 * In Kotlin we model `Drop` as [AutoCloseable] / [close]; callers should
 * always wrap installation in `use { ... }` or call [close] from a
 * `finally` block to mirror the deterministic drop semantics.
 */
class Tls private constructor() : AutoCloseable {

    fun drop() {
        TlsStorage.current = null
    }

    override fun close() = drop()

    companion object {
        /**
         * Direct port of upstream `(cfg(test)) fun test()`. Installs a
         * test-flavoured [Session] and an empty [FileText] and returns
         * the guard.
         */
        fun test(): Tls = install(Session.test(), FileText.test())

        /**
         * Direct port of upstream `(cfg(test)) fun testString(text: &str)`.
         * Installs a test-flavoured [Session] paired with a [FileText]
         * sourced from the supplied string.
         */
        fun testString(text: String): Tls =
            install(Session.test(), FileText.new("tmp.txt", text))

        /**
         * Installs `Tls` and returns a guard value. When this
         * value is dropped, the `Tls` entries will be removed. To access
         * the values from `Tls`, call `Tls.session()` or `Tls.fileText()`.
         */
        fun install(session: Session, fileText: FileText): Tls {
            val fields = TlsFields(session, fileText)

            check(TlsStorage.current == null)
            TlsStorage.current = fields

            return Tls()
        }

        private fun fields(): TlsFields =
            TlsStorage.current ?: error("TLS is not installed")

        fun session(): Session = fields().session

        fun fileText(): FileText = fields().fileText
    }
}
