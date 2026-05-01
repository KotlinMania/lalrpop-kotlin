import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework
import org.jetbrains.kotlin.gradle.swiftexport.ExperimentalSwiftExportDsl
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

plugins {
    kotlin("multiplatform") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
    id("com.android.kotlin.multiplatform.library") version "9.2.0"
    id("com.vanniktech.maven.publish") version "0.30.0"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("dev.detekt") version "2.0.0-alpha.3"
}

group = "io.github.kotlinmania"
version = "0.1.4"

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

    compilerOptions {
        allWarningsAsErrors.set(true)
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

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

    @OptIn(ExperimentalSwiftExportDsl::class)
    swiftExport {
        moduleName = "LALRPOP"
        flattenPackage = "io.github.kotlinmania.lalrpop"
        configure {
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
    }

    android {
        namespace = "io.github.kotlinmania.lalrpop"
        compileSdk = 34
        minSdk = 24
        withHostTestBuilder {}.configure {}
        withDeviceTestBuilder {
            sourceSetTreeName = "test"
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
                implementation("org.jetbrains.kotlinx:kotlinx-collections-immutable:0.3.8")
                implementation("io.github.kotlinmania:btree-kotlin:0.1.2")
            }
        }

        val commonTest by getting { dependencies { implementation(kotlin("test")) } }
        val posixMainPath = "src/posixMain/kotlin"
        val linuxX64Main by getting {
            kotlin.srcDir(posixMainPath)
        }
        val macosArm64Main by getting {
            kotlin.srcDir(posixMainPath)
        }
        val macosX64Main by getting {
            kotlin.srcDir(posixMainPath)
        }
        val iosArm64Main by getting {
            kotlin.srcDir(posixMainPath)
        }
        val iosX64Main by getting {
            kotlin.srcDir(posixMainPath)
        }
        val iosSimulatorArm64Main by getting {
            kotlin.srcDir(posixMainPath)
        }
    }
    jvmToolchain(21)
}

// The build gate is `./gradlew test` — the ported Rust tests must pass on
// the same inputs the Rust tests use.

// ApiTest (port of api/test.rs) uses `apiSetCurrentDir("./src/api/test_files")`
// from the project root, matching the upstream crate-root test working
// directory. Native test executables otherwise launch from
// `build/bin/<target>/debugTest/`, so set their working directory back to
// `rootDir` for parity with those fixture paths.
tasks
    .withType(org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeTest::class.java)
    .configureEach {
        workingDir = rootDir.absolutePath
    }

ktlint {
    version.set("1.8.0")
    enableExperimentalRules.set(true)
    outputToConsole.set(true)
    ignoreFailures.set(false)
    reporters {
        reporter(ReporterType.PLAIN)
        reporter(ReporterType.CHECKSTYLE)
        reporter(ReporterType.HTML)
        reporter(ReporterType.SARIF)
    }
    filter {
        exclude("**/build/**")
    }
}

detekt {
    toolVersion = "2.0.0-alpha.3"
    buildUponDefaultConfig = true
    allRules = true
    parallel = true
    ignoreFailures = false
    failOnSeverity = dev.detekt.gradle.extensions.FailOnSeverity.Warning
    source.setFrom(
        "src/commonMain/kotlin",
        "src/commonTest/kotlin",
        "src/nativeMain/kotlin",
        "src/jsMain/kotlin",
        "src/wasmJsMain/kotlin",
        "src/androidMain/kotlin",
    )
    basePath.set(projectDir)
}

tasks
    .withType<dev.detekt.gradle.Detekt>()
    .configureEach {
        jvmTarget.set("21")
        reports {
            html.required.set(true)
            markdown.required.set(true)
            sarif.required.set(true)
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
