// port-lint: source src/tls/mod.rs
package io.github.kotlinmania.lalrpop.tls

import io.github.kotlinmania.lalrpop.FileText
import io.github.kotlinmania.lalrpop.Session

/**
 * `#[cfg(test)] pub fn test() -> Tls`
 *
 * `Self::install(Rc::new(Session::test()), Rc::new(FileText::test()))`
 */
fun Tls.Companion.test(): Tls = install(Session.test(), FileText.test())

/**
 * `#[cfg(test)] pub fn test_string(text: &str) -> Tls`
 *
 * `Self::install(Rc::new(Session::test()), Rc::new(FileText::new(PathBuf::from("tmp.txt"), String::from(text))))`
 */
fun Tls.Companion.testString(text: String): Tls =
    install(Session.test(), FileText.new("tmp.txt", text))
