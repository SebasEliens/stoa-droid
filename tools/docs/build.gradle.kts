plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

kotlin { jvmToolchain(21) }

application { mainClass.set("dev.stoa.docs.DocsKt") }

dependencies {
    implementation(libs.kaml)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlin.test)
}

// ./gradlew :tools:docs:run builds build/site/ from the allowlisted sources (spec 002).
tasks.named<JavaExec>("run") { workingDir = rootDir }

// Some tests build the site from the real repo files, so a change to any source must rerun them.
tasks.withType<Test>().configureEach {
    systemProperty("stoa.root", rootDir.path)
    inputs.files(
        rootProject.fileTree("docs"),
        rootProject.fileTree("specs"),
        rootProject.fileTree("metrics"),
        rootProject.fileTree(".github"),
        rootProject.file("app/permissions.yaml"),
    )
}
