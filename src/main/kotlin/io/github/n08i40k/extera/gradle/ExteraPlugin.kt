package io.github.n08i40k.extera.gradle

import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.github.n08i40k.extera.gradle.extensions.ExteraPluginExtension
import io.github.n08i40k.extera.gradle.tasks.BuildDexTask
import io.github.n08i40k.extera.gradle.tasks.ProcessTelegramJarTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import org.gradle.kotlin.dsl.attributes
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileTool
import javax.inject.Inject


@Suppress("UnstableApiUsage")
abstract class ExteraPlugin : Plugin<Project> {
    private companion object {
        const val EXTENSION_NAME = "extera"
        const val TASK_GROUP = "extera"

        const val ANDROID_LIBRARY_PLUGIN = "com.android.library"
        const val R8_CONFIGURATION = "exteraR8"
    }

    override fun apply(target: Project) {
        val extension = target.extensions.create<ExteraPluginExtension>(EXTENSION_NAME)
            .apply {
                dexOutputDir.convention(target.layout.buildDirectory.dir("outputs/dex"))
                jarOutputDir.convention(target.layout.buildDirectory.dir("outputs/jar"))
            }

        target.pluginManager
            .withPlugin(ANDROID_LIBRARY_PLUGIN) { target.configureExtension(extension) }
    }

    private fun Project.configureExtension(extension: ExteraPluginExtension) {
        val r8 = configurations.resolvable(R8_CONFIGURATION)

        // use specified version of r8 in project
        dependencies.addProvider(
            R8_CONFIGURATION,
            extension.r8.version.map { "com.android.tools:r8:$it" })

        val processTelegramJar = tasks.register<ProcessTelegramJarTask>("processTelegramJar") {
            group = TASK_GROUP
            description = "Fix access modifiers for inner classes and strip conflicting packages"

            inputJar.set(extension.telegram.jar)
            excludedPrefixes.set(extension.telegram.conflictingPackages)
            outputJar.set(layout.buildDirectory.file("intermediates/telegram/Telegram.stripped.jar"))
        }

        // Add Telegram jar to compile classpath.
        dependencies.add("compileOnly", files(processTelegramJar.flatMap { it.outputJar }))

        val buildDexAll = tasks.register("buildDex") {
            group = TASK_GROUP
            description = "Builds the dex of every variant"
        }

        project.afterEvaluate {
            if (!extension.manifestConfigured.get())
                return@afterEvaluate

            tasks.register("packagePluginJar") {
                group = TASK_GROUP
                description = "Packages every variant into a plugin jar"
            }
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

                val shadedPackage = extension.shadow.targetPackage.get()

                for (spec in extension.shadow.relocations.get()) {
                    val pkg = spec.pkg + "."

                    relocate(pkg, "$shadedPackage.$pkg") {
                        spec.excludes.get().forEach(::exclude)
                    }
                }
            }

            val buildDexVariant = tasks.register<BuildDexTask>("buildDex$variantTitle") {
                group = TASK_GROUP
                description = "Compiles the $variantName fat jar into dex"

                programJars.from(shadedJar)
                bootClasspathJars.from(androidComponents.sdkComponents.bootClasspath)

                classpathJars.from(
                    compileJars.map { it.minus(runtimeJars.get()) },
                    processTelegramJar.flatMap { it.outputJar },
                )

                proguardFiles.from(extension.r8.proguardFiles)
                r8Classpath.from(r8)

                minSdk.set(extension.r8.minSdk.orElse(variant.minSdk.apiLevel))
                release.set(isRelease)

                mergedClasspathJar.set(layout.buildDirectory.file("intermediates/dex-classpath/$variantName/classpath.jar"))
                outputDir.set(extension.dexOutputDir.map { it.dir(variantName) })
            }

            buildDexAll.configure { dependsOn(buildDexVariant) }

            project.afterEvaluate {
                if (!extension.manifestConfigured.get())
                    return@afterEvaluate

                val packageJarVariant = tasks.register<Jar>("packagePluginJar$variantTitle") {
                    group = TASK_GROUP
                    description = "Packages the $variantName dex into a plugin jar"

                    destinationDirectory.set(extension.jarOutputDir)
                    archiveBaseName.set(extension.manifest.id)
                    archiveVersion.set(extension.manifest.version)
                    archiveClassifier.set(if (isRelease) "" else variantName)

                    isPreserveFileTimestamps = true
                    isReproducibleFileOrder = true

                    from(buildDexVariant.flatMap { it.outputDir })

                    manifest {
                        extension.manifest.apply {
                            attributes(
                                "Plugin-Id" to id.get(),
                                "Plugin-Name" to name.get(),
                                "Plugin-Description" to description.get(),
                                "Plugin-Author" to author.get(),
                                "Plugin-Version" to version.get(),
                                "Plugin-Min-Client-Version" to minClientVersion.get(),
                                "Plugin-Class" to entryClass.get(),
                            )
                        }
                    }
                }

                tasks.getByName("packagePluginJar")
                    .dependsOn(packageJarVariant)
            }
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