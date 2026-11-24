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
val hostResourceFolder = run {
    val os = System.getProperty("os.name").lowercase()
    val arch = if (System.getProperty("os.arch").lowercase().contains("aarch64")) "arm64" else "x64"
    when {
        os.contains("win") -> "windows-$arch"
        os.contains("mac") -> "macos-$arch"
        else -> "linux-$arch"
    }
}
val nativeResourcesRoot = layout.buildDirectory.dir("nativeResources")

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

val stageRustCore = tasks.register<Copy>("stageRustCore") {
    group = "upiicsa"
    description = "Copia el binario de core-rust a los recursos de la app para empaquetarlo en .deb / .msi / distribuible."
    dependsOn("buildRustCore")
    from(nativeCoreLibrary)
    into(nativeResourcesRoot.map { it.dir(hostResourceFolder) })
}

tasks.matching { it.name == "prepareAppResources" }.configureEach {
    dependsOn(stageRustCore)
}

tasks.withType<Test>().configureEach {
    systemProperty("java.awt.headless", "true")
    systemProperty("upiicsa.core.lib.path", nativeCoreLibrary.absolutePath)
    systemProperty("upiicsa.screenshots.dir", layout.buildDirectory.dir("screenshots").get().asFile.absolutePath)
    systemProperty("upiicsa.sources.dir", layout.projectDirectory.dir("src/desktopMain/kotlin").asFile.absolutePath)
}

compose.desktop {
    application {
        mainClass = "mx.ipn.upiicsa.connect.desktop.bootstrap.DesktopApplicationKt"

        nativeDistributions {
            targetFormats(TargetFormat.Deb, TargetFormat.Msi)
            packageName = "UPIICSA-Connect"
            packageVersion = "0.1.0"
            description = "Convergencia PC - Android UPIICSA-IPN"
            vendor = "Equipo UPIICSA-Connect"
            copyright = "Todos los derechos reservados al equipo desarrollador."
            appResourcesRootDir.set(nativeResourcesRoot)
            modules("java.instrument", "java.management", "java.naming", "jdk.unsupported")

            linux {
                packageName = "upiicsa-connect"
                shortcut = true
                menuGroup = "Network"
                appCategory = "net"
                iconFile.set(rootProject.file("packaging/icons/upiicsa-connect.png"))
            }

            windows {
                menu = true
                menuGroup = "UPIICSA-Connect"
                shortcut = true
                perUserInstall = true
                dirChooser = true
                upgradeUuid = "6f3d2a8e-5b1c-4e7a-9c42-0d8b7e1f5a63"
                iconFile.set(rootProject.file("packaging/icons/upiicsa-connect.ico"))
            }
        }
    }
}
