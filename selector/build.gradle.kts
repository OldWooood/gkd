plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm {}
    sourceSets {
        commonMain {
            dependencies {
                implementation(libs.kotlin.stdlib)
            }
        }
        jvmTest {
            dependencies {
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlin.test)
            }
        }
    }
}
