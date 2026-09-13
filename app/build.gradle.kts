plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.wasay.weddingphotosync"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.wasay.weddingphotosync"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        multiDexEnabled = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }
}

dependencies {

    // For foreground service (already have this)
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("androidx.cardview:cardview:1.0.0")
    // For device registration (add this)
    implementation("com.google.android.gms:play-services-base:18.5.0")

    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("androidx.multidex:multidex:2.0.1")

    // Updated Firebase BoM to the latest stable version
    implementation(platform("com.google.firebase:firebase-bom:33.9.0"))
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx")

    // Explicit gRPC dependencies - this is the key fix!
    implementation("io.grpc:grpc-okhttp:1.68.1")
    implementation("io.grpc:grpc-stub:1.68.1")
    implementation("io.grpc:grpc-protobuf-lite:1.68.1")

    // If you're using Android 14+, you might also need this
    implementation("io.grpc:grpc-core:1.68.1")

    // For Android 14+ compatibility
    implementation("org.apache.tomcat:annotations-api:6.0.53")

    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Google API Client and Drive
    implementation("com.google.android.gms:play-services-auth:21.2.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")

    // Use activity-ktx and appcompat
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
}