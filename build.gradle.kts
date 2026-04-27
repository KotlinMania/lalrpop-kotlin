import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import java.io.ByteArrayOutputStream

plugins {
    kotlin("multiplatform") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
    id("com.android.kotlin.multiplatform.library") version "8.6.0"
    id("com.vanniktech.maven.publish") version "0.30.0"
}

group = "io.github.kotlinmania"
version = "0.1.1"

val androidSdkDir: String? =
    providers.environmentVariable("ANDROID_SDK_ROOT").orNull
        ?: providers.environmentVariable("ANDROID_HOME").orNull

if (androidSdkDir != null && file(androidSdkDir).exists()) {
    val localProperties = rootProject.file("local.properties")
    if (!localProperties.exists()) {
        val sdkDirPropertyValue = file(androidSdkDir).absolutePath.replace("\\", "/")
        localProperties.writeText("sdk.dir=$sdkDirPropertyValue")
    }
}

kotlin {
    applyDefaultHierarchyTemplate()

    sourceSets.all {
        languageSettings.optIn("kotlin.time.ExperimentalTime")
        languageSettings.optIn("kotlin.concurrent.atomics.ExperimentalAtomicApi")
    }

    val xcf = XCFramework("LALRPOP")

    macosArm64 {
        binaries.framework {
            baseName = "LALRPOP"
            xcf.add(this)
        }
    }
    macosX64 {
        binaries.framework {
            baseName = "LALRPOP"
            xcf.add(this)
        }
    }
    linuxX64()
    mingwX64()
    iosArm64 {
        binaries.framework {
            baseName = "LALRPOP"
            xcf.add(this)
        }
    }
    iosX64 {
        binaries.framework {
            baseName = "LALRPOP"
            xcf.add(this)
        }
    }
    iosSimulatorArm64 {
        binaries.framework {
            baseName = "LALRPOP"
            xcf.add(this)
        }
    }
    js {
        browser()
        nodejs()
    }
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        nodejs()
    }

    androidLibrary {
        namespace = "io.github.kotlinmania.lalrpop"
        compileSdk = 34
        minSdk = 24
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
                implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.8")
            }
        }

        val commonTest by getting { dependencies { implementation(kotlin("test")) } }
    }
    jvmToolchain(21)
}

// ============================================================================
// ast_distance parity gate
// ============================================================================
//
// The build is gated on cross-language AST parity rather than compile-only
// success. The Kotlin compiler will happily green-light a port whose function
// bodies are stubs, whose identifiers diverge from upstream, or whose symbol
// coverage is incomplete — none of which are acceptable for a translation
// project. ast_distance's --deep + --symbol-parity passes catch all three.
//
// `gradle check` and `gradle build` short-circuit through `astDistanceParity`
// before any compile/test task runs. Failing the gate stops the build.
abstract class AstDistanceParityTask : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.InputFile
    abstract val astDistanceBinary: org.gradle.api.file.RegularFileProperty

    @get:org.gradle.api.tasks.InputDirectory
    abstract val rustSourceRoot: org.gradle.api.file.DirectoryProperty

    @get:org.gradle.api.tasks.InputDirectory
    abstract val kotlinPortRoot: org.gradle.api.file.DirectoryProperty

    @get:org.gradle.api.tasks.InputDirectory
    abstract val kotlinTestRoot: org.gradle.api.file.DirectoryProperty

    @get:org.gradle.api.tasks.OutputFile
    abstract val reportFile: org.gradle.api.file.RegularFileProperty

    @get:javax.inject.Inject
    abstract val execOps: org.gradle.process.ExecOperations

    @org.gradle.api.tasks.TaskAction
    fun run() {
        val bin = astDistanceBinary.get().asFile
        if (!bin.exists()) {
            throw org.gradle.api.GradleException(
                "ast_distance binary missing at ${bin.absolutePath}; " +
                    "build it with `(cd tools/ast_distance && cmake -B build -S . && cmake --build build && cp build/ast_distance .)`",
            )
        }
        val rust = rustSourceRoot.get().asFile
        if (!rust.exists()) {
            throw org.gradle.api.GradleException(
                "Rust source missing at ${rust.absolutePath}; rehydrate from upstream first.",
            )
        }
        val report = reportFile.get().asFile
        report.parentFile.mkdirs()

        // The Kotlin port is split across commonMain (production code) and
        // commonTest (test code). Symbol parity must check BOTH against the
        // upstream Rust tree to honour Sydney's "no fakery" gate, since the
        // tool only scans the path it's given. We materialise a temporary
        // staging directory whose contents are a flat union of both source
        // sets and run --symbol-parity / --deep against that.
        //
        // The staging directory lives outside `build/` because ast_distance's
        // `should_skip_path` filter drops anything under a Gradle build-output
        // tree (build/reports/, build/classes/, etc.) — placing the staging
        // there would zero out the file count and the gate would falsely
        // report 0/N instead of seeing the union.
        val staging = File(rust.parentFile.parentFile.parentFile, "parity-staging")
        if (staging.exists()) staging.deleteRecursively()
        staging.mkdirs()
        val mainRoot = kotlinPortRoot.get().asFile
        val testRoot = kotlinTestRoot.get().asFile
        if (mainRoot.exists()) {
            mainRoot.copyRecursively(File(staging, "commonMain"), overwrite = true)
        }
        if (testRoot.exists()) {
            testRoot.copyRecursively(File(staging, "commonTest"), overwrite = true)
        }

        // --deep
        val deepBuf = ByteArrayOutputStream()
        execOps.exec {
            workingDir = bin.parentFile.parentFile.parentFile
            commandLine(
                bin.absolutePath,
                "--deep",
                rust.absolutePath,
                "rust",
                staging.absolutePath,
                "kotlin",
            )
            standardOutput = deepBuf
            errorOutput = deepBuf
            isIgnoreExitValue = true
        }
        val deepReport = deepBuf.toString()
        report.writeText("========== --deep (commonMain ∪ commonTest) ==========\n\n$deepReport")

        // --symbol-parity --missing-only
        val parityBuf = ByteArrayOutputStream()
        execOps.exec {
            workingDir = bin.parentFile.parentFile.parentFile
            commandLine(
                bin.absolutePath,
                "--symbol-parity",
                rust.absolutePath,
                staging.absolutePath,
                "--missing-only",
            )
            standardOutput = parityBuf
            errorOutput = parityBuf
            isIgnoreExitValue = true
        }
        val parityReport = parityBuf.toString()
        report.appendText("\n\n========== --symbol-parity --missing-only (commonMain ∪ commonTest) ==========\n\n$parityReport")

        val ratioRegex = Regex("""Production definitions:\s+(\d+)/(\d+)""")
        val match = ratioRegex.find(parityReport)
            ?: throw org.gradle.api.GradleException(
                "Could not parse production-definition ratio from --symbol-parity output:\n\n$parityReport",
            )
        val matched = match.groupValues[1].toInt()
        val total = match.groupValues[2].toInt()
        val pct = if (total == 0) 100.0 else 100.0 * matched / total
        val pctStr = "%.1f".format(pct)

        // The "Extra (Kotlin-only):  N real + M stubs" line is always present.
        // The "Kotlin stubs detected: M" line only appears when M > 0, so we
        // can't rely on it to detect the stubs=0 case unambiguously. Use the
        // Extra-line regex for the canonical count.
        val stubsRegex = Regex("""Extra \(Kotlin-only\):\s+\d+\s+real\s*\+\s*(\d+)\s+stubs""")
        val stubs = stubsRegex.find(parityReport)?.groupValues?.get(1)?.toIntOrNull()
            ?: throw org.gradle.api.GradleException(
                "Could not parse stub count from --symbol-parity output:\n\n$parityReport",
            )

        // Supplementary symbols (constants, type aliases). The gate also
        // requires these to be 100% — Sydney's directive is "no fakery", and
        // a constant or type alias missing on the Kotlin side means the
        // translation skipped a symbol the upstream Rust author wrote.
        val supplementaryRegex = Regex("""Supplementary symbols?:\s+(\d+)/(\d+)""")
        val suppMatch = supplementaryRegex.find(parityReport)
            ?: throw org.gradle.api.GradleException(
                "Could not parse supplementary-symbol ratio from --symbol-parity output:\n\n$parityReport",
            )
        val suppMatched = suppMatch.groupValues[1].toInt()
        val suppTotal = suppMatch.groupValues[2].toInt()
        val suppPct = if (suppTotal == 0) 100.0 else 100.0 * suppMatched / suppTotal
        val suppPctStr = "%.1f".format(suppPct)

        // Test definitions. Same rule applies — a missing test means the
        // upstream wrote a #[test] fn that the Kotlin port skipped.
        val testRegex = Regex("""Test definitions:\s+(\d+)/(\d+)""")
        val testMatch = testRegex.find(parityReport)
            ?: throw org.gradle.api.GradleException(
                "Could not parse test-definition ratio from --symbol-parity output:\n\n$parityReport",
            )
        val testMatched = testMatch.groupValues[1].toInt()
        val testTotal = testMatch.groupValues[2].toInt()
        val testPct = if (testTotal == 0) 100.0 else 100.0 * testMatched / testTotal
        val testPctStr = "%.1f".format(testPct)

        logger.lifecycle(
            "ast_distance parity gate: " +
                "production=$matched/$total ($pctStr%), " +
                "supplementary=$suppMatched/$suppTotal ($suppPctStr%), " +
                "tests=$testMatched/$testTotal ($testPctStr%), " +
                "stubs=$stubs",
        )
        logger.lifecycle("Full report: ${report.absolutePath}")

        if (matched < total || suppMatched < suppTotal || testMatched < testTotal || stubs > 0) {
            throw org.gradle.api.GradleException(
                "ast_distance parity gate failed:\n" +
                    "  production:    $matched/$total ($pctStr%)\n" +
                    "  supplementary: $suppMatched/$suppTotal ($suppPctStr%)\n" +
                    "  tests:         $testMatched/$testTotal ($testPctStr%)\n" +
                    "  stubs:         $stubs\n" +
                    "The translation is incomplete; the build cannot pass until upstream\n" +
                    "production, supplementary, AND test symbol parity are 100% with zero\n" +
                    "stubs. See ${report.absolutePath} for the full report.",
            )
        }
    }
}

val astDistanceParity by tasks.registering(AstDistanceParityTask::class) {
    group = "verification"
    description = "Run ast_distance --deep + --symbol-parity; build short-circuits on insufficient parity."
    astDistanceBinary.set(layout.projectDirectory.file("tools/ast_distance/ast_distance"))
    rustSourceRoot.set(layout.projectDirectory.dir("tmp/lalrpop-rs/lalrpop/src"))
    kotlinPortRoot.set(layout.projectDirectory.dir("src/commonMain/kotlin/io/github/kotlinmania/lalrpop"))
    kotlinTestRoot.set(layout.projectDirectory.dir("src/commonTest/kotlin/io/github/kotlinmania/lalrpop"))
    reportFile.set(layout.buildDirectory.file("reports/ast_distance/parity.txt"))
}

// Short-circuit EVERY build target: `build`, `assemble`, `check`, `test`,
// every `compile*`, every `link*`, every klibrary/jar/framework producer,
// the publish chain — all of them must wait for ast_distance to declare the
// translation complete. The Kotlin compiler is no longer a green light; it
// only runs after the parity gate has approved the source.
tasks.configureEach {
    val n = name
    val isOurOwnTask = this == astDistanceParity.get()
    val isHelpOrInternal = n in setOf(
        "help", "tasks", "projects", "properties", "components", "dependencies",
        "dependencyInsight", "outgoingVariants", "buildEnvironment", "model",
        "kotlinDslAccessorsReport", "javaToolchains", "wrapper", "init", "clean",
        "prepareKotlinIdeaImport", "prepareKotlinBuildScriptModel",
    )
    val isAstDistanceItself = n.startsWith("astDistance")
    val isProblemsReport = n.startsWith("checkKotlinGradlePluginConfigurationErrors")
    val gateRelevant = !isOurOwnTask && !isHelpOrInternal && !isAstDistanceItself && !isProblemsReport && (
        n.startsWith("compile") ||
            n.startsWith("link") ||
            n.startsWith("assemble") ||
            n == "build" ||
            n == "check" ||
            n.endsWith("Test") ||
            n.endsWith("Tests") ||
            n.endsWith("Klibrary") ||
            n.endsWith("Jar") ||
            n.endsWith("Framework") ||
            n.endsWith("XCFramework") ||
            n.startsWith("publish") ||
            n.startsWith("sign") ||
            n.startsWith("kotlinNpm") ||
            n.startsWith("processResources") ||
            n.startsWith("processTestResources")
    )
    if (gateRelevant) {
        dependsOn(astDistanceParity)
    }
}


mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    coordinates(group.toString(), "lalrpop-kotlin", version.toString())

    pom {
        name.set("lalrpop-kotlin")
        description.set("Kotlin Multiplatform port of LALRPOP - LR(1) parser generator")
        inceptionYear.set("2026")
        url.set("https://github.com/KotlinMania/lalrpop-kotlin")

        licenses {
            license {
                name.set("Apache-2.0")
                url.set("https://opensource.org/licenses/Apache-2.0")
                distribution.set("repo")
            }
            license {
                name.set("MIT")
                url.set("https://opensource.org/licenses/MIT")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("sydneyrenee")
                name.set("Sydney Renee")
                email.set("sydney@solace.ofharmony.ai")
                url.set("https://github.com/sydneyrenee")
            }
        }

        scm {
            url.set("https://github.com/KotlinMania/lalrpop-kotlin")
            connection.set("scm:git:git://github.com/KotlinMania/lalrpop-kotlin.git")
            developerConnection.set("scm:git:ssh://github.com/KotlinMania/lalrpop-kotlin.git")
        }
    }
}
