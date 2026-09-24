plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "ir.wtafkik.mapoverlay"
    compileSdk = 34

    defaultConfig {
        applicationId = "ir.wtafkik.mapoverlay"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    // تصاویر پایه فشرده نشوند (aapt به‌صورت پیش‌فرض بعضی فرمت‌ها را دست نمی‌زند،
    // ولی برای اطمینان صریح مشخص می‌کنیم تا PNGها بدون تغییر باقی بمانند)
    androidResources {
        noCompress += "png"
    }

    // امضای ریلیز: اگر کلید اختصاصی از طریق GitHub Secrets داده شود از آن استفاده می‌شود،
    // وگرنه (مثلاً بیلد محلی) به کلید debug برمی‌گردد تا بیلد خراب نشود.
    val releaseKeystore = System.getenv("KEYSTORE_FILE")?.let { File(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // مبهم‌سازی کد (R8) و حذف منابع بلااستفاده برای سخت‌تر شدن مهندسی معکوس
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (releaseKeystore != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-ktx:1.9.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
