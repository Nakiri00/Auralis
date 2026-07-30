import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

android {
    namespace = "com.nakiri00.auralis"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nakiri00.auralis"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField(
            "String",
            "RAPIDAPI_KEY",
            "\"${localProperties.getProperty("RAPIDAPI_KEY", "")}\""
        )
        buildConfigField(
            "String",
            "Public_IP",
            "\"${localProperties.getProperty("PublicIP_URL", "https://10.0.2.2:8000")}\""
        )
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

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    applicationVariants.all {
        val variant = this
        variant.outputs.map { it as com.android.build.gradle.internal.api.ApkVariantOutputImpl }.forEach { output ->
            val newName = "Auralis-${variant.name}-${variant.versionName}.apk"
            output.outputFileName = newName
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.9.4")
    implementation("androidx.lifecycle:lifecycle-livedata:2.9.4")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("be.tarsos.dsp:core:2.5")
    implementation("be.tarsos.dsp:jvm:2.5")
    implementation(platform("com.google.firebase:firebase-bom:34.7.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.github.LeffelMania:android-midi-lib:master-SNAPSHOT")
}
