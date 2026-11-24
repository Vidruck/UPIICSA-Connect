plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
    id("com.android.library")
}

kotlin {
    jvm("desktop")
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                api(compose.materialIconsExtended)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        // [DECISIÓN TÉCNICA]: Conjunto intermedio JVM (escritorio + Android) para compartir el canal de sockets java.net sin duplicarlo.
        val jvmCommonMain by creating {
            dependsOn(commonMain)
        }
        val desktopMain by getting {
            dependsOn(jvmCommonMain)
        }
        val androidMain by getting {
            dependsOn(jvmCommonMain)
        }
        val desktopTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
            }
        }
    }
}

android {
    namespace = "mx.ipn.upiicsa.connect.shared"
    compileSdk = 34
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
