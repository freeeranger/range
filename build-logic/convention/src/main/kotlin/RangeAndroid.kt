import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.KotlinBaseExtension

/** One place for the SDK levels every Range app and library builds against. */
internal object RangeSdk {
    const val COMPILE = 37
    const val COMPILE_MINOR = 2
    const val TARGET = 37

    /** Android 12: the first release with the exact-alarm permission model we rely on. */
    const val MIN = 31

    const val JDK = 21
}

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** Shared Android + Kotlin + Compose setup for both apps and libraries. */
internal fun Project.configureRangeAndroid(android: CommonExtension) {
    pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

    android.apply {
        compileSdk = RangeSdk.COMPILE
        compileSdkMinor = RangeSdk.COMPILE_MINOR
        defaultConfig.minSdk = RangeSdk.MIN
        compileOptions.sourceCompatibility = JavaVersion.toVersion(RangeSdk.JDK)
        compileOptions.targetCompatibility = JavaVersion.toVersion(RangeSdk.JDK)
        buildFeatures.compose = true
        testOptions.unitTests.isIncludeAndroidResources = true
        // Robolectric's Android 16+ images reach into this JDK-internal package.
        testOptions.unitTests.all { it.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED") }
    }

    extensions.configure<KotlinBaseExtension> {
        jvmToolchain(RangeSdk.JDK)
    }

    dependencies {
        val bom = platform(libs.findLibrary("compose-bom").get())
        add("implementation", bom)
        add("testImplementation", bom)
        add("implementation", libs.findLibrary("compose-foundation").get())
        add("implementation", libs.findLibrary("compose-ui-tooling-preview").get())
        add("debugImplementation", libs.findLibrary("compose-ui-tooling").get())
    }
}
