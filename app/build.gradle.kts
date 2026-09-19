plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.xingyue.ufitools.monitor"
    buildToolsVersion = "37.0.0"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        applicationId = "com.xingyue.ufitools.monitor"
        // 31（Android 12）是硬性下限：kyant0 backdrop 的 drawBackdrop 内部要求 RenderEffect（API 31）。
        // miuix-blur 自己声明了 minSdk 33，但其 AGSL 能力全部走 isRuntimeShaderSupported() 运行时判断，
        // 因此在清单里用 tools:overrideLibrary 放行，并保证所有着色器调用点都按 33 判断。
        minSdk = 31
        targetSdk = 37
        versionCode = 2026083001
        versionName = "1.1.0"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            storeFile = file("release.keystore")
            storePassword = "ufitools"
            keyAlias = "ufitools"
            keyPassword = "ufitools"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-icons:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-blur:0.9.3")
    implementation("top.yukonga.miuix.kmp:miuix-navigation3-ui:0.9.3")
    implementation("androidx.navigation3:navigation3-runtime:1.1.4")
    implementation("io.github.kyant0:backdrop:1.0.6")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")
}
