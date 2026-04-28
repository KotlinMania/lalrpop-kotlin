// port-lint: source tls/mod.rs
package io.github.kotlinmania.lalrpop.tls

/**
 * Certain bits of environmental state are too annoying to thread
 * around everywhere, so pack them into TLS.
 */

import io.github.kotlinmania.lalrpop.FileText
import io.github.kotlinmania.lalrpop.Session

internal data class TlsFields(
    val session: Session,
    val fileText: FileText,
)

internal expect object TlsStorage {
    var current: TlsFields?
}

class Tls private constructor() : AutoCloseable {

    fun drop() {
        TlsStorage.current = null
    }

    override fun close() = drop()

    companion object {
        fun test(): Tls {
            return install(Session.test(), FileText.test())
        }

        fun testString(text: String): Tls {
            return install(
                Session.test(),
                FileText.new("tmp.txt", text),
            )
        }

        /**
         * Installs `Tls` and returns a placeholder value.  When this
         * value is dropped, the `Tls` entries will be removed. To access
         * the values from `Tls`, call `Tls.session()` or
         * `Tls.fileText()`.
         */
        fun install(session: Session, fileText: FileText): Tls {
            val fields = TlsFields(session = session, fileText = fileText)

            check(TlsStorage.current == null)
            TlsStorage.current = fields

            return Tls()
        }

        private fun fields(): TlsFields {
            return TlsStorage.current ?: error("TLS is not installed")
        }

        fun session(): Session {
            return fields().session
        }

        fun fileText(): FileText {
            return fields().fileText
        }
    }
}
