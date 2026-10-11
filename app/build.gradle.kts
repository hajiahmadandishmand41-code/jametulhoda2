plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
}

android {
  namespace = "com.jametulhoda.app"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.jametulhoda.app"
    minSdk = 24
    targetSdk = 36
    versionCode = 2
    versionName = "2.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    // نشانی عمومی سایت (بدون هیچ رمز/کلید محرمانه‌ای).
    buildConfigField("String", "API_BASE_URL", "\"https://jametulhoda.vercel.app\"")
  }

  // Two independent installable products with identical features and one API:
  //   full → com.aistudio.jametulhoda.app        (complete resources)
  //   lite → com.aistudio.jametulhoda.app.lite   (size/memory optimized)
  flavorDimensions += "distribution"
  productFlavors {
    create("full") {
      dimension = "distribution"
      applicationId = "com.aistudio.jametulhoda.app"
      versionNameSuffix = ""
      buildConfigField("boolean", "LIGHTWEIGHT", "false")
      buildConfigField("int", "IMAGE_MEMORY_PERCENT", "25")
      buildConfigField("long", "IMAGE_DISK_CACHE_BYTES", "67108864") // 64MB
      buildConfigField("int", "MAX_BITMAP_DIMEN", "2560")
    }
    create("lite") {
      dimension = "distribution"
      applicationId = "com.aistudio.jametulhoda.app.lite"
      versionNameSuffix = "-lite"
      buildConfigField("boolean", "LIGHTWEIGHT", "true")
      buildConfigField("int", "IMAGE_MEMORY_PERCENT", "12")
      buildConfigField("long", "IMAGE_DISK_CACHE_BYTES", "20971520") // 20MB
      buildConfigField("int", "MAX_BITMAP_DIMEN", "1280")
    }
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio. When the upload keystore is absent
  // (CI/local), release builds fall back to the isolated debug-compatible key so
  // the produced APKs stay installable; production publication must supply
  // KEYSTORE_PATH / STORE_PASSWORD / KEY_PASSWORD.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      val uploadKeystore = file(keystorePath)
      if (uploadKeystore.exists()) {
        storeFile = uploadKeystore
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      } else {
        storeFile = file("${rootDir}/debug.keystore")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Internal test APK; not suitable for Play Store publication.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
  packaging {
    resources {
      excludes += setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*", "META-INF/*.kotlin_module")
    }
  }
}

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.coil.compose)
  implementation(libs.okhttp)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.ui)

  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  // org.json واقعی برای تست‌های JVM (کلاس‌های stub اندروید در unit test استفاده نمی‌شوند)
  testImplementation(libs.org.json)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
