plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    // compileOnly: the root build puts the real plugins on the classpath, so all modules share one copy.
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.compose.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = libs.plugins.range.android.application.get().pluginId
            implementationClass = "RangeApplicationPlugin"
        }
        register("androidLibrary") {
            id = libs.plugins.range.android.library.get().pluginId
            implementationClass = "RangeLibraryPlugin"
        }
    }
}
