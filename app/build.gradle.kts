// --- add imports cho Kotlin DSL ---
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// Đọc khóa từ local.properties ở ROOT project (cùng cấp settings.gradle)
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) {
        f.inputStream().use { stream -> load(stream) }
    }
}
val aiKey: String = localProps.getProperty("GOOGLE_AI_KEY") ?: ""

android {
    namespace = "com.example.weatherforecast"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.weatherforecast"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Xuất hằng BuildConfig.GOOGLE_AI_KEY cho code Java dùng
        buildConfigField("String", "GOOGLE_AI_KEY", "\"$aiKey\"")
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

    // đảm bảo BuildConfig được sinh ra
    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)

    // RecyclerView (phòng khi chưa kéo transitively)
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Retrofit/Gson/OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    annotationProcessor("androidx.room:room-compiler:2.6.1")

    // SwipeRefreshLayout
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // Google Play services – Location
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // MPAndroidChart
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
