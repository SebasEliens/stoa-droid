plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin { jvmToolchain(21) }

application { mainClass.set("dev.stoa.eval.EvalKt") }

dependencies {
    implementation(project(":core:rag"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.litertlm.jvm)
    testImplementation(libs.kotlin.test)
}

tasks.named<JavaExec>("run") {
    workingDir = rootDir
    // ./gradlew :tools:eval:run -Preal=/path/to/model.litertlm
    providers.gradleProperty("real").orNull?.let { args("--real", it) }
}
