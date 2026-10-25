plugins {
    alias(libs.plugins.range.android.library)
}

android {
    namespace = "dev.freeranger.range.ui"
}

dependencies {
    // Apps build their screens from these, so expose them instead of making every app re-declare them.
    api(platform(libs.compose.bom))
    api(libs.compose.foundation)
    api(libs.compose.ui)
    // Range apps are built from Material 3 components so they feel native; range-ui themes them.
    api(libs.compose.material3)
    // Draws charts (Apache 2.0); RangeLineChart themes it.
    implementation(libs.vico.compose)
    // Picks the files backups are saved to and restored from.
    implementation(libs.androidx.activity.compose)
    // Stores the appearance settings every Range app shares.
    implementation(libs.androidx.datastore.preferences)
}
