plugins {
    `kotlin-dsl`
    id("com.gradle.plugin-publish") version "2.2.1"
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

kotlin {
    explicitApi()
}

configurations.configureEach {
    resolutionStrategy.force("org.jetbrains.kotlin:kotlin-stdlib:2.2.0")
}

configurations.compileClasspath {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-metadata-jvm")
}

gradlePlugin {
    website = "https://github.com/exteraStuff/gradle-plugin"
    vcsUrl = "https://github.com/exteraStuff/gradle-plugin.git"

    plugins.create("extera") {
        id = "io.github.exterastuff.plugin"
        implementationClass = "io.github.exterastuff.plugin.ExteraPlugin"

        displayName = "exteraGram plugin builder"
        description =
            "Builds an Android library module into an exteraGram plugin: relocates " +
                "dependencies into its own packages, runs everything through R8 and packs " +
                "the dex into a signed jar with plugin metadata."
        tags = listOf("android", "exteragram", "telegram", "r8", "dex")
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            licenses {
                license {
                    name = "MIT License"
                    url = "https://github.com/exteraStuff/gradle-plugin/blob/master/LICENSE"
                }
            }
        }
    }
}
