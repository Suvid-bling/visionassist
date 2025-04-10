

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.example.visionassist"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.visionassist"
        minSdk = 34
        targetSdk = 34
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
//project.ext.ASSET_DIR = projectDir.toString() + "/src/main/assets"
//project.ext.TEST_ASSETS_DIR = projectDir.toString() + "/src/androidTest/assets"

dependencies {
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.preference)
    val camerax_version = "1.2.1"
    implementation("androidx.camera:camera-camera2:$camerax_version")
    implementation ("com.google.mlkit:object-detection:17.0.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.camera.view)
    implementation(libs.litert)
    implementation(libs.litert.support.api)
    implementation(libs.androidx.camera.lifecycle)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}