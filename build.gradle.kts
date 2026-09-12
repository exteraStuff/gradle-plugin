plugins {
    `kotlin-dsl`
}

group = "io.github.exterastuff"

version = "0.1.0"

repositories {
    google()
    mavenCentral()
}

dependencies {
    compileOnly("com.android.tools.build:gradle-api:9.2.1")
    compileOnly("com.android.tools:r8:9.4.17")

    implementation("com.gradleup.shadow:shadow-gradle-plugin:9.6.1")
    implementation("org.ow2.asm:asm:9.8")
}

configurations.configureEach {
    resolutionStrategy.force("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
}

configurations.compileClasspath {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-metadata-jvm")
}

gradlePlugin {
    plugins.create("extera") {
        id = "io.github.exterastuff.gradle.plugin"
        implementationClass = "io.github.exterastuff.gradle.plugin.ExteraPlugin"
    }
}
