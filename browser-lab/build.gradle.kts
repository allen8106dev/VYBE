plugins { alias(libs.plugins.android.library) }
android {
    namespace = "com.allen.vybe.browser"
    compileSdk { version = release(37) { minorApiLevel = 1 } }
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation(project(":filter-native"))
    implementation("androidx.webkit:webkit:1.18.0")
    testImplementation(libs.junit)
}
