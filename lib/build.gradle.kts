plugins {
    // Apply the java-library plugin for API and implementation separation.
    `java-library`
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    // Use JUnit Jupiter for testing.
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.1")

    // This dependency is exported to consumers, that is to say found on their compile classpath.
    api("org.apache.commons:commons-math3:3.6.1")

    // This dependency is used internally, and not exposed to consumers on their own compile classpath.
    implementation("com.google.guava:guava:31.0.1-jre")
}

dependencies {
    implementation("org.jetbrains:annotations:20.1.0")
    implementation("com.google.code.gson:gson:2.9.1")
    implementation("org.json:json:20220924")
    implementation("commons-io:commons-io:2.11.0")
    implementation("commons-codec:commons-codec:1.15")
}

tasks.named<Test>("test") {
    // Use JUnit Platform for unit tests.
    useJUnitPlatform()
}