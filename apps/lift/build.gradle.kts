plugins {
    alias(libs.plugins.range.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room3)
}

android {
    namespace = "dev.freeranger.range.lift"

    defaultConfig {
        applicationId = "dev.freeranger.range.lift"
        versionCode = 1
        versionName = "0.1.0"
    }

    // The migration test reads old schemas as assets. Robolectric only sees the app's own merged
    // assets, so they go in debug builds (a few KB); release builds never include them.
    sourceSets.getByName("debug").assets.directories.add("$projectDir/schemas")
}

room3 {
    // Committed, so every schema change is reviewable and migrations can be tested against old versions.
    schemaDirectory("$projectDir/schemas")
}

roborazzi {
    // Committed, so screenshots of every screen are reviewable without building the app.
    outputDir = file("screenshots")
}

dependencies {
    implementation(projects.rangeUi)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.json)
    // Dragging a workout's exercises into a new order (Apache 2.0).
    implementation(libs.reorderable)

    implementation(libs.room3.runtime)
    implementation(libs.sqlite.framework)
    ksp(libs.room3.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.room3.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
