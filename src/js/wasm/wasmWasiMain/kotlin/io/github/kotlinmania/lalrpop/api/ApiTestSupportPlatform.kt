package io.github.kotlinmania.lalrpop.api

import io.github.kotlinmania.io.files.Path
import io.github.kotlinmania.io.files.SystemFileSystem
import io.github.kotlinmania.io.files.SystemTemporaryDirectory
import io.github.kotlinmania.io.files.createDirectory
import io.github.kotlinmania.io.files.isDirectory

internal var wasiCurrentDir: String = "."
internal val wasiEnvVars: MutableMap<String, String> = mutableMapOf()

internal actual fun apiTempDir(): String =
    SystemTemporaryDirectory.toString().trimEnd('/').ifEmpty { "/tmp" }

internal actual fun apiSetCurrentDir(path: String) {
    wasiCurrentDir = path
}

internal actual fun apiSetEnvVar(name: String, value: String) {
    wasiEnvVars[name] = value
}

internal actual fun apiPathExists(path: String): Boolean =
    SystemFileSystem.exists(Path(path))

internal actual fun apiCreateDir(path: String) {
    SystemFileSystem.createDirectory(Path(path))
}

internal actual fun apiRemoveDirAll(path: String) {
    val root = Path(path)
    if (!SystemFileSystem.exists(root)) return
    if (SystemFileSystem.isDirectory(root)) {
        for (child in SystemFileSystem.list(root)) {
            apiRemoveDirAll(child.toString())
        }
    }
    SystemFileSystem.delete(root, mustExist = false)
}

internal actual fun apiRemoveFile(path: String) {
    SystemFileSystem.delete(Path(path), mustExist = true)
}
