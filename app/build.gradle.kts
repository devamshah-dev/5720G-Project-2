plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.plugin.serialization") // Apply the serialization plugin
}

android {
    namespace = "com.devamshah.p2pwifimanagerroot"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.devamshah.p2pwifimanagerroot"
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    // Kotlin standard library and coroutines
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.1.20") // Same Kotlin version as plugin
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2") // Latest stable coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // AndroidX Core & Lifecycle Libraries
    implementation("androidx.lifecycle:lifecycle-service:2.9.1") // For LifecycleService
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1") // For lifecycleScope in services
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.9.1") // For LiveData to communicate service status to UI

    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json) // Latest stable serialization

    implementation(libs.material) // Latest stable Material Design components
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.work:work-runtime-ktx:2.10.2")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.work.runtime.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}