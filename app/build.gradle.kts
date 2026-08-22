import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// The google-services plugin hard-fails when google-services.json is absent.
// We apply it only when the file has actually been supplied, so that a fresh
// clone (which has no secrets) still builds and can run the `demo` flavour.
// See the README.
val googleServicesJson = file("google-services.json")
if (googleServicesJson.exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}

// Signing credentials come from keystore.properties. When it is absent the
// release build falls back to the debug signing config so that
// `assembleRelease` still produces an installable APK instead of failing —
// but one signed with a different key, which will not install over a build
// signed with the release key.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

val localPropertiesFile = rootProject.file("local.properties")
val localProperties = Properties().apply {
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use { load(it) }
    }
}

// Optional override for the Realtime Database URL.
//
// google-services.json only carries `firebase_url` once the Realtime Database
// has been created, and a file downloaded before that step omits it entirely,
// which fails at runtime rather than at build time. Supplying the URL here
// makes the configuration explicit and survives a stale downloaded file.
//
//   local.properties:  rtdb.url=https://<project>-default-rtdb.<region>.firebasedatabase.app
//   or:  ./gradlew assembleLiveDebug -PRTDB_URL=https://...
val realtimeDatabaseUrl: String =
    (project.findProperty("RTDB_URL") as String?)
        ?: localProperties.getProperty("rtdb.url")
        ?: ""
val hasReleaseKeystore = keystoreProperties.containsKey("storeFile") &&
    rootProject.file(keystoreProperties.getProperty("storeFile")).exists()

android {
    namespace = "lk.ac.ucsc.scs3311.smarthome"
    compileSdk = 36

    defaultConfig {
        applicationId = "lk.ac.ucsc.scs3311.smarthome"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        buildConfigField("String", "DATABASE_URL", "\"$realtimeDatabaseUrl\"")
    }

    // `demo` runs entirely on a FakeRepository so the demo video can be recorded
    // with no network and no Firebase project. `live` talks to Realtime Database.
    flavorDimensions += "backend"
    productFlavors {
        create("demo") {
            dimension = "backend"
            applicationIdSuffix = ".demo"
            versionNameSuffix = "-demo"
            buildConfigField("Boolean", "USE_FAKE_BACKEND", "true")
            resValue("string", "app_name", "HomeSense Demo")
        }
        create("live") {
            dimension = "backend"
            buildConfigField("Boolean", "USE_FAKE_BACKEND", "false")
            resValue("string", "app_name", "HomeSense")
        }
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (hasReleaseKeystore) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "META-INF/LICENSE.md",
            "META-INF/LICENSE-notice.md",
        )
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
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.database)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.messaging)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
    // Robolectric + androidx.test lets the Room DAO tests taught in the course
    // (Room.inMemoryDatabaseBuilder, @RunWith(AndroidJUnit4::class)) run as
    // ordinary JVM unit tests — no emulator, so CI can actually execute them.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.room.testing)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
