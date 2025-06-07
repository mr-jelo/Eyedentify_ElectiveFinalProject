plugins {
    kotlin("android") version "1.9.10" apply false
    id("com.android.application") version "8.7.2" apply false
    kotlin("kapt") version "1.9.21" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}

buildscript {
    extra["kotlin_version"] = "1.9.25"
    extra["compose_version"] = "1.5.4"
    dependencies {
        classpath("com.android.tools.build:gradle:8.1.4")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:${property("kotlin_version")}")
        classpath("com.google.gms:google-services:4.3.15")
    }
}

allprojects {
}



