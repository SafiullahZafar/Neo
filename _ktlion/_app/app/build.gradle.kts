plugins { id("com.android.application") }

android {
    namespace = "com.neo.assistant"
    compileSdk = 37
    buildFeatures { buildConfig = true }
    defaultConfig {
        applicationId = "com.neo.assistant"
        minSdk = 23
        targetSdk = 35
        versionCode = 5
        versionName = "0.5-recovery-preview"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies { testImplementation("junit:junit:4.13.2") }
