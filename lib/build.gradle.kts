plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    ndkVersion = "25.2.9519653"
    compileSdkVersion = "android-32"
    defaultConfig {
        minSdkVersion(32)
    }

    buildTypes {
        release {
            ndk.abiFilters.add("arm64-v8a")
        }
        debug {
            ndk.abiFilters.add("arm64-v8a")
        }
    }

    externalNativeBuild {
        ndkBuild {
            path(file("src/main/jni/Android.mk"))
        }
    }

    buildToolsVersion = "30.0.3"
}

dependencies {
    // This dependency is exported to consumers, that is to say found on their compile classpath.
    api("org.apache.commons:commons-math3:3.6.1")

    // This dependency is used internally, and not exposed to consumers on their own compile classpath.
    implementation("com.google.guava:guava:31.0.1-jre")
}

dependencies {
    implementation("org.jetbrains:annotations:24.0.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.json:json:20220924")
    implementation("commons-io:commons-io:2.11.0")
    implementation("commons-codec:commons-codec:1.15")
    implementation("androidx.legacy:legacy-support-v4:1.0.0")
    implementation("androidx.preference:preference:1.2.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("androidx.legacy:legacy-preference-v14:1.0.0")
    implementation("com.google.android.material:material:1.8.0")
    implementation("androidx.annotation:annotation:1.6.0")
    implementation("androidx.browser:browser:1.5.0")
    implementation("blank:unity-classes")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
}