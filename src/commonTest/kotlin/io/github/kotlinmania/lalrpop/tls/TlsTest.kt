// port-lint: source tls/mod.rs
package io.github.kotlinmania.lalrpop.tls

import io.github.kotlinmania.lalrpop.FileText
import io.github.kotlinmania.lalrpop.Session

fun Tls.Companion.test(): Tls = install(Session.test(), FileText.test())

fun Tls.Companion.testString(text: String): Tls =
    install(Session.test(), FileText.new("tmp.txt", text))
