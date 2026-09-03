plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// Firebase (notificaciones push) solo se activa si existe app/google-services.json.
// Sin el archivo la app compila y funciona igual, pero sin push.
val hasFirebase = file("google-services.json").exists()
if (hasFirebase) apply(plugin = "com.google.gms.google-services")

android {
    namespace = "com.pablobertino.tagmap"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.pablobertino.tagmap"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.3.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Proyecto Naima, schema `tagmap`. La anon key es pública por diseño (RLS protege los datos).
        buildConfigField("String", "SUPABASE_URL", "\"https://rlaxxavhzrrrmjrkymlm.supabase.co\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"sb_publishable_W8ACK0W--geUC-H67-DGpg_JkT67QAu\"")
        buildConfigField("String", "SUPABASE_SCHEMA", "\"tagmap\"")
        // Estilo de mapa sin clave (OpenFreeMap, vector tiles de OpenStreetMap)
        buildConfigField("String", "MAP_STYLE_URL", "\"https://tiles.openfreemap.org/styles/liberty\"")
        buildConfigField("boolean", "HAS_FIREBASE", hasFirebase.toString())
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

base {
    archivesName.set("TagMap")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.auth)
    implementation(libs.ktor.client.okhttp)

    implementation(libs.maplibre)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    implementation(libs.kotlinx.coroutines.play.services)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
