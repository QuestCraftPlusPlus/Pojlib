plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    compileSdkVersion = "android-29"
    defaultConfig {
        minSdkVersion(29)
    }

    externalNativeBuild {
        ndkBuild {
            path(file("src/main/jni/Android.mk"))
        }
    }

    buildToolsVersion = "30.0.3"
    ndkVersion = "22.1.7171670"
}

dependencies {
    implementation("com.google.code.gson:gson:2.9.1")
    implementation("commons-io:commons-io:2.11.0")
    implementation("commons-codec:commons-codec:1.15")
}
