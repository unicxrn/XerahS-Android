import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.xerahs.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xerahs.android"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "0.7.0"
    }

    val releaseSigning: com.android.build.api.dsl.ApkSigningConfig? = run {
        val propsFile = rootProject.file("keystore.properties")
        val keys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
        val envNames = mapOf(
            "storeFile" to "XERAHS_KEYSTORE",
            "storePassword" to "XERAHS_STORE_PASSWORD",
            "keyAlias" to "XERAHS_KEY_ALIAS",
            "keyPassword" to "XERAHS_KEY_PASSWORD"
        )
        val values: Map<String, String?>? = when {
            propsFile.exists() -> {
                val props = Properties()
                propsFile.inputStream().use { props.load(it) }
                keys.associateWith { k -> props.getProperty(k)?.trim()?.takeIf { it.isNotEmpty() } }
            }
            envNames.values.any { !System.getenv(it).isNullOrBlank() } ->
                keys.associateWith { k -> System.getenv(envNames.getValue(k))?.trim()?.takeIf { it.isNotEmpty() } }
            else -> null
        }
        if (values == null) {
            null
        } else {
            val missing = keys.filter { values[it] == null }
            if (missing.isNotEmpty()) {
                val source = if (propsFile.exists()) "keystore.properties" else "XERAHS_* env"
                throw GradleException("XerahS: incomplete release signing in $source, missing: ${missing.joinToString()}")
            }
            val rawPath = values.getValue("storeFile")!!
            val path = if (rawPath == "~" || rawPath.startsWith("~/")) {
                System.getProperty("user.home") + rawPath.removePrefix("~")
            } else rawPath
            val ks = rootProject.file(path)
            if (!ks.exists()) throw GradleException("XerahS: release keystore not found: ${ks.absolutePath}")
            signingConfigs.create("release") {
                storeFile = ks
                storePassword = values.getValue("storePassword")
                keyAlias = values.getValue("keyAlias")
                keyPassword = values.getValue("keyPassword")
            }
        }
    }

    if (releaseSigning == null) {
        gradle.taskGraph.whenReady {
            if (allTasks.any { it.path.startsWith(":app:") && it.name.contains("Release") }) {
                logger.warn("XerahS: release build is unsigned (no keystore.properties or XERAHS_* env)")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = releaseSigning
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
