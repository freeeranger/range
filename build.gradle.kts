// Declared here (not applied) so every module shares one classloader and one version of each plugin.
// The Kotlin Android plugin is never applied: AGP 9 compiles Kotlin itself, this only pins the Kotlin version it uses.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room3) apply false
}
