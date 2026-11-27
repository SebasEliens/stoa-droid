plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(21) }

dependencies {
    api(libs.kotlinx.coroutines.core)
    // shortcut: compiled against the JVM jar, whose classes match the Android AAR 1:1 (checked for 0.20.0).
    // Each consumer adds the runtime it needs: litertlm-android in :app, litertlm-jvm in :tools:eval.
    compileOnly(libs.litertlm.jvm)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.core)
}
