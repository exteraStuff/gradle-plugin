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
        id = "io.github.exterastuff.gradle.plugin"
        implementationClass = "io.github.exterastuff.gradle.plugin.ExteraPlugin"

        displayName = "exteraGram plugin builder"
        description =
            "Собирает Android-library модуль в плагин exteraGram: уводит зависимости " +
                "в свои пакеты, прогоняет через R8 и упаковывает dex в подписанный jar " +
                "с метаданными плагина."
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
