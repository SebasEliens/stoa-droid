plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin { jvmToolchain(21) }

dependencies {
    api(project(":core:data"))
    api(project(":core:llm"))
    testImplementation(libs.kotlin.test)
}
