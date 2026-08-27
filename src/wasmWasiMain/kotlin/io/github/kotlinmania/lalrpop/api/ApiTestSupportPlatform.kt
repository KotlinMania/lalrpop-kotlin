// port-lint: source api/mod.rs (platform glue, wasmWasi target)
package io.github.kotlinmania.lalrpop.api

internal actual fun apiTempDir(): String = "/tmp"
internal actual fun apiSetCurrentDir(path: String) {}
internal actual fun apiSetEnvVar(name: String, value: String) {}
internal actual fun apiPathExists(path: String): Boolean = false
internal actual fun apiCreateDir(path: String) {}
internal actual fun apiRemoveDirAll(path: String) {}
internal actual fun apiRemoveFile(path: String) {}
