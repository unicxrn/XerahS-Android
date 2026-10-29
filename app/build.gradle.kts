import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.xerahs.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.xerahs.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 7
        versionName = "0.7.0"
    }

    val releaseSigning: Any? = run {
        val propsFile = rootProject.file("keystore.properties")
        val props = Properties()
        if (propsFile.exists()) propsFile.inputStream().use { props.load(it) }
        val ksPath = props.getProperty("storeFile") ?: System.getenv("XERAHS_KEYSTORE")
        val storePw = props.getProperty("storePassword") ?: System.getenv("XERAHS_STORE_PASSWORD")
        val alias = props.getProperty("keyAlias") ?: System.getenv("XERAHS_KEY_ALIAS")
        val keyPw = props.getProperty("keyPassword") ?: System.getenv("XERAHS_KEY_PASSWORD")
        if (ksPath != null && storePw != null && alias != null && keyPw != null) {
            signingConfigs.create("release") {
                storeFile = file(ksPath)
                storePassword = storePw
                keyAlias = alias
                keyPassword = keyPw
            }
        } else {
            logger.warn("XerahS: release build is unsigned (no keystore.properties or XERAHS_* env)")
            null
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = releaseSigning as com.android.build.api.dsl.ApkSigningConfig?
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.compose.compiler.get()
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":feature:capture"))
    implementation(project(":feature:annotation"))
    implementation(project(":feature:upload"))
    implementation(project(":feature:history"))
    implementation(project(":feature:s3explorer"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:tools"))

    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.foundation)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.workmanager)
    implementation(libs.hilt.work)
    ksp(libs.hilt.work.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)

    implementation(libs.biometric)
    implementation(libs.coil.compose)

    debugImplementation(libs.compose.ui.tooling)
}
