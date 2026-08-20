import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// ---------------------------------------------------------------------------
// Aven development configuration, read from gitignored local.properties.
// API key defaults are exposed ONLY to debug builds and are seeded into the
// Android Keystore-backed secure store on first launch. They never appear in
// release builds, source code, or version control.
// ---------------------------------------------------------------------------
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun String.safe() = replace("\\", "\\\\").replace("\"", "\\\"")

val defaultZenKey: String = localProperties.getProperty("AVEN_DEFAULT_ZEN_API_KEY", "")
val defaultOpenRouterKey: String = localProperties.getProperty("AVEN_DEFAULT_OPENROUTER_API_KEY", "")
val defaultZaiKey: String = localProperties.getProperty("AVEN_DEFAULT_ZAI_API_KEY", "")
val bundleModel: Boolean = localProperties.getProperty("AVEN_BUNDLE_MODEL", "false").toBoolean()

android {
    namespace = "com.aven.ai"
    compileSdk {
        version = release(37)
    }

    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.aven.ai"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("boolean", "BUNDLE_MODEL", "$bundleModel")

        ndk {
            // Local inference targets arm64-v8a (x86_64 can be enabled for emulator use).
            abiFilters += listOf("arm64-v8a")
        }

        externalNativeBuild {
            cmake {
                // llama.cpp options: CPU-only, no OpenMP/llamafile/curl/openssl, no tools/examples.
                arguments += listOf(
                    "-DCMAKE_BUILD_TYPE=Release",
                    "-DLLAMA_BUILD_COMMON=OFF",
                    "-DLLAMA_BUILD_TESTS=OFF",
                    "-DLLAMA_BUILD_TOOLS=OFF",
                    "-DLLAMA_BUILD_EXAMPLES=OFF",
                    "-DLLAMA_BUILD_SERVER=OFF",
                    "-DLLAMA_BUILD_APP=OFF",
                    "-DLLAMA_BUILD_UI=OFF",
                    "-DLLAMA_OPENSSL=OFF",
                    "-DLLAMA_CURL=OFF",
                    "-DGGML_CURL=OFF",
                    "-DGGML_NATIVE=OFF",
                    "-DGGML_OPENMP=OFF",
                    "-DGGML_BLAS=OFF",
                    "-DGGML_LLAMAFILE=OFF",
                    "-DGGML_CPU_ALL_VARIANTS=OFF"
                )
                targets += "aven_llama"
            }
        }
    }

    buildTypes {
        debug {
            // Personal-debug-only default API keys (gitignored local.properties).
            // These are seeded into Keystore-backed secure storage on first run.
            buildConfigField("String", "DEFAULT_ZEN_API_KEY", "\"${defaultZenKey.safe()}\"")
            buildConfigField("String", "DEFAULT_OPENROUTER_API_KEY", "\"${defaultOpenRouterKey.safe()}\"")
            buildConfigField("String", "DEFAULT_ZAI_API_KEY", "\"${defaultZaiKey.safe()}\"")
        }
        release {
            // Release builds NEVER carry API key defaults.
            buildConfigField("String", "DEFAULT_ZEN_API_KEY", "\"\"")
            buildConfigField("String", "DEFAULT_OPENROUTER_API_KEY", "\"\"")
            buildConfigField("String", "DEFAULT_ZAI_API_KEY", "\"\"")
            optimization {
                enable = true
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    externalNativeBuild {
        cmake {
            version = "3.22.1"
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.splashscreen)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)

    // Persistence / data layer
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Networking / serialization
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Unit tests (JVM + Robolectric)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.mockwebserver)

    // Instrumented tests (compiled; device execution deferred)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}