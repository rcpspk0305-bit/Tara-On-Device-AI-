plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}
android {
    namespace = "dev.tara.data.settings"
    compileSdk = 36
    defaultConfig { minSdk = 29; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlin { jvmToolchain(17) }
    lint { abortOnError = true }
}
dependencies {
    implementation(project(":core:domain"))
    implementation(libs.core.ktx)
    implementation(libs.coroutines.android)
    implementation(libs.datastore)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.android.test.runner)
    androidTestImplementation(libs.android.test.junit)
}
