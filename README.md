# lalrpop-kotlin

[![GitHub](https://img.shields.io/badge/GitHub-KotlinMania%2Flalrpop--kotlin-blue.svg)](https://github.com/KotlinMania/lalrpop-kotlin)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.kotlinmania/lalrpop-kotlin)](https://central.sonatype.com/artifact/io.github.kotlinmania/lalrpop-kotlin)
[![CI](https://img.shields.io/github/actions/workflow/status/KotlinMania/lalrpop-kotlin/ci.yml?branch=main)](https://github.com/KotlinMania/lalrpop-kotlin/actions)

Kotlin Multiplatform LR(1) parser generator based on the grammar language and architecture of the upstream Rust project [lalrpop/lalrpop](https://github.com/lalrpop/lalrpop).

## Installation

```kotlin
dependencies {
    implementation("io.github.kotlinmania:lalrpop-kotlin:0.1.6")
}
```

## Basic Usage

```kotlin
import io.github.kotlinmania.lalrpop.api.Configuration

fun generate() {
    Configuration.new()
        .useCargoDirConventions()
        .processFile("src/syntax/grammar.lalrpop")
}
```

## Targets

- macOS arm64
- Linux x64
- Windows mingw-x64
- iOS arm64 / simulator-arm64 / simulator-x64 (Swift export + XCFramework)
- JS (Node.js)
- Wasm-JS (Node.js)
- Android (API 24+)

## License

Dual-licensed under **Apache-2.0 OR MIT**, matching upstream.

