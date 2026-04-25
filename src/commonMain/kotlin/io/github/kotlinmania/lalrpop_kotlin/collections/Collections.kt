// port-lint: source src/collections/mod.rs
package io.github.kotlinmania.lalrpop_kotlin.collections

// Rust mod.rs is a pure re-export:
//   pub use self::map::{Entry, Map, map};
//   pub use self::multimap::{Collection, Multimap};
//   pub use self::set::{Set, set};
// In Kotlin the directory is the package, so submodule symbols are
// reachable directly via their defining files (Map.kt, Multimap.kt, Set.kt)
// without an explicit re-export.
