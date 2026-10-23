import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class RangeApplicationPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("com.android.application")

        extensions.configure<ApplicationExtension> {
            configureRangeAndroid(this)
            defaultConfig.targetSdk = RangeSdk.TARGET

            buildTypes.getByName("release") {
                isMinifyEnabled = true
                isShrinkResources = true
                proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
                // Lets release builds install on a phone for testing until real signing keys exist.
                // F-Droid ignores this and signs with its own key.
                signingConfig = signingConfigs.getByName("debug")
            }

            // Lists the app's languages for Android's per-app language setting, and drops the
            // dozens of translations AndroidX libraries carry for languages Range doesn't support.
            // Each app needs res/resources.properties naming its default language.
            androidResources.generateLocaleConfig = true
            androidResources.localeFilters += listOf("en", "sv")

            // F-Droid rejects the encrypted dependency blob Google adds for the Play Store.
            dependenciesInfo {
                includeInApk = false
                includeInBundle = false
            }
        }
    }
}
