package io.github.kotlinmania.lalrpop

/*
 * Copyright (c) 2026 Sydney Renee, The Solace Project (Kotlin port).
 *
 * Licensed under either of
 *   - Apache License, Version 2.0
 *     (https://www.apache.org/licenses/LICENSE-2.0)
 *   - MIT license
 *     (https://opensource.org/licenses/MIT)
 * at your option.
 */

// File-system primitives whose C types differ between Apple, Linux and
// Android Native (DIR, dirent, mode_t widths). Shared POSIX code calls these
// so only plain Kotlin types cross the posix source-set boundary.

/** Names of the entries in the directory at [path], without "." and "..", or null if it cannot be opened. */
internal expect fun posixListDirectory(path: String): List<String>?

/**
 * The file-type bits (st_mode masked with S_IFMT) of [path], or null if it
 * cannot be inspected. With [followLinks] false a symbolic link reports its own type.
 */
internal expect fun posixFileType(path: String, followLinks: Boolean): Int?

/** Creates the directory [path] with mode 0755; true on success. */
internal expect fun posixMakeDirectory(path: String): Boolean
