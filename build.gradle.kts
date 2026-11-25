// Los plugins se cargan aquí una sola vez para todos los módulos.
// SKIP_ANDROID=1 permite compilar solo la versión de escritorio sin el SDK de Android.
buildscript {
    val skipAndroid = System.getenv("SKIP_ANDROID") != null
    repositories {
        if (!skipAndroid) google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
        classpath("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.0.21")
        classpath("org.jetbrains.compose:compose-gradle-plugin:1.7.3")
        if (!skipAndroid) classpath("com.android.tools.build:gradle:8.7.3")
    }
}
