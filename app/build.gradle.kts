import java.util.Properties
import java.io.FileInputStream

// Function to load properties from local.properties
fun getApiKey(projectRootDir: File, propertyName: String): String {
    val properties = Properties()
    val localPropertiesFile = File(projectRootDir, "local.properties")
    if (localPropertiesFile.isFile) {
        try {
            FileInputStream(localPropertiesFile).use { fis ->
                properties.load(fis)
            }
            return properties.getProperty(propertyName, "") // Return empty string if not found
        } catch (e: Exception) {
            println("Warning: Could not read local.properties file: ${e.message}")
        }
    } else {
        println("Warning: local.properties file not found in project root.")
    }
    return "" // Return empty string if file not found or error
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.elderhelper"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.elderhelper"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Retrieve GEMINI API key from local.properties and add to BuildConfig
        val geminiApiKey = getApiKey(rootProject.rootDir, "GEMINI_API_KEY")
        if (geminiApiKey.isEmpty()) {
             println("Warning: GEMINI_API_KEY not found in local.properties. AI features might not work.")
        }
        buildConfigField("String", "GEMINI_API_KEY", "\"${geminiApiKey}\"") // Changed field name
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
             // Ensure release builds also get the GEMINI API key
             val geminiApiKey = getApiKey(rootProject.rootDir, "GEMINI_API_KEY")
             buildConfigField("String", "GEMINI_API_KEY", "\"${geminiApiKey}\"")
        }
        debug {
             // Debug already gets it from defaultConfig
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.appcompat)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // --- REMOVE Old AI Dependencies (Ensure they are fully removed) --- //
    // implementation("com.aallam.openai:openai-client:...")
    // implementation("io.ktor:ktor-client-okhttp:...")

    // --- Ensure Google Generative AI Dependency is Correct --- //
    // Remove any other potential references to older versions if they exist.
    implementation("com.google.ai.client.generativeai:generativeai:0.9.0") // Explicitly using 0.4.0

}