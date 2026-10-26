plugins {
    alias(libs.plugins.range.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "dev.freeranger.range.calc"

    defaultConfig {
        applicationId = "dev.freeranger.range.calc"
        versionCode = 1
        versionName = "0.1.0"
    }
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
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
