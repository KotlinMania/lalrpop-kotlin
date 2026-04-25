// port-lint: source src/tls/mod.rs
//! Certain bits of environmental state are too annoying to thread
//! around everywhere, so pack them into TLS.
package io.github.kotlinmania.lalrpop_kotlin.tls

import io.github.kotlinmania.lalrpop_kotlin.FileText
import io.github.kotlinmania.lalrpop_kotlin.Session

/**
 * `#[derive(Clone)] struct TlsFields { session: Rc<Session>, file_text: Rc<FileText> }`
 *
 * `Rc<T>` collapses to a direct reference in Kotlin since the JVM/Native
 * backends already GC shared state.
 */
private data class TlsFields(
    val session: Session,
    val fileText: FileText,
)

/**
 * `thread_local! { static THE_TLS_FIELDS: RefCell<Option<TlsFields>> = const { RefCell::new(None) }; }`
 *
 * Kotlin Multiplatform's `commonMain` has no thread-local primitive, so
 * this slot is process-global. LALRPOP drives TLS from a single thread
 * during grammar processing, so the visible behavior matches Rust.
 */
private object TheTlsFields {
    var value: TlsFields? = null
}

/** `pub struct Tls { _dummy: () }` */
class Tls private constructor() : AutoCloseable {
    /**
     * `impl Drop for Tls { fn drop(&mut self) { ... } }`
     *
     * Rust's `Drop` runs when the value leaves scope; Kotlin has no
     * equivalent, so `Tls` implements `AutoCloseable` and callers use
     * `Tls.install(...).use { ... }` to recover the RAII guard.
     */
    override fun close() {
        TheTlsFields.value = null
    }

    companion object {
        /**
         * `pub fn install(session: Rc<Session>, file_text: Rc<FileText>) -> Tls`
         *
         * Installs `Tls` and returns a guard value. When that value is
         * closed, the `Tls` entries are removed. To access the values
         * from `Tls`, call `Tls.session()` or `Tls.fileText()`.
         */
        fun install(session: Session, fileText: FileText): Tls {
            val fields = TlsFields(session, fileText)

            // THE_TLS_FIELDS.with(|s| { let mut s = s.borrow_mut(); assert!(s.is_none()); *s = Some(fields); });
            check(TheTlsFields.value == null)
            TheTlsFields.value = fields

            return Tls()
        }

        /** `fn fields() -> TlsFields` */
        private fun fields(): TlsFields =
            TheTlsFields.value ?: error("TLS is not installed")

        /** `pub fn session() -> Rc<Session>` */
        fun session(): Session = fields().session

        /** `pub fn file_text() -> Rc<FileText>` */
        fun fileText(): FileText = fields().fileText
    }
}
