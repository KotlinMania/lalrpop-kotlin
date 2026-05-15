# lalrpop-kotlin in Kotlin

[![GitHub link](https://img.shields.io/badge/GitHub-KotlinMania%2Flalrpop--kotlin-blue.svg)](https://github.com/KotlinMania/lalrpop-kotlin)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.kotlinmania/lalrpop-kotlin)](https://central.sonatype.com/artifact/io.github.kotlinmania/lalrpop-kotlin)
[![Build status](https://img.shields.io/github/actions/workflow/status/KotlinMania/lalrpop-kotlin/ci.yml?branch=main)](https://github.com/KotlinMania/lalrpop-kotlin/actions)

This is a Kotlin Multiplatform line-by-line transliteration port of [`lalrpop/lalrpop`](https://github.com/lalrpop/lalrpop).

**Original Project:** This port is based on [`lalrpop/lalrpop`](https://github.com/lalrpop/lalrpop). All design credit and project intent belong to the upstream authors; this repository is a faithful port to Kotlin Multiplatform with no behavioural changes intended.

### Porting status

This is an **in-progress port**. The goal is feature parity with the upstream Rust crate while providing a native Kotlin Multiplatform API. Every Kotlin file carries a `// port-lint: source <path>` header naming its upstream Rust counterpart so the AST-distance tool can track provenance.

---

## Upstream README — `lalrpop/lalrpop`

> The text below is reproduced and lightly edited from [`https://github.com/lalrpop/lalrpop`](https://github.com/lalrpop/lalrpop). It is the upstream project's own description and remains under the upstream authors' authorship; links have been rewritten to absolute upstream URLs so they continue to resolve from this repository.

## LALRPOP

[![Join the chat at https://gitter.im/lalrpop/Lobby](https://badges.gitter.im/lalrpop/Lobby.svg)](https://gitter.im/lalrpop/Lobby?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

[![Deploy](https://github.com/lalrpop/lalrpop/actions/workflows/deploy.yml/badge.svg)](https://github.com/lalrpop/lalrpop/actions/workflows/deploy.yml)

LALRPOP is a Rust parser generator framework with *usability* as its
primary goal. You should be able to write compact, DRY, readable
grammars. To this end, LALRPOP offers a number of nifty features:

0. Nice error messages in case parser constructor fails.
1. Macros that let you extract common parts of your grammar. This
   means you can go beyond simple repetition like `Id*` and define
   things like `Comma<Id>` for a comma-separated list of identifiers.
2. Macros can also create subsets, so that you easily do something
   like `Expr<"all">` to represent the full range of expressions, but
   `Expr<"if">` to represent the subset of expressions that can appear
   in an `if` expression.
3. Builtin support for operators like `*` and `?`.
4. Compact defaults so that you can avoid writing action code much of the
   time.
5. Type inference so you can often omit the types of nonterminals.

Despite its name, LALRPOP in fact uses LR(1) by default (though you
can opt for LALR(1)), and really I hope to eventually move to
something general that can handle all CFGs (like GLL, GLR, LL(\*),
etc).

### Documentation

[The LALRPOP book] covers all things LALRPOP -- or at least it intends
to! Here are some tips:

- The [tutorial] covers the basics of setting up a LALRPOP parser.
- For the impatient, you may prefer the [quick start guide] section, which describes
  how to add LALRPOP to your `Cargo.toml`.
- Returning users of LALRPOP may benefit from the [cheat sheet].
- The [advanced setup] chapter shows how to configure other aspects of LALRPOP's
  preprocessing.
- docs.rs API documentation for [lalrpop](https://docs.rs/lalrpop/latest/lalrpop/) and [lalrpop-util]
- If you have any questions join our [gitter lobby].

### Example Uses

- [LALRPOP] is itself implemented in LALRPOP.
- [Gluon] is a statically typed functional programming language.
- [RustPython] is Python 3.5+ rewritten in Rust
- [Solang] is Ethereum Solidity rewritten in Rust

[The LALRPOP book]: https://lalrpop.github.io/lalrpop/
[quick start guide]: https://lalrpop.github.io/lalrpop/quick_start_guide.html
[advanced setup]: https://lalrpop.github.io/lalrpop/advanced_setup.html
[cheat sheet]: https://lalrpop.github.io/lalrpop/cheatsheet.html
[tutorial]: https://lalrpop.github.io/lalrpop/tutorial/index.html
[LALRPOP]: https://github.com/lalrpop/lalrpop/blob/master/lalrpop/src/parser/lrgrammar.lalrpop
[Gluon]: https://github.com/gluon-lang/gluon/blob/master/parser/src/grammar.lalrpop
[RustPython]: https://github.com/RustPython/Parser/blob/main/parser/src/python.lalrpop
[Solang]: https://github.com/hyperledger/solang/blob/main/solang-parser/src/solidity.lalrpop
[gitter lobby]: https://gitter.im/lalrpop/Lobby
[lalrpop-util]: https://docs.rs/lalrpop-util/latest/lalrpop_util/

### Contributing

You **really** should read `CONTRIBUTING.md` if you intend to change LALRPOP's own grammar.

---

## About this Kotlin port

### Installation

```kotlin
dependencies {
    implementation("io.github.kotlinmania:lalrpop-kotlin:0.1.6")
}
```

### Building

```bash
./gradlew build
./gradlew test
```

### Targets

- macOS arm64
- Linux x64
- Windows mingw-x64
- iOS arm64 / simulator-arm64 (Swift export + XCFramework)
- JS (browser + Node.js)
- Wasm-JS (browser + Node.js)
- Android (API 24+)

### Porting guidelines

See [AGENTS.md](AGENTS.md) and [CLAUDE.md](CLAUDE.md) for translator discipline, port-lint header convention, and Rust → Kotlin idiom mapping.

### License

This Kotlin port is distributed under the same Apache-2.0 license as the upstream [`lalrpop/lalrpop`](https://github.com/lalrpop/lalrpop). See [LICENSE](LICENSE) (and any sibling `LICENSE-*` / `NOTICE` files mirrored from upstream) for the full text.

Original work copyrighted by the lalrpop authors.  
Kotlin port: Copyright (c) 2026 Sydney Renee and The Solace Project.

### Acknowledgments

Thanks to the [`lalrpop/lalrpop`](https://github.com/lalrpop/lalrpop) maintainers and contributors for the original Rust implementation. This port reproduces their work in Kotlin Multiplatform; bug reports about upstream design or behavior should go to the upstream repository.
