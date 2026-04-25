# lalrpop-kotlin

A Kotlin Multiplatform port of [LALRPOP], the Rust LR(1) parser generator.

This project aims to bring LALRPOP's grammar-driven parser generation to the JVM, Native, and JS ecosystems. The long-term goal is a Gradle plugin that consumes `.lalrpop` grammar files and emits Kotlin parser code at build time — the same role LALRPOP's `build.rs` integration plays in Rust.

## Status

Early work. Rust sources from upstream LALRPOP live under [tmp/lalrpop-rs/](tmp/lalrpop-rs/) as the translation reference. Kotlin code lives under `src/` (forthcoming).

## Why

Kotlin projects that port grammar-driven Rust crates (for example, [starlark-kotlin]) currently carry committed parser tables — the output of running LALRPOP once in Rust and transliterating its 20k+-line output by hand. That output is awkward to maintain, awkward to audit, and diverges from upstream every time the grammar changes.

A native Kotlin LALRPOP lets `.lalrpop` files be the single source of truth on the Kotlin side as well.

## Layout

- `tmp/lalrpop-rs/lalrpop/` — the generator crate (the bulk of the port surface)
- `tmp/lalrpop-rs/lalrpop-util/` — runtime used by generated parsers
- `tmp/lalrpop-rs/lalrpop-test/` — conformance tests (used to validate the Kotlin port)
- `src/` — Kotlin implementation (forthcoming)

## Acknowledgements

LALRPOP is the work of [Niko Matsakis] and the LALRPOP contributors. This fork exists only because their design is worth translating — all credit for the architecture, grammar language, and LR(1) machinery belongs upstream. See the original project at <https://github.com/lalrpop/lalrpop>.

## License

Dual-licensed under **Apache-2.0 OR MIT**, matching upstream. See [LICENSE-APACHE](LICENSE-APACHE), [LICENSE-MIT](LICENSE-MIT), and [NOTICE](NOTICE).

## Maintainer

Sydney Renee `<sydney@solace.ofharmony.ai>` — The Solace Project.

Repository: <https://github.com/KotlinMania/lalrpop-kotlin>

[LALRPOP]: https://github.com/lalrpop/lalrpop
[starlark-kotlin]: https://github.com/KotlinMania/starlark-kotlin
[Niko Matsakis]: https://github.com/nikomatsakis
