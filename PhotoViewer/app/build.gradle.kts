plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.photoviewer"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.photoviewer"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val djangoToken = project.findProperty("DJANGO_API_TOKEN")
            ?.toString() ?: ""

        buildConfigField(
            "String",
            "DJANGO_API_TOKEN",
            "\"$djangoToken\""
        )
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}