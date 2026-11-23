import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

val rustCoreDir = rootProject.layout.projectDirectory.dir("core-rust")
val nativeCoreFileName = when {
    System.getProperty("os.name").lowercase().contains("win") -> "upiicsa_core_rs.dll"
    System.getProperty("os.name").lowercase().contains("mac") -> "libupiicsa_core_rs.dylib"
    else -> "libupiicsa_core_rs.so"
}
val nativeCoreLibrary = rustCoreDir.file("target/release/$nativeCoreFileName").asFile

kotlin {
    jvm("desktop")

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(project(":shared"))
                implementation(compose.desktop.currentOs)
                implementation(compose.material3)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")
                implementation("net.java.dev.jna:jna:5.15.0")
            }
        }
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            }
        }
    }
}

tasks.register<Exec>("buildRustCore") {
    group = "upiicsa"
    description = "Compila core-rust en modo release para que el escritorio lo cargue vía JNA."
    workingDir = rustCoreDir.asFile
    commandLine("cargo", "build", "--release")
}

tasks.withType<Test>().configureEach {
    systemProperty("upiicsa.core.lib.path", nativeCoreLibrary.absolutePath)
    systemProperty("upiicsa.screenshots.dir", layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
    systemProperty("upiicsa.sources.dir", layout.projectDirectory.dir("src/desktopMain/kotlin").asFile.absolutePath)
}

compose.desktop {
    application {
        mainClass = "mx.ipn.upiicsa.connect.desktop.bootstrap.DesktopApplicationKt"
        jvmArgs += listOf("-Dupiicsa.core.lib.path=${nativeCoreLibrary.absolutePath}")

        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Msi)
            packageName = "UPIICSA-Connect"
            packageVersion = "1.0.0"
            description = "Convergencia PC - Android UPIICSA-IPN"
            vendor = "Equipo UPIICSA-Connect"
        }
    }
}
