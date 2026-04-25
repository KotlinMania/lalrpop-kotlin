// port-lint: source src/lexer/mod.rs
/** Code related to generating tokenizers. */
package io.github.kotlinmania.lalrpop_kotlin.lexer

// Rust mod.rs declares submodules:
//   pub mod dfa;
//   pub mod intern_token;
//   pub mod nfa;
//   pub mod re;
// `#![allow(dead_code)] // not yet fully activated` has no Kotlin counterpart;
// dead-code suppression in Kotlin is per-symbol via `@Suppress`.
// In Kotlin the directory is the package, so submodule symbols are
// reachable via their defining files without an explicit declaration.
