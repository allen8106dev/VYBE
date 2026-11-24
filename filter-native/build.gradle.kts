plugins { alias(libs.plugins.android.library) }
android {
    namespace = "com.allen.vybe.filter"
    compileSdk { version = release(37) { minorApiLevel = 1 } }
    defaultConfig { minSdk = 29 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
}
dependencies {
    implementation("io.github.mlm-games:adblock:0.1.3") { exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib") }
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.2.10")
}
