package io.github.n08i40k.extera.gradle

import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.github.n08i40k.extera.gradle.extensions.ExteraPluginExtension
import io.github.n08i40k.extera.gradle.tasks.BuildDexTask
import io.github.n08i40k.extera.gradle.tasks.ProcessTelegramJarTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.Directory
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.attributes
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileTool
import javax.inject.Inject

private const val EXTENSION_NAME = "exteraPlugin"
private const val ANDROID_LIBRARY_PLUGIN = "com.android.library"

private const val TASK_GROUP = "extera plugin"
private const val R8_CONFIGURATION = "exteraR8"

private const val DEFAULT_R8_VERSION = "9.4.17"

private val DEFAULT_CONFLICTING_PACKAGES = listOf(
    "kotlin/",
    "kotlinx/coroutines/",
    "com/android/tools/r8/",
)

@Suppress("UnstableApiUsage")
abstract class ExteraPlugin @Inject constructor(
    private val archives: ArchiveOperations
) : Plugin<Project> {
    override fun apply(target: Project) {
        fun projectFile(path: String): RegularFile =
            target.layout.projectDirectory.file(path)

        fun projectDir(path: String): Directory =
            target.layout.projectDirectory.dir(path)

        val extension = target.extensions.create<ExteraPluginExtension>(EXTENSION_NAME).apply {
            telegramJar.convention(projectFile("libs/Telegram.jar"))
            conflictingPackages.convention(DEFAULT_CONFLICTING_PACKAGES)
            proguardFiles.convention(projectFile("proguard-rules.pro"))
            r8Version.convention(DEFAULT_R8_VERSION)
            outputDir.convention(projectDir("dist"))
        }

        target.pluginManager.withPlugin(ANDROID_LIBRARY_PLUGIN) {
            target.configureExtension(extension)
        }
    }

    private fun Project.configureExtension(extension: ExteraPluginExtension) {
        val r8 = configurations.resolvable(R8_CONFIGURATION)

        // use specified version of r8 in project
        dependencies.addProvider(
            R8_CONFIGURATION,
            extension.r8Version.map { "com.android.tools:r8:$it" })

        val processTelegramJar = tasks.register<ProcessTelegramJarTask>("processTelegramJar") {
            group = TASK_GROUP
            description = "Fix access modifiers for inner classes and strip conflicting packages"

            inputJar.set(extension.telegramJar)
            excludedPrefixes.set(extension.conflictingPackages)
            outputJar.set(layout.buildDirectory.file("intermediates/telegram/Telegram.stripped.jar"))
        }

        // Add Telegram jar to compile classpath.
        dependencies.add("compileOnly", files(processTelegramJar.flatMap { it.outputJar }))

        val buildDexAll = tasks.register("buildDex") {
            group = TASK_GROUP
            description = "Builds the dex of every variant"
        }

        val packageAll = tasks.register("packagePluginJar") {
            group = TASK_GROUP
            description = "Packages every variant into a plugin jar"
        }

        val androidComponents = extensions.getByType<LibraryAndroidComponentsExtension>()

        androidComponents.onVariants { variant ->
            val variantName = variant.name
            val variantTitle = variantName.replaceFirstChar(Char::uppercase)

            val isRelease = variant.buildType == "release"

            val compileJars = classesJarsOf("${variantName}CompileClasspath")
            val runtimeJars = classesJarsOf("${variantName}RuntimeClasspath")

            val shadedJar = tasks.register<ShadowJar>("shade$variantTitle") {
                group = TASK_GROUP
                description = "Assembles the relocated fat jar of the $variantName variant"

                destinationDirectory.set(layout.buildDirectory.dir("intermediates/shaded"))
                archiveFileName.set("classes-$variantName.jar")

                from(
                    tasks.named<KotlinCompileTool>("compile${variantTitle}Kotlin")
                        .flatMap { it.destinationDirectory })
                from(
                    tasks.named<JavaCompile>("compile${variantTitle}JavaWithJavac")
                        .flatMap { it.destinationDirectory })
                from(runtimeJars.map { jars -> jars.map(::zipTree) })

                val shadedPackage = extension.shadedPackage.get()

                for (spec in extension.relocations.get()) {
                    val pkg = spec.pkg + "."

                    relocate(pkg, "$shadedPackage.$pkg") {
                        spec.excludes.get().forEach(::exclude)
                    }
                }
            }

            val buildDex = tasks.register<BuildDexTask>("buildDex$variantTitle") {
                group = TASK_GROUP
                description = "Compiles the $variantName fat jar into dex"

                programJars.from(shadedJar)
                bootClasspathJars.from(androidComponents.sdkComponents.bootClasspath)

                classpathJars.from(
                    compileJars.map { it.minus(runtimeJars.get()) },
                    processTelegramJar.flatMap { it.outputJar },
                )

                proguardFiles.from(extension.proguardFiles)
                r8Classpath.from(r8)

                minSdk.set(extension.minSdk.orElse(variant.minSdk.apiLevel))
                release.set(isRelease)

                mergedClasspathJar.set(layout.buildDirectory.file("intermediates/dex-classpath/$variantName/classpath.jar"))
                outputDir.set(extension.outputDir.map { it.dir("dex/$variantName") })
            }

            val packageJar = tasks.register<Jar>("packagePluginJar$variantTitle") {
                group = TASK_GROUP
                description = "Packages the $variantName dex into a plugin jar"

                destinationDirectory.set(extension.outputDir)
                archiveBaseName.set(extension.pluginId)
                archiveVersion.set(extension.pluginVersion)
                archiveClassifier.set(if (isRelease) "" else variantName)

                isPreserveFileTimestamps = true
                isReproducibleFileOrder = true

                from(buildDex.flatMap { it.outputDir })

                manifest {
                    attributes(
                        "Plugin-Id" to extension.pluginId.get(),
                        "Plugin-Name" to extension.pluginName.get(),
                        "Plugin-Description" to extension.pluginDescription.get(),
                        "Plugin-Author" to extension.pluginAuthor.get(),
                        "Plugin-Version" to extension.pluginVersion.get(),
                        "Plugin-Min-Client-Version" to extension.minClientVersion.get(),
                        "Plugin-Class" to extension.entryClass.get(),
                    )
                }
            }

            buildDexAll.configure { dependsOn(buildDex) }
            packageAll.configure { dependsOn(packageJar) }
        }
    }

    /**
     * Get list of .jar files in provided configuration.
     * artifactView is required for getting .jar files from .aar libraries.
     */
    private fun Project.classesJarsOf(configurationName: String): Provider<FileCollection> =
        configurations.named(configurationName).map { configuration ->
            configuration.incoming
                .artifactView {
                    attributes.attribute(
                        Attribute.of("artifactType", String::class.java),
                        "android-classes-jar"
                    )
                    lenient(true)
                }
                .files
        }
}