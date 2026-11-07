# lalrpop-kotlin

<!-- Project & Registry Badges -->
[![GitHub Repository](https://img.shields.io/badge/GitHub-KotlinMania%2Flalrpop--kotlin-181717?logo=github&logoColor=white)](https://github.com/KotlinMania/lalrpop-kotlin)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.kotlinmania/lalrpop-kotlin?logo=apachemaven&logoColor=white&color=brightgreen)](https://central.sonatype.com/artifact/io.github.kotlinmania/lalrpop-kotlin)
[![GitHub Release](https://img.shields.io/github/v/release/KotlinMania/lalrpop-kotlin?include_prereleases&logo=github&label=Release)](https://github.com/KotlinMania/lalrpop-kotlin/releases)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.21-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org/docs/multiplatform.html)
[![Swift Export](https://img.shields.io/badge/Swift_Export-SPM_%2F_XCFramework-F05138?logo=swift&logoColor=white)](https://kotlinlang.org/docs/native-swift-export.html)
[![JVM Toolchain](https://img.shields.io/badge/JVM-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Android API](https://img.shields.io/badge/Android-API%2024%2B-3DDC84?logo=android&logoColor=white)](https://developer.android.com)
[![Runtime Crate](https://img.shields.io/badge/runtime-lalrpop--util--kotlin-7F52FF?logo=kotlin&logoColor=white)](https://github.com/KotlinMania/lalrpop-util-kotlin)
[![Upstream Rust Crate](https://img.shields.io/badge/upstream-lalrpop%200.23.1-DEA584?logo=rust&logoColor=white)](https://crates.io/crates/lalrpop)
[![Upstream docs.rs](https://img.shields.io/badge/docs.rs-lalrpop-DEA584?logo=docs.rs&logoColor=white)](https://docs.rs/lalrpop/latest/lalrpop/)
[![LALRPOP Book](https://img.shields.io/badge/book-lalrpop.github.io-blue?logo=mdbook&logoColor=white)](https://lalrpop.github.io/lalrpop/)
[![License: Apache-2.0 OR MIT](https://img.shields.io/badge/License-Apache--2.0%20OR%20MIT-blue.svg)](LICENSE-APACHE)
[![Last Commit](https://img.shields.io/github/last-commit/KotlinMania/lalrpop-kotlin?logo=git&logoColor=white)](https://github.com/KotlinMania/lalrpop-kotlin/commits/main)
[![GitHub Issues](https://img.shields.io/github/issues/KotlinMania/lalrpop-kotlin?logo=github)](https://github.com/KotlinMania/lalrpop-kotlin/issues)
[![GitHub Pull Requests](https://img.shields.io/github/issues-pr/KotlinMania/lalrpop-kotlin?logo=github)](https://github.com/KotlinMania/lalrpop-kotlin/pulls)

<!-- CI & Platform Workflow Badges -->
[![CI](https://img.shields.io/github/actions/workflow/status/KotlinMania/lalrpop-kotlin/ci.yml?branch=main&label=CI&logo=githubactions&logoColor=white)](https://github.com/KotlinMania/lalrpop-kotlin/actions/workflows/ci.yml)
[![Publish](https://img.shields.io/github/actions/workflow/status/KotlinMania/lalrpop-kotlin/publish.yml?label=Publish&logo=apachemaven&logoColor=white)](https://github.com/KotlinMania/lalrpop-kotlin/actions/workflows/publish.yml)
[![JVM](https://img.shields.io/github/actions/workflow/status/KotlinMania/lalrpop-kotlin/jvm.yml?branch=main&label=JVM&logo=openjdk&logoColor=white)](https://github.com/KotlinMania/lalrpop-kotlin/actions/workflows/jvm.yml)
[![Android](https://img.shields.io/github/actions/workflow/status/KotlinMania/lalrpop-kotlin/android.yml?branch=main&label=Android&logo=android&logoColor=white)](https://github.com/KotlinMania/lalrpop-kotlin/actions/workflows/android.yml)
[![Android Native](https://img.shields.io/github/actions/workflow/status/KotlinMania/lalrpop-kotlin/android-native.yml?branch=main&label=Android%20Native&logo=android&logoColor=white)](https://github.com/KotlinMania/lalrpop-kotlin/actions/workflows/android-native.yml)
[![macOS](https://img.shields.io/github/actions/workflow/status/KotlinMania/lalrpop-kotlin/macos.yml?branch=main&label=macOS&logo=apple&logoColor=white)](https://github.com/KotlinMania/lalrpop-kotlin/actions/workflows/macos.yml)
[![iOS](https://img.shields.io/github/actions/workflow/status/KotlinMania/lalrpop-kotlin/ios.yml?branch=main&label=iOS&logo=apple&logoColor=white)](https://github.com/KotlinMania/lalrpop-kotlin/actions/workflows/ios.yml)
[![watchOS](https://img.shields.io/github/actions/workflow/status/KotlinMania/lalrpop-kotlin/watchos.yml?branch=main&label=watchOS&logo=apple&logoColor=white)](https://github.com/KotlinMania/lalrpop-kotlin/actions/workflows/watchos.yml)
[![tvOS](https://img.shields.io/github/actions/workflow/status/KotlinMania/lalrpop-kotlin/tvos.yml?branch=main&label=tvOS&logo=apple&logoColor=white)](https://github.com/KotlinMania/lalrpop-kotlin/actions/workflows/tvos.yml)
[![Swift Export](https://img.shields.io/github/actions/workflow/status/KotlinMania/lalrpop-kotlin/swift.yml?branch=main&label=Swift%20Export&logo=swift&logoColor=white)](https://github.com/KotlinMania/lalrpop-kotlin/actions/workflows/swift.yml)
[![Linux](https://img.shields.io/github/actions/workflow/status/KotlinMania/lalrpop-kotlin/linux.yml?branch=main&label=Linux&logo=linux&logoColor=white)](https://github.com/KotlinMania/lalrpop-kotlin/actions/workflows/linux.yml)
[![Windows](https://img.shields.io/github/actions/workflow/status/KotlinMania/lalrpop-kotlin/windows.yml?branch=main&label=Windows&logo=windows&logoColor=white)](https://github.com/KotlinMania/lalrpop-kotlin/actions/workflows/windows.yml)
[![JS](https://img.shields.io/github/actions/workflow/status/KotlinMania/lalrpop-kotlin/js.yml?branch=main&label=JS&logo=javascript&logoColor=white)](https://github.com/KotlinMania/lalrpop-kotlin/actions/workflows/js.yml)
[![Wasm](https://img.shields.io/github/actions/workflow/status/KotlinMania/lalrpop-kotlin/wasm.yml?branch=main&label=Wasm&logo=webassembly&logoColor=white)](https://github.com/KotlinMania/lalrpop-kotlin/actions/workflows/wasm.yml)

<!-- Supported Target Badges -->
![Target JVM](https://img.shields.io/badge/target-JVM-blue)
![Target Android](https://img.shields.io/badge/target-Android-3DDC84)
![Target Android Native](https://img.shields.io/badge/target-Android%20Native%20(arm64%20%7C%20x64)-3DDC84)
![Target macOS](https://img.shields.io/badge/target-macOS%20(arm64)-000000)
![Target iOS](https://img.shields.io/badge/target-iOS%20(arm64%20%7C%20simArm64)-000000)
![Target watchOS](https://img.shields.io/badge/target-watchOS%20(deviceArm64%20%7C%20simArm64)-000000)
![Target tvOS](https://img.shields.io/badge/target-tvOS%20(arm64%20%7C%20simArm64)-000000)
![Target Linux](https://img.shields.io/badge/target-Linux%20(x64%20%7C%20arm64)-FCC624)
![Target Windows](https://img.shields.io/badge/target-Windows%20(mingwX64)-0078D6)
![Target JS](https://img.shields.io/badge/target-JS%20(Browser%20%7C%20Node.js)-F7DF1E)
![Target Wasm](https://img.shields.io/badge/target-Wasm%20(wasmJs%20%7C%20wasmWasi)-654FF0)

**`lalrpop-kotlin`** is a 100% pure Kotlin Multiplatform LR(1) and LALR(1) parser generator framework designed around grammar readability, expressiveness, and actionable diagnostics. It is a faithful 1:1 transliteration port of the Rust [`lalrpop/lalrpop`](https://github.com/lalrpop/lalrpop) crate (`v0.23.1`), paired with the companion [`lalrpop-util-kotlin`](https://github.com/KotlinMania/lalrpop-util-kotlin) runtime.

---

## Table of Contents

- [Key Features](#key-features)
- [Installation](#installation)
- [Quick Start & Kotlin Usage](#quick-start--kotlin-usage)
  - [Programmatic Configuration](#programmatic-configuration)
  - [Writing a `.lalrpop` Grammar](#writing-a-lalrpop-grammar)
  - [Rust-to-Kotlin API Mapping](#rust-to-kotlin-api-mapping)
  - [Swift Export Interop](#swift-export-interop)
- [Supported Kotlin Multiplatform Targets](#supported-kotlin-multiplatform-targets)
- [Building & Testing](#building--testing)
- [Attribution & Acknowledgments](#attribution--acknowledgments)
- [License](#license)

---

## Key Features

- **Compact, DRY `.lalrpop` Grammars**: Write expressive context-free grammars with parameterized macros (such as `Comma<Id>`), conditional nonterminal subsets (`Expr<"all">` vs. `Expr<"if">`), built-in repetition and optional operators (`*`, `+`, `?`), precedence/associativity annotations (`#[precedence]`, `#[assoc]`), compact default actions, and nonterminal type inference.
- **LR(1) Lane-Table & LALR(1) Construction**: Uses full LR(1) state construction with lane-table state splitting by default (with opt-in `#[LALR]` support), deterministic state/item ordering, and rich conflict diagnostics rendered with ASCII parse trees via `ascii-canvas`.
- **Exact UTF-8 Byte Spans & Unicode Scalar Fidelity**: Tracks source locations using UTF-8 byte offsets (`FileText`) and Unicode scalar boundaries (`Tokenizer`, `CharIndices`, `Escape`), ensuring multi-line error highlights (`reportParseError`), comments, string/regex literals, and supplementary Astral Unicode (`U+10000..U+10FFFF`) match upstream byte-for-byte.
- **Self-Hosted & Table-Driven Architecture**: `lalrpop-kotlin`'s own `.lalrpop` grammar parser (`LrGrammar.kt`) runs natively in Kotlin on [`lalrpop-util-kotlin`](https://github.com/KotlinMania/lalrpop-util-kotlin), alongside packed `ShortArray` table generation (`ACTION`, `EOF_ACTION`, `GOTO`) verified cell-for-cell against real-world grammars such as Starlark.
- **True 17-Target Kotlin Multiplatform + Swift 6 Export**: Built exclusively with pure Kotlin Multiplatform dependencies (`btree-kotlin`, `itertools-kotlin`, `km-io`, `logos-kotlin`, `rand-kotlin`, `regex-kotlin`, `regex-syntax-kotlin`, `lalrpop-util-kotlin`)—zero JVM-only `java.*` lock-in—plus native **Swift Export** (`Lalrpop` SPM package & XCFramework).

---

## Installation

### Gradle (Kotlin DSL — Kotlin Multiplatform)

Add `lalrpop-kotlin` (and `lalrpop-util-kotlin` when driving generated parsers at runtime) to your `commonMain` dependencies in `build.gradle.kts`:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("io.github.kotlinmania:lalrpop-kotlin:0.2.0")
            implementation("io.github.kotlinmania:lalrpop-util-kotlin:0.1.1")
        }
    }
}
```

### Gradle (Kotlin DSL — JVM / Android)

```kotlin
dependencies {
    implementation("io.github.kotlinmania:lalrpop-kotlin:0.2.0")
}
```

### Gradle Version Catalog (`gradle/libs.versions.toml`)

```toml
[versions]
lalrpop = "0.2.0"
lalrpopUtil = "0.1.1"

[libraries]
lalrpop = { module = "io.github.kotlinmania:lalrpop-kotlin", version.ref = "lalrpop" }
lalrpop-util = { module = "io.github.kotlinmania:lalrpop-util-kotlin", version.ref = "lalrpopUtil" }
```

### Maven (`pom.xml`)

```xml
<dependency>
    <groupId>io.github.kotlinmania</groupId>
    <artifactId>lalrpop-kotlin-jvm</artifactId>
    <version>0.2.0</version>
</dependency>
```

### Swift Package Manager / Xcode (Swift Export)

Build the Swift Export SPM package and XCFramework locally:

```bash
./gradlew embedSwiftExportForXcode
```

Then import the exported `Lalrpop` module in Swift (`macOS 15+`, Swift 6):

```swift
import Lalrpop
```

---

## Quick Start & Kotlin Usage

### Programmatic Configuration

Use `io.github.kotlinmania.lalrpop.api.Configuration` to configure input/output directories, conditional `#[cfg(feature = "...")]` flags, diagnostic verbosity, report generation, and build caching:

```kotlin
import io.github.kotlinmania.lalrpop.api.Configuration
import io.github.kotlinmania.lalrpop.api.processRoot
import io.github.kotlinmania.lalrpop.api.processSrc

fun generateParsers() {
    Configuration.new()
        .setInDir("src/main/grammars")
        .setOutDir("build/generated/lalrpop")
        .useColorsIfTty()
        .logInfo()
        .emitComments(true)
        .emitReport(true)
        .setMacroRecursionLimit(200)
        .setFeatures(listOf("ast-spans"))
        .forceBuild(false)
        .process()
}

// Or use the convenience entry points for standard directory layouts:
fun generateFromSrc() = processSrc()
fun generateFromRoot() = processRoot()
```

### Writing a `.lalrpop` Grammar

`lalrpop-kotlin` parses standard `.lalrpop` grammar files with macros, precedence tiers, and typed productions:

```lalrpop
grammar;

pub Expr: i32 = {
    #[precedence(level="1")]
    Term,
    #[precedence(level="2")] #[assoc(side="left")]
    <l:Expr> "+" <r:Expr> => l + r,
    <l:Expr> "-" <r:Expr> => l - r,
};

Term: i32 = {
    Num,
    "(" <Expr> ")",
};

Num: i32 = <s:r"[0-9]+"> => i32::from_str(s).unwrap();
```

For full `.lalrpop` syntax tutorials, macros, lexer customization, error recovery (`!`), and conditional compilation, see **[The LALRPOP Book](https://lalrpop.github.io/lalrpop/)** and the **[Cheat Sheet](https://lalrpop.github.io/lalrpop/cheatsheet.html)**.

### Rust-to-Kotlin API Mapping

| Upstream Rust (`lalrpop`) | Kotlin Multiplatform (`lalrpop-kotlin`) |
| :--- | :--- |
| `lalrpop::Configuration::new()` | `Configuration.new()` (`io.github.kotlinmania.lalrpop.api`) |
| `cfg.set_in_dir(dir)` / `cfg.set_out_dir(dir)` | `cfg.setInDir(dir)` / `cfg.setOutDir(dir)` |
| `cfg.generate_in_source_tree()` | `cfg.generateInSourceTree()` |
| `cfg.use_cargo_dir_conventions()` | `cfg.useCargoDirConventions()` |
| `cfg.force_build(true)` | `cfg.forceBuild(true)` |
| `cfg.emit_comments(true)` / `cfg.emit_report(true)` | `cfg.emitComments(true)` / `cfg.emitReport(true)` |
| `cfg.set_features(["feat"])` | `cfg.setFeatures(listOf("feat"))` |
| `cfg.process()` / `cfg.process_file(path)` | `cfg.process()` / `cfg.processFile(path)` |
| `lalrpop::process_root()` / `lalrpop::process_src()` | `processRoot()` / `processSrc()` |
| `lalrpop_util` runtime crate | [`io.github.kotlinmania:lalrpop-util-kotlin`](https://github.com/KotlinMania/lalrpop-util-kotlin) |

### Swift Export Interop

On Apple platforms, `lalrpop-kotlin` exports its configuration and diagnostic enums directly to Swift 6:

```swift
import Lalrpop

let config = api.Configuration.Companion.shared.new()
    .useColorsIfTty()
    .logInfo()
    .setMacroRecursionLimit(val: 200)
    .emitComments(val: true)
    .emitReport(val: true)
```

---

## Supported Kotlin Multiplatform Targets

`lalrpop-kotlin` builds and validates across all **17** supported 64-bit and Web KotlinMania targets:

| Platform Family | Gradle Targets | Notes |
| :--- | :--- | :--- |
| **JVM** | `jvm` | JDK 21 toolchain |
| **Android** | `androidLibrary` | `minSdk = 24`, `compileSdk = 34` |
| **Android Native** | `androidNativeArm64`, `androidNativeX64` | 64-bit Android NDK targets |
| **macOS** | `macosArm64` | Apple Silicon + Swift 6 Export (`Lalrpop` SPM & XCFramework) |
| **iOS** | `iosArm64`, `iosSimulatorArm64` | Static frameworks + XCFramework |
| **watchOS** | `watchosDeviceArm64`, `watchosSimulatorArm64` | 64-bit Apple Watch device & simulator |
| **tvOS** | `tvosArm64`, `tvosSimulatorArm64` | Apple TV device & simulator |
| **Linux** | `linuxX64`, `linuxArm64` | Native Linux binaries |
| **Windows** | `mingwX64` | Native 64-bit Windows binaries |
| **JavaScript** | `js(IR)` | Browser & Node.js |
| **WebAssembly** | `wasmJs`, `wasmWasi` | Browser, Node.js, and WASI runtimes |

---

## Building & Testing

```bash
# Run all host unit tests and the local Swift 6 Export test harness
./gradlew test

# Run the full 17-target multiplatform build gate, static analysis (detekt + ktlint), and cross-target validation
./gradlew build
```

See [`AGENTS.md`](AGENTS.md) and [`SWIFT.md`](SWIFT.md) for workspace porting conventions, `port-lint` provenance headers, and Swift Export guidelines.

---

## Attribution & Acknowledgments

`lalrpop-kotlin` is a faithful 1:1 Kotlin Multiplatform transliteration of the upstream Rust **[`lalrpop`](https://github.com/lalrpop/lalrpop)** parser generator framework (`v0.23.1`).

All credit for the original compiler architecture, LR(1) lane-table construction, grammar macro system, type inference, error recovery design, documentation, and test suites belongs to the upstream Rust creators, maintainers, and contributors. We extend our sincere thanks and appreciation to:

- **Niko Matsakis ([@nikomatsakis](https://github.com/nikomatsakis))** — Creator and original architect of [`lalrpop/lalrpop`](https://github.com/lalrpop/lalrpop), `ena`, and `ascii-canvas`, whose vision made LR(1) parser generation genuinely ergonomic and approachable.
- **The LALRPOP Project Maintainers & Contributors** — Everyone who has stewarded, optimized, documented, and expanded LALRPOP over more than a decade of Rust releases ([view all upstream contributors](https://github.com/lalrpop/lalrpop/graphs/contributors)).

### Upstream Resources

- **Upstream Repository**: <https://github.com/lalrpop/lalrpop>
- **Crates.io**: <https://crates.io/crates/lalrpop> & <https://crates.io/crates/lalrpop-util>
- **Rust API Docs (`docs.rs`)**: <https://docs.rs/lalrpop/latest/lalrpop/>
- **The LALRPOP Book**: <https://lalrpop.github.io/lalrpop/>

This repository brings their work natively to the Kotlin Multiplatform and Swift ecosystems. Questions or bug reports concerning upstream LALRPOP grammar semantics belong to [`lalrpop/lalrpop`](https://github.com/lalrpop/lalrpop); issues specific to the Kotlin Multiplatform port or Swift Export integration belong in [`KotlinMania/lalrpop-kotlin`](https://github.com/KotlinMania/lalrpop-kotlin/issues).

---

## License

`lalrpop-kotlin` is dual-licensed under the same terms as upstream [`lalrpop/lalrpop`](https://github.com/lalrpop/lalrpop), at your option:

- **Apache License, Version 2.0** — see [`LICENSE-APACHE`](LICENSE-APACHE) or <https://www.apache.org/licenses/LICENSE-2.0>
- **MIT License** — see [`LICENSE-MIT`](LICENSE-MIT) or <https://opensource.org/licenses/MIT>

Original Rust work: Copyright (c) 2015–2025 The LALRPOP Project Developers.  
Kotlin Multiplatform port: Copyright (c) 2026 Sydney Renee and The Solace Project.
