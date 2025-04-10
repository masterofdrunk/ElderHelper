import java.util.Properties
import java.io.FileInputStream

// Function to load local properties safely
fun getLocalProperty(key: String, project: Project): String {
    val properties = Properties()
    val localPropertiesFile = project.rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        properties.load(localPropertiesFile.inputStream())
    }
    return properties.getProperty(key, "")
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
        minSdk = 21
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

        // Use getLocalProperty for all keys
        val geminiApiKey = getLocalProperty("GEMINI_API_KEY", project)
        if (geminiApiKey.isEmpty()) {
             println("Warning: GEMINI_API_KEY not found in local.properties. AI features might not work.")
        }
        buildConfigField("String", "GEMINI_API_KEY", "\"${geminiApiKey}\"")

        buildConfigField("String", "BAIDU_APP_ID", "\"${getLocalProperty("baidu.appid", project)}\"")
        buildConfigField("String", "BAIDU_API_KEY", "\"${getLocalProperty("baidu.apikey", project)}\"")
        buildConfigField("String", "BAIDU_SECRET_KEY", "\"${getLocalProperty("baidu.secretkey", project)}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
             // Ensure release builds also get the GEMINI API key using getLocalProperty
             val geminiApiKey = getLocalProperty("GEMINI_API_KEY", project)
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
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
    // implementation(files("libs/bdasr_V3_20210628_cfe8c44.jar")) // REMOVE direct JAR dependency
    implementation(files("libs/bdasr_V3_20210628_cfe8c44.jar")) // ADD direct file dependency
    implementation("androidx.core:core-ktx:1.10.1") // Keep this dependency for app module

}