import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val appVersionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
val appVersionName = (project.findProperty("versionName") as String?) ?: "1.0-dev"
val keystorePath: String? = System.getenv("SIGNING_KEYSTORE_PATH")

android {
    namespace = "com.blacksingularities.openamp.wear"
    compileSdk = 36

    defaultConfig {
        // Ten sam identyfikator co na telefonie – Data Layer łączy tylko aplikacje o tej samej nazwie pakietu.
        applicationId = "com.blacksingularities.openamp"
        minSdk = 30
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            if (keystorePath != null && file(keystorePath).exists()) {
                storeFile = file(keystorePath)
                storeType = "pkcs12"
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (keystorePath != null && file(keystorePath).exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    // Wspólny opis protokołu (ten sam plik kompiluje moduł :app).
    sourceSets["main"].java.srcDir("../wearcommon/src")
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.01.00")
    val wearCompose = "1.4.1"
    val protoLayout = "1.2.1"

    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")

    implementation("androidx.wear.compose:compose-material:$wearCompose")
    implementation("androidx.wear.compose:compose-foundation:$wearCompose")
    implementation("androidx.wear:wear:1.3.0")

    // Kafelek i komplikacja na tarczę.
    implementation("androidx.wear.tiles:tiles:1.4.1")
    implementation("androidx.wear.protolayout:protolayout:$protoLayout")
    implementation("androidx.wear.protolayout:protolayout-material:$protoLayout")
    implementation("androidx.wear.protolayout:protolayout-expression:$protoLayout")
    implementation("androidx.wear.watchface:watchface-complications-data-source-ktx:1.2.1")

    // Połączenie z telefonem.
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.google.guava:guava:33.4.0-android")
}
