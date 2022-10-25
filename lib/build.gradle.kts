plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    compileSdkVersion = "android-31"
    defaultConfig {
        minSdkVersion(28)
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
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("androidx.preference:preference:1.1.1")
    implementation("androidx.appcompat:appcompat:1.4.0")
    implementation("androidx.legacy:legacy-preference-v14:1.0.0")
    implementation("com.google.android.material:material:1.4.0")
    implementation("androidx.annotation:annotation:1.3.0")
    implementation("androidx.browser:browser:1.4.0")

    implementation("androidx.constraintlayout:constraintlayout:2.1.2")
}
