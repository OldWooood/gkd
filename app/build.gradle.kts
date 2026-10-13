import com.android.build.api.variant.impl.VariantOutputImpl
import li.songe.gradle.GitInfo
import li.songe.gradle.buildProperty
import li.songe.gradle.gitInfo
import li.songe.gradle.readDebugSuffixResources
import kotlin.reflect.full.declaredMemberProperties

val gitInfo = project.gitInfo
val debugSuffixResources = project.readDebugSuffixResources()

val keystoreProperties: Map<String, String> = rootProject.file("key.properties")
    .takeIf { it.exists() }
    ?.readLines()
    ?.mapNotNull { line ->
        line.substringBefore('#').split('=', limit = 2)
            .takeIf { it.size == 2 }
            ?.let { (k, v) -> k.trim() to v.trim() }
            ?.takeIf { (_, v) -> v.isNotEmpty() }
    }
    ?.toMap()
    ?: emptyMap()

fun signingValue(vararg names: String): String? {
    return names.firstNotNullOfOrNull { name ->
        System.getenv(name)?.takeIf { it.isNotBlank() }
            ?: keystoreProperties[name]
            ?: providers.gradleProperty(name).orNull?.takeIf { it.isNotBlank() }
    }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.androidx.room)
    alias(libs.plugins.kotlin.parcelize)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlinx.atomicfu)
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.remap)
}

android {
    namespace = "li.songe.gkd"
    defaultConfig {
        applicationId = "li.songe.gkd"
        versionCode = 108
        versionName = "1.13.15"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        androidResources {
            localeFilters += listOf("zh-rCN", "en")
        }
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }

        GitInfo::class.declaredMemberProperties.onEach {
            manifestPlaceholders[it.name] = it.get(gitInfo) ?: ""
        }
    }

    buildFeatures {
        compose = true
        aidl = true
        resValues = true
    }

    val gkdStoreFile = signingValue("GKD_STORE_FILE", "storeFile")
    val gkdSigningConfig = if (gkdStoreFile != null) {
        signingConfigs.create("gkd") {
            storeFile = file(gkdStoreFile)
            storePassword = signingValue("GKD_STORE_PASSWORD", "storePassword")
            keyAlias = signingValue("GKD_KEY_ALIAS", "keyAlias")
            keyPassword = signingValue("GKD_KEY_PASSWORD", "keyPassword")
        }
    } else {
        signingConfigs.getByName("debug")
    }

    buildTypes {
        all {
            vcsInfo.include = false
            versionNameSuffix = gitInfo.versionNameSuffix
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            signingConfig = gkdSigningConfig
            applicationIdSuffix = ".debug"
            resValue("color", "better_black", "#FF5D92")
            for ((name, value) in debugSuffixResources) {
                resValue("string", name, value)
            }
        }
    }
    productFlavors {
        flavorDimensions += "channel"
        create("gkd") {
            isDefault = true
            signingConfig = gkdSigningConfig
            resValue("bool", "is_accessibility_tool", "true")
        }
        all {
            dimension = flavorDimensions.first()
            manifestPlaceholders["channel"] = name
            manifestPlaceholders["buildKey"] = gitInfo.buildKey(name)
        }
    }
    // https://github.com/LSPosed/AndroidHiddenApiBypass
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    // https://priv-kit.pages.dev/zh/guide/getting-started#native-library-packaging
    packaging.jniLibs.useLegacyPackaging = true
    packaging.resources.excludes += setOf(
        "META-INF/**",
        "DebugProbesKt.bin",
    )
}

if (buildProperty("GKD_RENAME_APK_FLAG").isPresent) {
    androidComponents.onVariants { variant ->
        variant.outputs.onEach { output ->
            output as VariantOutputImpl
            output.outputFileName = "gkd-v${output.versionName.get()}.apk"
        }
    }
}

// https://developer.android.com/jetpack/androidx/releases/room?hl=zh-cn#compiler-options
room {
    schemaDirectory("$projectDir/schemas")
}

composeCompiler {
    reportsDestination = layout.buildDirectory.dir("compose_compiler")
    stabilityConfigurationFiles.addAll(
        rootProject.layout.projectDirectory.file("stability_config.conf"),
    )
}

dependencies {
    implementation(libs.kotlin.stdlib)

    implementation(project(":selector"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)

    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.animation)
    implementation(libs.compose.animation.graphics)
    implementation(libs.compose.icons)
    implementation(libs.compose.preview)
    debugImplementation(libs.compose.tooling)
    androidTestImplementation(libs.compose.junit4)

    implementation(libs.compose.activity)
    implementation(libs.compose.material3)

    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso)

    // AndroidTest shares this runtime dependency with the app and requires the newer version.
    implementation(libs.androidx.concurrent.futures)

    compileOnly(project(":hidden-api"))
    implementation(libs.rikka.shizuku.api)
    implementation(libs.rikka.shizuku.provider)
    implementation(libs.priv.kit.core)
    implementation(libs.lsposed.hiddenapibypass)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.google.accompanist.drawablepainter)

    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)
    // https://github.com/Kotlin/kotlinx-atomicfu/issues/145
    implementation(libs.kotlinx.atomicfu)

    implementation(libs.exp4j)

    implementation(libs.json5)
}
