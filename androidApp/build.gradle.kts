plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

val rustCoreDir = rootProject.layout.projectDirectory.dir("core-rust")
val generatedJniLibsDir = layout.buildDirectory.dir("generated/jniLibs")
val buildRustCoreForAndroid = providers.gradleProperty("upiicsa.buildRustCore").map { it.toBoolean() }.getOrElse(false)
val releaseKeystorePath = providers.environmentVariable("UPIICSA_KEYSTORE_PATH").orNull

android {
    namespace = "mx.ipn.upiicsa.connect.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "mx.ipn.upiicsa.connect.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0-alpha"
    }

    signingConfigs {
        create("release") {
            if (releaseKeystorePath != null) {
                storeFile = file(releaseKeystorePath)
                storePassword = providers.environmentVariable("UPIICSA_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("UPIICSA_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("UPIICSA_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (releaseKeystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    sourceSets {
        getByName("main").jniLibs.srcDir(generatedJniLibsDir)
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}

val buildRustAndroidCore = tasks.register<Exec>("buildRustAndroidCore") {
    group = "upiicsa"
    description = "Compila core-rust con cargo-ndk (arm64-v8a, armeabi-v7a, x86, x86_64) y lo deja en build/generated/jniLibs."
    workingDir = rustCoreDir.asFile
    commandLine(
        "cargo", "ndk",
        "-t", "arm64-v8a", "-t", "armeabi-v7a", "-t", "x86", "-t", "x86_64",
        "-P", "26",
        "-o", generatedJniLibsDir.get().asFile.absolutePath,
        "build", "--release"
    )
}

if (buildRustCoreForAndroid) {
    tasks.named("preBuild") { dependsOn(buildRustAndroidCore) }
}
