plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}
android {
    namespace = "dev.tara.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.voice.offline"
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0-phase1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    // Preserve the old monolith as source history without shipping a second execution path.
    sourceSets {
        getByName("main") {
            java.setSrcDirs(listOf("src/phase1/kotlin"))
            assets.setSrcDirs(emptyList<String>())
            res.setSrcDirs(listOf("src/phase1/res"))
            manifest.srcFile("src/phase1/AndroidManifest.xml")
        }
        getByName("test").java.setSrcDirs(listOf("src/phase1Test/kotlin"))
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlin { jvmToolchain(17) }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    lint { abortOnError = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
kapt { correctErrorTypes = true }
dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:audio"))
    implementation(project(":core:permissions"))
    implementation(project(":core:designsystem"))
    implementation(project(":platform:androidactions"))
    implementation(project(":data:settings"))
    implementation(project(":feature:assistant"))
    implementation(project(":feature:onboarding"))
    implementation(project(":feature:privacy"))
    implementation(project(":feature:settings"))
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    androidTestImplementation(libs.android.test.espresso)
    androidTestImplementation(libs.android.test.runner)
    androidTestImplementation(libs.android.test.junit)
    debugImplementation(libs.compose.test.manifest)
}
