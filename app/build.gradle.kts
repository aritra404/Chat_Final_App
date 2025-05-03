plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
    alias(libs.plugins.jetbrains.kotlin.android)
    id("kotlin-kapt")
    id ("kotlin-parcelize")
}

android {
    namespace = "aritra.seal.new_chat"
    compileSdk = 34

    defaultConfig {
        applicationId = "aritra.seal.new_chat"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.firebase.database.ktx)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.swiperefreshlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)

    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-analytics")
    implementation ("com.google.firebase:firebase-auth")
    implementation ("com.google.firebase:firebase-firestore")
    implementation ("com.google.firebase:firebase-storage")
    androidTestImplementation(libs.androidx.espresso.core)

    implementation ("androidx.activity:activity-ktx:1.6.0") // or newer

    implementation ("com.github.bumptech.glide:glide:4.15.1")
    kapt ("com.github.bumptech.glide:compiler:4.15.1")

    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation ("com.github.rahatarmanahmed:circularprogressview:2.5.0")

  //implementation ("com.github.shts:StoriesProgressView:2.0.3")
    implementation ("com.google.firebase:firebase-messaging:23.4.0")

    // Circle ImageView
    implementation ("de.hdodenhof:circleimageview:3.1.0")


}